package com.wingedsheep.engine.handlers.effects.combat

import com.wingedsheep.engine.core.ExecutionResult
import com.wingedsheep.engine.handlers.EffectContext
import com.wingedsheep.engine.handlers.PredicateContext
import com.wingedsheep.engine.handlers.PredicateEvaluator
import com.wingedsheep.engine.handlers.effects.EffectExecutor
import com.wingedsheep.engine.mechanics.layers.StateProjector
import com.wingedsheep.engine.mechanics.layers.ActiveFloatingEffect
import com.wingedsheep.engine.mechanics.layers.FloatingEffectData
import com.wingedsheep.engine.mechanics.layers.Layer
import com.wingedsheep.engine.mechanics.layers.SerializableModification
import com.wingedsheep.engine.state.GameState
import com.wingedsheep.engine.state.components.identity.CardComponent
import com.wingedsheep.sdk.model.EntityId
import com.wingedsheep.sdk.scripting.effects.CantBlockGroupEffect
import kotlin.reflect.KClass

/**
 * Executor for CantBlockGroupEffect.
 * "Creatures can't block this turn." / "[filter] creatures can't block this turn."
 *
 * Creates a floating effect with SetCantBlock for all creatures matching the filter.
 */
class CantBlockGroupExecutor : EffectExecutor<CantBlockGroupEffect> {

    override val effectType: KClass<CantBlockGroupEffect> = CantBlockGroupEffect::class

    private val predicateEvaluator = PredicateEvaluator()
    private val stateProjector = StateProjector()

    override fun execute(
        state: GameState,
        effect: CantBlockGroupEffect,
        context: EffectContext
    ): ExecutionResult {
        val affectedEntities = mutableSetOf<EntityId>()
        val filter = effect.filter
        val predicateContext = PredicateContext.fromEffectContext(context)
        val projected = stateProjector.project(state)

        for (entityId in state.getBattlefield()) {
            val container = state.getEntity(entityId) ?: continue
            container.get<CardComponent>() ?: continue

            if (!projected.isCreature(entityId)) continue

            if (filter.excludeSelf && entityId == context.sourceId) continue

            if (!predicateEvaluator.matchesWithProjection(state, projected, entityId, filter.baseFilter, predicateContext)) {
                continue
            }

            affectedEntities.add(entityId)
        }

        if (affectedEntities.isEmpty()) {
            return ExecutionResult.success(state)
        }

        val floatingEffect = ActiveFloatingEffect(
            id = EntityId.generate(),
            effect = FloatingEffectData(
                layer = Layer.ABILITY,
                sublayer = null,
                modification = SerializableModification.SetCantBlock,
                affectedEntities = affectedEntities
            ),
            duration = effect.duration,
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
