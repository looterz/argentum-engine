package com.wingedsheep.gameserver.scenarios

import com.wingedsheep.engine.core.ActivateAbility
import com.wingedsheep.engine.core.SelectCardsDecision
import com.wingedsheep.engine.mechanics.layers.StateProjector
import com.wingedsheep.gameserver.ScenarioTestBase
import com.wingedsheep.sdk.core.Keyword
import com.wingedsheep.sdk.core.Phase
import com.wingedsheep.sdk.core.Step
import io.kotest.assertions.withClue
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf

/**
 * Scenario tests for Dauntless Bodyguard.
 *
 * Card reference:
 * - Dauntless Bodyguard ({W}): Creature — Human Knight 2/1
 *   "As Dauntless Bodyguard enters the battlefield, choose another creature you control.
 *    Sacrifice Dauntless Bodyguard: The chosen creature gains indestructible until end of turn."
 */
class DauntlessBodyguardScenarioTest : ScenarioTestBase() {

    private val stateProjector = StateProjector()

    init {
        context("Dauntless Bodyguard - as enters, choose creature") {

            test("choosing a creature on entry and granting indestructible") {
                val game = scenario()
                    .withPlayers("Player1", "Player2")
                    .withCardInHand(1, "Dauntless Bodyguard")
                    .withCardOnBattlefield(1, "Serra Angel")
                    .withLandsOnBattlefield(1, "Plains", 1)
                    .withActivePlayer(1)
                    .inPhase(Phase.PRECOMBAT_MAIN, Step.PRECOMBAT_MAIN)
                    .build()

                // Cast Dauntless Bodyguard
                val castResult = game.castSpell(1, "Dauntless Bodyguard")
                withClue("Should cast successfully: ${castResult.error}") {
                    castResult.error shouldBe null
                }

                // Resolve - should pause for creature choice
                game.resolveStack()

                withClue("Should have pending creature choice decision") {
                    game.hasPendingDecision() shouldBe true
                }
                val decision = game.getPendingDecision()
                decision.shouldNotBeNull()
                decision.shouldBeInstanceOf<SelectCardsDecision>()

                // Choose Serra Angel
                val serraId = game.findPermanent("Serra Angel")!!
                game.selectCards(listOf(serraId))

                // Bodyguard should be on the battlefield
                withClue("Dauntless Bodyguard should be on battlefield") {
                    game.isOnBattlefield("Dauntless Bodyguard") shouldBe true
                }

                // Activate: sacrifice self, chosen creature gains indestructible
                val bodyguardId = game.findPermanent("Dauntless Bodyguard")!!
                val cardDef = cardRegistry.getCard("Dauntless Bodyguard")!!
                val ability = cardDef.script.activatedAbilities.first()

                val activateResult = game.execute(
                    ActivateAbility(
                        playerId = game.player1Id,
                        sourceId = bodyguardId,
                        abilityId = ability.id
                    )
                )

                withClue("Activation should succeed: ${activateResult.error}") {
                    activateResult.error shouldBe null
                }

                // Bodyguard should be sacrificed
                withClue("Bodyguard should be in graveyard") {
                    game.isInGraveyard(1, "Dauntless Bodyguard") shouldBe true
                }

                // Resolve ability
                game.resolveStack()

                // Serra Angel should have indestructible
                val projected = stateProjector.project(game.state)
                withClue("Serra Angel should have indestructible") {
                    projected.hasKeyword(serraId, Keyword.INDESTRUCTIBLE) shouldBe true
                }
            }

            test("enters without choice when no other creatures on battlefield") {
                val game = scenario()
                    .withPlayers("Player1", "Player2")
                    .withCardInHand(1, "Dauntless Bodyguard")
                    .withLandsOnBattlefield(1, "Plains", 1)
                    .withActivePlayer(1)
                    .inPhase(Phase.PRECOMBAT_MAIN, Step.PRECOMBAT_MAIN)
                    .build()

                // Cast Dauntless Bodyguard
                game.castSpell(1, "Dauntless Bodyguard")

                // Resolve - should NOT pause for creature choice (no other creatures)
                game.resolveStack()

                // Should be on battlefield without any decision
                withClue("Dauntless Bodyguard should be on battlefield") {
                    game.isOnBattlefield("Dauntless Bodyguard") shouldBe true
                }
            }

            test("indestructible wears off at end of turn") {
                val game = scenario()
                    .withPlayers("Player1", "Player2")
                    .withCardInHand(1, "Dauntless Bodyguard")
                    .withCardOnBattlefield(1, "Serra Angel")
                    .withLandsOnBattlefield(1, "Plains", 1)
                    .withActivePlayer(1)
                    .inPhase(Phase.PRECOMBAT_MAIN, Step.PRECOMBAT_MAIN)
                    .build()

                // Cast and choose Serra Angel
                game.castSpell(1, "Dauntless Bodyguard")
                game.resolveStack()
                val serraId = game.findPermanent("Serra Angel")!!
                game.selectCards(listOf(serraId))

                // Activate ability
                val bodyguardId = game.findPermanent("Dauntless Bodyguard")!!
                val cardDef = cardRegistry.getCard("Dauntless Bodyguard")!!
                val ability = cardDef.script.activatedAbilities.first()
                game.execute(
                    ActivateAbility(
                        playerId = game.player1Id,
                        sourceId = bodyguardId,
                        abilityId = ability.id
                    )
                )
                game.resolveStack()

                // Serra Angel should have indestructible now
                var projected = stateProjector.project(game.state)
                projected.hasKeyword(serraId, Keyword.INDESTRUCTIBLE) shouldBe true

                // Pass to cleanup step
                game.passUntilPhase(Phase.ENDING, Step.CLEANUP)

                // After cleanup, indestructible should be gone
                projected = stateProjector.project(game.state)
                withClue("Indestructible should wear off at end of turn") {
                    projected.hasKeyword(serraId, Keyword.INDESTRUCTIBLE) shouldBe false
                }
            }
        }
    }
}
