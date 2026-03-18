package com.wingedsheep.engine.handlers.effects.permanent

import com.wingedsheep.engine.core.ExecutionResult
import com.wingedsheep.engine.handlers.EffectContext
import com.wingedsheep.engine.handlers.effects.EffectExecutor
import com.wingedsheep.engine.handlers.effects.TargetResolutionUtils
import com.wingedsheep.engine.handlers.effects.DamageUtils
import com.wingedsheep.engine.handlers.effects.ZoneMovementUtils
import com.wingedsheep.engine.handlers.effects.ReplacementEffectUtils
import com.wingedsheep.engine.handlers.effects.BattlefieldFilterUtils
import com.wingedsheep.engine.mechanics.layers.ActiveFloatingEffect
import com.wingedsheep.engine.mechanics.layers.FloatingEffectData
import com.wingedsheep.engine.mechanics.layers.Layer
import com.wingedsheep.engine.mechanics.layers.SerializableModification
import com.wingedsheep.engine.state.GameState
import com.wingedsheep.engine.state.components.identity.CardComponent
import com.wingedsheep.sdk.model.EntityId
import com.wingedsheep.sdk.scripting.effects.SetGroupCreatureSubtypesEffect
import kotlin.reflect.KClass

/**
 * Executor for SetGroupCreatureSubtypesEffect.
 * "Each creature you control becomes a Shade until end of turn" and similar group type effects.
 */
class SetGroupCreatureSubtypesExecutor : EffectExecutor<SetGroupCreatureSubtypesEffect> {

    override val effectType: KClass<SetGroupCreatureSubtypesEffect> = SetGroupCreatureSubtypesEffect::class

    override fun execute(
        state: GameState,
        effect: SetGroupCreatureSubtypesEffect,
        context: EffectContext
    ): ExecutionResult {
        val filter = effect.filter
        val excludeSelfId = if (filter.excludeSelf) context.sourceId else null
        val affectedEntities = BattlefieldFilterUtils.findMatchingOnBattlefield(
            state, filter.baseFilter, context, excludeSelfId
        ).toSet()

        if (affectedEntities.isEmpty()) {
            return ExecutionResult.success(state)
        }

        val sourceName = context.sourceId?.let { state.getEntity(it)?.get<CardComponent>()?.name }

        val floatingEffect = ActiveFloatingEffect(
            id = EntityId.generate(),
            effect = FloatingEffectData(
                layer = Layer.TYPE,
                sublayer = null,
                modification = SerializableModification.SetCreatureSubtypes(effect.subtypes),
                affectedEntities = affectedEntities
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
