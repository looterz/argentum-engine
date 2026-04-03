package com.wingedsheep.gameserver.scenarios

import com.wingedsheep.gameserver.ScenarioTestBase
import com.wingedsheep.sdk.core.Phase
import com.wingedsheep.sdk.core.Step
import com.wingedsheep.engine.state.components.identity.CardComponent
import io.kotest.matchers.shouldBe

/**
 * Scenario tests for Daring Waverider.
 *
 * Card reference:
 * - Daring Waverider (4UU): Creature — Otter Wizard 4/4
 *   "When this creature enters, you may cast target instant or sorcery card with mana value
 *   4 or less from your graveyard without paying its mana cost. If that spell would be put
 *   into your graveyard, exile it instead."
 */
class DaringWaveriderScenarioTest : ScenarioTestBase() {

    init {
        context("Daring Waverider") {
            test("ETB exiles target sorcery from graveyard and grants free cast") {
                val game = scenario()
                    .withPlayers("Player 1", "Player 2")
                    .withCardInHand(1, "Daring Waverider")
                    .withCardInGraveyard(1, "Volcanic Hammer")
                    .withLandsOnBattlefield(1, "Island", 6)
                    .withActivePlayer(1)
                    .inPhase(Phase.PRECOMBAT_MAIN, Step.PRECOMBAT_MAIN)
                    .build()

                // Cast Daring Waverider
                game.castSpell(1, "Daring Waverider")
                game.resolveStack()

                // ETB trigger fires — need to select target from graveyard
                val hammerIds = game.findCardsInGraveyard(1, "Volcanic Hammer")
                game.selectTargets(hammerIds)
                game.resolveStack()

                // The trigger resolved: Volcanic Hammer should now be in exile
                val exileZone = game.state.getExile(game.player1Id)
                val hammerInExile = exileZone.any { entityId ->
                    game.state.getEntity(entityId)?.get<CardComponent>()?.name == "Volcanic Hammer"
                }
                hammerInExile shouldBe true

                // Volcanic Hammer should no longer be in graveyard
                game.isInGraveyard(1, "Volcanic Hammer") shouldBe false
            }

            test("cast spell from exile for free, spell goes to exile after resolution") {
                val game = scenario()
                    .withPlayers("Player 1", "Player 2")
                    .withCardInHand(1, "Daring Waverider")
                    .withCardInGraveyard(1, "Volcanic Hammer")
                    .withCardOnBattlefield(2, "Grizzly Bears")
                    .withLandsOnBattlefield(1, "Island", 6)
                    .withActivePlayer(1)
                    .inPhase(Phase.PRECOMBAT_MAIN, Step.PRECOMBAT_MAIN)
                    .build()

                // Cast Daring Waverider
                game.castSpell(1, "Daring Waverider")
                game.resolveStack()

                // ETB trigger - select Volcanic Hammer from graveyard
                val hammerIds = game.findCardsInGraveyard(1, "Volcanic Hammer")
                game.selectTargets(hammerIds)
                game.resolveStack()

                // Now cast Volcanic Hammer from exile for free, targeting opponent's creature
                val bearsId = game.findPermanent("Grizzly Bears")!!
                game.castSpellFromExile(1, "Volcanic Hammer", bearsId)
                game.resolveStack()

                // Grizzly Bears should be dead (3 damage to a 2/2)
                game.isOnBattlefield("Grizzly Bears") shouldBe false

                // Volcanic Hammer should be in exile (not graveyard) due to ExileAfterResolveComponent
                val exileZone = game.state.getExile(game.player1Id)
                val hammerInExile = exileZone.any { entityId ->
                    game.state.getEntity(entityId)?.get<CardComponent>()?.name == "Volcanic Hammer"
                }
                hammerInExile shouldBe true
                game.isInGraveyard(1, "Volcanic Hammer") shouldBe false
            }
        }
    }
}
