package com.wingedsheep.gameserver.scenarios

import com.wingedsheep.engine.state.PendingSpellCopy
import com.wingedsheep.gameserver.ScenarioTestBase
import com.wingedsheep.sdk.core.Phase
import com.wingedsheep.sdk.core.Step
import com.wingedsheep.sdk.model.EntityId
import io.kotest.assertions.withClue
import io.kotest.matchers.shouldBe

/**
 * Scenario tests for The Mirari Conjecture Chapter III.
 *
 * Chapter III: "Until end of turn, whenever you cast an instant or sorcery spell, copy it.
 * You may choose new targets for the copy."
 *
 * This creates a persistent PendingSpellCopy that copies every instant/sorcery cast
 * for the rest of the turn, unlike Howl of the Horde which only copies the next one.
 */
class TheMirariConjectureScenarioTest : ScenarioTestBase() {

    init {
        context("The Mirari Conjecture - Chapter III spell copying") {

            test("copies a single spell cast after Chapter III resolves") {
                val game = scenario()
                    .withPlayers("Player1", "Player2")
                    .withCardInHand(1, "Shock")
                    .withLandsOnBattlefield(1, "Mountain", 1)
                    .withActivePlayer(1)
                    .inPhase(Phase.PRECOMBAT_MAIN, Step.PRECOMBAT_MAIN)
                    .build()

                // Simulate Chapter III having resolved by injecting a persistent PendingSpellCopy
                val player1Id = game.state.turnOrder[0]
                game.state = game.state.copy(
                    pendingSpellCopies = listOf(
                        PendingSpellCopy(
                            controllerId = player1Id,
                            copies = 1,
                            sourceId = EntityId.generate(),
                            sourceName = "The Mirari Conjecture",
                            persistent = true
                        )
                    )
                )

                // Cast Shock targeting Player 2
                game.castSpellTargetingPlayer(1, "Shock", 2)

                // Copy trigger resolves → asks for target for the copy
                game.passPriority() // P1
                game.passPriority() // P2 → copy trigger resolves → target decision
                game.selectTargets(listOf(game.player2Id))

                // Resolve remaining stack (copy + original)
                game.resolveStack()

                withClue("Player 2 should have taken 4 damage (2 from Shock + 2 from copy)") {
                    game.getLifeTotal(2) shouldBe 16
                }
            }

            test("copies every spell cast - not just the first one") {
                val game = scenario()
                    .withPlayers("Player1", "Player2")
                    .withCardInHand(1, "Shock")
                    .withCardInHand(1, "Shock")
                    .withLandsOnBattlefield(1, "Mountain", 2)
                    .withActivePlayer(1)
                    .inPhase(Phase.PRECOMBAT_MAIN, Step.PRECOMBAT_MAIN)
                    .build()

                val player1Id = game.state.turnOrder[0]
                game.state = game.state.copy(
                    pendingSpellCopies = listOf(
                        PendingSpellCopy(
                            controllerId = player1Id,
                            copies = 1,
                            sourceId = EntityId.generate(),
                            sourceName = "The Mirari Conjecture",
                            persistent = true
                        )
                    )
                )

                // Cast first Shock — gets copied
                game.castSpellTargetingPlayer(1, "Shock", 2)
                game.passPriority()
                game.passPriority()
                game.selectTargets(listOf(game.player2Id))
                game.resolveStack()

                withClue("Player 2 should have taken 4 damage (Shock + copy)") {
                    game.getLifeTotal(2) shouldBe 16
                }

                // Cast second Shock — also gets copied (persistent!)
                game.castSpellTargetingPlayer(1, "Shock", 2)
                game.passPriority()
                game.passPriority()
                game.selectTargets(listOf(game.player2Id))
                game.resolveStack()

                withClue("Player 2 should have taken 8 total damage (2 Shocks + 2 copies)") {
                    game.getLifeTotal(2) shouldBe 12
                }
            }

            test("does not copy opponent's spells") {
                val game = scenario()
                    .withPlayers("Player1", "Player2")
                    .withCardInHand(2, "Shock")
                    .withLandsOnBattlefield(2, "Mountain", 1)
                    .withActivePlayer(2)
                    .inPhase(Phase.PRECOMBAT_MAIN, Step.PRECOMBAT_MAIN)
                    .build()

                // The persistent copy belongs to Player 1, not Player 2
                val player1Id = game.state.turnOrder[0]
                game.state = game.state.copy(
                    pendingSpellCopies = listOf(
                        PendingSpellCopy(
                            controllerId = player1Id,
                            copies = 1,
                            sourceId = EntityId.generate(),
                            sourceName = "The Mirari Conjecture",
                            persistent = true
                        )
                    )
                )

                // Player 2 casts Shock targeting Player 1 — should NOT be copied
                game.castSpellTargetingPlayer(2, "Shock", 1)
                game.resolveStack()

                withClue("Player 1 should take only 2 damage (no copy for opponent's spell)") {
                    game.getLifeTotal(1) shouldBe 18
                }
            }

            test("persistent copy expires at end of turn") {
                val game = scenario()
                    .withPlayers("Player1", "Player2")
                    .withActivePlayer(1)
                    .inPhase(Phase.PRECOMBAT_MAIN, Step.PRECOMBAT_MAIN)
                    .build()

                val player1Id = game.state.turnOrder[0]
                game.state = game.state.copy(
                    pendingSpellCopies = listOf(
                        PendingSpellCopy(
                            controllerId = player1Id,
                            copies = 1,
                            sourceId = EntityId.generate(),
                            sourceName = "The Mirari Conjecture",
                            persistent = true
                        )
                    )
                )

                withClue("Pending spell copies should exist before end of turn") {
                    game.state.pendingSpellCopies.size shouldBe 1
                }

                // Pass through end of turn to next turn
                game.passUntilPhase(Phase.ENDING, Step.END)
                game.passUntilPhase(Phase.PRECOMBAT_MAIN, Step.PRECOMBAT_MAIN)

                withClue("Pending spell copies should be cleared at end of turn") {
                    game.state.pendingSpellCopies shouldBe emptyList()
                }
            }
        }
    }
}
