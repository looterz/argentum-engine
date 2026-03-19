package com.wingedsheep.engine.handlers.effects.removal

import com.wingedsheep.engine.core.*
import com.wingedsheep.engine.handlers.DecisionHandler
import com.wingedsheep.engine.handlers.EffectContext
import com.wingedsheep.engine.handlers.PredicateContext
import com.wingedsheep.engine.handlers.effects.EffectExecutor
import com.wingedsheep.engine.handlers.effects.BattlefieldFilterUtils
import com.wingedsheep.engine.handlers.effects.TargetResolutionUtils.resolvePlayerTargets
import com.wingedsheep.engine.state.GameState
import com.wingedsheep.engine.state.ZoneKey
import com.wingedsheep.engine.state.components.identity.CardComponent
import com.wingedsheep.sdk.core.Zone
import com.wingedsheep.sdk.model.EntityId
import com.wingedsheep.sdk.scripting.GameObjectFilter
import com.wingedsheep.sdk.scripting.effects.ForceSacrificeEffect
import kotlin.reflect.KClass

/**
 * Executor for ForceSacrificeEffect.
 *
 * Handles "target player sacrifices a creature" (edict effects) where an opponent
 * is forced to sacrifice permanents of their choice. Supports multi-player targets
 * like Player.EachOpponent.
 *
 * Examples:
 * - Cabal Executioner: "Whenever ~ deals combat damage to a player, that player sacrifices a creature."
 * - Chainer's Edict: "Target player sacrifices a creature."
 * - The Eldest Reborn: "Each opponent sacrifices a creature or planeswalker."
 */
class ForceSacrificeExecutor(
    private val decisionHandler: DecisionHandler = DecisionHandler()
) : EffectExecutor<ForceSacrificeEffect> {

    override val effectType: KClass<ForceSacrificeEffect> = ForceSacrificeEffect::class

    override fun execute(
        state: GameState,
        effect: ForceSacrificeEffect,
        context: EffectContext
    ): ExecutionResult {
        val playerIds = resolvePlayerTargets(effect.target, state, context)
        if (playerIds.isEmpty()) {
            return ExecutionResult.error(state, "No valid player for force sacrifice")
        }

        return processPlayers(state, playerIds, effect.filter, effect.count, context.sourceId)
    }

    /**
     * Process sacrifice for a list of players in order. Auto-sacrifices when possible,
     * pauses for a decision when a player must choose, storing remaining players
     * in the continuation.
     */
    fun processPlayers(
        state: GameState,
        playerIds: List<EntityId>,
        filter: GameObjectFilter,
        count: Int,
        sourceId: EntityId?
    ): ExecutionResult {
        val sourceName = if (sourceId != null) {
            state.getEntity(sourceId)?.get<CardComponent>()?.name ?: "Unknown"
        } else {
            "Unknown"
        }

        var currentState = state
        val allEvents = mutableListOf<GameEvent>()

        for ((index, playerId) in playerIds.withIndex()) {
            val validPermanents = findValidPermanents(currentState, playerId, filter)

            if (validPermanents.isEmpty()) {
                continue
            }

            if (validPermanents.size <= count) {
                // Auto-sacrifice without prompting
                val result = sacrificePermanents(currentState, playerId, validPermanents)
                currentState = result.state
                allEvents.addAll(result.events)
                continue
            }

            // Player must choose — pause and store remaining players in continuation
            val remainingPlayers = playerIds.drop(index + 1)
            return presentSacrificeDecision(
                currentState, playerId, sourceId, sourceName,
                validPermanents, minSelections = count, maxSelections = count,
                remainingPlayers = remainingPlayers, filter = filter, count = count,
                priorEvents = allEvents
            )
        }

        return ExecutionResult.success(currentState, allEvents)
    }

    private fun findValidPermanents(
        state: GameState,
        playerId: EntityId,
        filter: GameObjectFilter
    ): List<EntityId> {
        return BattlefieldFilterUtils.findMatchingOnBattlefield(
            state, filter.youControl(), PredicateContext(controllerId = playerId)
        )
    }

    private fun presentSacrificeDecision(
        state: GameState,
        playerId: EntityId,
        sourceId: EntityId?,
        sourceName: String,
        validPermanents: List<EntityId>,
        minSelections: Int,
        maxSelections: Int,
        remainingPlayers: List<EntityId>,
        filter: GameObjectFilter,
        count: Int,
        priorEvents: List<GameEvent>
    ): ExecutionResult {
        val prompt = buildString {
            append("Choose ")
            if (minSelections == 1) append("a creature") else append("$minSelections creatures")
            append(" to sacrifice")
        }

        val decisionResult = decisionHandler.createCardSelectionDecision(
            state = state,
            playerId = playerId,
            sourceId = sourceId,
            sourceName = sourceName,
            prompt = prompt,
            options = validPermanents,
            minSelections = minSelections,
            maxSelections = maxSelections,
            ordered = false,
            phase = DecisionPhase.RESOLUTION,
            useTargetingUI = true
        )

        val continuation = SacrificeContinuation(
            decisionId = decisionResult.pendingDecision!!.id,
            playerId = playerId,
            sourceId = sourceId,
            sourceName = sourceName,
            remainingPlayers = remainingPlayers,
            filter = filter,
            count = count
        )

        val stateWithContinuation = decisionResult.state.pushContinuation(continuation)

        return ExecutionResult.paused(
            stateWithContinuation,
            decisionResult.pendingDecision,
            priorEvents + decisionResult.events
        )
    }

    internal fun sacrificePermanents(
        state: GameState,
        playerId: EntityId,
        permanentIds: List<EntityId>
    ): ExecutionResult {
        val battlefieldZone = ZoneKey(playerId, Zone.BATTLEFIELD)
        val graveyardZone = ZoneKey(playerId, Zone.GRAVEYARD)

        var newState = state
        val events = mutableListOf<GameEvent>()

        for (permanentId in permanentIds) {
            if (permanentId !in newState.getZone(battlefieldZone)) continue

            val permanentName = newState.getEntity(permanentId)?.get<CardComponent>()?.name ?: "Unknown"
            newState = newState.removeFromZone(battlefieldZone, permanentId)
            newState = newState.addToZone(graveyardZone, permanentId)

            events.add(
                ZoneChangeEvent(
                    entityId = permanentId,
                    entityName = permanentName,
                    fromZone = Zone.BATTLEFIELD,
                    toZone = Zone.GRAVEYARD,
                    ownerId = playerId
                )
            )
        }

        if (permanentIds.isNotEmpty()) {
            val permanentNames = permanentIds.map { id ->
                state.getEntity(id)?.get<CardComponent>()?.name ?: "Unknown"
            }
            events.add(0, PermanentsSacrificedEvent(playerId, permanentIds, permanentNames))
        }

        return ExecutionResult.success(newState, events)
    }
}
