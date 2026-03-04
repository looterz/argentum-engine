package com.wingedsheep.gameserver.scenarios

import com.wingedsheep.gameserver.ScenarioTestBase
import com.wingedsheep.engine.core.PassPriority
import com.wingedsheep.engine.state.components.player.PlayerLostComponent
import com.wingedsheep.sdk.core.Phase
import com.wingedsheep.sdk.core.Step
import io.kotest.assertions.withClue
import io.kotest.matchers.shouldBe

/**
 * Scenario tests for Phage the Untouchable.
 *
 * Card reference:
 * - Phage the Untouchable ({3}{B}{B}{B}{B}): Legendary Creature — Avatar Minion, 4/4
 *   "When Phage the Untouchable enters, if you didn't cast it from your hand, you lose the game."
 *   "Whenever Phage the Untouchable deals combat damage to a creature, destroy that creature. It can't be regenerated."
 *   "Whenever Phage the Untouchable deals combat damage to a player, that player loses the game."
 */
class PhageTheUntouchableScenarioTest : ScenarioTestBase() {

    init {
        context("Phage the Untouchable cast from hand") {
            test("casting from hand does not trigger lose-the-game ETB") {
                val game = scenario()
                    .withPlayers("Phage Player", "Opponent")
                    .withCardInHand(1, "Phage the Untouchable")
                    .withLandsOnBattlefield(1, "Swamp", 7)
                    .withActivePlayer(1)
                    .inPhase(Phase.PRECOMBAT_MAIN, Step.PRECOMBAT_MAIN)
                    .build()

                // Cast Phage from hand
                game.castSpell(1, "Phage the Untouchable")
                game.resolveStack()

                // Should enter battlefield normally - no lose-the-game
                withClue("Phage should be on the battlefield") {
                    game.isOnBattlefield("Phage the Untouchable") shouldBe true
                }

                withClue("Player should not have lost") {
                    game.state.gameOver shouldBe false
                }
            }
        }

        context("Phage the Untouchable combat damage to player") {
            test("opponent loses the game when dealt combat damage") {
                val game = scenario()
                    .withPlayers("Phage Player", "Opponent")
                    .withCardOnBattlefield(1, "Phage the Untouchable")
                    .withActivePlayer(1)
                    .inPhase(Phase.PRECOMBAT_MAIN, Step.PRECOMBAT_MAIN)
                    .build()

                // Go to combat
                game.passUntilPhase(Phase.COMBAT, Step.DECLARE_ATTACKERS)

                // Attack with Phage
                game.declareAttackers(mapOf("Phage the Untouchable" to 2))

                // No blocks
                game.passUntilPhase(Phase.COMBAT, Step.DECLARE_BLOCKERS)
                game.declareNoBlockers()

                // Advance through combat damage - trigger fires
                var iterations = 0
                while (!game.state.gameOver && iterations < 50) {
                    val p = game.state.priorityPlayerId ?: break
                    game.execute(PassPriority(p))
                    iterations++
                }

                // Opponent should have lost the game
                withClue("Game should be over") {
                    game.state.gameOver shouldBe true
                }

                val opponentId = game.state.turnOrder[1]
                withClue("Opponent should have lost") {
                    game.state.getEntity(opponentId)?.has<PlayerLostComponent>() shouldBe true
                }
            }
        }

        context("Phage the Untouchable combat damage to creature") {
            test("destroys creature when dealing combat damage") {
                val game = scenario()
                    .withPlayers("Phage Player", "Opponent")
                    .withCardOnBattlefield(1, "Phage the Untouchable")
                    .withCardOnBattlefield(2, "Enormous Baloth") // 7/7 creature
                    .withActivePlayer(1)
                    .inPhase(Phase.PRECOMBAT_MAIN, Step.PRECOMBAT_MAIN)
                    .build()

                // Go to combat
                game.passUntilPhase(Phase.COMBAT, Step.DECLARE_ATTACKERS)

                // Attack with Phage
                game.declareAttackers(mapOf("Phage the Untouchable" to 2))

                // Block with Enormous Baloth
                game.passUntilPhase(Phase.COMBAT, Step.DECLARE_BLOCKERS)
                game.declareBlockers(mapOf("Enormous Baloth" to listOf("Phage the Untouchable")))

                // Advance through combat damage - trigger fires, then resolve it
                var iterations = 0
                while (iterations < 50) {
                    if (game.state.gameOver) break
                    val p = game.state.priorityPlayerId ?: break
                    game.execute(PassPriority(p))
                    iterations++
                }

                // Enormous Baloth should be destroyed (even though it's 7/7 and only took 4 damage)
                withClue("Enormous Baloth should be destroyed by trigger") {
                    game.isOnBattlefield("Enormous Baloth") shouldBe false
                }
            }
        }
    }
}
