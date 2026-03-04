package com.wingedsheep.gameserver.scenarios

import com.wingedsheep.engine.core.CastSpell
import com.wingedsheep.engine.core.TargetsResponse
import com.wingedsheep.engine.core.TurnFaceUp
import com.wingedsheep.engine.mechanics.layers.SerializableModification
import com.wingedsheep.engine.state.components.identity.CardComponent
import com.wingedsheep.engine.state.components.identity.ControllerComponent
import com.wingedsheep.engine.state.components.identity.FaceDownComponent
import com.wingedsheep.gameserver.ScenarioTestBase
import com.wingedsheep.sdk.core.Phase
import com.wingedsheep.sdk.core.Step
import io.kotest.assertions.withClue
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe

/**
 * Scenario tests for Chromeshell Crab (LGN #32).
 *
 * Card reference:
 * - Chromeshell Crab ({4}{U}): Creature — Crab Beast 3/3
 *   Morph {4}{U}
 *   When this creature is turned face up, you may exchange control of
 *   target creature you control and target creature an opponent controls.
 */
class ChromeshellCrabScenarioTest : ScenarioTestBase() {

    init {
        context("Chromeshell Crab exchange control") {

            test("turning face up and choosing targets exchanges control of two creatures") {
                val game = scenario()
                    .withPlayers("Player1", "Player2")
                    .withCardInHand(1, "Chromeshell Crab")
                    .withLandsOnBattlefield(1, "Island", 10)
                    .withCardOnBattlefield(1, "Glory Seeker")
                    .withCardOnBattlefield(2, "Grizzly Bears")
                    .withCardInLibrary(1, "Island")
                    .withCardInLibrary(2, "Island")
                    .withActivePlayer(1)
                    .inPhase(Phase.PRECOMBAT_MAIN, Step.PRECOMBAT_MAIN)
                    .build()

                // Cast Chromeshell Crab face-down
                val crabId = game.state.getHand(game.player1Id).first { entityId ->
                    game.state.getEntity(entityId)?.get<CardComponent>()?.name == "Chromeshell Crab"
                }
                val castResult = game.execute(CastSpell(game.player1Id, crabId, castFaceDown = true))
                withClue("Cast face-down should succeed: ${castResult.error}") {
                    castResult.error shouldBe null
                }
                game.resolveStack()

                val faceDownId = game.state.getBattlefield().find { entityId ->
                    game.state.getEntity(entityId)?.has<FaceDownComponent>() == true
                }
                withClue("Face-down creature should be on battlefield") {
                    faceDownId shouldNotBe null
                }

                // Turn face up
                val turnUpResult = game.execute(TurnFaceUp(game.player1Id, faceDownId!!))
                withClue("Turn face-up should succeed: ${turnUpResult.error}") {
                    turnUpResult.error shouldBe null
                }

                // Triggered ability asks for targets: creature you control + creature opponent controls
                // Select Glory Seeker (ours) and Grizzly Bears (theirs)
                val glorySeekerId = game.findAllPermanents("Glory Seeker").first()
                val grizzlyBearsId = game.findAllPermanents("Grizzly Bears").first()

                // Multi-target: index 0 = creature you control, index 1 = creature opponent controls
                val decisionId = game.state.pendingDecision?.id!!
                game.submitDecision(TargetsResponse(decisionId, mapOf(
                    0 to listOf(glorySeekerId),
                    1 to listOf(grizzlyBearsId)
                )))

                // Resolve the triggered ability
                game.resolveStack()

                // Verify control was exchanged
                val gloryController = game.state.getEntity(glorySeekerId)?.get<ControllerComponent>()?.playerId
                val bearsController = game.state.getEntity(grizzlyBearsId)?.get<ControllerComponent>()?.playerId

                // Check floating effects for control changes
                val controlEffects = game.state.floatingEffects.filter { floating ->
                    floating.effect.modification is SerializableModification.ChangeController &&
                        floating.sourceName == "Chromeshell Crab"
                }

                withClue("Should have two control-changing floating effects from Chromeshell Crab") {
                    controlEffects.size shouldBe 2
                }

                // Glory Seeker should now be controlled by Player2
                val gloryEffect = controlEffects.find { it.effect.affectedEntities.contains(glorySeekerId) }
                withClue("Glory Seeker should have a control change to Player2") {
                    gloryEffect shouldNotBe null
                    (gloryEffect!!.effect.modification as SerializableModification.ChangeController)
                        .newControllerId shouldBe game.player2Id
                }

                // Grizzly Bears should now be controlled by Player1
                val bearsEffect = controlEffects.find { it.effect.affectedEntities.contains(grizzlyBearsId) }
                withClue("Grizzly Bears should have a control change to Player1") {
                    bearsEffect shouldNotBe null
                    (bearsEffect!!.effect.modification as SerializableModification.ChangeController)
                        .newControllerId shouldBe game.player1Id
                }
            }

            test("declining the exchange does not change control") {
                val game = scenario()
                    .withPlayers("Player1", "Player2")
                    .withCardInHand(1, "Chromeshell Crab")
                    .withLandsOnBattlefield(1, "Island", 10)
                    .withCardOnBattlefield(1, "Glory Seeker")
                    .withCardOnBattlefield(2, "Grizzly Bears")
                    .withCardInLibrary(1, "Island")
                    .withCardInLibrary(2, "Island")
                    .withActivePlayer(1)
                    .inPhase(Phase.PRECOMBAT_MAIN, Step.PRECOMBAT_MAIN)
                    .build()

                // Cast face-down and turn face up
                val crabId = game.state.getHand(game.player1Id).first { entityId ->
                    game.state.getEntity(entityId)?.get<CardComponent>()?.name == "Chromeshell Crab"
                }
                game.execute(CastSpell(game.player1Id, crabId, castFaceDown = true))
                game.resolveStack()

                val faceDownId = game.state.getBattlefield().find { entityId ->
                    game.state.getEntity(entityId)?.has<FaceDownComponent>() == true
                }!!

                game.execute(TurnFaceUp(game.player1Id, faceDownId))

                // Decline by selecting no targets (optional ability)
                val decisionId = game.state.pendingDecision?.id!!
                game.submitDecision(TargetsResponse(decisionId, mapOf(
                    0 to emptyList(),
                    1 to emptyList()
                )))

                // No control-changing floating effects should exist
                val controlEffects = game.state.floatingEffects.filter { floating ->
                    floating.effect.modification is SerializableModification.ChangeController &&
                        floating.sourceName == "Chromeshell Crab"
                }
                withClue("No control changes should occur when declining") {
                    controlEffects.size shouldBe 0
                }
            }
        }
    }
}
