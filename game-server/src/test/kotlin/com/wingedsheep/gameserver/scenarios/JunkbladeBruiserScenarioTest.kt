package com.wingedsheep.gameserver.scenarios

import com.wingedsheep.gameserver.ScenarioTestBase
import com.wingedsheep.engine.state.components.player.ManaSpentOnSpellsThisTurnComponent
import com.wingedsheep.sdk.core.Phase
import com.wingedsheep.sdk.core.Step
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.kotest.matchers.ints.shouldBeGreaterThanOrEqual

/**
 * Tests for the Expend trigger mechanic and ManaSpentOnSpellsThisTurnComponent.
 *
 * Junkblade Bruiser has "Whenever you expend 4, this creature gets +2/+1 until end of turn."
 */
class JunkbladeBruiserScenarioTest : ScenarioTestBase() {

    init {
        test("Mana spent on spells is tracked in ManaSpentOnSpellsThisTurnComponent") {
            val game = scenario()
                .withPlayers("Player1", "Player2")
                .withCardInHand(1, "Shock") // {1}{R} = 2 mana
                .withLandsOnBattlefield(1, "Mountain", 2)
                .withCardInLibrary(1, "Mountain")
                .withCardInLibrary(2, "Mountain")
                .inPhase(Phase.PRECOMBAT_MAIN, Step.PRECOMBAT_MAIN)
                .withActivePlayer(1)
                .build()

            // Cast Shock for {1}{R} = 2 mana
            game.castSpellTargetingPlayer(1, "Shock", 2)
            game.resolveStack()

            // Check that mana spent is tracked
            val component = game.state.getEntity(game.player1Id)
                ?.get<ManaSpentOnSpellsThisTurnComponent>()
            component shouldNotBe null
            component!!.totalSpent shouldBeGreaterThanOrEqual 1
        }

        test("Expend 4 triggers Junkblade Bruiser when casting a 4-mana spell") {
            val game = scenario()
                .withPlayers("Player1", "Player2")
                .withCardOnBattlefield(1, "Junkblade Bruiser")
                .withCardInHand(1, "Hill Giant") // {3}{R} = 4 mana
                .withLandsOnBattlefield(1, "Mountain", 4)
                .withCardInLibrary(1, "Mountain")
                .withCardInLibrary(2, "Mountain")
                .inPhase(Phase.PRECOMBAT_MAIN, Step.PRECOMBAT_MAIN)
                .withActivePlayer(1)
                .build()

            // Before casting, check baseline P/T
            val bruiserId = game.findPermanent("Junkblade Bruiser")!!
            game.state.projectedState.getPower(bruiserId) shouldBe 4
            game.state.projectedState.getToughness(bruiserId) shouldBe 5

            // Cast Hill Giant for {3}{R} = 4 mana → crosses Expend 4 threshold
            game.castSpell(1, "Hill Giant")

            // Check mana was tracked
            val component = game.state.getEntity(game.player1Id)
                ?.get<ManaSpentOnSpellsThisTurnComponent>()
            component shouldNotBe null
            component!!.totalSpent shouldBeGreaterThanOrEqual 4

            // Resolve the Expend trigger (auto-resolves or needs manual resolve)
            game.resolveStack()

            // Check Junkblade Bruiser's projected P/T: should be 6/6 (4+2 / 5+1)
            game.state.projectedState.getPower(bruiserId) shouldBe 6
            game.state.projectedState.getToughness(bruiserId) shouldBe 6
        }
    }
}
