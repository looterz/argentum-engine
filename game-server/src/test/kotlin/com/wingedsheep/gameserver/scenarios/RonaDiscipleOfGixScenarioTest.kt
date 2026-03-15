package com.wingedsheep.gameserver.scenarios

import com.wingedsheep.engine.core.ActivateAbility
import com.wingedsheep.engine.core.CastSpell
import com.wingedsheep.engine.state.components.battlefield.LinkedExileComponent
import com.wingedsheep.engine.state.components.identity.CardComponent
import com.wingedsheep.gameserver.ScenarioTestBase
import com.wingedsheep.sdk.core.Phase
import com.wingedsheep.sdk.core.Step
import com.wingedsheep.sdk.model.EntityId
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe

class RonaDiscipleOfGixScenarioTest : ScenarioTestBase() {

    private fun TestGame.isInExile(playerNumber: Int, cardName: String): Boolean {
        val playerId = if (playerNumber == 1) player1Id else player2Id
        return state.getExile(playerId).any { entityId ->
            state.getEntity(entityId)?.get<CardComponent>()?.name == cardName
        }
    }

    private fun TestGame.findCardInExile(playerNumber: Int, cardName: String): EntityId? {
        val playerId = if (playerNumber == 1) player1Id else player2Id
        return state.getExile(playerId).find { entityId ->
            state.getEntity(entityId)?.get<CardComponent>()?.name == cardName
        }
    }

