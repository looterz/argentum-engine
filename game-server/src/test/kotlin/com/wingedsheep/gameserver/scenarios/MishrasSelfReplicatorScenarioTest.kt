package com.wingedsheep.gameserver.scenarios

import com.wingedsheep.gameserver.ScenarioTestBase
import com.wingedsheep.engine.state.components.identity.CardComponent
import com.wingedsheep.engine.state.components.identity.TokenComponent
import com.wingedsheep.sdk.core.Phase
import com.wingedsheep.sdk.core.Step
import com.wingedsheep.sdk.model.CharacteristicValue
import io.kotest.assertions.withClue
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe

/**
 * Scenario tests for Mishra's Self-Replicator.
 *
 * Card reference:
 * - Mishra's Self-Replicator ({5}): Artifact Creature — Assembly-Worker (2/2)
 *   "Whenever you cast a historic spell, you may pay {1}. If you do, create a token
 *    that's a copy of Mishra's Self-Replicator."
 */
class MishrasSelfReplicatorScenarioTest : ScenarioTestBase() {

    init {
        context("Mishra's Self-Replicator") {
            test("creates a token copy when player pays {1} after casting a historic spell") {
                val game = scenario()
                    .withPlayers("Player1", "Opponent")
                    .withCardOnBattlefield(1, "Mishra's Self-Replicator")
                    .withCardInHand(1, "Short Sword") // Artifact = historic
                    .withLandsOnBattlefield(1, "Plains", 2) // {1} for Short Sword + {1} for ability
                    .withCardInLibrary(1, "Plains")
                    .withCardInLibrary(2, "Plains")
                    .withActivePlayer(1)
                    .inPhase(Phase.PRECOMBAT_MAIN, Step.PRECOMBAT_MAIN)
                    .build()

                // Cast Short Sword (artifact = historic) to trigger Mishra's Self-Replicator
                game.castSpell(1, "Short Sword")

                // Trigger goes on stack above Short Sword. Resolve the trigger.
                game.resolveStack()

                // MayPayMana asks "pay {1}?"
                withClue("Should have pending yes/no decision") {
                    game.hasPendingDecision() shouldBe true
                }
                game.answerYesNo(true)

                // Pay mana
                if (game.hasPendingDecision()) {
                    game.submitManaSourcesAutoPay()
                }

                // Resolve remaining stack items (Short Sword)
                game.resolveStack()

                // Should have 2 Mishra's Self-Replicator on battlefield (original + token copy)
                val replicators = game.findAllPermanents("Mishra's Self-Replicator")
                withClue("Should have 2 Mishra's Self-Replicator on battlefield") {
                    replicators.size shouldBe 2
                }

                // Verify the copy is a token
                val tokenReplicator = replicators.find { entityId ->
                    game.state.getEntity(entityId)?.get<TokenComponent>() != null
                }
                withClue("One should be a token") {
                    tokenReplicator shouldNotBe null
                }

                // Verify token has same stats as original
                val tokenCard = game.state.getEntity(tokenReplicator!!)?.get<CardComponent>()!!
                withClue("Token should be named Mishra's Self-Replicator") {
                    tokenCard.name shouldBe "Mishra's Self-Replicator"
                }
                withClue("Token should have 2/2 stats") {
                    tokenCard.baseStats?.power shouldBe CharacteristicValue.Fixed(2)
                    tokenCard.baseStats?.toughness shouldBe CharacteristicValue.Fixed(2)
                }
            }

            test("does not create a token when player declines to pay") {
                val game = scenario()
                    .withPlayers("Player1", "Opponent")
                    .withCardOnBattlefield(1, "Mishra's Self-Replicator")
                    .withCardInHand(1, "Short Sword")
                    .withLandsOnBattlefield(1, "Plains", 2)
                    .withCardInLibrary(1, "Plains")
                    .withCardInLibrary(2, "Plains")
                    .withActivePlayer(1)
                    .inPhase(Phase.PRECOMBAT_MAIN, Step.PRECOMBAT_MAIN)
                    .build()

                game.castSpell(1, "Short Sword")
                game.resolveStack()

                // Decline to pay
                game.answerYesNo(false)
                game.resolveStack()

                // Should have only 1 Mishra's Self-Replicator on battlefield
                val replicators = game.findAllPermanents("Mishra's Self-Replicator")
                withClue("Should have only 1 Mishra's Self-Replicator") {
                    replicators.size shouldBe 1
                }
            }

            test("token copy has the triggered ability and can replicate itself") {
                val game = scenario()
                    .withPlayers("Player1", "Opponent")
                    .withCardOnBattlefield(1, "Mishra's Self-Replicator")
                    .withCardInHand(1, "Short Sword")
                    .withCardInHand(1, "Jousting Lance") // Second artifact
                    .withLandsOnBattlefield(1, "Plains", 6) // {1}+{1} for Short Sword+pay, {2}+{1}+{1} for Jousting Lance+2 pays
                    .withCardInLibrary(1, "Plains")
                    .withCardInLibrary(2, "Plains")
                    .withActivePlayer(1)
                    .inPhase(Phase.PRECOMBAT_MAIN, Step.PRECOMBAT_MAIN)
                    .build()

                // Cast first artifact to create first token copy
                game.castSpell(1, "Short Sword")
                game.resolveStack()
                game.answerYesNo(true)
                if (game.hasPendingDecision()) {
                    game.submitManaSourcesAutoPay()
                }
                game.resolveStack()

                // Verify we have 2 Self-Replicators now
                withClue("Should have 2 Self-Replicators after first copy") {
                    game.findAllPermanents("Mishra's Self-Replicator").size shouldBe 2
                }

                // Cast second artifact — both Self-Replicators should trigger
                game.castSpell(1, "Jousting Lance")

                // Resolve triggers — with 2 Self-Replicators we get 2 triggers
                // Handle all triggers and their may-pay decisions
                for (i in 1..10) { // Safety limit
                    if (!game.hasPendingDecision()) {
                        game.resolveStack()
                    }
                    if (game.hasPendingDecision()) {
                        game.answerYesNo(true)
                        if (game.hasPendingDecision()) {
                            game.submitManaSourcesAutoPay()
                        }
                    } else {
                        break
                    }
                }

                // Should have at least 3 Mishra's Self-Replicator (original + 1 from first cast + at least 1 more)
                // With 2 Self-Replicators triggering on the second cast, we expect 4 total
                val replicators = game.findAllPermanents("Mishra's Self-Replicator")
                withClue("Token copy should also trigger, creating more copies (expected 4: original + 1 + 2)") {
                    replicators.size shouldBe 4
                }
            }
        }
    }
}
