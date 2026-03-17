package com.wingedsheep.gameserver.scenarios

import com.wingedsheep.engine.core.CastSpell
import com.wingedsheep.engine.state.components.identity.CardComponent
import com.wingedsheep.engine.state.components.player.SkipNextTurnComponent
import com.wingedsheep.engine.state.components.stack.ChosenTarget
import com.wingedsheep.gameserver.ScenarioTestBase
import com.wingedsheep.sdk.core.Phase
import com.wingedsheep.sdk.core.Step
import com.wingedsheep.sdk.core.Zone
import com.wingedsheep.sdk.model.EntityId
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe

/**
 * Scenario test for Karn's Temporal Sundering.
 *
 * Card reference:
 * - Karn's Temporal Sundering ({4}{U}{U}): Legendary Sorcery
 *   "(You may cast a legendary sorcery only if you control a legendary creature or planeswalker.)
 *   Target player takes an extra turn after this one. Return up to one target nonland permanent
 *   to its owner's hand. Exile Karn's Temporal Sundering."
 */
class KarnsTemporalSunderingScenarioTest : ScenarioTestBase() {

    init {
        context("Karn's Temporal Sundering") {
            test("grants extra turn, bounces a nonland permanent, and exiles itself") {
                val game = scenario()
                    .withPlayers("Player1", "Player2")
                    .withCardInHand(1, "Karn's Temporal Sundering")
                    // Need a legendary creature to cast legendary sorcery
                    .withCardOnBattlefield(1, "Lyra Dawnbringer")
                    // 6 mana for {4}{U}{U}
                    .withLandsOnBattlefield(1, "Island", 6)
                    // Target to bounce
                    .withCardOnBattlefield(2, "Serra Angel")
                    // Library cards to prevent empty-library loss
                    .withCardInLibrary(1, "Island")
                    .withCardInLibrary(2, "Island")
                    .withActivePlayer(1)
                    .inPhase(Phase.PRECOMBAT_MAIN, Step.PRECOMBAT_MAIN)
                    .build()

                // Find the Serra Angel to target for bounce
                val serraAngelId = game.findPermanent("Serra Angel")!!

                // Cast Karn's Temporal Sundering with:
                // - Target player: player 1 (self, for extra turn)
                // - Target nonland permanent: Serra Angel (to bounce)
                val hand = game.state.getHand(game.player1Id)
                val cardId = hand.find { entityId ->
                    game.state.getEntity(entityId)?.get<CardComponent>()?.name == "Karn's Temporal Sundering"
                }!!

                val targets = listOf(
                    ChosenTarget.Player(game.player1Id),
                    ChosenTarget.Permanent(serraAngelId)
                )
                game.execute(CastSpell(game.player1Id, cardId, targets))
                game.resolveStack()

                // Serra Angel should be bounced back to player 2's hand
                game.isOnBattlefield("Serra Angel") shouldBe false
                game.state.getHand(game.player2Id).any { entityId ->
                    game.state.getEntity(entityId)?.get<CardComponent>()?.name == "Serra Angel"
                } shouldBe true

                // Karn's Temporal Sundering should be in exile (not graveyard)
                game.isInGraveyard(1, "Karn's Temporal Sundering") shouldBe false
                game.state.getExile(game.player1Id).any { entityId ->
                    game.state.getEntity(entityId)?.get<CardComponent>()?.name == "Karn's Temporal Sundering"
                } shouldBe true

                // Player 1 should get an extra turn (opponent gets SkipNextTurnComponent)
                game.state.getEntity(game.player2Id)?.has<SkipNextTurnComponent>() shouldBe true
            }

            test("can be cast with zero bounce targets (up to one)") {
                val game = scenario()
                    .withPlayers("Player1", "Player2")
                    .withCardInHand(1, "Karn's Temporal Sundering")
                    .withCardOnBattlefield(1, "Lyra Dawnbringer")
                    .withLandsOnBattlefield(1, "Island", 6)
                    .withCardInLibrary(1, "Island")
                    .withCardInLibrary(2, "Island")
                    .withActivePlayer(1)
                    .inPhase(Phase.PRECOMBAT_MAIN, Step.PRECOMBAT_MAIN)
                    .build()

                val hand = game.state.getHand(game.player1Id)
                val cardId = hand.find { entityId ->
                    game.state.getEntity(entityId)?.get<CardComponent>()?.name == "Karn's Temporal Sundering"
                }!!

                // Only target a player, no permanent target (up to one)
                val targets = listOf(
                    ChosenTarget.Player(game.player1Id)
                )
                game.execute(CastSpell(game.player1Id, cardId, targets))
                game.resolveStack()

                // Extra turn should still be granted
                game.state.getEntity(game.player2Id)?.has<SkipNextTurnComponent>() shouldBe true

                // Karn's Temporal Sundering should be in exile
                game.state.getExile(game.player1Id).any { entityId ->
                    game.state.getEntity(entityId)?.get<CardComponent>()?.name == "Karn's Temporal Sundering"
                } shouldBe true
            }

            test("cannot be cast without a legendary creature or planeswalker") {
                val game = scenario()
                    .withPlayers("Player1", "Player2")
                    .withCardInHand(1, "Karn's Temporal Sundering")
                    // No legendary creature - only a regular creature
                    .withCardOnBattlefield(1, "Serra Angel")
                    .withLandsOnBattlefield(1, "Island", 6)
                    .withCardInLibrary(1, "Island")
                    .withCardInLibrary(2, "Island")
                    .withActivePlayer(1)
                    .inPhase(Phase.PRECOMBAT_MAIN, Step.PRECOMBAT_MAIN)
                    .build()

                val hand = game.state.getHand(game.player1Id)
                val cardId = hand.find { entityId ->
                    game.state.getEntity(entityId)?.get<CardComponent>()?.name == "Karn's Temporal Sundering"
                }!!

                val targets = listOf(ChosenTarget.Player(game.player1Id))
                val result = game.execute(CastSpell(game.player1Id, cardId, targets))

                // Should fail to cast
                result.error shouldNotBe null
            }
        }
    }
}
