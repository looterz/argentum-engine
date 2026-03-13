package com.wingedsheep.gameserver.scenarios

import com.wingedsheep.engine.core.*
import com.wingedsheep.gameserver.ScenarioTestBase
import com.wingedsheep.sdk.core.Phase
import com.wingedsheep.sdk.core.Step
import io.kotest.assertions.withClue
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.kotest.matchers.string.shouldContainIgnoringCase

/**
 * Scenario tests for Relic Runner.
 *
 * Card reference:
 * - Relic Runner ({1}{U}): Creature — Human Rogue 2/1
 *   Can't be blocked if you've cast a historic spell this turn.
 */
class RelicRunnerScenarioTest : ScenarioTestBase() {

    init {
        context("Relic Runner evasion") {

            test("can't be blocked after casting a historic spell") {
                val game = scenario()
                    .withPlayers("Player", "Opponent")
                    .withCardOnBattlefield(1, "Relic Runner")
                    .withCardInHand(1, "Gilded Lotus") // Legendary artifact — historic
                    .withLandsOnBattlefield(1, "Island", 5)
                    .withCardOnBattlefield(2, "Hill Giant")
                    .withCardInLibrary(1, "Island")
                    .withCardInLibrary(2, "Island")
                    .withActivePlayer(1)
                    .inPhase(Phase.PRECOMBAT_MAIN, Step.PRECOMBAT_MAIN)
                    .build()

                // Cast Gilded Lotus (historic — legendary artifact)
                val castResult = game.castSpell(1, "Gilded Lotus")
                withClue("Cast should succeed: ${castResult.error}") {
                    castResult.error shouldBe null
                }
                game.resolveStack()

                // Advance to declare attackers
                game.passUntilPhase(Phase.COMBAT, Step.DECLARE_ATTACKERS)

                val runnerId = game.findPermanent("Relic Runner")!!
                val giantId = game.findPermanent("Hill Giant")!!

                // Declare Relic Runner as attacker
                val attackResult = game.execute(
                    DeclareAttackers(game.player1Id, mapOf(runnerId to game.player2Id))
                )
                withClue("Attack should succeed: ${attackResult.error}") {
                    attackResult.error shouldBe null
                }

                // Advance to declare blockers
                game.passUntilPhase(Phase.COMBAT, Step.DECLARE_BLOCKERS)

                // Try to block — should fail
                val blockResult = game.execute(
                    DeclareBlockers(game.player2Id, mapOf(giantId to listOf(runnerId)))
                )
                withClue("Block should fail — controller cast a historic spell") {
                    blockResult.error shouldNotBe null
                    blockResult.error!! shouldContainIgnoringCase "can't be blocked"
                }
            }

            test("can be blocked if no historic spell cast this turn") {
                val game = scenario()
                    .withPlayers("Player", "Opponent")
                    .withCardOnBattlefield(1, "Relic Runner")
                    .withCardOnBattlefield(2, "Hill Giant")
                    .withCardInLibrary(1, "Island")
                    .withCardInLibrary(2, "Island")
                    .withActivePlayer(1)
                    .inPhase(Phase.COMBAT, Step.DECLARE_ATTACKERS)
                    .build()

                val runnerId = game.findPermanent("Relic Runner")!!
                val giantId = game.findPermanent("Hill Giant")!!

                // Declare Relic Runner as attacker
                val attackResult = game.execute(
                    DeclareAttackers(game.player1Id, mapOf(runnerId to game.player2Id))
                )
                withClue("Attack should succeed: ${attackResult.error}") {
                    attackResult.error shouldBe null
                }

                // Advance to declare blockers
                game.passUntilPhase(Phase.COMBAT, Step.DECLARE_BLOCKERS)

                // Block should succeed — no historic spell cast
                val blockResult = game.execute(
                    DeclareBlockers(game.player2Id, mapOf(giantId to listOf(runnerId)))
                )
                withClue("Block should succeed — no historic spell cast this turn") {
                    blockResult.error shouldBe null
                }
            }
        }
    }
}
