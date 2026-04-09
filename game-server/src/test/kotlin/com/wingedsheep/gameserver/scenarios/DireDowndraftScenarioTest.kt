package com.wingedsheep.gameserver.scenarios

import com.wingedsheep.gameserver.ScenarioTestBase
import com.wingedsheep.sdk.core.Phase
import com.wingedsheep.sdk.core.Step
import io.kotest.assertions.withClue
import io.kotest.matchers.shouldBe

/**
 * Scenario tests for Dire Downdraft.
 *
 * Card reference:
 * - Dire Downdraft ({3}{U}): Instant
 *   This spell costs {1} less to cast if it targets an attacking or tapped creature.
 *   Target creature's owner puts it on their choice of the top or bottom of their library.
 */
class DireDowndraftScenarioTest : ScenarioTestBase() {

    init {
        context("Dire Downdraft target-conditional cost reduction") {

            test("costs {2}{U} when targeting a tapped creature") {
                val game = scenario()
                    .withPlayers("Player", "Opponent")
                    .withCardInHand(1, "Dire Downdraft")
                    .withCardOnBattlefield(2, "Glory Seeker", tapped = true)
                    .withLandsOnBattlefield(1, "Island", 3) // Only 3 mana — needs reduction
                    .withCardInLibrary(1, "Island")
                    .withCardInLibrary(2, "Island")
                    .withActivePlayer(1)
                    .inPhase(Phase.PRECOMBAT_MAIN, Step.PRECOMBAT_MAIN)
                    .build()

                val target = game.findPermanent("Glory Seeker")!!
                val castResult = game.castSpell(1, "Dire Downdraft", targetId = target)
                withClue("Cast should succeed with reduction: ${castResult.error}") {
                    castResult.error shouldBe null
                }
            }

            test("costs full {3}{U} when targeting an untapped non-attacking creature") {
                val game = scenario()
                    .withPlayers("Player", "Opponent")
                    .withCardInHand(1, "Dire Downdraft")
                    .withCardOnBattlefield(2, "Glory Seeker", tapped = false)
                    .withLandsOnBattlefield(1, "Island", 3) // 3 mana — not enough without reduction
                    .withCardInLibrary(1, "Island")
                    .withCardInLibrary(2, "Island")
                    .withActivePlayer(1)
                    .inPhase(Phase.PRECOMBAT_MAIN, Step.PRECOMBAT_MAIN)
                    .build()

                val target = game.findPermanent("Glory Seeker")!!
                val castResult = game.castSpell(1, "Dire Downdraft", targetId = target)
                withClue("Cast should fail without enough mana") {
                    castResult.error shouldBe "Not enough mana to cast this spell"
                }
            }

            test("can be cast at full cost {3}{U} with 4 mana against an untapped creature") {
                val game = scenario()
                    .withPlayers("Player", "Opponent")
                    .withCardInHand(1, "Dire Downdraft")
                    .withCardOnBattlefield(2, "Glory Seeker", tapped = false)
                    .withLandsOnBattlefield(1, "Island", 4)
                    .withCardInLibrary(1, "Island")
                    .withCardInLibrary(2, "Island")
                    .withActivePlayer(1)
                    .inPhase(Phase.PRECOMBAT_MAIN, Step.PRECOMBAT_MAIN)
                    .build()

                val target = game.findPermanent("Glory Seeker")!!
                val castResult = game.castSpell(1, "Dire Downdraft", targetId = target)
                withClue("Cast should succeed with full mana: ${castResult.error}") {
                    castResult.error shouldBe null
                }
            }
        }
    }
}
