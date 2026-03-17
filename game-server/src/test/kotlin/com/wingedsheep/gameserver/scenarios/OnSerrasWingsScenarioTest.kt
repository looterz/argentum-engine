package com.wingedsheep.gameserver.scenarios

import com.wingedsheep.engine.mechanics.layers.StateProjector
import com.wingedsheep.gameserver.ScenarioTestBase
import com.wingedsheep.sdk.core.Keyword
import com.wingedsheep.sdk.core.Phase
import com.wingedsheep.sdk.core.Step
import io.kotest.assertions.withClue
import io.kotest.matchers.shouldBe

/**
 * Scenario test for On Serra's Wings.
 *
 * Card reference:
 * - On Serra's Wings ({3}{W}): Legendary Enchantment — Aura
 *   "Enchant creature"
 *   "Enchanted creature is legendary, gets +1/+1, and has flying, vigilance, and lifelink."
 */
class OnSerrasWingsScenarioTest : ScenarioTestBase() {

    private val stateProjector = StateProjector()

    init {
        context("On Serra's Wings grants abilities and legendary") {

            test("enchanted creature gets +1/+1, flying, vigilance, lifelink, and becomes legendary") {
                val game = scenario()
                    .withPlayers("Player", "Opponent")
                    .withCardOnBattlefield(1, "Steel Leaf Champion") // 5/4
                    .withCardInHand(1, "On Serra's Wings")
                    .withLandsOnBattlefield(1, "Plains", 4)
                    .withCardInLibrary(1, "Plains")
                    .withCardInLibrary(2, "Plains")
                    .withActivePlayer(1)
                    .inPhase(Phase.PRECOMBAT_MAIN, Step.PRECOMBAT_MAIN)
                    .build()

                val creatureId = game.findPermanent("Steel Leaf Champion")!!

                game.castSpell(1, "On Serra's Wings", creatureId)
                game.resolveStack()

                val projected = stateProjector.project(game.state)

                withClue("Enchanted creature should have flying") {
                    projected.hasKeyword(creatureId, Keyword.FLYING) shouldBe true
                }
                withClue("Enchanted creature should have vigilance") {
                    projected.hasKeyword(creatureId, Keyword.VIGILANCE) shouldBe true
                }
                withClue("Enchanted creature should have lifelink") {
                    projected.hasKeyword(creatureId, Keyword.LIFELINK) shouldBe true
                }
                withClue("Enchanted creature should be legendary") {
                    projected.isLegendary(creatureId) shouldBe true
                }
                withClue("Enchanted creature should be 6/5") {
                    projected.getPower(creatureId) shouldBe 6
                    projected.getToughness(creatureId) shouldBe 5
                }
            }

            test("creature loses legendary when On Serra's Wings is removed") {
                val game = scenario()
                    .withPlayers("Player", "Opponent")
                    .withCardOnBattlefield(1, "Steel Leaf Champion")
                    .withCardInHand(1, "On Serra's Wings")
                    .withCardInHand(1, "Invoke the Divine") // {2}{W} destroy artifact or enchantment
                    .withLandsOnBattlefield(1, "Plains", 7)
                    .withCardInLibrary(1, "Plains")
                    .withCardInLibrary(2, "Plains")
                    .withActivePlayer(1)
                    .inPhase(Phase.PRECOMBAT_MAIN, Step.PRECOMBAT_MAIN)
                    .build()

                val creatureId = game.findPermanent("Steel Leaf Champion")!!

                game.castSpell(1, "On Serra's Wings", creatureId)
                game.resolveStack()

                val projectedBefore = stateProjector.project(game.state)
                withClue("Creature should be legendary while enchanted") {
                    projectedBefore.isLegendary(creatureId) shouldBe true
                }

                // Player destroys their own aura
                val auraId = game.findPermanent("On Serra's Wings")!!
                game.castSpell(1, "Invoke the Divine", auraId)
                game.resolveStack()

                val projectedAfter = stateProjector.project(game.state)
                withClue("Creature should no longer be legendary after aura removed") {
                    projectedAfter.isLegendary(creatureId) shouldBe false
                }
                withClue("Creature should return to base stats") {
                    projectedAfter.getPower(creatureId) shouldBe 5
                    projectedAfter.getToughness(creatureId) shouldBe 4
                }
            }
        }
    }
}
