package com.wingedsheep.gameserver.scenarios

import com.wingedsheep.engine.mechanics.layers.StateProjector
import com.wingedsheep.engine.state.components.battlefield.TappedComponent
import com.wingedsheep.gameserver.ScenarioTestBase
import com.wingedsheep.sdk.core.Phase
import com.wingedsheep.sdk.core.Step
import io.kotest.assertions.withClue
import io.kotest.matchers.shouldBe

/**
 * Scenario tests for Merfolk Trickster.
 *
 * Card reference:
 * - Merfolk Trickster ({U}{U}): 2/2 Creature — Merfolk Wizard
 *   Flash
 *   When Merfolk Trickster enters the battlefield, tap target creature
 *   an opponent controls. It loses all abilities until end of turn.
 */
class MerfolkTricksterScenarioTest : ScenarioTestBase() {

    private fun isTapped(game: TestGame, name: String): Boolean {
        val entityId = game.findPermanent(name) ?: return false
        return game.state.getEntity(entityId)?.has<TappedComponent>() == true
    }

    init {
        context("Merfolk Trickster") {

            test("ETB taps target creature and removes all abilities") {
                val game = scenario()
                    .withPlayers("Player", "Opponent")
                    .withCardInHand(1, "Merfolk Trickster")
                    .withCardOnBattlefield(2, "Serra Angel") // 4/4 flying, vigilance
                    .withLandsOnBattlefield(1, "Island", 2)
                    .withCardInLibrary(1, "Island")
                    .withCardInLibrary(2, "Island")
                    .withActivePlayer(1)
                    .inPhase(Phase.PRECOMBAT_MAIN, Step.PRECOMBAT_MAIN)
                    .build()

                // Cast Merfolk Trickster
                val castResult = game.castSpell(1, "Merfolk Trickster")
                withClue("Cast should succeed: ${castResult.error}") {
                    castResult.error shouldBe null
                }

                // Resolve the creature spell
                game.resolveStack()

                // ETB trigger fires — select Serra Angel as target
                withClue("Should have pending decision for ETB target") {
                    game.hasPendingDecision() shouldBe true
                }
                val angelId = game.findPermanent("Serra Angel")!!
                game.selectTargets(listOf(angelId))

                // Resolve the triggered ability
                game.resolveStack()

                // Verify Serra Angel is tapped
                withClue("Serra Angel should be tapped") {
                    isTapped(game, "Serra Angel") shouldBe true
                }

                // Verify Serra Angel lost all abilities (check projected state)
                val projected = StateProjector().project(game.state)
                withClue("Serra Angel should have lost all abilities") {
                    projected.hasLostAllAbilities(angelId) shouldBe true
                }
                withClue("Serra Angel should not have flying keyword") {
                    projected.hasKeyword(angelId, "FLYING") shouldBe false
                }
                withClue("Serra Angel should not have vigilance keyword") {
                    projected.hasKeyword(angelId, "VIGILANCE") shouldBe false
                }
            }

            test("ETB only targets creatures opponent controls") {
                val game = scenario()
                    .withPlayers("Player", "Opponent")
                    .withCardInHand(1, "Merfolk Trickster")
                    .withCardOnBattlefield(1, "Serra Angel") // own creature
                    .withLandsOnBattlefield(1, "Island", 2)
                    .withCardInLibrary(1, "Island")
                    .withCardInLibrary(2, "Island")
                    .withActivePlayer(1)
                    .inPhase(Phase.PRECOMBAT_MAIN, Step.PRECOMBAT_MAIN)
                    .build()

                // Cast Merfolk Trickster — no valid targets (only own creature)
                val castResult = game.castSpell(1, "Merfolk Trickster")
                withClue("Cast should succeed (ETB trigger will fizzle)") {
                    castResult.error shouldBe null
                }

                // Resolve creature spell
                game.resolveStack()

                // The ETB trigger should have no valid targets and fizzle
                // Merfolk Trickster should still be on the battlefield
                withClue("Merfolk Trickster should be on the battlefield") {
                    game.isOnBattlefield("Merfolk Trickster") shouldBe true
                }
            }
        }
    }
}
