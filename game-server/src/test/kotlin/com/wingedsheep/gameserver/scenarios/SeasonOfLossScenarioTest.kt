package com.wingedsheep.gameserver.scenarios

import com.wingedsheep.engine.core.BudgetModalDecision
import com.wingedsheep.engine.core.BudgetModalResponse
import com.wingedsheep.engine.core.SelectCardsDecision
import com.wingedsheep.gameserver.ScenarioTestBase
import com.wingedsheep.sdk.core.Phase
import com.wingedsheep.sdk.core.Step
import io.kotest.matchers.shouldBe

/**
 * Scenario tests for Season of Loss (BLB #112).
 *
 * Season of Loss: {3}{B}{B} Sorcery
 * Choose up to five {P} worth of modes. You may choose the same mode more than once.
 * {P} — Each player sacrifices a creature of their choice.
 * {P}{P} — Draw a card for each creature that died under your control this turn.
 * {P}{P}{P} — Each opponent loses X life, where X is the number of creature cards in your graveyard.
 */
class SeasonOfLossScenarioTest : ScenarioTestBase() {

    private fun submitBudgetModes(game: TestGame, vararg modeIndices: Int) {
        val decision = game.getPendingDecision() as BudgetModalDecision
        game.submitDecision(BudgetModalResponse(decision.id, modeIndices.toList()))
    }

    init {
        context("Season of Loss budget modal") {

            test("mode 1 - each player sacrifices a creature (auto-sacrifice with 1 creature each)") {
                val game = scenario()
                    .withPlayers("Caster", "Opponent")
                    .withCardInHand(1, "Season of Loss")
                    .withLandsOnBattlefield(1, "Swamp", 5)
                    .withCardOnBattlefield(1, "Grizzly Bears")
                    .withCardOnBattlefield(2, "Hill Giant")
                    .withActivePlayer(1)
                    .inPhase(Phase.PRECOMBAT_MAIN, Step.PRECOMBAT_MAIN)
                    .build()

                game.castSpell(1, "Season of Loss")
                game.resolveStack()

                // Select sacrifice mode once (mode 0, cost 1)
                submitBudgetModes(game, 0)

                game.isOnBattlefield("Grizzly Bears") shouldBe false
                game.isOnBattlefield("Hill Giant") shouldBe false
                game.isInGraveyard(1, "Grizzly Bears") shouldBe true
                game.isInGraveyard(2, "Hill Giant") shouldBe true
            }

            test("mode 1 - player chooses which creature to sacrifice when multiple") {
                val game = scenario()
                    .withPlayers("Caster", "Opponent")
                    .withCardInHand(1, "Season of Loss")
                    .withLandsOnBattlefield(1, "Swamp", 5)
                    .withCardOnBattlefield(1, "Grizzly Bears")
                    .withCardOnBattlefield(1, "Hill Giant")
                    .withCardOnBattlefield(2, "Hill Giant")
                    .withActivePlayer(1)
                    .inPhase(Phase.PRECOMBAT_MAIN, Step.PRECOMBAT_MAIN)
                    .build()

                game.castSpell(1, "Season of Loss")
                game.resolveStack()

                // Select sacrifice mode once
                submitBudgetModes(game, 0)

                // Player 1 has 2 creatures → must choose
                val p1SacDecision = game.getPendingDecision() as SelectCardsDecision
                game.selectCards(listOf(p1SacDecision.options.first()))

                // Player 2 has 1 creature → auto-sacrifice
                game.isInGraveyard(2, "Hill Giant") shouldBe true
            }

            test("mode 3 - opponent loses life equal to creature cards in graveyard") {
                val game = scenario()
                    .withPlayers("Caster", "Opponent")
                    .withCardInHand(1, "Season of Loss")
                    .withLandsOnBattlefield(1, "Swamp", 5)
                    .withCardInGraveyard(1, "Grizzly Bears")
                    .withCardInGraveyard(1, "Hill Giant")
                    .withCardInGraveyard(1, "Grizzly Bears")
                    .withActivePlayer(1)
                    .inPhase(Phase.PRECOMBAT_MAIN, Step.PRECOMBAT_MAIN)
                    .build()

                game.castSpell(1, "Season of Loss")
                game.resolveStack()

                // Select life loss mode (mode 2, cost 3)
                submitBudgetModes(game, 2)

                game.getLifeTotal(2) shouldBe 17
            }

            test("choosing no modes does nothing") {
                val game = scenario()
                    .withPlayers("Caster", "Opponent")
                    .withCardInHand(1, "Season of Loss")
                    .withLandsOnBattlefield(1, "Swamp", 5)
                    .withCardOnBattlefield(1, "Grizzly Bears")
                    .withActivePlayer(1)
                    .inPhase(Phase.PRECOMBAT_MAIN, Step.PRECOMBAT_MAIN)
                    .build()

                game.castSpell(1, "Season of Loss")
                game.resolveStack()

                submitBudgetModes(game)

                game.isOnBattlefield("Grizzly Bears") shouldBe true
                game.getLifeTotal(1) shouldBe 20
                game.getLifeTotal(2) shouldBe 20
            }

            test("combined modes - sacrifice then draw for dead creatures") {
                val game = scenario()
                    .withPlayers("Caster", "Opponent")
                    .withCardInHand(1, "Season of Loss")
                    .withLandsOnBattlefield(1, "Swamp", 5)
                    .withCardOnBattlefield(1, "Grizzly Bears")
                    .withCardOnBattlefield(2, "Hill Giant")
                    .withCardInLibrary(1, "Swamp")
                    .withCardInLibrary(2, "Swamp")
                    .withActivePlayer(1)
                    .inPhase(Phase.PRECOMBAT_MAIN, Step.PRECOMBAT_MAIN)
                    .build()

                val handSizeBefore = game.handSize(1)

                game.castSpell(1, "Season of Loss")
                game.resolveStack()

                // Select sacrifice (mode 0, cost 1) + draw (mode 1, cost 2) = 3 pawprints
                // Executed in printed order: sacrifice first, then draw
                submitBudgetModes(game, 0, 1)

                // Each player has 1 creature → auto-sacrifice
                // After sacrifice, mode 2 draws cards: 1 creature died under P1 → draw 1
                game.handSize(1) shouldBe handSizeBefore
            }
        }
    }
}
