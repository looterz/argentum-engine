package com.wingedsheep.gameserver.scenarios

import com.wingedsheep.engine.core.CastSpell
import com.wingedsheep.engine.mechanics.layers.StateProjector
import com.wingedsheep.engine.state.components.identity.CardComponent
import com.wingedsheep.engine.state.components.identity.FaceDownComponent
import com.wingedsheep.gameserver.ScenarioTestBase
import com.wingedsheep.sdk.core.Phase
import com.wingedsheep.sdk.core.Step
import io.kotest.assertions.withClue
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe

/**
 * Scenario test for Primal Whisperer.
 *
 * Card reference:
 * - Primal Whisperer (4G): 2/2 Creature — Elf Soldier
 *   This creature gets +2/+2 for each face-down creature on the battlefield.
 *   Morph {3}{G}
 */
class PrimalWhispererScenarioTest : ScenarioTestBase() {

    private val stateProjector = StateProjector()

    init {
        context("Primal Whisperer static +2/+2 per face-down creature") {
            test("base stats are 2/2 with no face-down creatures") {
                val game = scenario()
                    .withPlayers("Player", "Opponent")
                    .withCardOnBattlefield(1, "Primal Whisperer")
                    .withActivePlayer(1)
                    .inPhase(Phase.PRECOMBAT_MAIN, Step.PRECOMBAT_MAIN)
                    .build()

                val whisperer = game.findPermanent("Primal Whisperer")!!
                val projected = stateProjector.project(game.state)

                withClue("Primal Whisperer should be 2/2 with no face-down creatures") {
                    projected.getPower(whisperer) shouldBe 2
                    projected.getToughness(whisperer) shouldBe 2
                }
            }

            test("gets +2/+2 for each face-down creature on the battlefield") {
                val game = scenario()
                    .withPlayers("Player", "Opponent")
                    .withCardOnBattlefield(1, "Primal Whisperer")
                    .withCardInHand(1, "Krosan Cloudscraper") // Has morph
                    .withCardInHand(1, "Willbender")          // Has morph
                    .withLandsOnBattlefield(1, "Forest", 7)
                    .withActivePlayer(1)
                    .inPhase(Phase.PRECOMBAT_MAIN, Step.PRECOMBAT_MAIN)
                    .build()

                val whisperer = game.findPermanent("Primal Whisperer")!!

                // Cast first morph face-down
                val cloudscraperId = game.state.getHand(game.player1Id).first { entityId ->
                    game.state.getEntity(entityId)?.get<CardComponent>()?.name == "Krosan Cloudscraper"
                }
                game.execute(CastSpell(game.player1Id, cloudscraperId, castFaceDown = true))
                game.resolveStack()

                // 1 face-down creature → Primal Whisperer should be 4/4
                val projectedWith1 = stateProjector.project(game.state)
                withClue("Primal Whisperer should be 4/4 with 1 face-down creature") {
                    projectedWith1.getPower(whisperer) shouldBe 4
                    projectedWith1.getToughness(whisperer) shouldBe 4
                }

                // Cast second morph face-down
                val willbenderId = game.state.getHand(game.player1Id).first { entityId ->
                    game.state.getEntity(entityId)?.get<CardComponent>()?.name == "Willbender"
                }
                game.execute(CastSpell(game.player1Id, willbenderId, castFaceDown = true))
                game.resolveStack()

                // 2 face-down creatures → Primal Whisperer should be 6/6
                val projectedWith2 = stateProjector.project(game.state)
                withClue("Primal Whisperer should be 6/6 with 2 face-down creatures") {
                    projectedWith2.getPower(whisperer) shouldBe 6
                    projectedWith2.getToughness(whisperer) shouldBe 6
                }
            }

            test("counts opponent's face-down creatures too") {
                val game = scenario()
                    .withPlayers("Player", "Opponent")
                    .withCardOnBattlefield(1, "Primal Whisperer")
                    .withCardInHand(2, "Krosan Cloudscraper")
                    .withLandsOnBattlefield(2, "Forest", 4)
                    .withActivePlayer(2)
                    .inPhase(Phase.PRECOMBAT_MAIN, Step.PRECOMBAT_MAIN)
                    .build()

                val whisperer = game.findPermanent("Primal Whisperer")!!

                // Opponent casts morph face-down
                val cloudscraperId = game.state.getHand(game.player2Id).first { entityId ->
                    game.state.getEntity(entityId)?.get<CardComponent>()?.name == "Krosan Cloudscraper"
                }
                game.execute(CastSpell(game.player2Id, cloudscraperId, castFaceDown = true))
                game.resolveStack()

                // Opponent's face-down creature should also count
                val projected = stateProjector.project(game.state)
                withClue("Primal Whisperer should be 4/4 counting opponent's face-down creature") {
                    projected.getPower(whisperer) shouldBe 4
                    projected.getToughness(whisperer) shouldBe 4
                }
            }

            test("Primal Whisperer itself face-down does not get the bonus (it's a 2/2 morph)") {
                val game = scenario()
                    .withPlayers("Player", "Opponent")
                    .withCardInHand(1, "Primal Whisperer")
                    .withCardInHand(1, "Willbender")
                    .withLandsOnBattlefield(1, "Forest", 7)
                    .withActivePlayer(1)
                    .inPhase(Phase.PRECOMBAT_MAIN, Step.PRECOMBAT_MAIN)
                    .build()

                // Cast Willbender face-down first
                val willbenderId = game.state.getHand(game.player1Id).first { entityId ->
                    game.state.getEntity(entityId)?.get<CardComponent>()?.name == "Willbender"
                }
                game.execute(CastSpell(game.player1Id, willbenderId, castFaceDown = true))
                game.resolveStack()

                // Cast Primal Whisperer face-down
                val whispererId = game.state.getHand(game.player1Id).first { entityId ->
                    game.state.getEntity(entityId)?.get<CardComponent>()?.name == "Primal Whisperer"
                }
                game.execute(CastSpell(game.player1Id, whispererId, castFaceDown = true))
                game.resolveStack()

                // Both are face-down 2/2 creatures - Primal Whisperer face-down has no abilities
                val faceDownIds = game.state.getBattlefield().filter { entityId ->
                    game.state.getEntity(entityId)?.has<FaceDownComponent>() == true
                }
                withClue("Should have 2 face-down creatures") {
                    faceDownIds.size shouldBe 2
                }

                // All face-down creatures should be 2/2 (morph default)
                val projected = stateProjector.project(game.state)
                for (id in faceDownIds) {
                    withClue("Face-down creature should be 2/2") {
                        projected.getPower(id) shouldBe 2
                        projected.getToughness(id) shouldBe 2
                    }
                }
            }
        }
    }
}
