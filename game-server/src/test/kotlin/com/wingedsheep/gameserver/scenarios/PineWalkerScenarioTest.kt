package com.wingedsheep.gameserver.scenarios

import com.wingedsheep.engine.core.*
import com.wingedsheep.engine.state.components.identity.CardComponent
import com.wingedsheep.engine.state.components.identity.FaceDownComponent
import com.wingedsheep.engine.state.components.battlefield.TappedComponent
import com.wingedsheep.gameserver.ScenarioTestBase
import com.wingedsheep.sdk.core.Phase
import com.wingedsheep.sdk.core.Step
import io.kotest.assertions.withClue
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe

/**
 * Scenario tests for Pine Walker.
 *
 * Card reference:
 * - Pine Walker ({3}{G}{G}): Creature — Elemental 5/5
 *   Morph {4}{G}
 *   Whenever Pine Walker or another creature you control is turned face up, untap that creature.
 */
class PineWalkerScenarioTest : ScenarioTestBase() {

    init {
        context("Pine Walker") {

            test("untaps itself when turned face up") {
                val game = scenario()
                    .withPlayers("Player", "Opponent")
                    .withCardInHand(1, "Pine Walker")
                    .withLandsOnBattlefield(1, "Forest", 8)
                    .withCardInLibrary(1, "Forest")
                    .withCardInLibrary(2, "Forest")
                    .withActivePlayer(1)
                    .inPhase(Phase.PRECOMBAT_MAIN, Step.PRECOMBAT_MAIN)
                    .build()

                // Cast Pine Walker face-down for {3}
                val cardId = game.state.getHand(game.player1Id).first { entityId ->
                    game.state.getEntity(entityId)?.get<CardComponent>()?.name == "Pine Walker"
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

                // Turn face up for {4}{G}
                val turnUpResult = game.execute(TurnFaceUp(game.player1Id, faceDownId!!))
                withClue("Turn face-up should succeed: ${turnUpResult.error}") {
                    turnUpResult.error shouldBe null
                }

                // Resolve the untap trigger
                game.resolveStack()

                // Pine Walker should be face-up on battlefield
                val pineWalker = game.findPermanent("Pine Walker")
                withClue("Pine Walker should be face-up on battlefield") {
                    pineWalker shouldNotBe null
                }

                // Pine Walker should be untapped (its own trigger untaps it)
                val isTapped = game.state.getEntity(pineWalker!!)?.has<TappedComponent>() == true
                withClue("Pine Walker should be untapped after turning face up") {
                    isTapped shouldBe false
                }
            }

            test("untaps another creature you control when it is turned face up") {
                val game = scenario()
                    .withPlayers("Player", "Opponent")
                    .withCardInHand(1, "Woolly Loxodon") // Another morph creature
                    .withCardOnBattlefield(1, "Pine Walker")
                    .withLandsOnBattlefield(1, "Forest", 10)
                    .withCardInLibrary(1, "Forest")
                    .withCardInLibrary(2, "Forest")
                    .withActivePlayer(1)
                    .inPhase(Phase.PRECOMBAT_MAIN, Step.PRECOMBAT_MAIN)
                    .build()

                // Cast Woolly Loxodon face-down for {3}
                val cardId = game.state.getHand(game.player1Id).first { entityId ->
                    game.state.getEntity(entityId)?.get<CardComponent>()?.name == "Woolly Loxodon"
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

                // Turn Woolly Loxodon face up for {5}{G}
                val turnUpResult = game.execute(TurnFaceUp(game.player1Id, faceDownId!!))
                withClue("Turn face-up should succeed: ${turnUpResult.error}") {
                    turnUpResult.error shouldBe null
                }

                // Resolve Pine Walker's trigger (untap the creature that was turned face up)
                game.resolveStack()

                // Woolly Loxodon should be untapped
                val woollyId = game.findPermanent("Woolly Loxodon")
                withClue("Woolly Loxodon should be on battlefield") {
                    woollyId shouldNotBe null
                }
                val isTapped = game.state.getEntity(woollyId!!)?.has<TappedComponent>() == true
                withClue("Woolly Loxodon should be untapped after Pine Walker's trigger") {
                    isTapped shouldBe false
                }
            }

            test("does not trigger when opponent's creature is turned face up") {
                val game = scenario()
                    .withPlayers("Player", "Opponent")
                    .withCardOnBattlefield(1, "Pine Walker")
                    .withCardInHand(2, "Woolly Loxodon")
                    .withLandsOnBattlefield(2, "Forest", 10)
                    .withCardInLibrary(1, "Forest")
                    .withCardInLibrary(2, "Forest")
                    .withActivePlayer(2)
                    .inPhase(Phase.PRECOMBAT_MAIN, Step.PRECOMBAT_MAIN)
                    .build()

                // Opponent casts Woolly Loxodon face-down for {3}
                val cardId = game.state.getHand(game.player2Id).first { entityId ->
                    game.state.getEntity(entityId)?.get<CardComponent>()?.name == "Woolly Loxodon"
                }
                val castResult = game.execute(CastSpell(game.player2Id, cardId, castFaceDown = true))
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

                // Opponent turns Woolly Loxodon face up
                val turnUpResult = game.execute(TurnFaceUp(game.player2Id, faceDownId!!))
                withClue("Turn face-up should succeed: ${turnUpResult.error}") {
                    turnUpResult.error shouldBe null
                }

                // The stack should be empty (no trigger from Pine Walker since it's not the same controller)
                withClue("Stack should be empty - Pine Walker should not trigger for opponent's creature") {
                    game.state.stack.size shouldBe 0
                }
            }
        }
    }
}
