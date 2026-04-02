package com.wingedsheep.gameserver.scenarios

import com.wingedsheep.engine.state.components.identity.CardComponent
import com.wingedsheep.gameserver.ScenarioTestBase
import com.wingedsheep.sdk.core.Phase
import com.wingedsheep.sdk.core.Step
import io.kotest.assertions.withClue
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe

/**
 * Scenario tests for Cache Grab.
 *
 * Cache Grab {1}{G}
 * Instant
 *
 * Mill four cards. You may put a permanent card from among the cards milled
 * this way into your hand. If you control a Squirrel or returned a Squirrel
 * card to your hand this way, create a Food token.
 */
class CacheGrabScenarioTest : ScenarioTestBase() {

    init {
        context("Cache Grab — mill and retrieve") {

            test("mills four cards and allows selecting a permanent card to hand") {
                val game = scenario()
                    .withPlayers("Player", "Opponent")
                    .withCardInHand(1, "Cache Grab")
                    .withLandsOnBattlefield(1, "Forest", 2)
                    // Top of library: creature (permanent), then fillers
                    .withCardInLibrary(1, "Glory Seeker") // permanent card
                    .withCardInLibrary(1, "Forest")       // permanent card
                    .withCardInLibrary(1, "Forest")       // permanent card
                    .withCardInLibrary(1, "Forest")       // permanent card
                    .withCardInLibrary(1, "Forest")       // safety
                    .withCardInLibrary(2, "Forest")
                    .withActivePlayer(1)
                    .inPhase(Phase.PRECOMBAT_MAIN, Step.PRECOMBAT_MAIN)
                    .build()

                val initialGraveyardSize = game.graveyardSize(1)

                // Cast Cache Grab
                val castResult = game.castSpell(1, "Cache Grab")
                withClue("Should cast Cache Grab: ${castResult.error}") {
                    castResult.error shouldBe null
                }
                game.resolveStack()

                // Should have a selection decision for the milled permanent cards
                withClue("Should have pending selection decision") {
                    game.hasPendingDecision() shouldBe true
                }

                // Find Glory Seeker among the milled cards in the decision
                val graveyard = game.state.getGraveyard(game.player1Id)
                val glorySeeker = graveyard.find { entityId ->
                    game.state.getEntity(entityId)?.get<CardComponent>()?.name == "Glory Seeker"
                }
                withClue("Glory Seeker should be in graveyard after milling") {
                    glorySeeker shouldNotBe null
                }

                // Select Glory Seeker to put in hand
                game.selectCards(listOf(glorySeeker!!))

                // Verify Glory Seeker is in hand
                withClue("Glory Seeker should be in hand after selection") {
                    game.isInHand(1, "Glory Seeker") shouldBe true
                }

                // 4 milled - 1 returned = 3 in graveyard (plus Cache Grab itself)
                val expectedGraveyardSize = initialGraveyardSize + 3 + 1 // 3 remaining milled + Cache Grab
                withClue("Graveyard should have 3 milled cards + Cache Grab") {
                    game.graveyardSize(1) shouldBe expectedGraveyardSize
                }

                // No Squirrel controlled or returned → no Food token
                withClue("Should NOT have Food token (no Squirrel)") {
                    game.findPermanent("Food") shouldBe null
                }
            }

            test("creates Food token when controlling a Squirrel") {
                val game = scenario()
                    .withPlayers("Player", "Opponent")
                    .withCardInHand(1, "Cache Grab")
                    .withLandsOnBattlefield(1, "Forest", 2)
                    .withCardOnBattlefield(1, "Curious Forager") // Squirrel creature
                    .withCardInLibrary(1, "Glory Seeker")
                    .withCardInLibrary(1, "Forest")
                    .withCardInLibrary(1, "Forest")
                    .withCardInLibrary(1, "Forest")
                    .withCardInLibrary(1, "Forest")
                    .withCardInLibrary(2, "Forest")
                    .withActivePlayer(1)
                    .inPhase(Phase.PRECOMBAT_MAIN, Step.PRECOMBAT_MAIN)
                    .build()

                val castResult = game.castSpell(1, "Cache Grab")
                withClue("Should cast: ${castResult.error}") {
                    castResult.error shouldBe null
                }
                game.resolveStack()

                // Select Glory Seeker (not a Squirrel)
                val graveyard = game.state.getGraveyard(game.player1Id)
                val glorySeeker = graveyard.find { entityId ->
                    game.state.getEntity(entityId)?.get<CardComponent>()?.name == "Glory Seeker"
                }
                game.selectCards(listOf(glorySeeker!!))

                // Should create Food because we control a Squirrel
                withClue("Should have Food token (controls Squirrel)") {
                    game.findPermanent("Food") shouldNotBe null
                }
            }

            test("creates Food token when returning a Squirrel card") {
                val game = scenario()
                    .withPlayers("Player", "Opponent")
                    .withCardInHand(1, "Cache Grab")
                    .withLandsOnBattlefield(1, "Forest", 2)
                    // Put a Squirrel card in library to be milled
                    .withCardInLibrary(1, "Curious Forager") // Squirrel creature
                    .withCardInLibrary(1, "Forest")
                    .withCardInLibrary(1, "Forest")
                    .withCardInLibrary(1, "Forest")
                    .withCardInLibrary(1, "Forest")
                    .withCardInLibrary(2, "Forest")
                    .withActivePlayer(1)
                    .inPhase(Phase.PRECOMBAT_MAIN, Step.PRECOMBAT_MAIN)
                    .build()

                val castResult = game.castSpell(1, "Cache Grab")
                withClue("Should cast: ${castResult.error}") {
                    castResult.error shouldBe null
                }
                game.resolveStack()

                // Select Curious Forager (a Squirrel) from the milled cards
                val graveyard = game.state.getGraveyard(game.player1Id)
                val squirrel = graveyard.find { entityId ->
                    game.state.getEntity(entityId)?.get<CardComponent>()?.name == "Curious Forager"
                }
                withClue("Curious Forager should be in graveyard after milling") {
                    squirrel shouldNotBe null
                }
                game.selectCards(listOf(squirrel!!))

                // Should create Food because we returned a Squirrel
                withClue("Should have Food token (returned Squirrel)") {
                    game.findPermanent("Food") shouldNotBe null
                }

                // Curious Forager should be in hand
                withClue("Curious Forager should be in hand") {
                    game.isInHand(1, "Curious Forager") shouldBe true
                }
            }

            test("no Food when skipping selection and no Squirrel on battlefield") {
                val game = scenario()
                    .withPlayers("Player", "Opponent")
                    .withCardInHand(1, "Cache Grab")
                    .withLandsOnBattlefield(1, "Forest", 2)
                    .withCardInLibrary(1, "Glory Seeker")
                    .withCardInLibrary(1, "Forest")
                    .withCardInLibrary(1, "Forest")
                    .withCardInLibrary(1, "Forest")
                    .withCardInLibrary(1, "Forest")
                    .withCardInLibrary(2, "Forest")
                    .withActivePlayer(1)
                    .inPhase(Phase.PRECOMBAT_MAIN, Step.PRECOMBAT_MAIN)
                    .build()

                val castResult = game.castSpell(1, "Cache Grab")
                withClue("Should cast: ${castResult.error}") {
                    castResult.error shouldBe null
                }
                game.resolveStack()

                // Skip selection (choose nothing)
                game.skipSelection()

                // No Food should be created
                withClue("Should NOT have Food token") {
                    game.findPermanent("Food") shouldBe null
                }
            }
        }
    }
}
