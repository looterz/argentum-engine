package com.wingedsheep.engine.scenarios

import com.wingedsheep.engine.support.GameTestDriver
import com.wingedsheep.engine.support.TestCards
import com.wingedsheep.mtg.sets.definitions.bloomburrow.cards.DragonhawkFatesTempest
import com.wingedsheep.sdk.core.Color
import com.wingedsheep.sdk.core.ManaCost
import com.wingedsheep.sdk.core.Step
import com.wingedsheep.sdk.model.CardDefinition
import com.wingedsheep.sdk.model.Deck
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe

/**
 * Tests for Dragonhawk, Fate's Tempest.
 *
 * Dragonhawk, Fate's Tempest {3}{R}{R}
 * Legendary Creature — Bird Dragon
 * 5/5
 * Flying
 *
 * Whenever Dragonhawk enters or attacks, exile the top X cards of your library,
 * where X is the number of creatures you control with power 4 or greater. You may
 * play those cards until your next end step. At the beginning of your next end step,
 * Dragonhawk deals 2 damage to each opponent for each of those cards that are still exiled.
 */
class DragonhawkFatesTempestTest : FunSpec({

    val bigCreature = CardDefinition.creature("Test Big Creature", ManaCost.parse("{2}{G}{G}"), emptySet(), 4, 4)

    fun createDriver(): GameTestDriver {
        val driver = GameTestDriver()
        driver.registerCards(TestCards.all + listOf(DragonhawkFatesTempest, bigCreature))
        return driver
    }

    /**
     * Advance from the current turn's end step through to the controller's next turn's end step.
     * Handles passing through cleanup, opponent's turn, and into the next turn cycle.
     */
    fun GameTestDriver.advanceToControllerNextEndStep(p1: com.wingedsheep.sdk.model.EntityId, p2: com.wingedsheep.sdk.model.EntityId) {
        // Pass through current end step
        passPriority(p1)
        passPriority(p2)
        // Advance through P2's turn to P1's next precombat main
        passPriorityUntil(Step.PRECOMBAT_MAIN, maxPasses = 200)
        // If we landed on P2's turn, advance one more cycle
        if (state.activePlayerId != p1) {
            passPriorityUntil(Step.END, maxPasses = 200)
            passPriority(p1)
            passPriority(p2)
            passPriorityUntil(Step.PRECOMBAT_MAIN, maxPasses = 200)
        }
        // Now at P1's next turn's main phase — advance to end step
        passPriorityUntil(Step.END, maxPasses = 200)
    }

    test("ETB exiles cards and delayed trigger deals damage for still-exiled cards at next end step") {
        val driver = createDriver()
        driver.initMirrorMatch(
            deck = Deck.of("Mountain" to 30),
            startingLife = 20
        )

        val p1 = driver.activePlayer!!
        val p2 = driver.getOpponent(p1)

        driver.passPriorityUntil(Step.PRECOMBAT_MAIN)

        // Dragonhawk is 5/5 → counts toward X. With just Dragonhawk, X = 1.
        driver.putCardOnTopOfLibrary(p1, "Mountain")

        val dragonhawkId = driver.putCardInHand(p1, "Dragonhawk, Fate's Tempest")
        driver.giveMana(p1, Color.RED, 2)
        driver.giveColorlessMana(p1, 3)
        driver.castSpell(p1, dragonhawkId)

        // Resolve spell → ETB trigger → resolve ETB (exiles 1 card)
        driver.passPriority(p1)
        driver.passPriority(p2)
        driver.passPriority(p1)
        driver.passPriority(p2)

        driver.getExileCardNames(p1).size shouldBe 1
        driver.getLifeTotal(p2) shouldBe 20

        // Advance to this turn's end step — trigger should NOT fire yet
        driver.passPriorityUntil(Step.END, maxPasses = 200)

        // Advance to P1's next turn's end step — delayed trigger fires
        driver.advanceToControllerNextEndStep(p1, p2)

        // Resolve delayed trigger
        driver.passPriority(p1)
        driver.passPriority(p2)

        // 1 still exiled × 2 damage = 2 damage to opponent
        driver.getLifeTotal(p2) shouldBe 18
    }

    test("no damage if all exiled cards were played before next end step") {
        val driver = createDriver()
        driver.initMirrorMatch(
            deck = Deck.of("Mountain" to 30),
            startingLife = 20
        )

        val p1 = driver.activePlayer!!
        val p2 = driver.getOpponent(p1)

        driver.passPriorityUntil(Step.PRECOMBAT_MAIN)
        driver.putCardOnTopOfLibrary(p1, "Mountain")

        val dragonhawkId = driver.putCardInHand(p1, "Dragonhawk, Fate's Tempest")
        driver.giveMana(p1, Color.RED, 2)
        driver.giveColorlessMana(p1, 3)
        driver.castSpell(p1, dragonhawkId)

        driver.passPriority(p1)
        driver.passPriority(p2)
        driver.passPriority(p1)
        driver.passPriority(p2)

        driver.getExileCardNames(p1).size shouldBe 1

        // Play the exiled Mountain (impulse land play from exile)
        val exiledMountain = driver.getExile(p1).first()
        driver.playLand(p1, exiledMountain)
        driver.getExileCardNames(p1).size shouldBe 0

        // Advance to end step and then to next end step
        driver.passPriorityUntil(Step.END, maxPasses = 200)
        driver.advanceToControllerNextEndStep(p1, p2)
        driver.passPriority(p1)
        driver.passPriority(p2)

        // 0 cards exiled → 0 damage
        driver.getLifeTotal(p2) shouldBe 20
    }

    test("X equals number of creatures with power 4 or greater") {
        val driver = createDriver()
        driver.initMirrorMatch(
            deck = Deck.of("Mountain" to 30),
            startingLife = 20
        )

        val p1 = driver.activePlayer!!
        val p2 = driver.getOpponent(p1)

        driver.passPriorityUntil(Step.PRECOMBAT_MAIN)

        // 4/4 creature on battlefield before Dragonhawk
        driver.putCreatureOnBattlefield(p1, "Test Big Creature")

        // 2 cards on top (X = 2: Dragonhawk 5/5 + Big Creature 4/4)
        driver.putCardOnTopOfLibrary(p1, "Mountain")
        driver.putCardOnTopOfLibrary(p1, "Mountain")

        val dragonhawkId = driver.putCardInHand(p1, "Dragonhawk, Fate's Tempest")
        driver.giveMana(p1, Color.RED, 2)
        driver.giveColorlessMana(p1, 3)
        driver.castSpell(p1, dragonhawkId)

        driver.passPriority(p1)
        driver.passPriority(p2)
        driver.passPriority(p1)
        driver.passPriority(p2)

        driver.getExileCardNames(p1).size shouldBe 2

        driver.passPriorityUntil(Step.END, maxPasses = 200)
        driver.advanceToControllerNextEndStep(p1, p2)
        driver.passPriority(p1)
        driver.passPriority(p2)

        // 2 still exiled × 2 damage = 4 damage
        driver.getLifeTotal(p2) shouldBe 16
    }
})
