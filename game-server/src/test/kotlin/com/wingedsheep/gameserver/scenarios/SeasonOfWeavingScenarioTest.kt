package com.wingedsheep.gameserver.scenarios

import com.wingedsheep.engine.core.BudgetModalDecision
import com.wingedsheep.engine.core.BudgetModalResponse
import com.wingedsheep.engine.core.SelectCardsDecision
import com.wingedsheep.gameserver.ScenarioTestBase
import com.wingedsheep.sdk.core.Phase
import com.wingedsheep.sdk.core.Step
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

    /** Submit a budget modal response with the given mode indices (can repeat). */
    private fun submitBudgetModes(game: TestGame, vararg modeIndices: Int) {
        val decision = game.getPendingDecision() as BudgetModalDecision
        game.submitDecision(BudgetModalResponse(decision.id, modeIndices.toList()))
    }

    init {
        context("Season of Weaving budget modal") {

            test("draw x5 by selecting draw mode 5 times") {
                val game = scenario()
                    .withPlayers("Caster", "Opponent")
                    .withCardInHand(1, "Season of Weaving")
                    .withLandsOnBattlefield(1, "Island", 6)
                    .withCardInLibrary(1, "Island")
                    .withCardInLibrary(1, "Island")
                    .withCardInLibrary(1, "Island")
                    .withCardInLibrary(1, "Island")
                    .withCardInLibrary(1, "Island")
                    .withCardInLibrary(1, "Island")
                    .withCardInLibrary(2, "Island")
                    .withActivePlayer(1)
                    .inPhase(Phase.PRECOMBAT_MAIN, Step.PRECOMBAT_MAIN)
                    .build()

                val handSizeBefore = game.handSize(1)

                game.castSpell(1, "Season of Weaving")
                game.resolveStack()

                // Select draw (mode 0) five times: 5 × 1 = 5 pawprints
                submitBudgetModes(game, 0, 0, 0, 0, 0)

                game.handSize(1) shouldBe (handSizeBefore - 1 + 5)
            }

            test("create token copy of chosen creature") {
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

                // Select copy mode (mode 1, cost 2)
                submitBudgetModes(game, 1)

                // Multiple creatures → must choose
                val selectDecision = game.getPendingDecision() as SelectCardsDecision
                val hillGiantId = selectDecision.options.find { entityId ->
                    game.state.getEntity(entityId)?.get<com.wingedsheep.engine.state.components.identity.CardComponent>()?.name == "Hill Giant"
                }!!
                game.selectCards(listOf(hillGiantId))

                game.findAllPermanents("Hill Giant").size shouldBe 2
                game.findAllPermanents("Grizzly Bears").size shouldBe 1
            }

            test("auto-selects when only one valid target for copy") {
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

                // Select copy mode only
                submitBudgetModes(game, 1)

                // Only one creature → auto-selects
                game.findAllPermanents("Grizzly Bears").size shouldBe 2
            }

            test("return all nonland nontoken permanents to hand") {
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

                // Select bounce mode (mode 2, cost 3)
                submitBudgetModes(game, 2)

                game.isOnBattlefield("Grizzly Bears") shouldBe false
                game.isOnBattlefield("Hill Giant") shouldBe false
                game.isInHand(1, "Grizzly Bears") shouldBe true
                game.isInHand(2, "Hill Giant") shouldBe true
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

                // Submit empty selection
                submitBudgetModes(game)

                game.isOnBattlefield("Grizzly Bears") shouldBe true
                game.getLifeTotal(1) shouldBe 20
            }

            test("copy then bounce — token survives because it is a token") {
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

                // Select copy (mode 1, cost 2) + bounce (mode 2, cost 3) = 5 pawprints
                // Executed in printed order: copy first, then bounce
                submitBudgetModes(game, 1, 2)

                // Copy auto-selects Grizzly Bears (only creature P1 controls)
                // Token copy stays — it IS a token, so "nontoken" filter skips it
                game.findAllPermanents("Grizzly Bears").size shouldBe 1 // token copy survives
                game.isInHand(1, "Grizzly Bears") shouldBe true // original bounced
                game.isOnBattlefield("Hill Giant") shouldBe false // opponent's creature bounced
                game.findAllPermanents("Island").size shouldBe 6 // lands stay
            }

            test("budget modal decision has correct mode info") {
                val game = scenario()
                    .withPlayers("Caster", "Opponent")
                    .withCardInHand(1, "Season of Weaving")
                    .withLandsOnBattlefield(1, "Island", 6)
                    .withCardInLibrary(1, "Island")
                    .withCardInLibrary(2, "Island")
                    .withActivePlayer(1)
                    .inPhase(Phase.PRECOMBAT_MAIN, Step.PRECOMBAT_MAIN)
                    .build()

                game.castSpell(1, "Season of Weaving")
                game.resolveStack()

                val decision = game.getPendingDecision() as BudgetModalDecision
                decision.budget shouldBe 5
                decision.modes.size shouldBe 3
                decision.modes[0].cost shouldBe 1
                decision.modes[1].cost shouldBe 2
                decision.modes[2].cost shouldBe 3

                // Clean up by submitting empty
                game.submitDecision(BudgetModalResponse(decision.id, emptyList()))
            }
        }
    }
}
