package com.wingedsheep.engine.ai

import com.wingedsheep.engine.ai.advisor.modules.BloomburrowAdvisorModule
import com.wingedsheep.engine.core.*
import com.wingedsheep.engine.registry.CardRegistry
import com.wingedsheep.engine.state.GameState
import com.wingedsheep.engine.state.components.identity.CardComponent
import com.wingedsheep.engine.support.GameTestDriver
import com.wingedsheep.mtg.sets.definitions.bloomburrow.BloomburrowSet
import com.wingedsheep.mtg.sets.definitions.portal.PortalSet
import com.wingedsheep.sdk.core.*
import com.wingedsheep.sdk.model.CardDefinition
import com.wingedsheep.sdk.model.Deck
import com.wingedsheep.sdk.model.EntityId
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.booleans.shouldBeTrue
import io.kotest.matchers.shouldBe

/**
 * Tests that the AI actually casts removal/bite spells when it has mana and targets,
 * rather than hoarding them forever.
 */
class SpellCastingAdvisorTest : FunSpec({

    val bear = CardDefinition.creature(
        name = "Grizzly Bears",
        manaCost = ManaCost.parse("{1}{G}"),
        subtypes = setOf(Subtype("Bear")),
        power = 2, toughness = 2
    )

    val ogre = CardDefinition.creature(
        name = "Ogre Warrior",
        manaCost = ManaCost.parse("{3}{R}"),
        subtypes = setOf(Subtype("Ogre"), Subtype("Warrior")),
        power = 4, toughness = 4
    )

    val allCards = BloomburrowSet.allCards + BloomburrowSet.basicLands +
        PortalSet.allCards + listOf(bear, ogre)

    fun createRegistryAndDriver(): Pair<CardRegistry, GameTestDriver> {
        val registry = CardRegistry().apply { register(allCards) }
        val driver = GameTestDriver().apply {
            registerCards(allCards)
            initMirrorMatch(
                deck = Deck.of("Forest" to 20, "Mountain" to 20),
                startingLife = 20
            )
        }
        return registry to driver
    }

    fun cardNamesInHand(driver: GameTestDriver, playerId: EntityId): List<String> {
        return driver.getHand(playerId).mapNotNull { entityId ->
            driver.state.getEntity(entityId)?.get<CardComponent>()?.name
        }
    }

    fun runFullTurnCycle(
        driver: GameTestDriver,
        registry: CardRegistry,
        aiPlayerId: EntityId,
        opponentId: EntityId
    ) {
        val ai = AIPlayer.create(registry, aiPlayerId, listOf(BloomburrowAdvisorModule()))
        val opponent = AIPlayer.create(registry, opponentId, listOf(BloomburrowAdvisorModule()))
        val processor = ActionProcessor(registry)

        var state = driver.state
        var safety = 0
        val startTurn = state.turnNumber

        while (state.turnNumber < startTurn + 2 && !state.gameOver && safety < 300) {
            val nextState: GameState? = when (state.priorityPlayerId) {
                aiPlayerId -> ai.playPriorityWindow(state, processor)
                opponentId -> opponent.playPriorityWindow(state, processor)
                else -> {
                    val decision = state.pendingDecision
                    if (decision != null) {
                        val responder = if (decision.playerId == aiPlayerId) ai else opponent
                        val response = responder.respondToDecision(state, decision)
                        val result = processor.process(state, SubmitDecision(decision.playerId, response))
                        if (result.error != null) null else result.state
                    } else null
                }
            }
            if (nextState == null) break
            state = nextState
            safety++
        }

        driver.replaceState(state)
    }

    // ═════════════════════════════════════════════════════════════════════
    // Polliwallop
    // ═════════════════════════════════════════════════════════════════════

    test("AI chooses to cast Polliwallop when it has a creature and opponent has a bigger one") {
        val (registry, driver) = createRegistryAndDriver()
        val p1 = driver.player1
        val p2 = driver.player2

        driver.passPriorityUntil(Step.PRECOMBAT_MAIN)

        // P1 has a 2/2, P2 has a 4/4 — Polliwallop deals 2×2 = 4, killing the 4/4
        val myBear = driver.putCreatureOnBattlefield(p1, "Grizzly Bears")
        driver.removeSummoningSickness(myBear)
        driver.putCreatureOnBattlefield(p2, "Ogre Warrior")

        driver.putCardInHand(p1, "Polliwallop")
        driver.giveMana(p1, Color.GREEN, 1)
        driver.giveColorlessMana(p1, 3)

        val ai = AIPlayer.create(registry, p1, listOf(BloomburrowAdvisorModule()))
        val action = ai.chooseAction(driver.state)
        (action is CastSpell).shouldBeTrue()
    }

    test("AI casts Polliwallop within a full turn cycle") {
        val (registry, driver) = createRegistryAndDriver()
        val p1 = driver.player1
        val p2 = driver.player2

        driver.passPriorityUntil(Step.PRECOMBAT_MAIN)

        val myBear = driver.putCreatureOnBattlefield(p1, "Grizzly Bears")
        driver.removeSummoningSickness(myBear)
        driver.putCreatureOnBattlefield(p2, "Ogre Warrior")

        driver.putCardInHand(p1, "Polliwallop")
        driver.giveMana(p1, Color.GREEN, 1)
        driver.giveColorlessMana(p1, 3)

        runFullTurnCycle(driver, registry, p1, p2)

        cardNamesInHand(driver, p1).contains("Polliwallop") shouldBe false
    }

    // ═════════════════════════════════════════════════════════════════════
    // Dire Downdraft
    // ═════════════════════════════════════════════════════════════════════

    test("AI chooses to cast Dire Downdraft when opponent has a creature") {
        val (registry, driver) = createRegistryAndDriver()
        val p1 = driver.player1
        val p2 = driver.player2

        driver.passPriorityUntil(Step.PRECOMBAT_MAIN)

        driver.putCreatureOnBattlefield(p2, "Ogre Warrior")

        driver.putCardInHand(p1, "Dire Downdraft")
        driver.giveMana(p1, Color.BLUE, 1)
        driver.giveColorlessMana(p1, 3)

        val ai = AIPlayer.create(registry, p1, listOf(BloomburrowAdvisorModule()))
        val action = ai.chooseAction(driver.state)
        (action is CastSpell).shouldBeTrue()
    }

    test("AI casts Dire Downdraft within a full turn cycle") {
        val (registry, driver) = createRegistryAndDriver()
        val p1 = driver.player1
        val p2 = driver.player2

        driver.passPriorityUntil(Step.PRECOMBAT_MAIN)

        driver.putCreatureOnBattlefield(p2, "Ogre Warrior")

        driver.putCardInHand(p1, "Dire Downdraft")
        driver.giveMana(p1, Color.BLUE, 1)
        driver.giveColorlessMana(p1, 3)

        runFullTurnCycle(driver, registry, p1, p2)

        cardNamesInHand(driver, p1).contains("Dire Downdraft") shouldBe false
    }
})