    init {
        context("Rona, Disciple of Gix ETB trigger") {

            test("exiles target historic card from your graveyard when you choose to") {
                val game = scenario()
                    .withPlayers("Player1", "Player2")
                    .withCardInHand(1, "Rona, Disciple of Gix")
                    .withCardInGraveyard(1, "Gilded Lotus") // Artifact = historic
                    .withLandsOnBattlefield(1, "Island", 2)
                    .withLandsOnBattlefield(1, "Swamp", 1)
                    .withCardInLibrary(1, "Island")
                    .withCardInLibrary(2, "Swamp")
                    .withActivePlayer(1)
                    .inPhase(Phase.PRECOMBAT_MAIN, Step.PRECOMBAT_MAIN)
                    .build()

                game.castSpell(1, "Rona, Disciple of Gix")
                game.resolveStack()

                // ETB trigger — MayEffect asks yes/no first
                game.hasPendingDecision() shouldBe true
                game.answerYesNo(true)

                // Now select target in graveyard
                val lotusInGraveyard = game.findCardsInGraveyard(1, "Gilded Lotus")
                lotusInGraveyard.size shouldBe 1
                game.selectTargets(lotusInGraveyard)

                // Resolve the triggered ability
                game.resolveStack()

                // Gilded Lotus should now be in exile
                game.isInGraveyard(1, "Gilded Lotus") shouldBe false
                game.isInExile(1, "Gilded Lotus") shouldBe true

                // Check LinkedExileComponent on Rona
                val ronaId = game.findPermanent("Rona, Disciple of Gix")!!
                val linked = game.state.getEntity(ronaId)?.get<LinkedExileComponent>()
                linked shouldNotBe null
                linked!!.exiledIds.size shouldBe 1
            }

            test("can decline the may ability") {
                val game = scenario()
                    .withPlayers("Player1", "Player2")
                    .withCardInHand(1, "Rona, Disciple of Gix")
                    .withCardInGraveyard(1, "Gilded Lotus")
                    .withLandsOnBattlefield(1, "Island", 2)
                    .withLandsOnBattlefield(1, "Swamp", 1)
                    .withCardInLibrary(1, "Island")
                    .withCardInLibrary(2, "Swamp")
                    .withActivePlayer(1)
                    .inPhase(Phase.PRECOMBAT_MAIN, Step.PRECOMBAT_MAIN)
                    .build()

                game.castSpell(1, "Rona, Disciple of Gix")
                game.resolveStack()

                // Decline the may ability
                game.hasPendingDecision() shouldBe true
                game.answerYesNo(false)

                // Gilded Lotus should still be in graveyard
                game.isInGraveyard(1, "Gilded Lotus") shouldBe true
            }
        }

        context("Rona activated ability") {

            test("exile top card of library and link to Rona") {
                val game = scenario()
                    .withPlayers("Player1", "Player2")
                    .withCardOnBattlefield(1, "Rona, Disciple of Gix")
                    .withLandsOnBattlefield(1, "Island", 2)
                    .withLandsOnBattlefield(1, "Swamp", 2)
                    .withCardInLibrary(1, "Divination") // Top card
                    .withCardInLibrary(1, "Island")     // Second card (prevent empty library)
                    .withCardInLibrary(2, "Swamp")
                    .withActivePlayer(1)
                    .inPhase(Phase.PRECOMBAT_MAIN, Step.PRECOMBAT_MAIN)
                    .build()

                val ronaId = game.findPermanent("Rona, Disciple of Gix")!!
                val ronaDef = cardRegistry.getCard("Rona, Disciple of Gix")!!
                val abilityId = ronaDef.activatedAbilities.first().id

                val result = game.execute(
                    ActivateAbility(
                        playerId = game.player1Id,
                        sourceId = ronaId,
                        abilityId = abilityId
                    )
                )
                result.error shouldBe null

                game.resolveStack()

                // Top card should be in exile and linked
                game.isInExile(1, "Divination") shouldBe true

                val linked = game.state.getEntity(ronaId)?.get<LinkedExileComponent>()
                linked shouldNotBe null
                linked!!.exiledIds.size shouldBe 1
            }
        }

        context("Cast from linked exile") {

            test("can cast nonland spell exiled with Rona") {
                val game = scenario()
                    .withPlayers("Player1", "Player2")
                    .withCardInHand(1, "Rona, Disciple of Gix")
                    .withCardInGraveyard(1, "Gilded Lotus") // Artifact (historic), costs {5}
                    .withLandsOnBattlefield(1, "Island", 5)
                    .withLandsOnBattlefield(1, "Swamp", 3)
                    .withCardInLibrary(1, "Island")
                    .withCardInLibrary(2, "Swamp")
                    .withActivePlayer(1)
                    .inPhase(Phase.PRECOMBAT_MAIN, Step.PRECOMBAT_MAIN)
                    .build()

                // Cast Rona
                game.castSpell(1, "Rona, Disciple of Gix")
                game.resolveStack()

                // Accept ETB trigger, target Gilded Lotus
                game.answerYesNo(true)
                val lotusInGraveyard = game.findCardsInGraveyard(1, "Gilded Lotus")
                game.selectTargets(lotusInGraveyard)
                game.resolveStack()

                // Gilded Lotus is now in exile linked to Rona
                game.isInExile(1, "Gilded Lotus") shouldBe true

                // Now cast Gilded Lotus from exile (costs {5})
                game.castSpellFromExile(1, "Gilded Lotus")
                game.resolveStack()

                // Gilded Lotus should be on the battlefield
                game.isOnBattlefield("Gilded Lotus") shouldBe true
            }

            test("exiled cards cannot be cast after Rona leaves the battlefield") {
                val game = scenario()
                    .withPlayers("Player1", "Player2")
                    .withCardInHand(1, "Rona, Disciple of Gix")
                    .withCardInGraveyard(1, "Gilded Lotus")
                    .withCardInHand(2, "Blessed Light") // Instant: exile target creature
                    .withLandsOnBattlefield(1, "Island", 5)
                    .withLandsOnBattlefield(1, "Swamp", 3)
                    .withLandsOnBattlefield(2, "Plains", 5)
                    .withCardInLibrary(1, "Island")
                    .withCardInLibrary(2, "Swamp")
                    .withActivePlayer(1)
                    .inPhase(Phase.PRECOMBAT_MAIN, Step.PRECOMBAT_MAIN)
                    .build()

                // Cast Rona, exile Gilded Lotus
                game.castSpell(1, "Rona, Disciple of Gix")
                game.resolveStack()
                game.answerYesNo(true)
                val lotusInGraveyard = game.findCardsInGraveyard(1, "Gilded Lotus")
                game.selectTargets(lotusInGraveyard)
                game.resolveStack()

                game.isInExile(1, "Gilded Lotus") shouldBe true

                // Player 1 passes priority so player 2 can respond
                game.passPriority()

                // Opponent exiles Rona with Blessed Light (instant)
                game.castSpell(2, "Blessed Light", game.findPermanent("Rona, Disciple of Gix"))
                game.resolveStack()

                game.isOnBattlefield("Rona, Disciple of Gix") shouldBe false

                // Gilded Lotus should still be in exile but NOT castable
                game.isInExile(1, "Gilded Lotus") shouldBe true

                // Trying to cast it from exile should fail
                val lotusInExile = game.findCardInExile(1, "Gilded Lotus")!!
                val result = game.execute(CastSpell(game.player1Id, lotusInExile))
                result.error shouldNotBe null
            }
        }
    }
}
