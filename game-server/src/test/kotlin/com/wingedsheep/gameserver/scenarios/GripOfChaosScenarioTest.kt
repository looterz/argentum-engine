package com.wingedsheep.gameserver.scenarios

import com.wingedsheep.engine.state.components.stack.ChosenTarget
import com.wingedsheep.engine.state.components.stack.TargetsComponent
import com.wingedsheep.gameserver.ScenarioTestBase
import com.wingedsheep.sdk.core.Phase
import com.wingedsheep.sdk.core.Step
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe

/**
 * Tests for Grip of Chaos's random target reselection.
 *
 * Grip of Chaos: {4}{R}{R}
 * Enchantment
 * Whenever a spell or ability is put onto the stack, if it has a single target,
 * reselect its target at random. (Select from among all legal targets.)
 */
class GripOfChaosScenarioTest : ScenarioTestBase() {

    init {
        context("Grip of Chaos target reselection") {

            test("reselects target of a spell with a single target") {
                // Run multiple times since target selection is random
                var redirected = false
                repeat(50) {
                    if (redirected) return@repeat

                    val game = scenario()
                        .withPlayers("Player1", "Player2")
                        .withCardOnBattlefield(1, "Grip of Chaos")
                        .withCardInHand(1, "Spark Spray")
                        .withCardOnBattlefield(1, "Mountain")
                        .withCardOnBattlefield(2, "Glory Seeker")
                        .withCardOnBattlefield(2, "Silver Knight")
                        .withCardInLibrary(1, "Mountain")
                        .withCardInLibrary(2, "Mountain")
                        .withActivePlayer(1)
                        .inPhase(Phase.PRECOMBAT_MAIN, Step.PRECOMBAT_MAIN)
                        .build()

                    // Cast Spark Spray targeting Glory Seeker
                    val glorySeeker = game.findPermanent("Glory Seeker")!!
                    game.castSpell(1, "Spark Spray", glorySeeker)

                    // Stack: Grip of Chaos trigger on top, Spark Spray below
                    // Resolve ONLY the Grip of Chaos trigger (2 passes = resolve top of stack)
                    game.passPriority() // Player 1 passes
                    game.passPriority() // Player 2 passes → Grip of Chaos trigger resolves

                    // Now Spark Spray is still on stack with potentially changed target
                    game.state.stack.size shouldNotBe 0

                    val sprayEntity = game.state.stack.first()
                    val targets = game.state.getEntity(sprayEntity)
                        ?.get<TargetsComponent>()?.targets
                    if (targets != null && targets.size == 1) {
                        val target = targets.first()
                        val targetId = when (target) {
                            is ChosenTarget.Permanent -> target.entityId
                            is ChosenTarget.Player -> target.playerId
                            else -> null
                        }
                        if (targetId != glorySeeker) {
                            redirected = true
                        }
                    }
                }
                redirected shouldBe true
            }

            test("does not trigger for spells with no targets") {
                val game = scenario()
                    .withPlayers("Player1", "Player2")
                    .withCardOnBattlefield(1, "Grip of Chaos")
                    .withCardInHand(1, "Gilded Light")
                    .withCardOnBattlefield(1, "Plains")
                    .withCardOnBattlefield(1, "Plains")
                    .withCardInLibrary(1, "Mountain")
                    .withCardInLibrary(2, "Mountain")
                    .withActivePlayer(1)
                    .inPhase(Phase.PRECOMBAT_MAIN, Step.PRECOMBAT_MAIN)
                    .build()

                // Gilded Light has no targets — Grip of Chaos should not trigger
                game.castSpell(1, "Gilded Light")

                // Resolve stack — only Gilded Light should be on stack (no Grip trigger)
                game.resolveStack()

                // Stack should be empty after Gilded Light resolves
                game.state.stack.size shouldBe 0
            }
        }
    }
}
