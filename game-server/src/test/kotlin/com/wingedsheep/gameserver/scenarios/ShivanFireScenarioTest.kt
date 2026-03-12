package com.wingedsheep.gameserver.scenarios

import com.wingedsheep.engine.core.CastSpell
import com.wingedsheep.engine.state.components.identity.CardComponent
import com.wingedsheep.engine.state.components.stack.ChosenTarget
import com.wingedsheep.gameserver.ScenarioTestBase
import com.wingedsheep.sdk.core.Phase
import com.wingedsheep.sdk.core.Step
import io.kotest.assertions.withClue
import io.kotest.matchers.shouldBe

/**
 * Scenario tests for Shivan Fire with kicker.
 *
 * Card reference:
 * - Shivan Fire ({R}): Instant
 *   Kicker {4}
 *   Shivan Fire deals 2 damage to target creature or planeswalker.
 *   If this spell was kicked, it deals 4 damage instead.
 */
class ShivanFireScenarioTest : ScenarioTestBase() {

    init {
        context("Shivan Fire kicker") {

            test("unkicked deals 2 damage - creature with 3 toughness survives") {
                val game = scenario()
                    .withPlayers("Player", "Opponent")
                    .withCardInHand(1, "Shivan Fire")
                    .withCardOnBattlefield(2, "Hill Giant") // 3/3
                    .withLandsOnBattlefield(1, "Mountain", 1)
                    .withActivePlayer(1)
                    .inPhase(Phase.PRECOMBAT_MAIN, Step.PRECOMBAT_MAIN)
                    .build()

                val giantId = game.findPermanent("Hill Giant")!!

                val castResult = game.castSpell(1, "Shivan Fire", giantId)
                withClue("Cast should succeed: ${castResult.error}") {
                    castResult.error shouldBe null
                }
                game.resolveStack()

                // Hill Giant is 3/3, unkicked deals 2 damage → survives
                withClue("Hill Giant (3/3) should survive 2 damage") {
                    game.isOnBattlefield("Hill Giant") shouldBe true
                }
            }

            test("unkicked deals 2 damage - creature with 2 toughness dies") {
                val game = scenario()
                    .withPlayers("Player", "Opponent")
                    .withCardInHand(1, "Shivan Fire")
                    .withCardOnBattlefield(2, "Glory Seeker") // 2/2
                    .withLandsOnBattlefield(1, "Mountain", 1)
                    .withActivePlayer(1)
                    .inPhase(Phase.PRECOMBAT_MAIN, Step.PRECOMBAT_MAIN)
                    .build()

                val castResult = game.castSpell(1, "Shivan Fire", game.findPermanent("Glory Seeker")!!)
                withClue("Cast should succeed") {
                    castResult.error shouldBe null
                }
                game.resolveStack()

                withClue("Glory Seeker (2/2) should die from 2 damage") {
                    game.isOnBattlefield("Glory Seeker") shouldBe false
                }
            }

            test("kicked deals 4 damage - creature with 3 toughness dies") {
                val game = scenario()
                    .withPlayers("Player", "Opponent")
                    .withCardInHand(1, "Shivan Fire")
                    .withCardOnBattlefield(2, "Hill Giant") // 3/3
                    .withLandsOnBattlefield(1, "Mountain", 5) // {R} + {4} kicker
                    .withActivePlayer(1)
                    .inPhase(Phase.PRECOMBAT_MAIN, Step.PRECOMBAT_MAIN)
                    .build()

                val giantId = game.findPermanent("Hill Giant")!!
                val playerId = game.player1Id
                val hand = game.state.getHand(playerId)
                val cardId = hand.find { entityId ->
                    game.state.getEntity(entityId)?.get<CardComponent>()?.name == "Shivan Fire"
                }!!

                // Cast with kicker
                val castResult = game.execute(
                    CastSpell(playerId, cardId, targets = listOf(ChosenTarget.Permanent(giantId)), wasKicked = true)
                )
                withClue("Kicked cast should succeed: ${castResult.error}") {
                    castResult.error shouldBe null
                }
                game.resolveStack()

                // Hill Giant is 3/3, kicked deals 4 damage → dies
                withClue("Hill Giant (3/3) should die from 4 kicked damage") {
                    game.isOnBattlefield("Hill Giant") shouldBe false
                }
            }

            test("kicked deals 4 damage - creature with 5 toughness survives") {
                val game = scenario()
                    .withPlayers("Player", "Opponent")
                    .withCardInHand(1, "Shivan Fire")
                    .withCardOnBattlefield(2, "Serra Angel") // 4/4
                    .withLandsOnBattlefield(1, "Mountain", 5)
                    .withActivePlayer(1)
                    .inPhase(Phase.PRECOMBAT_MAIN, Step.PRECOMBAT_MAIN)
                    .build()

                val playerId = game.player1Id
                val hand = game.state.getHand(playerId)
                val cardId = hand.find { entityId ->
                    game.state.getEntity(entityId)?.get<CardComponent>()?.name == "Shivan Fire"
                }!!
                val angelId = game.findPermanent("Serra Angel")!!

                val castResult = game.execute(
                    CastSpell(playerId, cardId, targets = listOf(ChosenTarget.Permanent(angelId)), wasKicked = true)
                )
                withClue("Kicked cast should succeed") {
                    castResult.error shouldBe null
                }
                game.resolveStack()

                // Serra Angel is 4/4, kicked deals 4 damage → dies (4 >= 4)
                withClue("Serra Angel (4/4) should die from 4 kicked damage") {
                    game.isOnBattlefield("Serra Angel") shouldBe false
                }
            }

            test("kicker fails without enough mana") {
                val game = scenario()
                    .withPlayers("Player", "Opponent")
                    .withCardInHand(1, "Shivan Fire")
                    .withCardOnBattlefield(2, "Hill Giant")
                    .withLandsOnBattlefield(1, "Mountain", 1) // Only 1 mana, can't pay {4}{R}
                    .withActivePlayer(1)
                    .inPhase(Phase.PRECOMBAT_MAIN, Step.PRECOMBAT_MAIN)
                    .build()

                val giantId = game.findPermanent("Hill Giant")!!
                val playerId = game.player1Id
                val hand = game.state.getHand(playerId)
                val cardId = hand.find { entityId ->
                    game.state.getEntity(entityId)?.get<CardComponent>()?.name == "Shivan Fire"
                }!!

                val castResult = game.execute(
                    CastSpell(playerId, cardId, targets = listOf(ChosenTarget.Permanent(giantId)), wasKicked = true)
                )
                withClue("Kicked cast should fail with insufficient mana") {
                    castResult.error shouldBe "Not enough mana to cast this spell"
                }
            }
        }
    }
}
