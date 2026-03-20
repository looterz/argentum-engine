package com.wingedsheep.gameserver.scenarios

import com.wingedsheep.gameserver.ScenarioTestBase
import com.wingedsheep.sdk.core.Phase
import com.wingedsheep.sdk.core.Step
import io.kotest.assertions.withClue
import io.kotest.matchers.shouldBe

/**
 * Scenario tests for Sunspine Lynx.
 *
 * Card reference:
 * - Sunspine Lynx ({2}{R}{R}): Creature — Elemental Cat 5/4
 *   "Players can't gain life."
 *   "Damage can't be prevented."
 *   "When Sunspine Lynx enters, it deals damage to each player equal to the
 *    number of nonbasic lands that player controls."
 */
class SunspineLynxScenarioTest : ScenarioTestBase() {

    init {
        context("Sunspine Lynx ETB damage") {
            test("deals damage to each player based on nonbasic lands") {
                val game = scenario()
                    .withPlayers("Caster", "Opponent")
                    .withCardInHand(1, "Sunspine Lynx")
                    .withLandsOnBattlefield(1, "Mountain", 4)
                    // Player 1 has 1 nonbasic land
                    .withCardOnBattlefield(1, "Dismal Backwater")
                    // Player 2 has 2 nonbasic lands
                    .withCardOnBattlefield(2, "Dismal Backwater")
                    .withCardOnBattlefield(2, "Swiftwater Cliffs")
                    .withActivePlayer(1)
                    .inPhase(Phase.PRECOMBAT_MAIN, Step.PRECOMBAT_MAIN)
                    .build()

                val castResult = game.castSpell(1, "Sunspine Lynx")
                withClue("Should cast successfully: ${castResult.error}") {
                    castResult.error shouldBe null
                }
                game.resolveStack()

                // Player 1 controls 1 nonbasic land → takes 1 damage
                withClue("Caster should take 1 damage (1 nonbasic land)") {
                    game.getLifeTotal(1) shouldBe 19
                }

                // Player 2 controls 2 nonbasic lands → takes 2 damage
                withClue("Opponent should take 2 damage (2 nonbasic lands)") {
                    game.getLifeTotal(2) shouldBe 18
                }
            }

            test("deals no damage when all lands are basic") {
                val game = scenario()
                    .withPlayers("Caster", "Opponent")
                    .withCardInHand(1, "Sunspine Lynx")
                    .withLandsOnBattlefield(1, "Mountain", 4)
                    .withLandsOnBattlefield(2, "Mountain", 2)
                    .withActivePlayer(1)
                    .inPhase(Phase.PRECOMBAT_MAIN, Step.PRECOMBAT_MAIN)
                    .build()

                val castResult = game.castSpell(1, "Sunspine Lynx")
                withClue("Should cast successfully: ${castResult.error}") {
                    castResult.error shouldBe null
                }
                game.resolveStack()

                withClue("Caster should take no damage") {
                    game.getLifeTotal(1) shouldBe 20
                }
                withClue("Opponent should take no damage") {
                    game.getLifeTotal(2) shouldBe 20
                }
            }
        }

        context("Sunspine Lynx prevents life gain") {
            test("prevents life gain while on battlefield") {
                val game = scenario()
                    .withPlayers("Caster", "Opponent")
                    .withCardOnBattlefield(1, "Sunspine Lynx")
                    .withCardInHand(1, "Windgrace Acolyte")
                    .withLandsOnBattlefield(1, "Swamp", 5)
                    .withActivePlayer(1)
                    .inPhase(Phase.PRECOMBAT_MAIN, Step.PRECOMBAT_MAIN)
                    .build()

                // Windgrace Acolyte: "When this creature enters, mill three cards and you gain 3 life."
                val castResult = game.castSpell(1, "Windgrace Acolyte")
                withClue("Should cast successfully: ${castResult.error}") {
                    castResult.error shouldBe null
                }
                game.resolveStack()

                // Life gain should be prevented by Sunspine Lynx
                withClue("Caster should not gain life") {
                    game.getLifeTotal(1) shouldBe 20
                }
            }
        }

        context("Sunspine Lynx damage can't be prevented") {
            test("combat damage goes through protection from red") {
                // Sunspine Lynx attacks, Silver Knight blocks
                // Silver Knight has protection from red → normally prevents red damage
                // But Sunspine Lynx says "Damage can't be prevented"
                val game = scenario()
                    .withPlayers("Attacker", "Blocker")
                    .withCardOnBattlefield(1, "Sunspine Lynx", summoningSickness = false)
                    .withCardOnBattlefield(2, "Silver Knight")
                    .withLandsOnBattlefield(1, "Mountain", 1)
                    .withLandsOnBattlefield(2, "Plains", 1)
                    .withActivePlayer(1)
                    .inPhase(Phase.COMBAT, Step.DECLARE_ATTACKERS)
                    .build()

                // Declare Sunspine Lynx as attacker
                game.declareAttackers(mapOf("Sunspine Lynx" to 2))

                // Advance to declare blockers step
                game.passPriority() // P1 passes after declaring attackers
                game.passPriority() // P2 gets priority

                // Silver Knight blocks Sunspine Lynx
                game.declareBlockers(mapOf("Silver Knight" to listOf("Sunspine Lynx")))

                // Pass through all combat steps to postcombat main
                var iterations = 0
                while (game.state.step != Step.POSTCOMBAT_MAIN && iterations < 20) {
                    val priorityPlayer = game.state.priorityPlayerId ?: break
                    game.execute(com.wingedsheep.engine.core.PassPriority(priorityPlayer))
                    iterations++
                }

                // Silver Knight (2/2) should be dead from 5 red combat damage despite protection
                withClue("Silver Knight should die from combat damage (DamageCantBePrevented)") {
                    game.isOnBattlefield("Silver Knight") shouldBe false
                }

                // Sunspine Lynx (5/4) takes 2 first strike damage, survives
                withClue("Sunspine Lynx should survive") {
                    game.isOnBattlefield("Sunspine Lynx") shouldBe true
                }
            }
        }
    }
}
