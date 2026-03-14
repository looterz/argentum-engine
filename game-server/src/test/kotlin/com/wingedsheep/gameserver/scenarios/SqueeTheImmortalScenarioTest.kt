package com.wingedsheep.gameserver.scenarios

import com.wingedsheep.gameserver.ScenarioTestBase
import com.wingedsheep.sdk.core.Phase
import com.wingedsheep.sdk.core.Step
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe

/**
 * Scenario tests for Squee, the Immortal.
 *
 * Card reference:
 * - Squee, the Immortal ({1}{R}{R}): Legendary Creature — Goblin 2/1
 *   You may cast this card from your graveyard or from exile.
 */
class SqueeTheImmortalScenarioTest : ScenarioTestBase() {

    init {
        context("Squee, the Immortal - cast from graveyard") {

            test("can cast Squee from graveyard") {
                val game = scenario()
                    .withPlayers("Player", "Opponent")
                    .withCardInGraveyard(1, "Squee, the Immortal")
                    .withLandsOnBattlefield(1, "Mountain", 3)
                    .inPhase(Phase.PRECOMBAT_MAIN, Step.PRECOMBAT_MAIN)
                    .build()

                game.castSpellFromGraveyard(1, "Squee, the Immortal")
                game.resolveStack()

                game.isOnBattlefield("Squee, the Immortal") shouldBe true
                game.isInGraveyard(1, "Squee, the Immortal") shouldBe false
            }

            test("cannot cast Squee from graveyard without enough mana") {
                val game = scenario()
                    .withPlayers("Player", "Opponent")
                    .withCardInGraveyard(1, "Squee, the Immortal")
                    .withLandsOnBattlefield(1, "Mountain", 2) // Only 2 mana, needs 3
                    .inPhase(Phase.PRECOMBAT_MAIN, Step.PRECOMBAT_MAIN)
                    .build()

                val result = game.castSpellFromGraveyard(1, "Squee, the Immortal")
                result.error shouldNotBe null
            }
        }

        context("Squee, the Immortal - cast from exile") {

            test("can cast Squee from exile") {
                val game = scenario()
                    .withPlayers("Player", "Opponent")
                    .withCardInExile(1, "Squee, the Immortal")
                    .withLandsOnBattlefield(1, "Mountain", 3)
                    .inPhase(Phase.PRECOMBAT_MAIN, Step.PRECOMBAT_MAIN)
                    .build()

                game.castSpellFromExile(1, "Squee, the Immortal")
                game.resolveStack()

                game.isOnBattlefield("Squee, the Immortal") shouldBe true
            }
        }

        context("Squee, the Immortal - cast from hand") {

            test("can still cast Squee from hand normally") {
                val game = scenario()
                    .withPlayers("Player", "Opponent")
                    .withCardInHand(1, "Squee, the Immortal")
                    .withLandsOnBattlefield(1, "Mountain", 3)
                    .inPhase(Phase.PRECOMBAT_MAIN, Step.PRECOMBAT_MAIN)
                    .build()

                game.castSpell(1, "Squee, the Immortal")
                game.resolveStack()

                game.isOnBattlefield("Squee, the Immortal") shouldBe true
            }
        }
    }
}
