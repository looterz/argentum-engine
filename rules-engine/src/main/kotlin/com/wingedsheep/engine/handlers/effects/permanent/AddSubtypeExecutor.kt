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
import com.wingedsheep.sdk.scripting.effects.AddSubtypeEffect
import kotlin.reflect.KClass

/**
 * Executor for AddSubtypeEffect.
 * Adds a subtype to any permanent (creature, land, artifact, etc.) via a floating effect.
 * Unlike AddCreatureTypeExecutor, this does not check if the target is a creature.
 */
class AddSubtypeExecutor : EffectExecutor<AddSubtypeEffect> {

    override val effectType: KClass<AddSubtypeEffect> = AddSubtypeEffect::class

    override fun execute(
        state: GameState,
        effect: AddSubtypeEffect,
        context: EffectContext
    ): ExecutionResult {
        val targetId = resolveTarget(effect.target, context)
            ?: return ExecutionResult.success(state)

        if (targetId !in state.getBattlefield()) {
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
