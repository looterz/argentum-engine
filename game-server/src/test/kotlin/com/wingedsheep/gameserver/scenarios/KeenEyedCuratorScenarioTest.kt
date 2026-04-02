package com.wingedsheep.gameserver.scenarios

import com.wingedsheep.engine.core.ActivateAbility
import com.wingedsheep.engine.state.components.stack.ChosenTarget
import com.wingedsheep.gameserver.ScenarioTestBase
import com.wingedsheep.sdk.core.Keyword
import com.wingedsheep.sdk.core.Phase
import com.wingedsheep.sdk.core.Step
import com.wingedsheep.sdk.core.Zone
import io.kotest.assertions.withClue
import io.kotest.matchers.shouldBe

class KeenEyedCuratorScenarioTest : ScenarioTestBase() {

    init {
        context("Keen-Eyed Curator - linked exile card type tracking") {
            test("gets +4/+4 and trample with 4 card types exiled") {
                val game = scenario()
                    .withPlayers("Player1", "Player2")
                    .withCardOnBattlefield(1, "Keen-Eyed Curator")
                    // 4 different card types in opponent's graveyard
                    .withCardInGraveyard(2, "Pawpatch Recruit")    // creature
                    .withCardInGraveyard(2, "Forest")              // land
                    .withCardInGraveyard(2, "Banishing Light")     // enchantment
                    .withCardInGraveyard(2, "Conduct Electricity") // instant
                    .withLandsOnBattlefield(1, "Forest", 4)
                    .withCardInLibrary(1, "Plains")
                    .withCardInLibrary(2, "Plains")
                    .withActivePlayer(1)
                    .inPhase(Phase.PRECOMBAT_MAIN, Step.PRECOMBAT_MAIN)
                    .build()

                val curatorId = game.findPermanent("Keen-Eyed Curator")!!
                val cardDef = cardRegistry.getCard("Keen-Eyed Curator")!!
                val ability = cardDef.script.activatedAbilities.first()

                // Exile creature
                val creatureId = game.findCardsInGraveyard(2, "Pawpatch Recruit").first()
                game.execute(ActivateAbility(game.player1Id, curatorId, ability.id,
                    targets = listOf(ChosenTarget.Card(creatureId, game.player2Id, Zone.GRAVEYARD))))
                game.resolveStack()

                // Exile land
                val landId = game.findCardsInGraveyard(2, "Forest").first()
                game.execute(ActivateAbility(game.player1Id, curatorId, ability.id,
                    targets = listOf(ChosenTarget.Card(landId, game.player2Id, Zone.GRAVEYARD))))
                game.resolveStack()

                // Exile enchantment
                val enchId = game.findCardsInGraveyard(2, "Banishing Light").first()
                game.execute(ActivateAbility(game.player1Id, curatorId, ability.id,
                    targets = listOf(ChosenTarget.Card(enchId, game.player2Id, Zone.GRAVEYARD))))
                game.resolveStack()

                // Still 3/3 (only 3 types)
                val state3 = game.getClientState(1)
                withClue("Curator should be 3/3 with 3 types") {
                    state3.cards[curatorId]!!.power shouldBe 3
                    state3.cards[curatorId]!!.toughness shouldBe 3
                }

                // Exile instant (4th type)
                val instantId = game.findCardsInGraveyard(2, "Conduct Electricity").first()
                game.execute(ActivateAbility(game.player1Id, curatorId, ability.id,
                    targets = listOf(ChosenTarget.Card(instantId, game.player2Id, Zone.GRAVEYARD))))
                game.resolveStack()

                // Now 7/7 with trample (4+ types → +4/+4)
                val state4 = game.getClientState(1)
                val curatorInfo = state4.cards[curatorId]!!
                withClue("Curator should be 7/7 with 4+ types") {
                    curatorInfo.power shouldBe 7
                    curatorInfo.toughness shouldBe 7
                }
                withClue("Curator should have trample") {
                    curatorInfo.keywords.contains(Keyword.TRAMPLE) shouldBe true
                }
            }
        }
    }
}
