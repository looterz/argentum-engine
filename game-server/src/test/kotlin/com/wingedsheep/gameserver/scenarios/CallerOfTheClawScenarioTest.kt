package com.wingedsheep.gameserver.scenarios

import com.wingedsheep.engine.state.components.identity.CardComponent
import com.wingedsheep.engine.state.components.identity.ControllerComponent
import com.wingedsheep.engine.state.components.identity.TokenComponent
import com.wingedsheep.engine.state.components.player.NonTokenCreaturesDiedThisTurnComponent
import com.wingedsheep.gameserver.ScenarioTestBase
import com.wingedsheep.sdk.core.Phase
import com.wingedsheep.sdk.core.Step
import io.kotest.matchers.shouldBe

/**
 * Scenario test for Caller of the Claw.
 *
 * Card reference:
 * - Caller of the Claw ({2}{G}): 2/2 Creature — Elf
 *   Flash
 *   When Caller of the Claw enters the battlefield, create a 2/2 green Bear creature
 *   token for each nontoken creature put into your graveyard from the battlefield this turn.
 */
class CallerOfTheClawScenarioTest : ScenarioTestBase() {

    private fun TestGame.countTokens(playerNumber: Int, tokenName: String): Int {
        val playerId = if (playerNumber == 1) player1Id else player2Id
        return state.getBattlefield().count { entityId ->
            val container = state.getEntity(entityId) ?: return@count false
            val card = container.get<CardComponent>() ?: return@count false
            val isToken = container.has<TokenComponent>()
            val controller = container.get<ControllerComponent>()?.playerId
            isToken && card.name == tokenName && controller == playerId
        }
    }

    init {
        context("Caller of the Claw ETB token creation") {
            test("creates Bear tokens equal to nontoken creatures that died this turn") {
                // P2 is active and casts Wrath, then P1 casts Caller with Flash
                val game = scenario()
                    .withPlayers("Player", "Opponent")
                    .withCardOnBattlefield(1, "Glory Seeker")
                    .withCardOnBattlefield(1, "Glory Seeker")
                    .withCardInHand(2, "Wrath of God")
                    .withCardInHand(1, "Caller of the Claw")
                    .withLandsOnBattlefield(2, "Plains", 4)
                    .withLandsOnBattlefield(1, "Forest", 3)
                    .withCardInLibrary(1, "Forest")
                    .withCardInLibrary(2, "Plains")
                    .withActivePlayer(2)
                    .inPhase(Phase.PRECOMBAT_MAIN, Step.PRECOMBAT_MAIN)
                    .build()

                // Opponent casts Wrath of God (sorcery, on their turn)
                game.castSpell(2, "Wrath of God")
                game.resolveStack()

                // Both Glory Seekers should be dead
                game.findAllPermanents("Glory Seeker").size shouldBe 0

                // P2 has priority after stack resolves. Pass to P1.
                game.passPriority()

                // Player 1 casts Caller of the Claw (flash)
                game.castSpell(1, "Caller of the Claw")
                // Resolve Caller entering → ETB trigger goes on stack → resolve trigger
                game.resolveStack()

                // Should have 2 Bear tokens (one for each Glory Seeker that died)
                game.countTokens(1, "Bear Token") shouldBe 2

                // Caller of the Claw should be on the battlefield
                game.isOnBattlefield("Caller of the Claw") shouldBe true
            }

            test("creates no tokens if no creatures died this turn") {
                val game = scenario()
                    .withPlayers("Player", "Opponent")
                    .withCardInHand(1, "Caller of the Claw")
                    .withLandsOnBattlefield(1, "Forest", 3)
                    .withCardInLibrary(1, "Forest")
                    .withCardInLibrary(2, "Forest")
                    .withActivePlayer(1)
                    .inPhase(Phase.PRECOMBAT_MAIN, Step.PRECOMBAT_MAIN)
                    .build()

                game.castSpell(1, "Caller of the Claw")
                game.resolveStack()

                // No creatures died, so no Bear tokens
                game.countTokens(1, "Bear Token") shouldBe 0

                // Caller itself should be on the battlefield
                game.isOnBattlefield("Caller of the Claw") shouldBe true
            }

            test("does not count token creatures dying") {
                // P1 is active, P2 kills Glory Seeker with Shock, P1 casts Caller
                val game = scenario()
                    .withPlayers("Player", "Opponent")
                    .withCardOnBattlefield(1, "Glory Seeker")
                    .withCardInHand(1, "Caller of the Claw")
                    .withCardInHand(2, "Shock")
                    .withLandsOnBattlefield(1, "Forest", 3)
                    .withLandsOnBattlefield(2, "Mountain", 1)
                    .withCardInLibrary(1, "Forest")
                    .withCardInLibrary(2, "Mountain")
                    .withActivePlayer(1)
                    .withPriorityPlayer(2)
                    .inPhase(Phase.PRECOMBAT_MAIN, Step.PRECOMBAT_MAIN)
                    .build()

                // Opponent kills Glory Seeker with Shock
                val glorySeekerIds = game.findAllPermanents("Glory Seeker")
                game.castSpell(2, "Shock", glorySeekerIds.first())
                game.resolveStack()

                // Glory Seeker should be dead
                game.isOnBattlefield("Glory Seeker") shouldBe false

                // P2 has priority after resolving their Shock. Pass to P1.
                game.passPriority()

                // P1 now has priority — cast Caller of the Claw (flash)
                game.castSpell(1, "Caller of the Claw")
                game.resolveStack()

                // Should have exactly 1 Bear token (only the Glory Seeker counted)
                game.countTokens(1, "Bear Token") shouldBe 1
            }
        }
    }
}
