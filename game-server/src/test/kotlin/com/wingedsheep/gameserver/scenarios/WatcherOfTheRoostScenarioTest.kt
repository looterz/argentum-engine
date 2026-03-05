package com.wingedsheep.gameserver.scenarios

import com.wingedsheep.engine.core.*
import com.wingedsheep.engine.state.components.identity.CardComponent
import com.wingedsheep.engine.state.components.identity.FaceDownComponent
import com.wingedsheep.engine.state.components.identity.LifeTotalComponent
import com.wingedsheep.gameserver.ScenarioTestBase
import com.wingedsheep.sdk.core.Phase
import com.wingedsheep.sdk.core.Step
import com.wingedsheep.sdk.core.Zone
import io.kotest.assertions.withClue
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe

/**
 * Scenario tests for Watcher of the Roost.
 *
 * Card reference:
 * - Watcher of the Roost ({2}{W}): Creature — Bird Soldier 2/1, Flying
 *   Morph—Reveal a white card in your hand.
 *   When this creature is turned face up, you gain 2 life.
 */
class WatcherOfTheRoostScenarioTest : ScenarioTestBase() {

    init {
        context("Watcher of the Roost") {

            test("can turn face up by revealing a white card from hand") {
                val game = scenario()
                    .withPlayers("Player", "Opponent")
                    .withCardInHand(1, "Watcher of the Roost")
                    .withCardInHand(1, "Kill Shot") // White card to reveal
                    .withLandsOnBattlefield(1, "Plains", 3)
                    .withCardInLibrary(1, "Plains")
                    .withCardInLibrary(2, "Plains")
                    .withActivePlayer(1)
                    .inPhase(Phase.PRECOMBAT_MAIN, Step.PRECOMBAT_MAIN)
                    .build()

                // Cast Watcher face-down for {3}
                val cardId = game.state.getHand(game.player1Id).first { entityId ->
                    game.state.getEntity(entityId)?.get<CardComponent>()?.name == "Watcher of the Roost"
                }
                val castResult = game.execute(CastSpell(game.player1Id, cardId, castFaceDown = true))
                withClue("Cast face-down should succeed: ${castResult.error}") {
                    castResult.error shouldBe null
                }
                game.resolveStack()

                // Find the face-down creature on battlefield
                val faceDownId = game.state.getBattlefield().find { entityId ->
                    game.state.getEntity(entityId)?.has<FaceDownComponent>() == true
                }
                withClue("Face-down creature should be on battlefield") {
                    faceDownId shouldNotBe null
                }

                // Find the white card in hand to reveal
                val whiteCardInHand = game.state.getHand(game.player1Id).first { entityId ->
                    game.state.getEntity(entityId)?.get<CardComponent>()?.name == "Kill Shot"
                }

                val lifeBefore = game.state.getEntity(game.player1Id)
                    ?.get<LifeTotalComponent>()?.life ?: 20

                // Turn face up by revealing the white card
                val turnUpResult = game.execute(
                    TurnFaceUp(game.player1Id, faceDownId!!, costTargetIds = listOf(whiteCardInHand))
                )
                withClue("Turn face-up should succeed: ${turnUpResult.error}") {
                    turnUpResult.error shouldBe null
                }

                // Watcher of the Roost should be face-up on battlefield
                val watcherOnBattlefield = game.findPermanent("Watcher of the Roost")
                withClue("Watcher of the Roost should be face-up on battlefield") {
                    watcherOnBattlefield shouldNotBe null
                }

                // The revealed card (Kill Shot) should still be in hand
                val killShotInHand = game.state.getHand(game.player1Id).count { entityId ->
                    game.state.getEntity(entityId)?.get<CardComponent>()?.name == "Kill Shot"
                }
                withClue("Kill Shot should still be in hand after being revealed") {
                    killShotInHand shouldBe 1
                }

                // Player should have gained 2 life from "turned face up" trigger
                // Need to resolve the trigger first
                game.resolveStack()
                val lifeAfter = game.state.getEntity(game.player1Id)
                    ?.get<LifeTotalComponent>()?.life ?: 0
                withClue("Player should have gained 2 life") {
                    lifeAfter shouldBe lifeBefore + 2
                }
            }

            test("cannot turn face up without a white card in hand") {
                val game = scenario()
                    .withPlayers("Player", "Opponent")
                    .withCardInHand(1, "Watcher of the Roost")
                    .withLandsOnBattlefield(1, "Plains", 3)
                    .withCardInLibrary(1, "Plains")
                    .withCardInLibrary(2, "Plains")
                    .withActivePlayer(1)
                    .inPhase(Phase.PRECOMBAT_MAIN, Step.PRECOMBAT_MAIN)
                    .build()

                // Cast Watcher face-down for {3}
                val cardId = game.state.getHand(game.player1Id).first { entityId ->
                    game.state.getEntity(entityId)?.get<CardComponent>()?.name == "Watcher of the Roost"
                }
                val castResult = game.execute(CastSpell(game.player1Id, cardId, castFaceDown = true))
                withClue("Cast face-down should succeed: ${castResult.error}") {
                    castResult.error shouldBe null
                }
                game.resolveStack()

                // Find the face-down creature on battlefield
                val faceDownId = game.state.getBattlefield().find { entityId ->
                    game.state.getEntity(entityId)?.has<FaceDownComponent>() == true
                }
                withClue("Face-down creature should be on battlefield") {
                    faceDownId shouldNotBe null
                }

                // Hand should be empty (no white card to reveal)
                val handSize = game.state.getHand(game.player1Id).size
                withClue("Hand should be empty") {
                    handSize shouldBe 0
                }

                // Try to turn face up with empty costTargetIds — should fail
                val turnUpResult = game.execute(
                    TurnFaceUp(game.player1Id, faceDownId!!, costTargetIds = emptyList())
                )
                withClue("Turn face-up should fail without a white card to reveal") {
                    turnUpResult.error shouldNotBe null
                }
            }

            test("cannot reveal a non-white card to pay morph cost") {
                val game = scenario()
                    .withPlayers("Player", "Opponent")
                    .withCardInHand(1, "Watcher of the Roost")
                    .withCardInHand(1, "Cancel") // Blue card, not white
                    .withLandsOnBattlefield(1, "Plains", 3)
                    .withCardInLibrary(1, "Plains")
                    .withCardInLibrary(2, "Plains")
                    .withActivePlayer(1)
                    .inPhase(Phase.PRECOMBAT_MAIN, Step.PRECOMBAT_MAIN)
                    .build()

                // Cast Watcher face-down for {3}
                val cardId = game.state.getHand(game.player1Id).first { entityId ->
                    game.state.getEntity(entityId)?.get<CardComponent>()?.name == "Watcher of the Roost"
                }
                val castResult = game.execute(CastSpell(game.player1Id, cardId, castFaceDown = true))
                withClue("Cast face-down should succeed: ${castResult.error}") {
                    castResult.error shouldBe null
                }
                game.resolveStack()

                // Find the face-down creature on battlefield
                val faceDownId = game.state.getBattlefield().find { entityId ->
                    game.state.getEntity(entityId)?.has<FaceDownComponent>() == true
                }
                withClue("Face-down creature should be on battlefield") {
                    faceDownId shouldNotBe null
                }

                // Find the Cancel in hand (blue, not white)
                val cancelInHand = game.state.getHand(game.player1Id).first { entityId ->
                    game.state.getEntity(entityId)?.get<CardComponent>()?.name == "Cancel"
                }

                // Try to reveal non-white card — should fail validation
                val turnUpResult = game.execute(
                    TurnFaceUp(game.player1Id, faceDownId!!, costTargetIds = listOf(cancelInHand))
                )
                withClue("Turn face-up should fail with non-white card reveal") {
                    turnUpResult.error shouldNotBe null
                }
            }
        }
    }
}
