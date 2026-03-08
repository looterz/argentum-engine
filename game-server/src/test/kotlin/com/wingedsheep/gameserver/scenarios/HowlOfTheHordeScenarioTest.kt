package com.wingedsheep.gameserver.scenarios

import com.wingedsheep.gameserver.ScenarioTestBase
import com.wingedsheep.sdk.core.Phase
import com.wingedsheep.sdk.core.Step
import io.kotest.assertions.withClue
import io.kotest.matchers.shouldBe

/**
 * Scenario tests for Howl of the Horde.
 *
 * Howl of the Horde creates a delayed effect that copies the next instant or sorcery
 * spell cast this turn. With Raid (attacked this turn), it copies an additional time.
 */
class HowlOfTheHordeScenarioTest : ScenarioTestBase() {

    init {
        context("Howl of the Horde") {

            test("without raid - copies next spell once (1 copy = 2 total)") {
                val game = scenario()
                    .withPlayers("Player1", "Player2")
                    .withCardInHand(1, "Howl of the Horde")
                    .withCardInHand(1, "Shock")
                    .withLandsOnBattlefield(1, "Mountain", 4)
                    .withActivePlayer(1)
                    .inPhase(Phase.PRECOMBAT_MAIN, Step.PRECOMBAT_MAIN)
                    .build()

                // Cast and resolve Howl
                game.castSpell(1, "Howl of the Horde")
                game.resolveStack()

                // Cast Shock targeting Player 2 — copy trigger goes on stack
                game.castSpellTargetingPlayer(1, "Shock", 2)

                // Copy trigger needs to resolve — first pass priority
                // The copy trigger (StormCopyEffect) resolves and asks for targets
                game.passPriority() // P1 passes
                game.passPriority() // P2 passes → copy trigger resolves → target decision

                // Select target for the copy
                game.selectTargets(listOf(game.player2Id))

                // Now resolve the remaining stack (copy + original Shock)
                game.resolveStack()

                // Shock deals 2 + copy deals 2 = 4 total damage
                withClue("Player 2 should have taken 4 damage (2 from Shock + 2 from copy)") {
                    game.getLifeTotal(2) shouldBe 16
                }
            }

            test("with raid - copies next spell twice (2 copies = 3 total)") {
                val game = scenario()
                    .withPlayers("Player1", "Player2")
                    .withCardInHand(1, "Howl of the Horde")
                    .withCardInHand(1, "Shock")
                    .withLandsOnBattlefield(1, "Mountain", 4)
                    .withCardOnBattlefield(1, "Valley Dasher", summoningSickness = false)
                    .withActivePlayer(1)
                    .inPhase(Phase.PRECOMBAT_MAIN, Step.PRECOMBAT_MAIN)
                    .build()

                // Attack with Valley Dasher to enable Raid
                game.passUntilPhase(Phase.COMBAT, Step.DECLARE_ATTACKERS)
                game.declareAttackers(mapOf("Valley Dasher" to 2))
                game.passUntilPhase(Phase.COMBAT, Step.DECLARE_BLOCKERS)
                game.declareNoBlockers()
                game.passUntilPhase(Phase.POSTCOMBAT_MAIN, Step.POSTCOMBAT_MAIN)

                val lifeAfterCombat = game.getLifeTotal(2)

                // Cast and resolve Howl (raid active → 2 pending copies)
                game.castSpell(1, "Howl of the Horde")
                game.resolveStack()

                withClue("Should have 2 pending spell copies with raid") {
                    game.state.pendingSpellCopies.size shouldBe 2
                }

                // Cast Shock targeting Player 2
                game.castSpellTargetingPlayer(1, "Shock", 2)

                // Copy trigger resolves → asks for target for copy 1
                game.passPriority() // P1
                game.passPriority() // P2 → triggers resolve → target decision for copy 1
                game.selectTargets(listOf(game.player2Id))

                // Target decision for copy 2
                game.selectTargets(listOf(game.player2Id))

                // Resolve remaining stack
                game.resolveStack()

                // Shock deals 2 × 3 = 6 total damage
                withClue("Player 2 should have taken 6 damage from Shock + 2 copies") {
                    game.getLifeTotal(2) shouldBe lifeAfterCombat - 6
                }
            }

            test("only copies the next spell - second spell is not copied") {
                val game = scenario()
                    .withPlayers("Player1", "Player2")
                    .withCardInHand(1, "Howl of the Horde")
                    .withCardInHand(1, "Shock")
                    .withCardInHand(1, "Shock")
                    .withLandsOnBattlefield(1, "Mountain", 5)
                    .withActivePlayer(1)
                    .inPhase(Phase.PRECOMBAT_MAIN, Step.PRECOMBAT_MAIN)
                    .build()

                // Cast and resolve Howl
                game.castSpell(1, "Howl of the Horde")
                game.resolveStack()

                // Cast first Shock — gets copied
                game.castSpellTargetingPlayer(1, "Shock", 2)
                game.passPriority()
                game.passPriority()
                game.selectTargets(listOf(game.player2Id))
                game.resolveStack()

                withClue("Player 2 should have taken 4 damage (Shock + copy)") {
                    game.getLifeTotal(2) shouldBe 16
                }

                // Cast second Shock — should NOT be copied
                game.castSpellTargetingPlayer(1, "Shock", 2)

                withClue("No pending decision for second spell (no copies)") {
                    game.hasPendingDecision() shouldBe false
                }

                game.resolveStack()

                withClue("Player 2 should have taken only 2 more damage (no copy)") {
                    game.getLifeTotal(2) shouldBe 14
                }
            }
        }
    }
}
