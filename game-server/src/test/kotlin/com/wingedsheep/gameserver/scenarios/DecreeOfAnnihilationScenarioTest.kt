package com.wingedsheep.gameserver.scenarios

import com.wingedsheep.engine.state.components.identity.CardComponent
import com.wingedsheep.gameserver.ScenarioTestBase
import com.wingedsheep.sdk.core.Phase
import com.wingedsheep.sdk.core.Step
import io.kotest.assertions.withClue
import io.kotest.matchers.shouldBe

/**
 * Scenario tests for Decree of Annihilation.
 *
 * Decree of Annihilation: {8}{R}{R} Sorcery
 * Exile all artifacts, creatures, and lands from the battlefield, all cards from all graveyards,
 * and all cards from all hands.
 * Cycling {5}{R}{R}
 * When you cycle Decree of Annihilation, destroy all lands.
 */
class DecreeOfAnnihilationScenarioTest : ScenarioTestBase() {

    private fun ScenarioTestBase.TestGame.isInExile(playerNumber: Int, cardName: String): Boolean {
        val playerId = if (playerNumber == 1) player1Id else player2Id
        return state.getExile(playerId).any { entityId ->
            state.getEntity(entityId)?.get<CardComponent>()?.name == cardName
        }
    }

    private fun ScenarioTestBase.TestGame.exileSize(playerNumber: Int): Int {
        val playerId = if (playerNumber == 1) player1Id else player2Id
        return state.getExile(playerId).size
    }

    init {
        context("Decree of Annihilation main spell") {

            test("exiles all artifacts, creatures, and lands from battlefield") {
                val game = scenario()
                    .withPlayers("Player1", "Player2")
                    .withCardInHand(1, "Decree of Annihilation")
                    .withLandsOnBattlefield(1, "Mountain", 10)
                    .withCardOnBattlefield(1, "Severed Legion")    // creature
                    .withCardOnBattlefield(2, "Glory Seeker")      // creature
                    .withLandsOnBattlefield(2, "Plains", 3)
                    .withCardOnBattlefield(2, "Ark of Blight")     // artifact
                    .withCardInLibrary(1, "Mountain")
                    .withCardInLibrary(2, "Plains")
                    .withActivePlayer(1)
                    .inPhase(Phase.PRECOMBAT_MAIN, Step.PRECOMBAT_MAIN)
                    .build()

                val result = game.castSpell(1, "Decree of Annihilation")
                withClue("Should cast successfully: ${result.error}") {
                    result.error shouldBe null
                }

                game.resolveStack()

                // All creatures should be exiled
                withClue("Player 1's creature should be exiled") {
                    game.isOnBattlefield("Severed Legion") shouldBe false
                }
                withClue("Player 2's creature should be exiled") {
                    game.isOnBattlefield("Glory Seeker") shouldBe false
                }

                // All lands should be exiled
                withClue("Player 1's lands should be exiled") {
                    game.isOnBattlefield("Mountain") shouldBe false
                }
                withClue("Player 2's lands should be exiled") {
                    game.isOnBattlefield("Plains") shouldBe false
                }

                // Artifact should be exiled
                withClue("Player 2's artifact should be exiled") {
                    game.isOnBattlefield("Ark of Blight") shouldBe false
                }
            }

            test("exiles all cards from all graveyards") {
                val game = scenario()
                    .withPlayers("Player1", "Player2")
                    .withCardInHand(1, "Decree of Annihilation")
                    .withLandsOnBattlefield(1, "Mountain", 10)
                    .withCardInGraveyard(1, "Severed Legion")
                    .withCardInGraveyard(2, "Glory Seeker")
                    .withCardInLibrary(1, "Mountain")
                    .withCardInLibrary(2, "Plains")
                    .withActivePlayer(1)
                    .inPhase(Phase.PRECOMBAT_MAIN, Step.PRECOMBAT_MAIN)
                    .build()

                withClue("Player 1 should have a card in graveyard before") {
                    game.graveyardSize(1) shouldBe 1
                }
                withClue("Player 2 should have a card in graveyard before") {
                    game.graveyardSize(2) shouldBe 1
                }

                val result = game.castSpell(1, "Decree of Annihilation")
                withClue("Should cast successfully: ${result.error}") {
                    result.error shouldBe null
                }

                game.resolveStack()

                // Player 1's graveyard has only the Decree itself (sorcery goes to graveyard after resolution)
                withClue("Player 1's graveyard should only have the Decree") {
                    game.graveyardSize(1) shouldBe 1
                    game.isInGraveyard(1, "Decree of Annihilation") shouldBe true
                }
                withClue("Player 2's graveyard should be empty") {
                    game.graveyardSize(2) shouldBe 0
                }
            }

            test("exiles all cards from all hands") {
                val game = scenario()
                    .withPlayers("Player1", "Player2")
                    .withCardInHand(1, "Decree of Annihilation")
                    .withCardInHand(2, "Glory Seeker")
                    .withCardInHand(2, "Plains")
                    .withLandsOnBattlefield(1, "Mountain", 10)
                    .withCardInLibrary(1, "Mountain")
                    .withCardInLibrary(2, "Plains")
                    .withActivePlayer(1)
                    .inPhase(Phase.PRECOMBAT_MAIN, Step.PRECOMBAT_MAIN)
                    .build()

                val result = game.castSpell(1, "Decree of Annihilation")
                withClue("Should cast successfully: ${result.error}") {
                    result.error shouldBe null
                }

                game.resolveStack()

                // Both players' hands should be empty after resolution
                withClue("Player 1's hand should be empty") {
                    game.handSize(1) shouldBe 0
                }
                withClue("Player 2's hand should be empty") {
                    game.handSize(2) shouldBe 0
                }
            }
        }

        context("Decree of Annihilation cycling trigger") {

            test("cycling destroys all lands") {
                val game = scenario()
                    .withPlayers("Player1", "Player2")
                    .withCardInHand(1, "Decree of Annihilation")
                    .withLandsOnBattlefield(1, "Mountain", 7)  // {5}{R}{R} for cycling
                    .withLandsOnBattlefield(2, "Plains", 3)
                    .withCardOnBattlefield(2, "Glory Seeker")  // creature should survive
                    .withCardInLibrary(1, "Mountain")
                    .withCardInLibrary(2, "Plains")
                    .withActivePlayer(1)
                    .inPhase(Phase.PRECOMBAT_MAIN, Step.PRECOMBAT_MAIN)
                    .build()

                val cycleResult = game.cycleCard(1, "Decree of Annihilation")
                withClue("Cycling should succeed: ${cycleResult.error}") {
                    cycleResult.error shouldBe null
                }

                // Resolve the cycling trigger (destroy all lands)
                game.resolveStack()

                // All lands should be destroyed
                withClue("Player 1's lands should be destroyed") {
                    game.isOnBattlefield("Mountain") shouldBe false
                }
                withClue("Player 2's lands should be destroyed") {
                    game.isOnBattlefield("Plains") shouldBe false
                }

                // Creature should survive — cycling trigger only destroys lands
                withClue("Glory Seeker should survive") {
                    game.isOnBattlefield("Glory Seeker") shouldBe true
                }
            }
        }
    }
}
