package com.wingedsheep.gameserver.scenarios

import com.wingedsheep.gameserver.ScenarioTestBase
import com.wingedsheep.sdk.core.Phase
import com.wingedsheep.sdk.core.Step
import io.kotest.assertions.withClue
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe

/**
 * Scenario tests for Barrage of Boulders.
 *
 * Card reference:
 * - Barrage of Boulders ({2}{R}): Sorcery
 *   "Barrage of Boulders deals 1 damage to each creature you don't control.
 *    Ferocious — If you control a creature with power 4 or greater,
 *    creatures can't block this turn."
 */
class BarrageOfBouldersScenarioTest : ScenarioTestBase() {

    init {
        context("Barrage of Boulders damage") {
            test("deals 1 damage to each creature opponent controls") {
                val game = scenario()
                    .withPlayers("Caster", "Opponent")
                    .withCardOnBattlefield(2, "Grizzly Bears")  // 2/2 - survives 1 damage
                    .withCardOnBattlefield(2, "Muck Rats")      // 1/1 - dies to 1 damage
                    .withCardInHand(1, "Barrage of Boulders")
                    .withLandsOnBattlefield(1, "Mountain", 3)
                    .withActivePlayer(1)
                    .inPhase(Phase.PRECOMBAT_MAIN, Step.PRECOMBAT_MAIN)
                    .build()

                val castResult = game.castSpell(1, "Barrage of Boulders")
                withClue("Should cast successfully: ${castResult.error}") {
                    castResult.error shouldBe null
                }
                game.resolveStack()

                // Muck Rats (1/1) should die from 1 damage
                withClue("Muck Rats (1/1) should die from 1 damage") {
                    game.isOnBattlefield("Muck Rats") shouldBe false
                }

                // Grizzly Bears (2/2) should survive with 1 damage
                withClue("Grizzly Bears (2/2) should survive") {
                    game.isOnBattlefield("Grizzly Bears") shouldBe true
                }
            }

            test("does not damage caster's own creatures") {
                val game = scenario()
                    .withPlayers("Caster", "Opponent")
                    .withCardOnBattlefield(1, "Muck Rats")   // 1/1 - own creature
                    .withCardOnBattlefield(2, "Muck Rats")   // 1/1 - opponent's
                    .withCardInHand(1, "Barrage of Boulders")
                    .withLandsOnBattlefield(1, "Mountain", 3)
                    .withActivePlayer(1)
                    .inPhase(Phase.PRECOMBAT_MAIN, Step.PRECOMBAT_MAIN)
                    .build()

                game.castSpell(1, "Barrage of Boulders")
                game.resolveStack()

                // Only one Muck Rats should remain (the caster's)
                val rats = game.findAllPermanents("Muck Rats")
                withClue("Only caster's Muck Rats should survive") {
                    rats.size shouldBe 1
                }
            }
        }

        context("Barrage of Boulders Ferocious - can't block") {
            test("creatures can't block when caster controls creature with power 4+") {
                val game = scenario()
                    .withPlayers("Caster", "Opponent")
                    .withCardOnBattlefield(1, "Tusked Colossodon")  // 6/5 - enables Ferocious
                    .withCardOnBattlefield(2, "Grizzly Bears")       // 2/2 - would-be blocker
                    .withCardInHand(1, "Barrage of Boulders")
                    .withLandsOnBattlefield(1, "Mountain", 3)
                    .withActivePlayer(1)
                    .inPhase(Phase.PRECOMBAT_MAIN, Step.PRECOMBAT_MAIN)
                    .build()

                game.castSpell(1, "Barrage of Boulders")
                game.resolveStack()

                // Advance to combat - attack with Tusked Colossodon
                game.passUntilPhase(Phase.COMBAT, Step.DECLARE_ATTACKERS)
                game.declareAttackers(mapOf("Tusked Colossodon" to 2))

                game.passUntilPhase(Phase.COMBAT, Step.DECLARE_BLOCKERS)

                // Grizzly Bears should NOT be able to block due to Ferocious
                val blockResult = game.declareBlockers(mapOf(
                    "Grizzly Bears" to listOf("Tusked Colossodon")
                ))
                withClue("Creatures should not be able to block after Ferocious") {
                    blockResult.error shouldNotBe null
                }
            }

            test("creatures CAN block when caster does NOT control creature with power 4+") {
                val game = scenario()
                    .withPlayers("Caster", "Opponent")
                    .withCardOnBattlefield(1, "Grizzly Bears")   // 2/2 - too small for Ferocious
                    .withCardOnBattlefield(2, "Grizzly Bears")   // 2/2 - blocker
                    .withCardInHand(1, "Barrage of Boulders")
                    .withLandsOnBattlefield(1, "Mountain", 3)
                    .withActivePlayer(1)
                    .inPhase(Phase.PRECOMBAT_MAIN, Step.PRECOMBAT_MAIN)
                    .build()

                game.castSpell(1, "Barrage of Boulders")
                game.resolveStack()

                // Advance to combat
                game.passUntilPhase(Phase.COMBAT, Step.DECLARE_ATTACKERS)

                val attackerId = game.state.getBattlefield().find { entityId ->
                    val container = game.state.getEntity(entityId)
                    val card = container?.get<com.wingedsheep.engine.state.components.identity.CardComponent>()
                    val controller = container?.get<com.wingedsheep.engine.state.components.identity.ControllerComponent>()
                    card?.name == "Grizzly Bears" && controller?.playerId == game.player1Id
                }!!

                game.execute(com.wingedsheep.engine.core.DeclareAttackers(
                    game.player1Id,
                    mapOf(attackerId to game.player2Id)
                ))

                game.passUntilPhase(Phase.COMBAT, Step.DECLARE_BLOCKERS)

                val blockerId = game.state.getBattlefield().find { entityId ->
                    val container = game.state.getEntity(entityId)
                    val card = container?.get<com.wingedsheep.engine.state.components.identity.CardComponent>()
                    val controller = container?.get<com.wingedsheep.engine.state.components.identity.ControllerComponent>()
                    card?.name == "Grizzly Bears" && controller?.playerId == game.player2Id
                }!!

                // Without Ferocious, blocking should be allowed
                val blockResult = game.execute(
                    com.wingedsheep.engine.core.DeclareBlockers(
                        game.player2Id,
                        mapOf(blockerId to listOf(attackerId))
                    )
                )
                withClue("Creatures should be able to block without Ferocious: ${blockResult.error}") {
                    blockResult.error shouldBe null
                }
            }
        }
    }
}
