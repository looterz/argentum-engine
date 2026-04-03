package com.wingedsheep.engine.ai

import com.wingedsheep.engine.core.*
import com.wingedsheep.engine.registry.CardRegistry
import com.wingedsheep.engine.state.GameState
import com.wingedsheep.engine.state.components.identity.LifeTotalComponent
import com.wingedsheep.mtg.sets.definitions.portal.PortalSet
import com.wingedsheep.sdk.model.CardDefinition
import com.wingedsheep.sdk.model.Deck
import com.wingedsheep.sdk.model.Rarity
import io.kotest.core.spec.style.FunSpec
import java.util.concurrent.ExecutorCompletionService
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicInteger
import kotlin.math.roundToInt
import kotlin.time.measureTime

private val logFile = java.io.File(System.getProperty("java.io.tmpdir"), "benchmark-progress.log").also {
    it.writeText("") // clear on start
}

private fun log(msg: String) {
    println(msg)
    logFile.appendText(msg + "\n")
}

/**
 * Benchmark: runs N AI-vs-AI games in parallel with random sealed decks.
 *
 * Disabled by default. Run with:
 *   ./gradlew :rules-engine:test --tests "*.GameBenchmark" -Dbenchmark=true
 */
class GameBenchmark : FunSpec({

    val numGames = 10
    val benchmarkEnabled = System.getProperty("benchmark") == "true"

    test("benchmark: $numGames AI-vs-AI games in parallel (random sealed decks)").config(enabled = benchmarkEnabled) {
        val registry = CardRegistry().apply { register(PortalSet.allCards) }
        val allCards = PortalSet.allCards
        val cores = Runtime.getRuntime().availableProcessors()
        val pool = Executors.newFixedThreadPool(cores)
        val completionService = ExecutorCompletionService<GameResult>(pool)
        val finished = AtomicInteger(0)

        log("=== BENCHMARK: $numGames games on $cores threads (random sealed decks, maxTurns=20) ===")

        val wallTime = measureTime {
            (1..numGames).forEach { i ->
                completionService.submit {
                    val deck1 = buildRandomSealedDeck(allCards)
                    val deck2 = buildRandomSealedDeck(allCards)
                    log("  [Game $i] started [${deckSummary(deck1)} vs ${deckSummary(deck2)}]")
                    playGame(registry, deck1, deck2, i, maxTurns = 20) { turn, p1Life, p2Life, actions ->
                        log("  [Game $i] turn $turn: P1=$p1Life P2=$p2Life ($actions actions)")
                    }.also { r ->
                        val n = finished.incrementAndGet()
                        log("  [Game $i] done ($n/$numGames): ${r.turns} turns, ${r.actions} actions, " +
                                "${r.durationMs}ms, ${r.winner} (${r.p1Life} vs ${r.p2Life})")
                    }
                }
            }

            val results = (1..numGames).map { completionService.take().get() }

            val completed = results.count { it.gameOver }
            val avgTurns = results.map { it.turns }.average().roundToInt()
            val avgActions = results.map { it.actions }.average().roundToInt()
            val avgMs = results.map { it.durationMs }.average().roundToInt()
            val totalActions = results.sumOf { it.actions }
            val totalCpuMs = results.sumOf { it.durationMs }

            log("")
            log("--- SUMMARY ---")
            log("Completed: $completed / ${results.size}")
            log("Turns:     avg=$avgTurns, min=${results.minOf { it.turns }}, max=${results.maxOf { it.turns }}")
            log("Actions:   avg=$avgActions, min=${results.minOf { it.actions }}, max=${results.maxOf { it.actions }}")
            log("CPU time:  avg=${avgMs}ms, min=${results.minOf { it.durationMs }}ms, max=${results.maxOf { it.durationMs }}ms")
            if (totalCpuMs > 0) {
                log("Throughput: ~${(totalActions * 1000.0 / totalCpuMs).roundToInt()} actions/sec (per thread)")
            }
        }

        log("Wall time: ${wallTime.inWholeMilliseconds}ms")
        pool.shutdown()
    }
})

private data class GameResult(
    val id: Int, val turns: Int, val actions: Int, val durationMs: Long,
    val winner: String, val p1Life: Int, val p2Life: Int, val gameOver: Boolean,
    val deck1Summary: String, val deck2Summary: String
)

private fun buildRandomSealedDeck(allCards: List<CardDefinition>): Deck {
    val pool = generateSealedPool(allCards)
    val deckMap = buildHeuristicSealedDeck(pool)
    return Deck(deckMap.flatMap { (name, count) -> List(count) { name } })
}

