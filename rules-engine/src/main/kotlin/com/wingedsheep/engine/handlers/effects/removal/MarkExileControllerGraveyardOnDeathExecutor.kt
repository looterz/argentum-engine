package com.wingedsheep.engine.handlers.effects.removal

import com.wingedsheep.engine.core.ExecutionResult
import com.wingedsheep.engine.handlers.EffectContext
import com.wingedsheep.engine.handlers.effects.EffectExecutor
import com.wingedsheep.engine.handlers.effects.TargetResolutionUtils.resolveTarget
import com.wingedsheep.engine.mechanics.layers.ActiveFloatingEffect
import com.wingedsheep.engine.mechanics.layers.FloatingEffectData
import com.wingedsheep.engine.mechanics.layers.Layer
import com.wingedsheep.engine.mechanics.layers.SerializableModification
import com.wingedsheep.engine.state.GameState
import com.wingedsheep.engine.state.components.identity.CardComponent
import com.wingedsheep.engine.state.components.identity.LifeTotalComponent
import com.wingedsheep.sdk.model.EntityId
import com.wingedsheep.sdk.scripting.Duration
import com.wingedsheep.sdk.scripting.effects.MarkExileControllerGraveyardOnDeathEffect
import kotlin.reflect.KClass

/**
 * Executor for MarkExileControllerGraveyardOnDeathEffect.
 * Adds an ExileControllerGraveyardOnDeath floating effect so that when the target creature
 * dies this turn, its controller's graveyard is exiled.
 *
 * If the target is a player (not a creature), this effect does nothing.
 */
class MarkExileControllerGraveyardOnDeathExecutor : EffectExecutor<MarkExileControllerGraveyardOnDeathEffect> {

    override val effectType: KClass<MarkExileControllerGraveyardOnDeathEffect> =
        MarkExileControllerGraveyardOnDeathEffect::class

    override fun execute(
        state: GameState,
        effect: MarkExileControllerGraveyardOnDeathEffect,
        context: EffectContext
    ): ExecutionResult {
        val targetId = resolveTarget(effect.target, context)
            ?: return ExecutionResult.error(state, "No valid target for exile-controller-graveyard-on-death marker")

        // Only applies to creatures, not players
        val isPlayer = state.getEntity(targetId)?.get<LifeTotalComponent>() != null
        if (isPlayer) {
            return ExecutionResult.success(state)
        }

        val floatingEffect = ActiveFloatingEffect(
            id = EntityId.generate(),
            effect = FloatingEffectData(
                layer = Layer.ABILITY,
                sublayer = null,
                modification = SerializableModification.ExileControllerGraveyardOnDeath,
                affectedEntities = setOf(targetId)
            ),
            duration = Duration.EndOfTurn,
            sourceId = context.sourceId,
            sourceName = context.sourceId?.let { state.getEntity(it)?.get<CardComponent>()?.name },
            controllerId = context.controllerId,
            timestamp = System.currentTimeMillis()
        )

        val newState = state.copy(
            floatingEffects = state.floatingEffects + floatingEffect
        )

        return ExecutionResult.success(newState)
    }
}
