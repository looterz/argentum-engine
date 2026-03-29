package com.wingedsheep.gameserver.scenarios

import com.wingedsheep.engine.mechanics.layers.StateProjector
import com.wingedsheep.gameserver.ScenarioTestBase
import com.wingedsheep.sdk.core.Keyword
import com.wingedsheep.sdk.core.Phase
import com.wingedsheep.sdk.core.Step
import io.kotest.assertions.withClue
import io.kotest.matchers.shouldBe

/**
 * Scenario tests for Waterspout Warden.
 *
 * Card reference:
 * - Waterspout Warden ({2}{U}): Creature — Frog Soldier 3/2
 *   Whenever this creature attacks, if another creature entered the battlefield under your
 *   control this turn, this creature gains flying until end of turn.
 */
class WaterspoutWardenScenarioTest : ScenarioTestBase() {

    private val stateProjector = StateProjector()

    init {
        context("Waterspout Warden attack trigger") {

            test("gains flying when another creature entered this turn") {
                val game = scenario()
                    .withPlayers("Player1", "Player2")
                    .withCardOnBattlefield(1, "Waterspout Warden", summoningSickness = false)
                    .withCardInHand(1, "Glory Seeker")
                    .withLandsOnBattlefield(1, "Plains", 2)
                    .withLandsOnBattlefield(2, "Plains", 1)
                    .withActivePlayer(1)
                    .inPhase(Phase.PRECOMBAT_MAIN, Step.PRECOMBAT_MAIN)
                    .build()

                // Cast Glory Seeker so another creature enters this turn
                val castResult = game.castSpell(1, "Glory Seeker")
                withClue("Should cast successfully: ${castResult.error}") {
                    castResult.error shouldBe null
                }
                game.resolveStack()

                // Advance to declare attackers
                game.passUntilPhase(Phase.COMBAT, Step.DECLARE_ATTACKERS)

                // Declare Waterspout Warden as attacker
                game.declareAttackers(mapOf("Waterspout Warden" to 2))

                // Resolve the attack trigger
                game.resolveStack()

                // Waterspout Warden should have flying
                val wardenId = game.findPermanent("Waterspout Warden")!!
                val projected = stateProjector.project(game.state)
                withClue("Waterspout Warden should have flying") {
                    projected.hasKeyword(wardenId, Keyword.FLYING) shouldBe true
                }
            }

            test("does not gain flying when no other creature entered this turn") {
                val game = scenario()
                    .withPlayers("Player1", "Player2")
                    .withCardOnBattlefield(1, "Waterspout Warden", summoningSickness = false)
                    .withLandsOnBattlefield(1, "Plains", 1)
                    .withLandsOnBattlefield(2, "Plains", 1)
                    .withActivePlayer(1)
                    .inPhase(Phase.PRECOMBAT_MAIN, Step.PRECOMBAT_MAIN)
                    .build()

                // Advance to declare attackers without casting any creature
                game.passUntilPhase(Phase.COMBAT, Step.DECLARE_ATTACKERS)

                // Declare Waterspout Warden as attacker
                game.declareAttackers(mapOf("Waterspout Warden" to 2))

                // No trigger should have gone on the stack (intervening-if fails)
                // Waterspout Warden should NOT have flying
                val wardenId = game.findPermanent("Waterspout Warden")!!
                val projected = stateProjector.project(game.state)
                withClue("Waterspout Warden should not have flying") {
                    projected.hasKeyword(wardenId, Keyword.FLYING) shouldBe false
                }
            }
        }
    }
}
