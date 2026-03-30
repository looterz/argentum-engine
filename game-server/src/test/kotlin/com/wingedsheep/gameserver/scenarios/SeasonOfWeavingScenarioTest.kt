package com.wingedsheep.gameserver.scenarios

import com.wingedsheep.engine.core.ChooseOptionDecision
import com.wingedsheep.engine.core.OptionChosenResponse
import com.wingedsheep.engine.core.SelectCardsDecision
import com.wingedsheep.gameserver.ScenarioTestBase
import com.wingedsheep.sdk.core.Phase
import com.wingedsheep.sdk.core.Step
import io.kotest.matchers.ints.shouldBeGreaterThan
import io.kotest.matchers.shouldBe

/**
 * Scenario tests for Season of Weaving (BLB #68).
 *
 * Season of Weaving: {4}{U}{U} Sorcery
 * Choose up to five {P} worth of modes. You may choose the same mode more than once.
 * {P} — Draw a card.
 * {P}{P} — Choose an artifact or creature you control. Create a token that's a copy of it.
 * {P}{P}{P} — Return each nonland, nontoken permanent to its owner's hand.
 */
class SeasonOfWeavingScenarioTest : ScenarioTestBase() {

    init {
        context("Season of Weaving budget modal") {

            test("mode 1 - draw a card") {
                val game = scenario()
                    .withPlayers("Caster", "Opponent")
                    .withCardInHand(1, "Season of Weaving")
                    .withLandsOnBattlefield(1, "Island", 6)
                    .withCardInLibrary(1, "Island")
                    .withCardInLibrary(1, "Island")
                    .withCardInLibrary(1, "Island")
                    .withCardInLibrary(1, "Island")
                    .withCardInLibrary(1, "Island")
                    .withCardInLibrary(1, "Island") // enough cards to draw 5
                    .withCardInLibrary(2, "Island")
                    .withActivePlayer(1)
                    .inPhase(Phase.PRECOMBAT_MAIN, Step.PRECOMBAT_MAIN)
                    .build()

                val handSizeBefore = game.handSize(1)

                game.castSpell(1, "Season of Weaving")
                game.resolveStack()

                // Choose draw x5 (5 pawprints)
                val modeDecision = game.getPendingDecision() as ChooseOptionDecision
                val draw5Index = modeDecision.options.indexOfFirst {
                    it.contains("Draw a card") && it.contains("x5")
                }
                draw5Index shouldBeGreaterThan -1
                game.submitDecision(OptionChosenResponse(modeDecision.id, draw5Index))

                // Should have drawn 5 cards (minus the one we cast)
                game.handSize(1) shouldBe (handSizeBefore - 1 + 5)
            }

            test("mode 2 - create token copy of chosen creature with multiple options") {
                val game = scenario()
                    .withPlayers("Caster", "Opponent")
                    .withCardInHand(1, "Season of Weaving")
                    .withLandsOnBattlefield(1, "Island", 6)
                    .withCardOnBattlefield(1, "Hill Giant")
                    .withCardOnBattlefield(1, "Grizzly Bears")
                    .withCardInLibrary(1, "Island")
                    .withCardInLibrary(2, "Island")
                    .withActivePlayer(1)
                    .inPhase(Phase.PRECOMBAT_MAIN, Step.PRECOMBAT_MAIN)
                    .build()

                game.castSpell(1, "Season of Weaving")
                game.resolveStack()

                // Choose copy mode only (2 pawprints)
                val modeDecision = game.getPendingDecision() as ChooseOptionDecision
                val copyOnlyIndex = modeDecision.options.indexOfFirst {
                    it.contains("copy") && !it.contains("Draw") && !it.contains("Return")
                }
                copyOnlyIndex shouldBeGreaterThan -1
                game.submitDecision(OptionChosenResponse(modeDecision.id, copyOnlyIndex))

                // Multiple creatures → must choose
                val selectDecision = game.getPendingDecision() as SelectCardsDecision
                // Find and choose the Hill Giant
                val hillGiantId = selectDecision.options.find { entityId ->
                    game.state.getEntity(entityId)?.get<com.wingedsheep.engine.state.components.identity.CardComponent>()?.name == "Hill Giant"
                }!!
                game.selectCards(listOf(hillGiantId))

                // Should now have 2 Hill Giants on the battlefield
                game.findAllPermanents("Hill Giant").size shouldBe 2
                game.findAllPermanents("Grizzly Bears").size shouldBe 1
            }

            test("mode 2 - auto-selects when only one valid target") {
                val game = scenario()
                    .withPlayers("Caster", "Opponent")
                    .withCardInHand(1, "Season of Weaving")
                    .withLandsOnBattlefield(1, "Island", 6)
                    .withCardOnBattlefield(1, "Grizzly Bears")
                    .withCardInLibrary(1, "Island")
                    .withCardInLibrary(2, "Island")
                    .withActivePlayer(1)
                    .inPhase(Phase.PRECOMBAT_MAIN, Step.PRECOMBAT_MAIN)
                    .build()

                game.castSpell(1, "Season of Weaving")
                game.resolveStack()

                // Choose copy mode only (2 pawprints)
                val modeDecision = game.getPendingDecision() as ChooseOptionDecision
                val copyOnlyIndex = modeDecision.options.indexOfFirst {
                    it.contains("copy") && !it.contains("Draw") && !it.contains("Return")
                }
                copyOnlyIndex shouldBeGreaterThan -1
                game.submitDecision(OptionChosenResponse(modeDecision.id, copyOnlyIndex))

                // Only one creature → auto-selects
                game.findAllPermanents("Grizzly Bears").size shouldBe 2
            }

            test("mode 3 - return all nonland nontoken permanents to hand") {
                val game = scenario()
                    .withPlayers("Caster", "Opponent")
                    .withCardInHand(1, "Season of Weaving")
                    .withLandsOnBattlefield(1, "Island", 6)
                    .withCardOnBattlefield(1, "Grizzly Bears")
                    .withCardOnBattlefield(2, "Hill Giant")
                    .withCardInLibrary(1, "Island")
                    .withCardInLibrary(2, "Island")
                    .withActivePlayer(1)
                    .inPhase(Phase.PRECOMBAT_MAIN, Step.PRECOMBAT_MAIN)
                    .build()

                game.castSpell(1, "Season of Weaving")
                game.resolveStack()

                // Choose bounce all mode (3 pawprints)
                val modeDecision = game.getPendingDecision() as ChooseOptionDecision
                val bounceIndex = modeDecision.options.indexOfFirst {
                    it.contains("Return") && !it.contains("Draw") && !it.contains("copy")
                }
                bounceIndex shouldBeGreaterThan -1
                game.submitDecision(OptionChosenResponse(modeDecision.id, bounceIndex))

                // All nonland, nontoken permanents returned to hand
                game.isOnBattlefield("Grizzly Bears") shouldBe false
                game.isOnBattlefield("Hill Giant") shouldBe false
                game.isInHand(1, "Grizzly Bears") shouldBe true
                game.isInHand(2, "Hill Giant") shouldBe true

                // Lands should still be on the battlefield
                game.findAllPermanents("Island").size shouldBe 6
            }

            test("choosing no modes does nothing") {
                val game = scenario()
                    .withPlayers("Caster", "Opponent")
                    .withCardInHand(1, "Season of Weaving")
                    .withLandsOnBattlefield(1, "Island", 6)
                    .withCardOnBattlefield(1, "Grizzly Bears")
                    .withCardInLibrary(1, "Island")
                    .withCardInLibrary(2, "Island")
                    .withActivePlayer(1)
                    .inPhase(Phase.PRECOMBAT_MAIN, Step.PRECOMBAT_MAIN)
                    .build()

                game.castSpell(1, "Season of Weaving")
                game.resolveStack()

                val modeDecision = game.getPendingDecision() as ChooseOptionDecision
                val emptyIndex = modeDecision.options.indexOfFirst { it.contains("No modes") }
                emptyIndex shouldBeGreaterThan -1
                game.submitDecision(OptionChosenResponse(modeDecision.id, emptyIndex))

                game.isOnBattlefield("Grizzly Bears") shouldBe true
                game.getLifeTotal(1) shouldBe 20
            }
        }
    }
}
