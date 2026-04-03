package com.wingedsheep.gameserver.scenarios

import com.wingedsheep.engine.mechanics.layers.StateProjector
import com.wingedsheep.engine.state.components.battlefield.CountersComponent
import com.wingedsheep.gameserver.ScenarioTestBase
import com.wingedsheep.sdk.core.CounterType
import com.wingedsheep.sdk.core.Phase
import com.wingedsheep.sdk.core.Step
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe

/**
 * Scenario tests for Eluge, the Shoreless Sea.
 *
 * Card reference:
 * - {1}{U}{U}{U} Legendary Creature — Elemental Fish *|*
 *   P/T = number of Islands you control.
 *   Whenever Eluge enters or attacks, put a flood counter on target land.
 *   It's an Island in addition to its other types for as long as it has a flood counter.
 *   First instant/sorcery each turn costs {U} less per flood-counter land you control.
 */
class ElugeTheShoreslessSeaScenarioTest : ScenarioTestBase() {

    private val stateProjector = StateProjector()

    init {
        context("Eluge ETB trigger") {
            test("puts flood counter on target land and makes it an Island") {
                val game = scenario()
                    .withPlayers("Player1", "Player2")
                    .withCardInHand(1, "Eluge, the Shoreless Sea")
                    .withLandsOnBattlefield(1, "Island", 4)
                    .withLandsOnBattlefield(1, "Mountain", 1)
                    .withCardInLibrary(1, "Island")
                    .withCardInLibrary(2, "Island")
                    .withActivePlayer(1)
                    .inPhase(Phase.PRECOMBAT_MAIN, Step.PRECOMBAT_MAIN)
                    .build()

                val castResult = game.castSpell(1, "Eluge, the Shoreless Sea")
                castResult.error shouldBe null
                game.resolveStack()

                // ETB trigger — target the Mountain
                val mountainId = game.findPermanent("Mountain")!!
                game.selectTargets(listOf(mountainId))
                game.resolveStack()

                // Mountain should have a flood counter
                val counters = game.state.getEntity(mountainId)?.get<CountersComponent>()
                counters shouldNotBe null
                counters?.getCount(CounterType.FLOOD) shouldBe 1

                // Mountain should now also be an Island (in addition to Mountain)
                val projected = stateProjector.project(game.state)
                projected.hasSubtype(mountainId, "Island") shouldBe true
                projected.hasSubtype(mountainId, "Mountain") shouldBe true
            }
        }

        context("Eluge dynamic P/T") {
            test("P/T equals number of Islands you control") {
                // 4 Islands + 1 Mountain (will become Island via ETB) = 5 Islands after ETB
                val game = scenario()
                    .withPlayers("Player1", "Player2")
                    .withCardInHand(1, "Eluge, the Shoreless Sea")
                    .withLandsOnBattlefield(1, "Island", 4)
                    .withLandsOnBattlefield(1, "Mountain", 1)
                    .withCardInLibrary(1, "Island")
                    .withCardInLibrary(2, "Island")
                    .withActivePlayer(1)
                    .inPhase(Phase.PRECOMBAT_MAIN, Step.PRECOMBAT_MAIN)
                    .build()

                val castResult = game.castSpell(1, "Eluge, the Shoreless Sea")
                castResult.error shouldBe null
                game.resolveStack()

                // ETB trigger — target the Mountain to make it an Island
                val mountainId = game.findPermanent("Mountain")!!
                game.selectTargets(listOf(mountainId))
                game.resolveStack()

                // Now we have 5 Islands (4 original + Mountain with flood counter)
                val elugeId = game.findPermanent("Eluge, the Shoreless Sea")!!
                val projected = stateProjector.project(game.state)
                projected.getPower(elugeId) shouldBe 5
                projected.getToughness(elugeId) shouldBe 5
            }
        }

        context("Eluge cost reduction") {
            test("first instant/sorcery costs {U} less per flood-counter land") {
                // Shore Up costs {U}. With 1 flood-counter land, reduced to {0} (free).
                val game = scenario()
                    .withPlayers("Player1", "Player2")
                    .withCardInHand(1, "Eluge, the Shoreless Sea")
                    .withCardInHand(1, "Shore Up") // {U} instant — target creature you control
                    .withLandsOnBattlefield(1, "Island", 4)
                    .withLandsOnBattlefield(1, "Mountain", 1)
                    .withCardInLibrary(1, "Island")
                    .withCardInLibrary(2, "Island")
                    .withActivePlayer(1)
                    .inPhase(Phase.PRECOMBAT_MAIN, Step.PRECOMBAT_MAIN)
                    .build()

                // Cast Eluge (costs {1}{U}{U}{U} = 4 mana from 4 Islands, Mountain untapped)
                val castResult = game.castSpell(1, "Eluge, the Shoreless Sea")
                castResult.error shouldBe null
                game.resolveStack()

                // ETB trigger — target the Mountain to give it a flood counter
                val mountainId = game.findPermanent("Mountain")!!
                game.selectTargets(listOf(mountainId))
                game.resolveStack()

                // Mountain has flood counter and is now also an Island.
                // Shore Up costs {U}. With 1 flood-counter land,
                // the first instant costs {U} less → free.
                // Verify via CostCalculator
                val shoreUpDef = cardRegistry.getCard("Shore Up")!!
                val costCalc = com.wingedsheep.engine.mechanics.mana.CostCalculator(cardRegistry)
                val effectiveCost = costCalc.calculateEffectiveCost(game.state, shoreUpDef, game.player1Id)
                effectiveCost.cmc shouldBe 0

                // Cast Shore Up targeting Eluge — should be free
                val elugeId = game.findPermanent("Eluge, the Shoreless Sea")!!
                val shoreUpResult = game.castSpell(1, "Shore Up", elugeId)
                shoreUpResult.error shouldBe null
            }
        }
    }
}
