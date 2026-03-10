package com.wingedsheep.gameserver.scenarios

import com.wingedsheep.engine.state.components.battlefield.CountersComponent
import com.wingedsheep.gameserver.ScenarioTestBase
import com.wingedsheep.sdk.core.CounterType
import com.wingedsheep.sdk.core.Phase
import com.wingedsheep.sdk.core.Step
import io.kotest.assertions.withClue
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe

/**
 * Scenario tests for planeswalker combat mechanics.
 *
 * Tests cover:
 * - Creature attacking a planeswalker (valid target)
 * - Combat damage removes loyalty counters from planeswalker
 * - Planeswalker dies when loyalty reaches 0 from combat damage
 * - Some creatures attack player, some attack planeswalker
 * - Cannot attack own planeswalker
 */
class PlaneswalkerCombatScenarioTest : ScenarioTestBase() {

    init {
        context("Planeswalker Combat") {

            test("creature can attack a planeswalker and combat damage removes loyalty counters") {
                val game = scenario()
                    .withPlayers("Player", "Opponent")
                    .withCardOnBattlefield(1, "Glory Seeker") // 2/2
                    .withCardOnBattlefield(2, "Sarkhan, the Dragonspeaker")
                    .withCardInLibrary(1, "Mountain")
                    .withCardInLibrary(2, "Mountain")
                    .withActivePlayer(1)
                    .inPhase(Phase.PRECOMBAT_MAIN, Step.PRECOMBAT_MAIN)
                    .build()

                val sarkhanId = game.findPermanent("Sarkhan, the Dragonspeaker")!!

                // Set Sarkhan's loyalty to 4
                game.state = game.state.updateEntity(sarkhanId) { c ->
                    val counters = c.get<CountersComponent>() ?: CountersComponent()
                    c.with(counters.withAdded(CounterType.LOYALTY, 4))
                }

                game.passUntilPhase(Phase.COMBAT, Step.DECLARE_ATTACKERS)

                // Declare Glory Seeker attacking Sarkhan
                val result = game.declareAttackersWithPlaneswalkerTargets(
                    planeswalkerAttackers = mapOf("Glory Seeker" to "Sarkhan, the Dragonspeaker")
                )
                withClue("Declaring attacker on planeswalker should succeed: ${result.error}") {
                    result.error shouldBe null
                }

                game.passUntilPhase(Phase.COMBAT, Step.DECLARE_BLOCKERS)
                game.declareNoBlockers()
                game.passUntilPhase(Phase.POSTCOMBAT_MAIN, Step.POSTCOMBAT_MAIN)

                // Sarkhan should have 2 loyalty left (4 - 2 = 2)
                val counters = game.state.getEntity(sarkhanId)?.get<CountersComponent>()
                withClue("Sarkhan should have 2 loyalty counters after taking 2 combat damage") {
                    counters?.getCount(CounterType.LOYALTY) shouldBe 2
                }
                withClue("Sarkhan should still be on the battlefield") {
                    game.isOnBattlefield("Sarkhan, the Dragonspeaker") shouldBe true
                }
            }

            test("planeswalker dies from combat damage when loyalty reaches 0") {
                val game = scenario()
                    .withPlayers("Player", "Opponent")
                    .withCardOnBattlefield(1, "Alpine Grizzly") // 4/2
                    .withCardOnBattlefield(2, "Sarkhan, the Dragonspeaker")
                    .withCardInLibrary(1, "Mountain")
                    .withCardInLibrary(2, "Mountain")
                    .withActivePlayer(1)
                    .inPhase(Phase.PRECOMBAT_MAIN, Step.PRECOMBAT_MAIN)
                    .build()

                val sarkhanId = game.findPermanent("Sarkhan, the Dragonspeaker")!!

                // Set Sarkhan's loyalty to 3 (less than Alpine Grizzly's power of 4)
                game.state = game.state.updateEntity(sarkhanId) { c ->
                    val counters = c.get<CountersComponent>() ?: CountersComponent()
                    c.with(counters.withAdded(CounterType.LOYALTY, 3))
                }

                game.passUntilPhase(Phase.COMBAT, Step.DECLARE_ATTACKERS)
                game.declareAttackersWithPlaneswalkerTargets(
                    planeswalkerAttackers = mapOf("Alpine Grizzly" to "Sarkhan, the Dragonspeaker")
                )

                game.passUntilPhase(Phase.COMBAT, Step.DECLARE_BLOCKERS)
                game.declareNoBlockers()
                game.passUntilPhase(Phase.POSTCOMBAT_MAIN, Step.POSTCOMBAT_MAIN)

                // Sarkhan should be dead (3 - 4 = -1, SBA puts it in graveyard)
                withClue("Sarkhan should be in graveyard after combat damage") {
                    game.isOnBattlefield("Sarkhan, the Dragonspeaker") shouldBe false
                    game.isInGraveyard(2, "Sarkhan, the Dragonspeaker") shouldBe true
                }
            }

            test("some creatures attack player while others attack planeswalker") {
                val game = scenario()
                    .withPlayers("Player", "Opponent")
                    .withCardOnBattlefield(1, "Glory Seeker")   // 2/2
                    .withCardOnBattlefield(1, "Alpine Grizzly") // 4/2
                    .withCardOnBattlefield(2, "Sarkhan, the Dragonspeaker")
                    .withCardInLibrary(1, "Mountain")
                    .withCardInLibrary(2, "Mountain")
                    .withActivePlayer(1)
                    .inPhase(Phase.PRECOMBAT_MAIN, Step.PRECOMBAT_MAIN)
                    .build()

                val sarkhanId = game.findPermanent("Sarkhan, the Dragonspeaker")!!

                game.state = game.state.updateEntity(sarkhanId) { c ->
                    val counters = c.get<CountersComponent>() ?: CountersComponent()
                    c.with(counters.withAdded(CounterType.LOYALTY, 4))
                }

                game.passUntilPhase(Phase.COMBAT, Step.DECLARE_ATTACKERS)

                // Glory Seeker attacks Sarkhan, Alpine Grizzly attacks the opponent player
                val result = game.declareAttackersWithPlaneswalkerTargets(
                    playerAttackers = mapOf("Alpine Grizzly" to 2),
                    planeswalkerAttackers = mapOf("Glory Seeker" to "Sarkhan, the Dragonspeaker")
                )
                withClue("Mixed attack should succeed: ${result.error}") {
                    result.error shouldBe null
                }

                game.passUntilPhase(Phase.COMBAT, Step.DECLARE_BLOCKERS)
                game.declareNoBlockers()
                game.passUntilPhase(Phase.POSTCOMBAT_MAIN, Step.POSTCOMBAT_MAIN)

                // Sarkhan should have 2 loyalty left (4 - 2 = 2)
                val counters = game.state.getEntity(sarkhanId)?.get<CountersComponent>()
                withClue("Sarkhan should have 2 loyalty after Glory Seeker's 2 damage") {
                    counters?.getCount(CounterType.LOYALTY) shouldBe 2
                }

                // Opponent should have taken 4 damage from Alpine Grizzly
                withClue("Opponent should be at 16 life") {
                    game.getLifeTotal(2) shouldBe 16
                }
            }

            test("cannot attack own planeswalker") {
                val game = scenario()
                    .withPlayers("Player", "Opponent")
                    .withCardOnBattlefield(1, "Glory Seeker")
                    .withCardOnBattlefield(1, "Sarkhan, the Dragonspeaker") // Own planeswalker
                    .withCardInLibrary(1, "Mountain")
                    .withCardInLibrary(2, "Mountain")
                    .withActivePlayer(1)
                    .inPhase(Phase.PRECOMBAT_MAIN, Step.PRECOMBAT_MAIN)
                    .build()

                val sarkhanId = game.findPermanent("Sarkhan, the Dragonspeaker")!!

                game.state = game.state.updateEntity(sarkhanId) { c ->
                    val counters = c.get<CountersComponent>() ?: CountersComponent()
                    c.with(counters.withAdded(CounterType.LOYALTY, 4))
                }

                game.passUntilPhase(Phase.COMBAT, Step.DECLARE_ATTACKERS)

                // Try to attack own planeswalker — should fail
                val result = game.declareAttackersWithPlaneswalkerTargets(
                    planeswalkerAttackers = mapOf("Glory Seeker" to "Sarkhan, the Dragonspeaker")
                )
                withClue("Should not be able to attack own planeswalker") {
                    result.error shouldNotBe null
                }
            }
        }
    }
}
