package com.wingedsheep.gameserver.scenarios

import com.wingedsheep.engine.state.components.battlefield.DamageComponent
import com.wingedsheep.gameserver.ScenarioTestBase
import com.wingedsheep.sdk.core.Phase
import com.wingedsheep.sdk.core.Step
import io.kotest.assertions.withClue
import io.kotest.matchers.shouldBe

/**
 * Scenario tests for Valley Flamecaller.
 *
 * Card reference:
 * - Valley Flamecaller ({2}{R}): Creature — Lizard Warlock, 3/3
 *   "If a Lizard, Mouse, Otter, or Raccoon you control would deal damage to a permanent or player,
 *    it deals that much damage plus 1 instead."
 */
class ValleyFlamecallerScenarioTest : ScenarioTestBase() {

    init {
        context("Valley Flamecaller - damage bonus") {
            test("adds 1 to combat damage dealt by a Lizard you control") {
                val game = scenario()
                    .withPlayers("Player1", "Player2")
                    .withCardOnBattlefield(1, "Valley Flamecaller") // 3/3 Lizard Warlock
                    .withCardOnBattlefield(1, "Ravine Raider") // 1/1 Lizard Rogue
                    .withActivePlayer(1)
                    .inPhase(Phase.PRECOMBAT_MAIN, Step.PRECOMBAT_MAIN)
                    .build()

                val startingLife = game.getLifeTotal(2)

                game.passUntilPhase(Phase.COMBAT, Step.DECLARE_ATTACKERS)
                game.declareAttackers(mapOf("Ravine Raider" to 2))
                game.passUntilPhase(Phase.COMBAT, Step.COMBAT_DAMAGE)
                game.passUntilPhase(Phase.POSTCOMBAT_MAIN, Step.POSTCOMBAT_MAIN)

                withClue("Opponent should take 1 + 1 = 2 damage from Ravine Raider with Flamecaller bonus") {
                    game.getLifeTotal(2) shouldBe startingLife - 2
                }
            }

            test("Valley Flamecaller itself gets the bonus (it is a Lizard)") {
                val game = scenario()
                    .withPlayers("Player1", "Player2")
                    .withCardOnBattlefield(1, "Valley Flamecaller") // 3/3 Lizard Warlock
                    .withActivePlayer(1)
                    .inPhase(Phase.PRECOMBAT_MAIN, Step.PRECOMBAT_MAIN)
                    .build()

                val startingLife = game.getLifeTotal(2)

                game.passUntilPhase(Phase.COMBAT, Step.DECLARE_ATTACKERS)
                game.declareAttackers(mapOf("Valley Flamecaller" to 2))
                game.passUntilPhase(Phase.COMBAT, Step.COMBAT_DAMAGE)
                game.passUntilPhase(Phase.POSTCOMBAT_MAIN, Step.POSTCOMBAT_MAIN)

                withClue("Opponent should take 3 + 1 = 4 damage from Valley Flamecaller (it's a Lizard)") {
                    game.getLifeTotal(2) shouldBe startingLife - 4
                }
            }

            test("does not add bonus to non-Lizard/Mouse/Otter/Raccoon creatures") {
                val game = scenario()
                    .withPlayers("Player1", "Player2")
                    .withCardOnBattlefield(1, "Valley Flamecaller")
                    .withCardOnBattlefield(1, "Glory Seeker") // 2/2 Human Soldier
                    .withActivePlayer(1)
                    .inPhase(Phase.PRECOMBAT_MAIN, Step.PRECOMBAT_MAIN)
                    .build()

                val startingLife = game.getLifeTotal(2)

                game.passUntilPhase(Phase.COMBAT, Step.DECLARE_ATTACKERS)
                game.declareAttackers(mapOf("Glory Seeker" to 2))
                game.passUntilPhase(Phase.COMBAT, Step.COMBAT_DAMAGE)
                game.passUntilPhase(Phase.POSTCOMBAT_MAIN, Step.POSTCOMBAT_MAIN)

                withClue("Glory Seeker is not a Lizard/Mouse/Otter/Raccoon, so no bonus") {
                    game.getLifeTotal(2) shouldBe startingLife - 2
                }
            }

            test("does not add bonus to opponent's Lizards") {
                val game = scenario()
                    .withPlayers("Player1", "Player2")
                    .withCardOnBattlefield(1, "Valley Flamecaller")
                    .withCardOnBattlefield(2, "Ravine Raider") // opponent's Lizard
                    .withActivePlayer(2)
                    .inPhase(Phase.PRECOMBAT_MAIN, Step.PRECOMBAT_MAIN)
                    .build()

                val startingLife = game.getLifeTotal(1)

                game.passUntilPhase(Phase.COMBAT, Step.DECLARE_ATTACKERS)
                game.declareAttackers(mapOf("Ravine Raider" to 1))
                game.passUntilPhase(Phase.COMBAT, Step.COMBAT_DAMAGE)
                game.passUntilPhase(Phase.POSTCOMBAT_MAIN, Step.POSTCOMBAT_MAIN)

                withClue("Opponent's Ravine Raider should not get the bonus") {
                    game.getLifeTotal(1) shouldBe startingLife - 1
                }
            }
        }
    }
}
