package com.wingedsheep.engine.handlers.effects.permanent

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
import com.wingedsheep.sdk.model.EntityId
import com.wingedsheep.sdk.scripting.effects.AddCreatureTypeEffect
import kotlin.reflect.KClass

/**
 * Executor for AddCreatureTypeEffect.
 * "It becomes a Zombie in addition to its other types." — adds a creature subtype to a single target.
 */
class AddCreatureTypeExecutor : EffectExecutor<AddCreatureTypeEffect> {

    override val effectType: KClass<AddCreatureTypeEffect> = AddCreatureTypeEffect::class

    override fun execute(
        state: GameState,
        effect: AddCreatureTypeEffect,
        context: EffectContext
    ): ExecutionResult {
        val targetId = resolveTarget(effect.target, context)
            ?: return ExecutionResult.success(state)

        if (targetId !in state.getBattlefield()) {
            return ExecutionResult.success(state)
        }

        val projected = state.projectedState
        if (!projected.isCreature(targetId)) {
            return ExecutionResult.success(state)
        }

        val subtype = if (effect.fromChosenValueKey != null) {
            context.chosenValues[effect.fromChosenValueKey]
                ?: return ExecutionResult.success(state)
        } else {
            effect.subtype
        }

        val sourceName = context.sourceId?.let { state.getEntity(it)?.get<CardComponent>()?.name }

        val floatingEffect = ActiveFloatingEffect(
            id = EntityId.generate(),
            effect = FloatingEffectData(
                layer = Layer.TYPE,
                sublayer = null,
                modification = SerializableModification.AddSubtype(subtype),
                affectedEntities = setOf(targetId)
            ),
            duration = effect.duration,
            sourceId = context.sourceId,
            sourceName = sourceName,
            controllerId = context.controllerId,
            timestamp = System.currentTimeMillis()
        )

        val newState = state.copy(
            floatingEffects = state.floatingEffects + floatingEffect
        )

        return ExecutionResult.success(newState)
    }
}
