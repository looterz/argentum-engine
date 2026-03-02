package com.wingedsheep.engine.handlers.effects.library

import com.wingedsheep.engine.core.ExecutionResult
import com.wingedsheep.engine.core.ZoneChangeEvent
import com.wingedsheep.engine.handlers.EffectContext
import com.wingedsheep.engine.handlers.effects.EffectExecutor
import com.wingedsheep.engine.state.GameState
import com.wingedsheep.engine.state.ZoneKey
import com.wingedsheep.engine.state.components.battlefield.LinkedExileComponent
import com.wingedsheep.engine.state.components.identity.CardComponent
import com.wingedsheep.engine.state.components.identity.OwnerComponent
import com.wingedsheep.sdk.core.Zone
import com.wingedsheep.sdk.scripting.effects.TakeFromLinkedExileEffect
import kotlin.reflect.KClass

/**
 * Executor for TakeFromLinkedExileEffect.
 *
 * Takes the first card from the source permanent's LinkedExileComponent
 * and moves it to the controller's hand. Updates the linked exile list.
 *
 * If the linked exile pile is empty, nothing happens.
 */
class TakeFromLinkedExileExecutor : EffectExecutor<TakeFromLinkedExileEffect> {

    override val effectType: KClass<TakeFromLinkedExileEffect> = TakeFromLinkedExileEffect::class

    override fun execute(
        state: GameState,
        effect: TakeFromLinkedExileEffect,
        context: EffectContext
    ): ExecutionResult {
        val sourceId = context.sourceId ?: return ExecutionResult.success(state)
        val sourceContainer = state.getEntity(sourceId)
            ?: return ExecutionResult.success(state)

        val linkedExile = sourceContainer.get<LinkedExileComponent>()
            ?: return ExecutionResult.success(state)

        if (linkedExile.exiledIds.isEmpty()) {
            return ExecutionResult.success(state)
        }

        // Take the first card from the pile
        val cardId = linkedExile.exiledIds.first()
        val remainingIds = linkedExile.exiledIds.drop(1)

        var newState = state

        // Update the linked exile component
        newState = newState.updateEntity(sourceId) { c ->
            c.with(LinkedExileComponent(remainingIds))
        }

        // Move card from exile to controller's hand
        val ownerId = newState.getEntity(cardId)?.get<OwnerComponent>()?.playerId
            ?: context.controllerId
        val exileZone = ZoneKey(ownerId, Zone.EXILE)
        val handZone = ZoneKey(context.controllerId, Zone.HAND)
        val cardName = newState.getEntity(cardId)?.get<CardComponent>()?.name ?: "Card"

        newState = newState.removeFromZone(exileZone, cardId)
        newState = newState.addToZone(handZone, cardId)

        val events = listOf(
            ZoneChangeEvent(
                entityId = cardId,
                entityName = cardName,
                fromZone = Zone.EXILE,
                toZone = Zone.HAND,
                ownerId = ownerId
            )
        )

        return ExecutionResult.success(newState, events)
    }
}
