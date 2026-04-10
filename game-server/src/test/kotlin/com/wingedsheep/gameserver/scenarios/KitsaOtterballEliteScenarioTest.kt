package com.wingedsheep.gameserver.scenarios

import com.wingedsheep.engine.core.ActivateAbility
import com.wingedsheep.engine.core.TurnManager
import com.wingedsheep.engine.handlers.ConditionEvaluator
import com.wingedsheep.engine.handlers.PredicateEvaluator
import com.wingedsheep.engine.legalactions.LegalActionEnumerator
import com.wingedsheep.engine.mechanics.mana.CostCalculator
import com.wingedsheep.engine.mechanics.mana.ManaSolver
import com.wingedsheep.engine.state.components.battlefield.CountersComponent
import com.wingedsheep.gameserver.ScenarioTestBase
import com.wingedsheep.sdk.core.CounterType
import com.wingedsheep.sdk.core.Phase
import com.wingedsheep.sdk.core.Step
import io.kotest.assertions.withClue
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe

/**
 * Scenario tests for Kitsa, Otterball Elite.
 *
 * Kitsa, Otterball Elite: {1}{U}
 * Legendary Creature — Otter Wizard, 1/3
 * Vigilance, Prowess
 * {T}: Draw a card, then discard a card.
 * {2}, {T}: Copy target instant or sorcery spell you control. You may choose new targets
 * for the copy. Activate only if Kitsa's power is 3 or greater.
 */
class KitsaOtterballEliteScenarioTest : ScenarioTestBase() {

    private val legalActionEnumerator = LegalActionEnumerator(
        cardRegistry, ManaSolver(cardRegistry), CostCalculator(cardRegistry), PredicateEvaluator(), ConditionEvaluator(), TurnManager(cardRegistry)
    )

    init {
        context("Kitsa, Otterball Elite - copy spell power restriction") {
            test("cannot activate copy ability when power is less than 3") {
                val game = scenario()
                    .withPlayers("Player1", "Player2")
                    .withCardOnBattlefield(1, "Kitsa, Otterball Elite")
                    .withCardInHand(1, "Shock")
                    .withLandsOnBattlefield(1, "Mountain", 1)
                    .withLandsOnBattlefield(1, "Island", 2)
                    .withCardInLibrary(1, "Island")
                    .withCardInLibrary(2, "Island")
                    .withActivePlayer(1)
                    .inPhase(Phase.PRECOMBAT_MAIN, Step.PRECOMBAT_MAIN)
                    .build()

                // Cast Shock targeting opponent — puts it on the stack
                // Prowess trigger also goes on stack but hasn't resolved yet,
                // so Kitsa's power is still 1 (base)
                game.castSpellTargetingPlayer(1, "Shock", 2)

                val kitsaId = game.findPermanent("Kitsa, Otterball Elite")!!
                val cardDef = cardRegistry.getCard("Kitsa, Otterball Elite")!!
                val copyAbility = cardDef.script.activatedAbilities[1]

                val result = game.execute(
                    ActivateAbility(
                        playerId = game.player1Id,
                        sourceId = kitsaId,
                        abilityId = copyAbility.id
                    )
                )
                withClue("Copy ability should fail when power < 3") {
                    result.error shouldNotBe null
                }
            }

            test("can activate copy ability when power is 3 or greater") {
                val game = scenario()
                    .withPlayers("Player1", "Player2")
                    .withCardOnBattlefield(1, "Kitsa, Otterball Elite")
                    .withCardInHand(1, "Shock")
                    .withLandsOnBattlefield(1, "Mountain", 1)
                    .withLandsOnBattlefield(1, "Island", 2)
                    .withCardInLibrary(1, "Island")
                    .withCardInLibrary(2, "Island")
                    .withActivePlayer(1)
                    .inPhase(Phase.PRECOMBAT_MAIN, Step.PRECOMBAT_MAIN)
                    .build()

                // Give Kitsa two +1/+1 counters so power = 3 (base 1 + 2 counters)
                val kitsaId = game.findPermanent("Kitsa, Otterball Elite")!!
                game.state = game.state.updateEntity(kitsaId) { container ->
                    container.with(CountersComponent(mapOf(CounterType.PLUS_ONE_PLUS_ONE to 2)))
                }

                // Cast Shock targeting opponent — puts it on the stack
                game.castSpellTargetingPlayer(1, "Shock", 2)

                // Kitsa has power 3 (1 base + 2 counters), and Shock is on the stack
                val cardDef = cardRegistry.getCard("Kitsa, Otterball Elite")!!
                val copyAbility = cardDef.script.activatedAbilities[1]

                // Check that the copy ability appears in legal actions
                val legalActions = legalActionEnumerator.enumerate(game.state, game.player1Id)
                val copyActions = legalActions.filter { it.description.contains("Copy target") }

                withClue("Copy ability should appear in legal actions when power >= 3 and spell on stack. " +
                    "Stack: ${game.state.stack.size}, " +
                    "Power: ${game.state.projectedState.getPower(kitsaId)}, " +
                    "All ActivateAbility: ${legalActions.filter { it.actionType == "ActivateAbility" }.map { it.description }}") {
                    copyActions shouldHaveSize 1
                }
            }
        }
    }
}
