package com.wingedsheep.gameserver.scenarios

import com.wingedsheep.engine.core.ActivateAbility
import com.wingedsheep.engine.state.components.stack.ChosenTarget
import com.wingedsheep.gameserver.ScenarioTestBase
import com.wingedsheep.sdk.core.Phase
import com.wingedsheep.sdk.core.Step
import com.wingedsheep.sdk.scripting.AdditionalCostPayment
import io.kotest.assertions.withClue
import io.kotest.matchers.shouldBe
import io.kotest.matchers.nulls.shouldNotBeNull

/**
 * Scenario tests for Necropolis Fiend.
 *
 * Card reference:
 * - Necropolis Fiend ({7}{B}{B}): Creature — Demon 4/5
 *   Delve, Flying
 *   {X}, {T}, Exile X cards from your graveyard: Target creature gets -X/-X until end of turn.
 */
class NecropolisFiendScenarioTest : ScenarioTestBase() {

    init {
        context("Necropolis Fiend activated ability") {
            test("activating with X=2 gives target creature -2/-2") {
                val game = scenario()
                    .withPlayers("Player1", "Player2")
                    .withCardOnBattlefield(1, "Necropolis Fiend")
                    .withCardInGraveyard(1, "Grizzly Bears")
                    .withCardInGraveyard(1, "Glory Seeker")
                    .withCardOnBattlefield(2, "Hill Giant") // 3/3
                    .withLandsOnBattlefield(1, "Swamp", 2)
                    .withActivePlayer(1)
                    .inPhase(Phase.PRECOMBAT_MAIN, Step.PRECOMBAT_MAIN)
                    .build()

                val fiendId = game.findPermanent("Necropolis Fiend")!!
                val hillGiantId = game.findPermanent("Hill Giant")!!

                val cardDef = cardRegistry.getCard("Necropolis Fiend")!!
                val ability = cardDef.script.activatedAbilities.first()

                // Find graveyard cards for exile
                val graveyardCards = game.state.getGraveyard(game.player1Id)
                val exileTargets = graveyardCards.take(2)

                val result = game.execute(
                    ActivateAbility(
                        playerId = game.player1Id,
                        sourceId = fiendId,
                        abilityId = ability.id,
                        targets = listOf(ChosenTarget.Permanent(hillGiantId)),
                        xValue = 2,
                        costPayment = AdditionalCostPayment(
                            exiledCards = exileTargets
                        )
                    )
                )

                withClue("Ability should activate successfully: ${result.error}") {
                    result.error shouldBe null
                }

                // Graveyard cards should be exiled
                withClue("Grizzly Bears should no longer be in graveyard") {
                    game.isInGraveyard(1, "Grizzly Bears") shouldBe false
                }
                withClue("Glory Seeker should no longer be in graveyard") {
                    game.isInGraveyard(1, "Glory Seeker") shouldBe false
                }

                // Resolve the ability
                game.resolveStack()

                // Hill Giant (3/3) should get -2/-2, becoming 1/1
                val clientState = game.getClientState(2)
                val hillGiantInfo = clientState.cards[hillGiantId]
                withClue("Hill Giant should be 1/1 after -2/-2") {
                    hillGiantInfo.shouldNotBeNull()
                    hillGiantInfo.power shouldBe 1
                    hillGiantInfo.toughness shouldBe 1
                }
            }

            test("activating with X=3 kills a 3/3 creature") {
                val game = scenario()
                    .withPlayers("Player1", "Player2")
                    .withCardOnBattlefield(1, "Necropolis Fiend")
                    .withCardInGraveyard(1, "Grizzly Bears")
                    .withCardInGraveyard(1, "Glory Seeker")
                    .withCardInGraveyard(1, "Hill Giant")
                    .withCardOnBattlefield(2, "Hill Giant") // 3/3
                    .withLandsOnBattlefield(1, "Swamp", 3)
                    .withActivePlayer(1)
                    .inPhase(Phase.PRECOMBAT_MAIN, Step.PRECOMBAT_MAIN)
                    .build()

                val fiendId = game.findPermanent("Necropolis Fiend")!!
                val hillGiantId = game.findPermanent("Hill Giant")!!

                val cardDef = cardRegistry.getCard("Necropolis Fiend")!!
                val ability = cardDef.script.activatedAbilities.first()

                val graveyardCards = game.state.getGraveyard(game.player1Id)
                val exileTargets = graveyardCards.take(3)

                val result = game.execute(
                    ActivateAbility(
                        playerId = game.player1Id,
                        sourceId = fiendId,
                        abilityId = ability.id,
                        targets = listOf(ChosenTarget.Permanent(hillGiantId)),
                        xValue = 3,
                        costPayment = AdditionalCostPayment(
                            exiledCards = exileTargets
                        )
                    )
                )

                withClue("Ability should activate successfully: ${result.error}") {
                    result.error shouldBe null
                }

                // Resolve the ability
                game.resolveStack()

                // Hill Giant (3/3) should get -3/-3 and die
                withClue("Hill Giant should be dead") {
                    game.isOnBattlefield("Hill Giant") shouldBe false
                }
            }
        }
    }
}