private fun generateSealedPool(allCards: List<CardDefinition>): List<CardDefinition> {
    val nonBasics = allCards.filter { !it.typeLine.isBasicLand }
    val commons = nonBasics.filter { it.metadata.rarity == Rarity.COMMON }
    val uncommons = nonBasics.filter { it.metadata.rarity == Rarity.UNCOMMON }
    val rares = nonBasics.filter { it.metadata.rarity == Rarity.RARE }
    val mythics = nonBasics.filter { it.metadata.rarity == Rarity.MYTHIC }

    val pool = mutableListOf<CardDefinition>()
    repeat(6) {
        val usedNames = mutableSetOf<String>()
        fun pick(from: List<CardDefinition>): CardDefinition? {
            val available = from.filter { it.name !in usedNames }
            if (available.isEmpty()) return null
            return available.random().also { usedNames.add(it.name) }
        }
        repeat(11) { pick(commons)?.let { pool.add(it) } }
        repeat(3) { pick(uncommons)?.let { pool.add(it) } }
        val rare = if (mythics.isNotEmpty() && Math.random() < 0.125) pick(mythics) else null
        pool.add(rare ?: pick(rares) ?: pick(uncommons) ?: pick(commons)!!)
    }
    return pool
}

private fun deckSummary(deck: Deck): String {
    val basics = setOf("Plains", "Island", "Swamp", "Mountain", "Forest")
    val landCount = deck.cards.count { it in basics }
    val colors = deck.cards.filter { it in basics }.groupingBy { it }.eachCount()
        .entries.sortedByDescending { it.value }.map { it.key.first() }
    return "${colors.joinToString("")} ${deck.size - landCount}sp"
}

private fun playGame(
    registry: CardRegistry, deck1: Deck, deck2: Deck, id: Int, maxTurns: Int = 50,
    onTurn: (turn: Int, p1Life: Int, p2Life: Int, actions: Int) -> Unit = { _, _, _, _ -> }
): GameResult {
    val processor = ActionProcessor(registry)
    val initializer = GameInitializer(registry)
    var actionCount = 0
    var turns = 0

    val result = initializer.initializeGame(
        GameConfig(
            players = listOf(PlayerConfig("P1", deck1), PlayerConfig("P2", deck2)),
            skipMulligans = true, startingPlayerIndex = 0
        )
    )

    val p1 = result.state.turnOrder[0]
    val p2 = result.state.turnOrder[1]
    val ai1 = AIPlayer.create(registry, p1)
    val ai2 = AIPlayer.create(registry, p2)
    var state: GameState = result.state

    var lastProgressTurn = 0
    var lastProgressAction = 0
    var stuckCount = 0

    val duration = measureTime {
        while (!state.gameOver && turns < maxTurns) {
            // Detect infinite loops: if turn hasn't advanced in 1000 iterations, bail out
            if (actionCount - lastProgressAction > 1000 && turns == lastProgressTurn) {
                stuckCount++
                if (stuckCount >= 3) {
                    val who = when (state.priorityPlayerId) { p1 -> "P1"; p2 -> "P2"; else -> "none" }
                    val decision = state.pendingDecision?.let { it::class.simpleName } ?: "none"
                    log("  [Game $id] STUCK at turn=$turns phase=${state.phase} step=${state.step} $who priority, decision=$decision, breaking out")
                    break
                }
            }
            if (turns > lastProgressTurn) {
                lastProgressTurn = turns
                lastProgressAction = actionCount
                stuckCount = 0
            }
            val iterStart = System.currentTimeMillis()
            // Handle pending decisions first (e.g., DeclareAttackers) before priority
            val d = state.pendingDecision
            val next: GameState? = if (d != null) {
                actionCount++
                val ai = if (d.playerId == p1) ai1 else ai2
                val r = processor.process(state, SubmitDecision(d.playerId, ai.respondToDecision(state, d)))
                if (r.error != null) null else r.state
            } else when (state.priorityPlayerId) {
                p1 -> { actionCount++; ai1.playPriorityWindow(state, processor) }
                p2 -> { actionCount++; ai2.playPriorityWindow(state, processor) }
                else -> null
            }
            if (next == null) break
            state = next
            if (state.turnNumber > turns) {
                turns = state.turnNumber
                val p1Life = state.getEntity(p1)?.get<LifeTotalComponent>()?.life ?: 0
                val p2Life = state.getEntity(p2)?.get<LifeTotalComponent>()?.life ?: 0
                onTurn(turns, p1Life, p2Life, actionCount)
            }
        }
    }

    return GameResult(
        id = id, turns = turns, actions = actionCount, durationMs = duration.inWholeMilliseconds,
        winner = when {
            !state.gameOver -> "draw (turn limit)"
            state.winnerId == p1 -> "P1"
            state.winnerId == p2 -> "P2"
            else -> "draw"
        },
        p1Life = state.getEntity(p1)?.get<LifeTotalComponent>()?.life ?: 0,
        p2Life = state.getEntity(p2)?.get<LifeTotalComponent>()?.life ?: 0,
        gameOver = state.gameOver,
        deck1Summary = deckSummary(deck1),
        deck2Summary = deckSummary(deck2)
    )
}
