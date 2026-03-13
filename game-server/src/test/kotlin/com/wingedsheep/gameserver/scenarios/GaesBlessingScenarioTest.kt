package com.wingedsheep.gameserver.scenarios

import com.wingedsheep.gameserver.ScenarioTestBase
import com.wingedsheep.sdk.core.Phase
import com.wingedsheep.sdk.core.Step
import io.kotest.assertions.withClue
import io.kotest.matchers.shouldBe

/**
 * Scenario tests for Gaea's Blessing.
 *
 * Card reference:
 * - Gaea's Blessing ({1}{G}): Sorcery
 *   Target player shuffles up to three target cards from their graveyard into
 *   their library. Draw a card.
 *   When Gaea's Blessing is put into your graveyard from your library,
 *   shuffle your graveyard into your library.
 */
class GaesBlessingScenarioTest : ScenarioTestBase() {

    init {
        context("Gaea's Blessing spell effect") {

            test("returns graveyard card to library and draws a card") {
                val game = scenario()
                    .withPlayers("Player", "Opponent")
                    .withCardInHand(1, "Gaea's Blessing")
                    .withCardInGraveyard(1, "Hill Giant")
                    .withLandsOnBattlefield(1, "Forest", 2)
                    .withCardInLibrary(1, "Mountain")
                    .withCardInLibrary(2, "Mountain")
                    .withActivePlayer(1)
                    .inPhase(Phase.PRECOMBAT_MAIN, Step.PRECOMBAT_MAIN)
                    .build()

                val castResult = game.castSpellTargetingGraveyardCard(1, "Gaea's Blessing", 1, "Hill Giant")
                withClue("Cast should succeed: ${castResult.error}") {
                    castResult.error shouldBe null
                }
                game.resolveStack()

                // Hill Giant should no longer be in graveyard (moved to library)
                withClue("Hill Giant should not be in graveyard") {
                    game.isInGraveyard(1, "Hill Giant") shouldBe false
                }

                // Player should have drawn a card (hand size: initial - 1 for casting + 1 for draw = initial)
                // Gaea's Blessing goes to graveyard after resolving
                withClue("Player should have drawn a card") {
                    // Initial hand had Gaea's Blessing. After casting it (hand -1) and drawing (hand +1),
                    // hand size should be back to initial size - 1 (since Gaea's Blessing was cast)
                    // Actually: initial hand = [Gaea's Blessing], so size 1
                    // After cast: hand = [] (size 0)
                    // After draw: hand = [Mountain] (size 1)
                    game.state.getHand(game.player1Id).size shouldBe 1
                }
            }
        }

        context("Gaea's Blessing mill trigger") {

            test("when milled, shuffles graveyard into library") {
                // Set up so that milling will hit Gaea's Blessing
                // Player has some cards in graveyard and Gaea's Blessing on top of library
                val game = scenario()
                    .withPlayers("Player", "Opponent")
                    .withCardInHand(2, "Weight of Memory") // {3}{U}{U} - draw 3, target mills 3
                    .withCardInGraveyard(1, "Hill Giant")
                    .withCardInGraveyard(1, "Glory Seeker")
                    .withCardInLibrary(1, "Gaea's Blessing") // Will be milled
                    .withCardInLibrary(1, "Mountain")
                    .withCardInLibrary(1, "Forest")
                    .withCardInLibrary(1, "Mountain") // Extra library cards
                    .withCardInLibrary(1, "Forest")
                    .withLandsOnBattlefield(2, "Island", 5)
                    .withCardInLibrary(2, "Mountain")
                    .withCardInLibrary(2, "Mountain")
                    .withCardInLibrary(2, "Mountain")
                    .withCardInLibrary(2, "Mountain")
                    .withActivePlayer(2)
                    .inPhase(Phase.PRECOMBAT_MAIN, Step.PRECOMBAT_MAIN)
                    .build()

                val initialGraveyardSize = game.state.getGraveyard(game.player1Id).size
                withClue("Player 1 should start with 2 cards in graveyard") {
                    initialGraveyardSize shouldBe 2
                }

                // Opponent casts Weight of Memory targeting Player 1 for mill
                val castResult = game.castSpellTargetingPlayer(2, "Weight of Memory", 1)
                withClue("Cast should succeed: ${castResult.error}") {
                    castResult.error shouldBe null
                }
                game.resolveStack()

                // After Gaea's Blessing is milled, its trigger should shuffle all graveyard
                // cards (including itself) into the library. The graveyard should be empty or
                // nearly empty (Gaea's Blessing trigger shuffles everything in).
                withClue("Player 1's graveyard should be empty after Gaea's Blessing trigger") {
                    game.state.getGraveyard(game.player1Id).size shouldBe 0
                }
            }
        }
    }
}
