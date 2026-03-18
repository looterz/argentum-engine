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
import com.wingedsheep.sdk.scripting.effects.SetCreatureSubtypesEffect
import kotlin.reflect.KClass

/**
 * Executor for SetCreatureSubtypesEffect.
 * "It becomes a Bird Giant." — sets creature subtypes on a single target.
 */
class SetCreatureSubtypesExecutor : EffectExecutor<SetCreatureSubtypesEffect> {

    override val effectType: KClass<SetCreatureSubtypesEffect> = SetCreatureSubtypesEffect::class

    override fun execute(
        state: GameState,
        effect: SetCreatureSubtypesEffect,
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

        // Resolve subtypes: from chosen value in context, or from hardcoded field
        val subtypes = if (effect.fromChosenValueKey != null) {
            val chosen = context.chosenValues[effect.fromChosenValueKey]
                ?: return ExecutionResult.success(state)
            setOf(chosen)
        } else {
            effect.subtypes
        }

        val sourceName = context.sourceId?.let { state.getEntity(it)?.get<CardComponent>()?.name }

        val floatingEffect = ActiveFloatingEffect(
            id = EntityId.generate(),
            effect = FloatingEffectData(
                layer = Layer.TYPE,
                sublayer = null,
                modification = SerializableModification.SetCreatureSubtypes(subtypes),
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
