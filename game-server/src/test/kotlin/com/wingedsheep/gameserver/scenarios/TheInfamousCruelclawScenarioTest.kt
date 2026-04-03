package com.wingedsheep.gameserver.scenarios

import com.wingedsheep.engine.core.CastSpell
import com.wingedsheep.engine.state.components.stack.ChosenTarget
import com.wingedsheep.engine.state.components.identity.CardComponent
import com.wingedsheep.engine.state.components.identity.MayPlayFromExileComponent
import com.wingedsheep.engine.state.components.identity.PlayWithAdditionalCostComponent
import com.wingedsheep.engine.state.components.identity.PlayWithoutPayingCostComponent
import com.wingedsheep.gameserver.ScenarioTestBase
import com.wingedsheep.sdk.core.Phase
import com.wingedsheep.sdk.core.Step
import com.wingedsheep.sdk.scripting.AdditionalCostPayment
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe

/**
 * Scenario tests for The Infamous Cruelclaw.
 *
 * {1}{B}{R} Legendary Creature — Weasel Mercenary 3/3
 * Menace
 * Whenever The Infamous Cruelclaw deals combat damage to a player, exile cards
 * from the top of your library until you exile a nonland card. You may cast that
 * card by discarding a card rather than paying its mana cost.
 */
class TheInfamousCruelclawScenarioTest : ScenarioTestBase() {

    init {
        context("The Infamous Cruelclaw combat damage trigger") {

            test("exiles cards until nonland and grants cast permission with discard cost") {
                val game = scenario()
                    .withPlayers("Attacker", "Defender")
                    .withCardOnBattlefield(1, "The Infamous Cruelclaw")
                    // Library: two lands on top, then a nonland
                    .withCardInLibrary(1, "Mountain")  // bottom (won't be reached)
                    .withCardInLibrary(1, "Shock")  // nonland - will be exiled and available
                    .withCardInLibrary(1, "Forest")  // top - land, exiled first
                    .withCardInHand(1, "Swamp")  // card to discard as cost
                    .withCardInLibrary(2, "Mountain")
                    .withActivePlayer(1)
                    .inPhase(Phase.PRECOMBAT_MAIN, Step.PRECOMBAT_MAIN)
                    .build()

                // Advance to combat and attack
                game.passUntilPhase(Phase.COMBAT, Step.DECLARE_ATTACKERS)
                game.declareAttackers(mapOf("The Infamous Cruelclaw" to 2))

                // Advance through combat (blockers, damage) - trigger fires and resolves
                game.passUntilPhase(Phase.POSTCOMBAT_MAIN, Step.POSTCOMBAT_MAIN)

                // Shock should be in exile with cast permission
                val exileCards = game.state.getExile(game.player1Id)
                val shockInExile = exileCards.find { id ->
                    game.state.getEntity(id)?.get<CardComponent>()?.name == "Shock"
                }
                shockInExile shouldNotBe null

                // Should have MayPlayFromExile + PlayWithoutPayingCost + PlayWithAdditionalCost
                val shockContainer = game.state.getEntity(shockInExile!!)!!
                shockContainer.get<MayPlayFromExileComponent>() shouldNotBe null
                shockContainer.get<PlayWithoutPayingCostComponent>() shouldNotBe null
                shockContainer.get<PlayWithAdditionalCostComponent>() shouldNotBe null

                // All revealed cards should be in exile
                exileCards.size shouldBe 2  // Forest + Shock
            }

            test("can cast exiled nonland by discarding a card") {
                val game = scenario()
                    .withPlayers("Attacker", "Defender")
                    .withCardOnBattlefield(1, "The Infamous Cruelclaw")
                    .withCardInLibrary(1, "Mountain")  // bottom
                    .withCardInLibrary(1, "Shock")  // nonland
                    .withCardInHand(1, "Swamp")  // card to discard
                    .withCardInLibrary(2, "Mountain")
                    .withActivePlayer(1)
                    .inPhase(Phase.PRECOMBAT_MAIN, Step.PRECOMBAT_MAIN)
                    .build()

                // Combat damage trigger
                game.passUntilPhase(Phase.COMBAT, Step.DECLARE_ATTACKERS)
                game.declareAttackers(mapOf("The Infamous Cruelclaw" to 2))
                game.passUntilPhase(Phase.POSTCOMBAT_MAIN, Step.POSTCOMBAT_MAIN)

                // Defender takes 3 combat damage
                game.getLifeTotal(2) shouldBe 17

                // Find Shock in exile
                val shockId = game.state.getExile(game.player1Id).find { id ->
                    game.state.getEntity(id)?.get<CardComponent>()?.name == "Shock"
                }!!

                // Find Swamp in hand to discard
                val swampId = game.state.getHand(game.player1Id).find { id ->
                    game.state.getEntity(id)?.get<CardComponent>()?.name == "Swamp"
                }!!

                // Cast Shock from exile by discarding Swamp, targeting defender
                val result = game.execute(
                    CastSpell(
                        playerId = game.player1Id,
                        cardId = shockId,
                        targets = listOf(ChosenTarget.Player(game.player2Id)),
                        additionalCostPayment = AdditionalCostPayment(
                            discardedCards = listOf(swampId)
                        )
                    )
                )
                result.error shouldBe null

                // Resolve Shock
                game.resolveStack()

                // Defender should take 2 more damage from Shock (3 combat + 2 shock = 15 life)
                game.getLifeTotal(2) shouldBe 15
            }

            test("cannot cast exiled card without discarding") {
                val game = scenario()
                    .withPlayers("Attacker", "Defender")
                    .withCardOnBattlefield(1, "The Infamous Cruelclaw")
                    .withCardInLibrary(1, "Mountain")
                    .withCardInLibrary(1, "Shock")
                    .withCardInLibrary(2, "Mountain")
                    // No cards in hand to discard
                    .withActivePlayer(1)
                    .inPhase(Phase.PRECOMBAT_MAIN, Step.PRECOMBAT_MAIN)
                    .build()

                // Combat damage trigger
                game.passUntilPhase(Phase.COMBAT, Step.DECLARE_ATTACKERS)
                game.declareAttackers(mapOf("The Infamous Cruelclaw" to 2))
                game.passUntilPhase(Phase.POSTCOMBAT_MAIN, Step.POSTCOMBAT_MAIN)

                // Find Shock in exile
                val shockId = game.state.getExile(game.player1Id).find { id ->
                    game.state.getEntity(id)?.get<CardComponent>()?.name == "Shock"
                }!!

                // Try to cast without discarding - should fail
                val result = game.execute(
                    CastSpell(
                        playerId = game.player1Id,
                        cardId = shockId,
                        targets = listOf(ChosenTarget.Player(game.player2Id))
                    )
                )
                result.error shouldNotBe null
            }
        }
    }
}
