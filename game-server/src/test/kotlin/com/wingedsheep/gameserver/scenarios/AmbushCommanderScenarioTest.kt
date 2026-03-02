package com.wingedsheep.gameserver.scenarios

import com.wingedsheep.engine.core.*
import com.wingedsheep.engine.state.components.stack.ChosenTarget
import com.wingedsheep.gameserver.ScenarioTestBase
import com.wingedsheep.sdk.core.Phase
import com.wingedsheep.sdk.core.Step
import com.wingedsheep.sdk.scripting.AdditionalCostPayment
import io.kotest.assertions.withClue
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe

/**
 * Scenario tests for Ambush Commander.
 *
 * Ambush Commander: {3}{G}{G} 2/2 Creature — Elf
 * Forests you control are 1/1 green Elf creatures that are still lands.
 * {1}{G}, Sacrifice an Elf: Target creature gets +3/+3 until end of turn.
 */
class AmbushCommanderScenarioTest : ScenarioTestBase() {

    init {
        context("Ambush Commander static ability - Forests become creatures") {

            test("Forests you control become 1/1 green Elf creatures that are still lands") {
                val game = scenario()
                    .withPlayers()
                    .withCardOnBattlefield(1, "Ambush Commander")
                    .withLandsOnBattlefield(1, "Forest", 2)
                    .withCardInLibrary(1, "Forest")
                    .withCardInLibrary(2, "Forest")
                    .withActivePlayer(1)
                    .inPhase(Phase.PRECOMBAT_MAIN, Step.PRECOMBAT_MAIN)
                    .build()

                val forests = game.findAllPermanents("Forest")
                withClue("Should have 2 Forests on battlefield") {
                    forests.size shouldBe 2
                }

                val clientState = game.getClientState(1)
                val forest = clientState.cards[forests[0]]!!

                withClue("Forest should be a Creature") {
                    forest.cardTypes.contains("CREATURE") shouldBe true
                }
                withClue("Forest should still be a Land") {
                    forest.cardTypes.contains("LAND") shouldBe true
                }
                withClue("Forest should have Elf subtype") {
                    forest.subtypes.any { it.equals("Elf", ignoreCase = true) } shouldBe true
                }
                withClue("Forest should be 1/1") {
                    forest.power shouldBe 1
                    forest.toughness shouldBe 1
                }
            }

            test("opponent's Forests are not affected") {
                val game = scenario()
                    .withPlayers()
                    .withCardOnBattlefield(1, "Ambush Commander")
                    .withLandsOnBattlefield(1, "Forest", 1)
                    .withLandsOnBattlefield(2, "Forest", 1)
                    .withCardInLibrary(1, "Forest")
                    .withCardInLibrary(2, "Forest")
                    .withActivePlayer(1)
                    .inPhase(Phase.PRECOMBAT_MAIN, Step.PRECOMBAT_MAIN)
                    .build()

                // Find all Forests, then check which one P2 controls
                val allForests = game.findAllPermanents("Forest")
                val clientState = game.getClientState(2)

                // Find the Forest controlled by P2
                val p2Forest = allForests
                    .map { clientState.cards[it]!! }
                    .find { it.controllerId == game.player2Id }

                withClue("P2 should have a Forest") {
                    p2Forest shouldNotBe null
                }
                withClue("Opponent's Forest should NOT be a Creature") {
                    p2Forest!!.cardTypes.contains("CREATURE") shouldBe false
                }
                withClue("Opponent's Forest should still be a Land") {
                    p2Forest!!.cardTypes.contains("LAND") shouldBe true
                }
            }

            test("non-Forest lands are not affected") {
                val game = scenario()
                    .withPlayers()
                    .withCardOnBattlefield(1, "Ambush Commander")
                    .withLandsOnBattlefield(1, "Forest", 1)
                    .withCardOnBattlefield(1, "Plains")
                    .withCardInLibrary(1, "Forest")
                    .withCardInLibrary(2, "Forest")
                    .withActivePlayer(1)
                    .inPhase(Phase.PRECOMBAT_MAIN, Step.PRECOMBAT_MAIN)
                    .build()

                val plainsId = game.findPermanent("Plains")!!
                val clientState = game.getClientState(1)
                val plains = clientState.cards[plainsId]!!

                withClue("Plains should NOT be a Creature") {
                    plains.cardTypes.contains("CREATURE") shouldBe false
                }
            }
        }

        context("Ambush Commander activated ability - sacrifice Elf for +3/+3") {

            test("sacrifice an Elf Forest to give +3/+3 to target creature") {
                val game = scenario()
                    .withPlayers()
                    .withCardOnBattlefield(1, "Ambush Commander")
                    .withLandsOnBattlefield(1, "Forest", 3) // 2 for mana + 1 to sacrifice
                    .withCardInLibrary(1, "Forest")
                    .withCardInLibrary(2, "Forest")
                    .withActivePlayer(1)
                    .inPhase(Phase.PRECOMBAT_MAIN, Step.PRECOMBAT_MAIN)
                    .build()

                val commanderId = game.findPermanent("Ambush Commander")!!
                val forests = game.findAllPermanents("Forest")

                val cardDef = cardRegistry.getCard("Ambush Commander")!!
                val pumpAbility = cardDef.script.activatedAbilities[0]

                // Activate: {1}{G}, Sacrifice an Elf: Target creature gets +3/+3
                val activateResult = game.execute(
                    ActivateAbility(
                        playerId = game.player1Id,
                        sourceId = commanderId,
                        abilityId = pumpAbility.id,
                        targets = listOf(ChosenTarget.Permanent(commanderId)),
                        costPayment = AdditionalCostPayment(
                            sacrificedPermanents = listOf(forests[0])
                        )
                    )
                )
                withClue("Pump activation should succeed: ${activateResult.error}") {
                    activateResult.error shouldBe null
                }

                // Resolve the ability
                game.resolveStack()

                // Ambush Commander should be 5/5 (2+3/2+3)
                val clientState = game.getClientState(1)
                val commander = clientState.cards[commanderId]
                withClue("Ambush Commander should be 5/5 after pump") {
                    commander!!.power shouldBe 5
                    commander.toughness shouldBe 5
                }

                // Sacrificed Forest should be gone - only 2 remain on battlefield
                val remainingForests = game.findAllPermanents("Forest")
                withClue("Should have one fewer Forest after sacrifice") {
                    remainingForests.size shouldBe 2
                }
            }
        }
    }
}
