package com.wingedsheep.engine.handlers.continuations

import com.wingedsheep.engine.core.*
import com.wingedsheep.engine.handlers.effects.TargetResolutionUtils
import com.wingedsheep.engine.handlers.effects.DamageUtils
import com.wingedsheep.engine.handlers.effects.ZoneMovementUtils
import com.wingedsheep.engine.handlers.effects.ReplacementEffectUtils
import com.wingedsheep.engine.handlers.effects.BattlefieldFilterUtils
import com.wingedsheep.engine.handlers.effects.ZoneMovementUtils.stripBattlefieldComponents
import com.wingedsheep.engine.state.GameState
import com.wingedsheep.engine.state.ZoneKey
import com.wingedsheep.engine.state.components.battlefield.CountersComponent
import com.wingedsheep.engine.state.components.identity.CardComponent
import com.wingedsheep.engine.state.components.identity.ControllerComponent
import com.wingedsheep.sdk.core.CounterType
import com.wingedsheep.sdk.core.Zone
import com.wingedsheep.sdk.model.EntityId

class StateBasedContinuationResumer(
    private val ctx: ContinuationContext
) : ContinuationResumerModule {

    override fun resumers(): List<ContinuationResumer<*>> = listOf(
        resumer(LegendRuleContinuation::class, ::resumeLegendRule)
    )

    private fun resumeLegendRule(
        state: GameState,
        continuation: LegendRuleContinuation,
        response: DecisionResponse,
        checkForMore: CheckForMore
    ): ExecutionResult {
        if (response !is CardsSelectedResponse) {
            return ExecutionResult.error(state, "Expected card selection response for legend rule")
        }

        if (response.selectedCards.size != 1) {
            return ExecutionResult.error(state, "Must select exactly one legendary permanent to keep")
        }

        val keepEntityId = response.selectedCards.first()

        // Validate the selection is one of the duplicates
        if (keepEntityId !in continuation.allDuplicates) {
            return ExecutionResult.error(state, "Selected permanent is not one of the legendary duplicates")
        }

        // Put all other duplicates into graveyard
        val toRemove = continuation.allDuplicates.filter { it != keepEntityId }

        var newState = state
        val events = mutableListOf<GameEvent>()

        for (entityId in toRemove) {
            val container = newState.getEntity(entityId) ?: continue
            val cardComponent = container.get<CardComponent>() ?: continue
            val result = putPermanentInGraveyardForLegendRule(newState, entityId, cardComponent)
            newState = result.newState
            events.addAll(result.events)
        }

        return checkForMore(newState, events)
    }

    /**
     * Puts a permanent into the graveyard for the legend rule (704.5j).
     * Respects zone change replacement effects.
     */
    private fun putPermanentInGraveyardForLegendRule(
        state: GameState,
        entityId: EntityId,
        cardComponent: CardComponent
    ): ExecutionResult {
        val container = state.getEntity(entityId) ?: return ExecutionResult.success(state)
        val lastKnownCounterCount = container.get<CountersComponent>()
            ?.getCount(CounterType.PLUS_ONE_PLUS_ONE) ?: 0
        // Capture last-known projected power/toughness before stripping (for trigger filters)
        val projected = state.projectedState
        val lastKnownPower = projected.getPower(entityId)
        val lastKnownToughness = projected.getToughness(entityId)
        val controllerId = container.get<ControllerComponent>()?.playerId
            ?: cardComponent.ownerId
            ?: return ExecutionResult.success(state)

        val ownerId = cardComponent.ownerId ?: controllerId

        // Check for zone change replacement effects
        val redirectResult = ZoneMovementUtils.checkZoneChangeRedirect(
            state, entityId, Zone.BATTLEFIELD, Zone.GRAVEYARD
        )
        val destinationZone = redirectResult.destinationZone

        val battlefieldZone = ZoneKey(controllerId, Zone.BATTLEFIELD)
        val destinationZoneKey = ZoneKey(ownerId, destinationZone)

        var newState = state
        newState = newState.removeFromZone(battlefieldZone, entityId)
        newState = newState.addToZone(destinationZoneKey, entityId)

        // Clean up combat references before stripping components
        newState = ZoneMovementUtils.cleanupCombatReferences(newState, entityId)

        // Remove permanent components
        newState = newState.updateEntity(entityId) { c -> stripBattlefieldComponents(c) }

        val events = mutableListOf<GameEvent>(
            ZoneChangeEvent(
                entityId,
                cardComponent.name,
                Zone.BATTLEFIELD,
                destinationZone,
                ownerId,
                lastKnownCounterCount = lastKnownCounterCount,
                lastKnownPower = lastKnownPower,
                lastKnownToughness = lastKnownToughness
            )
        )

        // Apply additional replacement effect if any
        if (redirectResult.additionalEffect != null) {
            newState = ZoneMovementUtils.applyReplacementAdditionalEffect(
                newState, redirectResult.additionalEffect, redirectResult.effectControllerId, entityId
            )
        }

        return ExecutionResult.success(newState, events)
    }
}
