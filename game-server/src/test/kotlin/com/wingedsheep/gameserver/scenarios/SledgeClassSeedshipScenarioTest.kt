package com.wingedsheep.gameserver.scenarios

import com.wingedsheep.engine.core.ActivateAbility
import com.wingedsheep.engine.mechanics.layers.StateProjector
import com.wingedsheep.engine.state.components.battlefield.CountersComponent
import com.wingedsheep.gameserver.ScenarioTestBase
import com.wingedsheep.sdk.core.CounterType
import com.wingedsheep.sdk.core.Phase
import com.wingedsheep.sdk.core.Step
import com.wingedsheep.sdk.scripting.AdditionalCostPayment
import io.kotest.assertions.withClue
import io.kotest.matchers.shouldBe

/**
 * Scenario tests for Sledge-Class Seedship (Station mechanic).
 *
 * Card reference:
 * - Sledge-Class Seedship ({2}{G}): 4/5 Artifact — Spacecraft
 *   Station (Tap another creature you control: Put charge counters equal to its power
 *   on this Spacecraft. Station only as a sorcery. It's an artifact creature at 7+.)
 *   7+ | Flying
 *   Whenever this Spacecraft attacks, you may put a creature card from your hand
 *   onto the battlefield.
 */
class SledgeClassSeedshipScenarioTest : ScenarioTestBase() {

    private val stateProjector = StateProjector()

    init {
        context("Station mechanic") {

            test("adds charge counters equal to tapped creature's power") {
                val game = scenario()
                    .withPlayers("Player", "Opponent")
                    .withCardOnBattlefield(1, "Sledge-Class Seedship")
                    .withCardOnBattlefield(1, "Anaconda") // 3/3
                    .withCardInLibrary(1, "Forest")
                    .withCardInLibrary(2, "Forest")
                    .withActivePlayer(1)
                    .inPhase(Phase.PRECOMBAT_MAIN, Step.PRECOMBAT_MAIN)
                    .build()

                val seedshipId = game.findPermanent("Sledge-Class Seedship")!!
                val armodonId = game.findPermanent("Anaconda")!!

                val cardDef = cardRegistry.getCard("Sledge-Class Seedship")!!
                val ability = cardDef.script.activatedAbilities.first()

                val activateResult = game.execute(
                    ActivateAbility(
                        playerId = game.player1Id,
                        sourceId = seedshipId,
                        abilityId = ability.id,
                        costPayment = AdditionalCostPayment(
                            tappedPermanents = listOf(armodonId)
                        )
                    )
                )

                withClue("Ability should activate successfully: ${activateResult.error}") {
                    activateResult.error shouldBe null
                }

                game.resolveStack()

                val counters = game.state.getEntity(seedshipId)?.get<CountersComponent>()
                withClue("Should have 3 charge counters (equal to Anaconda's power)") {
                    counters?.getCount(CounterType.CHARGE) shouldBe 3
                }
            }

            test("is not a creature with fewer than 7 charge counters") {
                val game = scenario()
                    .withPlayers("Player", "Opponent")
                    .withCardOnBattlefield(1, "Sledge-Class Seedship")
                    .withCardInLibrary(1, "Forest")
                    .withCardInLibrary(2, "Forest")
                    .withActivePlayer(1)
                    .inPhase(Phase.PRECOMBAT_MAIN, Step.PRECOMBAT_MAIN)
                    .build()

                val seedshipId = game.findPermanent("Sledge-Class Seedship")!!

                // Add 6 charge counters (below threshold)
                game.state = game.state.updateEntity(seedshipId) {
                    it.with(CountersComponent().withAdded(CounterType.CHARGE, 6))
                }

                val projected = stateProjector.project(game.state)
                withClue("Spacecraft with 6 charge counters should NOT be a creature") {
                    projected.isCreature(seedshipId) shouldBe false
                }
            }

            test("becomes a creature with flying at 7+ charge counters") {
                val game = scenario()
                    .withPlayers("Player", "Opponent")
                    .withCardOnBattlefield(1, "Sledge-Class Seedship")
                    .withCardInLibrary(1, "Forest")
                    .withCardInLibrary(2, "Forest")
                    .withActivePlayer(1)
                    .inPhase(Phase.PRECOMBAT_MAIN, Step.PRECOMBAT_MAIN)
                    .build()

                val seedshipId = game.findPermanent("Sledge-Class Seedship")!!

                // Add 7 charge counters (at threshold)
                game.state = game.state.updateEntity(seedshipId) {
                    it.with(CountersComponent().withAdded(CounterType.CHARGE, 7))
                }

                val projected = stateProjector.project(game.state)
                withClue("Spacecraft with 7 charge counters should be a creature") {
                    projected.isCreature(seedshipId) shouldBe true
                }
                withClue("Spacecraft with 7 charge counters should have flying") {
                    projected.hasKeyword(seedshipId, "FLYING") shouldBe true
                }
            }

            test("has correct P/T when it becomes a creature") {
                val game = scenario()
                    .withPlayers("Player", "Opponent")
                    .withCardOnBattlefield(1, "Sledge-Class Seedship")
                    .withCardInLibrary(1, "Forest")
                    .withCardInLibrary(2, "Forest")
                    .withActivePlayer(1)
                    .inPhase(Phase.PRECOMBAT_MAIN, Step.PRECOMBAT_MAIN)
                    .build()

                val seedshipId = game.findPermanent("Sledge-Class Seedship")!!

                game.state = game.state.updateEntity(seedshipId) {
                    it.with(CountersComponent().withAdded(CounterType.CHARGE, 7))
                }

                val projected = stateProjector.project(game.state)
                withClue("Spacecraft should be 4/5 when it becomes a creature") {
                    projected.getPower(seedshipId) shouldBe 4
                    projected.getToughness(seedshipId) shouldBe 5
                }
            }
        }
    }
}
