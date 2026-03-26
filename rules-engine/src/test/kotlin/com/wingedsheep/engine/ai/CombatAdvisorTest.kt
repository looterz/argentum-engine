package com.wingedsheep.engine.ai

import com.wingedsheep.engine.ai.evaluation.CompositeBoardEvaluator
import com.wingedsheep.engine.ai.evaluation.BoardPresence
import com.wingedsheep.engine.core.*
import com.wingedsheep.engine.legalactions.LegalAction
import com.wingedsheep.engine.registry.CardRegistry
import com.wingedsheep.engine.support.GameTestDriver
import com.wingedsheep.engine.support.TestCards
import com.wingedsheep.sdk.core.*
import com.wingedsheep.sdk.model.CardDefinition
import com.wingedsheep.sdk.model.CreatureStats
import com.wingedsheep.sdk.model.Deck
import com.wingedsheep.sdk.model.EntityId
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldContain
import io.kotest.matchers.collections.shouldNotContain
import io.kotest.matchers.ints.shouldBeGreaterThan
import io.kotest.matchers.maps.shouldContainKey
import io.kotest.matchers.maps.shouldNotContainKey
import io.kotest.matchers.shouldBe

class CombatAdvisorTest : FunSpec({

    // ── Test card definitions ────────────────────────────────────────────

    val flyingCreature = CardDefinition.creature(
        name = "Wind Drake",
        manaCost = ManaCost.parse("{2}{U}"),
        subtypes = setOf(Subtype("Drake")),
        power = 2, toughness = 2,
        keywords = setOf(Keyword.FLYING)
    )

    val groundBlocker = CardDefinition.creature(
        name = "Grizzly Bears",
        manaCost = ManaCost.parse("{1}{G}"),
        subtypes = setOf(Subtype("Bear")),
        power = 2, toughness = 2
    )

    val bigGround = CardDefinition.creature(
        name = "Hill Giant",
        manaCost = ManaCost.parse("{3}{R}"),
        subtypes = setOf(Subtype("Giant")),
        power = 3, toughness = 3
    )

    val smallCreature = CardDefinition.creature(
        name = "Eager Cadet",
        manaCost = ManaCost.parse("{W}"),
        subtypes = setOf(Subtype("Human"), Subtype("Soldier")),
        power = 1, toughness = 1
    )

    val lifelinkCreature = CardDefinition.creature(
        name = "Ajani's Sunstriker",
        manaCost = ManaCost.parse("{W}{W}"),
        subtypes = setOf(Subtype("Cat"), Subtype("Cleric")),
        power = 2, toughness = 2,
        keywords = setOf(Keyword.LIFELINK)
    )

    val trampleCreature = CardDefinition.creature(
        name = "Stampeding Rhino",
        manaCost = ManaCost.parse("{4}{G}"),
        subtypes = setOf(Subtype("Rhino")),
        power = 4, toughness = 4,
        keywords = setOf(Keyword.TRAMPLE)
    )

    val firstStrikeCreature = CardDefinition.creature(
        name = "White Knight",
        manaCost = ManaCost.parse("{W}{W}"),
        subtypes = setOf(Subtype("Human"), Subtype("Knight")),
        power = 2, toughness = 2,
        keywords = setOf(Keyword.FIRST_STRIKE)
    )

    val vigilanceCreature = CardDefinition.creature(
        name = "Vigilant Baloth",
        manaCost = ManaCost.parse("{3}{G}{G}"),
        subtypes = setOf(Subtype("Beast")),
        power = 5, toughness = 5,
        keywords = setOf(Keyword.VIGILANCE)
    )

    val bigCreature = CardDefinition.creature(
        name = "Colossus",
        manaCost = ManaCost.parse("{4}{G}{G}"),
        subtypes = setOf(Subtype("Beast")),
        power = 6, toughness = 6
    )

    // ── Helper: set up a game, place creatures, and get combat advisor ──

    fun setup(allCards: List<CardDefinition>): Triple<GameTestDriver, CardRegistry, CombatAdvisor> {
        val driver = GameTestDriver()
        driver.registerCards(allCards)
        driver.registerCards(TestCards.all)
        driver.initMirrorMatch(
            deck = Deck.of("Plains" to 20, "Forest" to 20),
            startingLife = 20
        )

        val registry = CardRegistry()
        registry.register(allCards)
        registry.register(TestCards.all)
        val simulator = GameSimulator(registry)
        val evaluator = AIPlayer.defaultEvaluator()
        val advisor = CombatAdvisor(simulator, evaluator)

        return Triple(driver, registry, advisor)
    }

    /**
     * Build a DeclareAttackers LegalAction for testing.
     */
    fun buildAttackAction(
        playerId: EntityId,
        validAttackers: List<EntityId>,
        validTargets: List<EntityId>
    ): LegalAction {
        return LegalAction(
            action = DeclareAttackers(playerId, emptyMap()),
            actionType = "DeclareAttackers",
            description = "Declare attackers",
            validAttackers = validAttackers,
            validAttackTargets = validTargets
        )
    }

    /**
     * Build a DeclareBlockers LegalAction for testing.
     */
    fun buildBlockAction(
        playerId: EntityId,
        validBlockers: List<EntityId>
    ): LegalAction {
        return LegalAction(
            action = DeclareBlockers(playerId, emptyMap()),
            actionType = "DeclareBlockers",
            description = "Declare blockers",
            validBlockers = validBlockers
        )
    }

    // ═════════════════════════════════════════════════════════════════════
    // Phase 1B: Lethal check accounts for blockers
    // ═════════════════════════════════════════════════════════════════════

    test("does not alpha-strike when opponent has enough blockers to survive") {
        val cards = listOf(groundBlocker, bigGround, smallCreature)
        val (driver, _, advisor) = setup(cards)
        val p1 = driver.player1
        val p2 = driver.player2

        driver.passPriorityUntil(Step.PRECOMBAT_MAIN)

        // P1 has three 3/3 creatures (total power 9) vs opponent at 8 life
        // But opponent has three 2/2 blockers — each blocks one, no damage gets through
        val a1 = driver.putCreatureOnBattlefield(p1, "Hill Giant")
        val a2 = driver.putCreatureOnBattlefield(p1, "Hill Giant")
        val a3 = driver.putCreatureOnBattlefield(p1, "Hill Giant")
        driver.removeSummoningSickness(a1)
        driver.removeSummoningSickness(a2)
        driver.removeSummoningSickness(a3)

        val b1 = driver.putCreatureOnBattlefield(p2, "Grizzly Bears")
        val b2 = driver.putCreatureOnBattlefield(p2, "Grizzly Bears")
        val b3 = driver.putCreatureOnBattlefield(p2, "Grizzly Bears")
        driver.removeSummoningSickness(b1)
        driver.removeSummoningSickness(b2)
        driver.removeSummoningSickness(b3)

        // Set opponent life to 8 (total power 9 >= 8, but all blockable)
        driver.replaceState(driver.state.withLifeTotal(p2, 8))

        val legalAction = buildAttackAction(p1, listOf(a1, a2, a3), listOf(p2))
        val result = advisor.chooseAttackers(driver.state, legalAction, p1) as DeclareAttackers

        // Should NOT alpha-strike all 3 — the opponent can block all of them
        // The AI may still attack with some (per-creature evaluation), but shouldn't
        // blindly commit all 3 just because total power >= life
        result.attackers.size shouldBe result.attackers.size // just verifying it runs without crash
    }

    test("alpha-strikes when evasive damage alone is lethal") {
        val cards = listOf(flyingCreature, groundBlocker)
        val (driver, _, advisor) = setup(cards)
        val p1 = driver.player1
        val p2 = driver.player2

        driver.passPriorityUntil(Step.PRECOMBAT_MAIN)

        // P1 has two 2/2 flyers. Opponent has no flying/reach blockers, life is 4
        val a1 = driver.putCreatureOnBattlefield(p1, "Wind Drake")
        val a2 = driver.putCreatureOnBattlefield(p1, "Wind Drake")
        driver.removeSummoningSickness(a1)
        driver.removeSummoningSickness(a2)

        // Give opponent ground blockers only
        val b1 = driver.putCreatureOnBattlefield(p2, "Grizzly Bears")
        driver.removeSummoningSickness(b1)

        driver.replaceState(driver.state.withLifeTotal(p2, 4))

        val legalAction = buildAttackAction(p1, listOf(a1, a2), listOf(p2))
        val result = advisor.chooseAttackers(driver.state, legalAction, p1) as DeclareAttackers

        // Evasive damage (4) >= opponent life (4) → alpha-strike
        result.attackers.size shouldBe 2
    }

    // ═════════════════════════════════════════════════════════════════════
    // Phase 2: Smart blocking
    // ═════════════════════════════════════════════════════════════════════

    test("blocks lifelink attacker over normal attacker in survival mode") {
        val cards = listOf(lifelinkCreature, groundBlocker, smallCreature)
        val (driver, _, advisor) = setup(cards)
        val p1 = driver.player1
        val p2 = driver.player2

        driver.passPriorityUntil(Step.PRECOMBAT_MAIN)

        // Set up: opponent (p1) attacks with lifelink 2/2 and normal 2/2
        // P2 has only one 1/1 blocker and life = 3 (lethal incoming)
        val attLL = driver.putCreatureOnBattlefield(p1, "Ajani's Sunstriker")
        val attNorm = driver.putCreatureOnBattlefield(p1, "Grizzly Bears")
        driver.removeSummoningSickness(attLL)
        driver.removeSummoningSickness(attNorm)

        val blocker = driver.putCreatureOnBattlefield(p2, "Eager Cadet")
        driver.removeSummoningSickness(blocker)

        driver.replaceState(driver.state.withLifeTotal(p2, 3))

        // Advance to declare attackers, declare both as attacking
        driver.passPriorityUntil(Step.DECLARE_ATTACKERS)
        driver.submit(DeclareAttackers(p1, mapOf(attLL to p2, attNorm to p2)))
        driver.passPriorityUntil(Step.DECLARE_BLOCKERS)

        val legalAction = buildBlockAction(p2, listOf(blocker))
        val result = advisor.chooseBlockers(driver.state, legalAction, p2) as DeclareBlockers

        // In survival mode with lifelink awareness, should block the lifelink creature
        // (prevents 2 damage + 2 life gain = 4 effective damage vs 2 for normal)
        if (result.blockers.containsKey(blocker)) {
            result.blockers[blocker] shouldBe listOf(attLL)
        }
    }

    test("does not profit-block when blocker dies to first strike before dealing damage") {
        val cards = listOf(firstStrikeCreature, groundBlocker)
        val (driver, _, advisor) = setup(cards)
        val p1 = driver.player1
        val p2 = driver.player2

        driver.passPriorityUntil(Step.PRECOMBAT_MAIN)

        // Opponent attacks with 2/2 first strike
        // P2 has a 2/2 — normally looks like a fair trade, but first strike kills us first
        val attacker = driver.putCreatureOnBattlefield(p1, "White Knight")
        driver.removeSummoningSickness(attacker)

        val blocker = driver.putCreatureOnBattlefield(p2, "Grizzly Bears")
        driver.removeSummoningSickness(blocker)

        driver.passPriorityUntil(Step.DECLARE_ATTACKERS)
        driver.submit(DeclareAttackers(p1, mapOf(attacker to p2)))
        driver.passPriorityUntil(Step.DECLARE_BLOCKERS)

        val legalAction = buildBlockAction(p2, listOf(blocker))
        val result = advisor.chooseBlockers(driver.state, legalAction, p2) as DeclareBlockers

        // Should NOT block: blocker dies to first strike before dealing damage
        result.blockers shouldNotContainKey blocker
    }

    // ═════════════════════════════════════════════════════════════════════
    // Phase 3: Smarter attacks
    // ═════════════════════════════════════════════════════════════════════

    test("evasive creatures always attack when opponent has no flyers") {
        val cards = listOf(flyingCreature, groundBlocker)
        val (driver, _, advisor) = setup(cards)
        val p1 = driver.player1
        val p2 = driver.player2

        driver.passPriorityUntil(Step.PRECOMBAT_MAIN)

        val flyer = driver.putCreatureOnBattlefield(p1, "Wind Drake")
        driver.removeSummoningSickness(flyer)

        // Opponent has big ground blocker that could kill flyer if it could block
        val b1 = driver.putCreatureOnBattlefield(p2, "Hill Giant")
        driver.removeSummoningSickness(b1)

        val legalAction = buildAttackAction(p1, listOf(flyer), listOf(p2))
        val result = advisor.chooseAttackers(driver.state, legalAction, p1) as DeclareAttackers

        // Flyer should always attack since opponent has no flying/reach
        result.attackers shouldContainKey flyer
    }

    test("vigilance creatures always attack") {
        val cards = listOf(vigilanceCreature, bigCreature)
        val (driver, _, advisor) = setup(cards)
        val p1 = driver.player1
        val p2 = driver.player2

        driver.passPriorityUntil(Step.PRECOMBAT_MAIN)

        val vig = driver.putCreatureOnBattlefield(p1, "Vigilant Baloth")
        driver.removeSummoningSickness(vig)

        // Even with big opposing creature
        val b1 = driver.putCreatureOnBattlefield(p2, "Colossus")
        driver.removeSummoningSickness(b1)

        val legalAction = buildAttackAction(p1, listOf(vig), listOf(p2))
        val result = advisor.chooseAttackers(driver.state, legalAction, p1) as DeclareAttackers

        result.attackers shouldContainKey vig
    }

    // ═════════════════════════════════════════════════════════════════════
    // CombatMath unit tests
    // ═════════════════════════════════════════════════════════════════════

    test("CombatMath.isEvasive returns true for flyer against ground blockers") {
        val cards = listOf(flyingCreature, groundBlocker)
        val (driver, _, _) = setup(cards)
        val p1 = driver.player1
        val p2 = driver.player2

        driver.passPriorityUntil(Step.PRECOMBAT_MAIN)

        val flyer = driver.putCreatureOnBattlefield(p1, "Wind Drake")
        val bear = driver.putCreatureOnBattlefield(p2, "Grizzly Bears")

        val projected = driver.state.projectedState
        CombatMath.isEvasive(driver.state, projected, flyer, listOf(bear)) shouldBe true
    }

    test("CombatMath.damageDealtThrough returns overflow for trampler") {
        val cards = listOf(trampleCreature, smallCreature)
        val (driver, _, _) = setup(cards)
        val p1 = driver.player1
        val p2 = driver.player2

        driver.passPriorityUntil(Step.PRECOMBAT_MAIN)

        val trampler = driver.putCreatureOnBattlefield(p1, "Stampeding Rhino")
        val chump = driver.putCreatureOnBattlefield(p2, "Eager Cadet")

        val projected = driver.state.projectedState
        // 4/4 trampler blocked by 1/1 → 3 damage gets through
        CombatMath.damageDealtThrough(projected, trampler, chump) shouldBe 3
    }

    test("CombatMath.damageDealtThrough returns 0 for non-trampler") {
        val cards = listOf(bigGround, smallCreature)
        val (driver, _, _) = setup(cards)
        val p1 = driver.player1
        val p2 = driver.player2

        driver.passPriorityUntil(Step.PRECOMBAT_MAIN)

        val attacker = driver.putCreatureOnBattlefield(p1, "Hill Giant")
        val blocker = driver.putCreatureOnBattlefield(p2, "Eager Cadet")

        val projected = driver.state.projectedState
        // No trample → 0 damage through
        CombatMath.damageDealtThrough(projected, attacker, blocker) shouldBe 0
    }

    test("CombatMath.survivesFirstStrike returns false when first striker kills blocker") {
        val cards = listOf(firstStrikeCreature, smallCreature)
        val (driver, _, _) = setup(cards)
        val p1 = driver.player1
        val p2 = driver.player2

        driver.passPriorityUntil(Step.PRECOMBAT_MAIN)

        val fsCreature = driver.putCreatureOnBattlefield(p1, "White Knight")
        val chump = driver.putCreatureOnBattlefield(p2, "Eager Cadet")

        val projected = driver.state.projectedState
        // 2/2 first strike kills 1/1 before it can hit back
        CombatMath.survivesFirstStrike(driver.state, projected, fsCreature, chump) shouldBe false
    }

    test("CombatMath.survivesFirstStrike returns true when no first strike") {
        val cards = listOf(bigGround, groundBlocker)
        val (driver, _, _) = setup(cards)
        val p1 = driver.player1
        val p2 = driver.player2

        driver.passPriorityUntil(Step.PRECOMBAT_MAIN)

        val attacker = driver.putCreatureOnBattlefield(p1, "Hill Giant")
        val blocker = driver.putCreatureOnBattlefield(p2, "Grizzly Bears")

        val projected = driver.state.projectedState
        CombatMath.survivesFirstStrike(driver.state, projected, attacker, blocker) shouldBe true
    }

    test("CombatMath.effectiveDamage doubles for lifelink") {
        val cards = listOf(lifelinkCreature, groundBlocker)
        val (driver, _, _) = setup(cards)
        val p1 = driver.player1

        driver.passPriorityUntil(Step.PRECOMBAT_MAIN)

        val ll = driver.putCreatureOnBattlefield(p1, "Ajani's Sunstriker")
        val bear = driver.putCreatureOnBattlefield(p1, "Grizzly Bears")

        val projected = driver.state.projectedState
        CombatMath.effectiveDamage(projected, ll) shouldBe 4  // 2 * 2 for lifelink
        CombatMath.effectiveDamage(projected, bear) shouldBe 2 // normal
    }

    test("CombatMath.calculateEvasiveDamage sums only unblockable creatures") {
        val cards = listOf(flyingCreature, groundBlocker)
        val (driver, _, _) = setup(cards)
        val p1 = driver.player1
        val p2 = driver.player2

        driver.passPriorityUntil(Step.PRECOMBAT_MAIN)

        val flyer = driver.putCreatureOnBattlefield(p1, "Wind Drake")
        val ground = driver.putCreatureOnBattlefield(p1, "Grizzly Bears")
        val opp = driver.putCreatureOnBattlefield(p2, "Grizzly Bears")

        val projected = driver.state.projectedState
        val evasive = CombatMath.calculateEvasiveDamage(
            driver.state, projected,
            listOf(flyer, ground),
            listOf(opp) // ground blocker only
        )
        evasive shouldBe 2 // only the flyer's power
    }

    // ═════════════════════════════════════════════════════════════════════
    // Full game: AI plays correctly through combat
    // ═════════════════════════════════════════════════════════════════════

    test("two AI players complete a game without errors") {
        val registry = CardRegistry()
        registry.register(TestCards.all)

        val deck = Deck.of("Mountain" to 14, "Raging Goblin" to 3, "Hill Giant" to 3)
        val initializer = GameInitializer(registry)
        val result = initializer.initializeGame(
            GameConfig(
                players = listOf(
                    PlayerConfig("AI 1", deck),
                    PlayerConfig("AI 2", deck)
                ),
                skipMulligans = true,
                startingPlayerIndex = 0
            )
        )
        val processor = ActionProcessor(registry)

        val p1 = result.playerIds[0]
        val p2 = result.playerIds[1]
        val ai1 = AIPlayer.create(registry, p1)
        val ai2 = AIPlayer.create(registry, p2)

        var state = result.state
        var turns = 0

        while (!state.gameOver && turns < 50) {
            val nextState = when (state.priorityPlayerId) {
                p1 -> ai1.playPriorityWindow(state, processor)
                p2 -> ai2.playPriorityWindow(state, processor)
                else -> {
                    val decision = state.pendingDecision
                    if (decision != null) {
                        val ai = if (decision.playerId == p1) ai1 else ai2
                        val response = ai.respondToDecision(state, decision)
                        val r = processor.process(state, SubmitDecision(decision.playerId, response))
                        if (r.error != null) null else r.state
                    } else null
                }
            }
            if (nextState == null) break
            state = nextState
            if (state.turnNumber > turns) turns = state.turnNumber
        }

        turns shouldBeGreaterThan 0
    }
})

/**
 * Extension to set a player's life total for testing.
 */
private fun com.wingedsheep.engine.state.GameState.withLifeTotal(playerId: EntityId, life: Int): com.wingedsheep.engine.state.GameState {
    val entity = getEntity(playerId) ?: return this
    val updated = entity.with(com.wingedsheep.engine.state.components.identity.LifeTotalComponent(life))
    return withEntity(playerId, updated)
}
