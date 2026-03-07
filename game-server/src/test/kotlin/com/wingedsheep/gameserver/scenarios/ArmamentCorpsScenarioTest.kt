package com.wingedsheep.gameserver.scenarios

import com.wingedsheep.engine.state.components.battlefield.CountersComponent
import com.wingedsheep.gameserver.ScenarioTestBase
import com.wingedsheep.sdk.core.CounterType
import com.wingedsheep.sdk.core.Phase
import com.wingedsheep.sdk.core.Step
import io.kotest.matchers.shouldBe

/**
 * Scenario tests for Armament Corps.
 *
 * Card reference:
 * - Armament Corps ({2}{W}{B}{G}): Creature — Human Soldier 4/4
 *   When this creature enters, distribute two +1/+1 counters among one or two
 *   target creatures you control.
 */
class ArmamentCorpsScenarioTest : ScenarioTestBase() {

    private fun TestGame.getCounters(name: String): Int {
        val entityId = findPermanent(name) ?: return 0
        return state.getEntity(entityId)
            ?.get<CountersComponent>()
            ?.getCount(CounterType.PLUS_ONE_PLUS_ONE) ?: 0
    }

    init {
        context("Armament Corps ETB counter distribution") {

            test("distributes 2 counters on a single target") {
                val game = scenario()
                    .withPlayers("Player1", "Player2")
                    .withCardInHand(1, "Armament Corps")
                    .withCardOnBattlefield(1, "Glory Seeker") // 2/2
                    .withLandsOnBattlefield(1, "Plains", 2)
                    .withLandsOnBattlefield(1, "Swamp", 2)
                    .withLandsOnBattlefield(1, "Forest", 1)
                    .withCardInLibrary(1, "Plains")
                    .withCardInLibrary(2, "Plains")
                    .withActivePlayer(1)
                    .inPhase(Phase.PRECOMBAT_MAIN, Step.PRECOMBAT_MAIN)
                    .build()

                game.castSpell(1, "Armament Corps")
                game.resolveStack() // resolve creature → ETB triggers

                // ETB trigger fires — select 1 target
                game.hasPendingDecision() shouldBe true
                val glorySeekerId = game.findPermanent("Glory Seeker")!!
                game.selectTargets(listOf(glorySeekerId))
                game.resolveStack() // resolve ETB

                // Glory Seeker should have 2 +1/+1 counters
                game.getCounters("Glory Seeker") shouldBe 2
            }

            test("distributes 1 counter each on two targets") {
                val game = scenario()
                    .withPlayers("Player1", "Player2")
                    .withCardInHand(1, "Armament Corps")
                    .withCardOnBattlefield(1, "Glory Seeker") // 2/2
                    .withCardOnBattlefield(1, "Grizzly Bears") // 2/2
                    .withLandsOnBattlefield(1, "Plains", 2)
                    .withLandsOnBattlefield(1, "Swamp", 2)
                    .withLandsOnBattlefield(1, "Forest", 1)
                    .withCardInLibrary(1, "Plains")
                    .withCardInLibrary(2, "Plains")
                    .withActivePlayer(1)
                    .inPhase(Phase.PRECOMBAT_MAIN, Step.PRECOMBAT_MAIN)
                    .build()

                game.castSpell(1, "Armament Corps")
                game.resolveStack() // resolve creature → ETB triggers

                // ETB trigger fires — select 2 targets
                game.hasPendingDecision() shouldBe true
                val glorySeekerId = game.findPermanent("Glory Seeker")!!
                val bearsId = game.findPermanent("Grizzly Bears")!!
                game.selectTargets(listOf(glorySeekerId, bearsId))
                game.resolveStack() // resolve ETB

                // Each should have 1 +1/+1 counter
                game.getCounters("Glory Seeker") shouldBe 1
                game.getCounters("Grizzly Bears") shouldBe 1
            }

            test("can target itself with the ETB trigger") {
                val game = scenario()
                    .withPlayers("Player1", "Player2")
                    .withCardInHand(1, "Armament Corps")
                    .withLandsOnBattlefield(1, "Plains", 2)
                    .withLandsOnBattlefield(1, "Swamp", 2)
                    .withLandsOnBattlefield(1, "Forest", 1)
                    .withCardInLibrary(1, "Plains")
                    .withCardInLibrary(2, "Plains")
                    .withActivePlayer(1)
                    .inPhase(Phase.PRECOMBAT_MAIN, Step.PRECOMBAT_MAIN)
                    .build()

                game.castSpell(1, "Armament Corps")
                game.resolveStack() // resolve creature → ETB triggers

                // ETB trigger fires — target Armament Corps itself
                game.hasPendingDecision() shouldBe true
                val corpsId = game.findPermanent("Armament Corps")!!
                game.selectTargets(listOf(corpsId))
                game.resolveStack() // resolve ETB

                // Should have 2 +1/+1 counters on itself
                game.getCounters("Armament Corps") shouldBe 2
            }

            test("works with Hardened Scales for extra counters") {
                val game = scenario()
                    .withPlayers("Player1", "Player2")
                    .withCardOnBattlefield(1, "Hardened Scales")
                    .withCardInHand(1, "Armament Corps")
                    .withCardOnBattlefield(1, "Glory Seeker")
                    .withLandsOnBattlefield(1, "Plains", 2)
                    .withLandsOnBattlefield(1, "Swamp", 2)
                    .withLandsOnBattlefield(1, "Forest", 1)
                    .withCardInLibrary(1, "Plains")
                    .withCardInLibrary(2, "Plains")
                    .withActivePlayer(1)
                    .inPhase(Phase.PRECOMBAT_MAIN, Step.PRECOMBAT_MAIN)
                    .build()

                game.castSpell(1, "Armament Corps")
                game.resolveStack() // resolve creature → ETB triggers

                // ETB trigger fires — select 1 target
                game.hasPendingDecision() shouldBe true
                val glorySeekerId = game.findPermanent("Glory Seeker")!!
                game.selectTargets(listOf(glorySeekerId))
                game.resolveStack() // resolve ETB

                // Should have 3 counters (2 + 1 from Hardened Scales)
                game.getCounters("Glory Seeker") shouldBe 3
            }
        }
    }
}
