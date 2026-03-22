package com.wingedsheep.gameserver.scenarios

import com.wingedsheep.gameserver.ScenarioTestBase
import com.wingedsheep.sdk.core.Phase
import com.wingedsheep.sdk.core.Step
import com.wingedsheep.engine.state.components.battlefield.TappedComponent
import io.kotest.assertions.withClue
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe

class MouseTrapperScenarioTest : ScenarioTestBase() {

    init {
        context("Mouse Trapper Valiant trigger") {
            test("targeting Mouse Trapper with a spell triggers Valiant and taps opponent creature") {
                val game = scenario()
                    .withPlayers("Player1", "Opponent")
                    .withCardOnBattlefield(1, "Mouse Trapper")
                    .withCardOnBattlefield(2, "Glory Seeker")
                    .withCardInHand(1, "Angelic Blessing")
                    .withLandsOnBattlefield(1, "Plains", 3)
                    .withCardInLibrary(1, "Plains")
                    .withCardInLibrary(2, "Plains")
                    .withActivePlayer(1)
                    .inPhase(Phase.PRECOMBAT_MAIN, Step.PRECOMBAT_MAIN)
                    .build()

                val mouseTrapperId = game.findPermanent("Mouse Trapper")!!
                val glorySeekerId = game.findPermanent("Glory Seeker")!!

                // Verify Glory Seeker is untapped
                withClue("Glory Seeker should be untapped initially") {
                    game.state.getEntity(glorySeekerId)?.has<TappedComponent>() shouldBe false
                }

                // Cast Angelic Blessing targeting Mouse Trapper — triggers Valiant
                game.castSpell(1, "Angelic Blessing", mouseTrapperId)

                // Valiant trigger goes on stack; select target (Glory Seeker) for the tap effect
                game.selectTargets(listOf(glorySeekerId))

                // Resolve Valiant trigger
                game.resolveStack()

                // Glory Seeker should be tapped
                withClue("Glory Seeker should be tapped by Valiant trigger") {
                    game.state.getEntity(glorySeekerId)?.has<TappedComponent>() shouldBe true
                }
            }

            test("second targeting in same turn does not trigger Valiant") {
                val game = scenario()
                    .withPlayers("Player1", "Opponent")
                    .withCardOnBattlefield(1, "Mouse Trapper")
                    .withCardOnBattlefield(2, "Glory Seeker")
                    .withCardInHand(1, "Angelic Blessing")
                    .withCardInHand(1, "Angelic Blessing")
                    .withLandsOnBattlefield(1, "Plains", 6)
                    .withCardInLibrary(1, "Plains")
                    .withCardInLibrary(2, "Plains")
                    .withActivePlayer(1)
                    .inPhase(Phase.PRECOMBAT_MAIN, Step.PRECOMBAT_MAIN)
                    .build()

                val mouseTrapperId = game.findPermanent("Mouse Trapper")!!
                val glorySeekerId = game.findPermanent("Glory Seeker")!!

                // First Angelic Blessing targeting Mouse Trapper — triggers Valiant
                game.castSpell(1, "Angelic Blessing", mouseTrapperId)

                // Select target for Valiant trigger (Glory Seeker)
                game.selectTargets(listOf(glorySeekerId))

                // Resolve Valiant trigger first (it's on top of stack)
                game.resolveStack()

                // Resolve Angelic Blessing
                game.resolveStack()

                // Now cast second Angelic Blessing targeting Mouse Trapper
                game.castSpell(1, "Angelic Blessing", mouseTrapperId)

                // No Valiant trigger should fire this time — resolve Angelic Blessing directly
                game.resolveStack()

                // Mouse Trapper should still be on battlefield
                withClue("Mouse Trapper should still be on battlefield") {
                    game.findPermanent("Mouse Trapper") shouldNotBe null
                }
            }

            test("opponent targeting Mouse Trapper does not trigger Valiant") {
                val game = scenario()
                    .withPlayers("Player1", "Opponent")
                    .withCardOnBattlefield(1, "Mouse Trapper")
                    .withCardOnBattlefield(2, "Glory Seeker")
                    .withCardInHand(2, "Angelic Blessing")
                    .withLandsOnBattlefield(2, "Plains", 3)
                    .withCardInLibrary(1, "Plains")
                    .withCardInLibrary(2, "Plains")
                    .withActivePlayer(2)
                    .inPhase(Phase.PRECOMBAT_MAIN, Step.PRECOMBAT_MAIN)
                    .build()

                val mouseTrapperId = game.findPermanent("Mouse Trapper")!!

                // Opponent casts Angelic Blessing targeting our Mouse Trapper
                game.castSpell(2, "Angelic Blessing", mouseTrapperId)

                // No Valiant trigger — resolve Angelic Blessing directly
                game.resolveStack()

                // Glory Seeker should remain untapped (no Valiant trigger fired)
                val glorySeekerId = game.findPermanent("Glory Seeker")!!
                withClue("Glory Seeker should be untapped — Valiant doesn't trigger from opponent targeting") {
                    game.state.getEntity(glorySeekerId)?.has<TappedComponent>() shouldBe false
                }
            }
        }
    }
}
