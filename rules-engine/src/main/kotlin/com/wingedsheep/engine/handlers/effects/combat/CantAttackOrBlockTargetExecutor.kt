package com.wingedsheep.engine.handlers.effects.combat

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
import com.wingedsheep.sdk.scripting.effects.CantAttackOrBlockTargetEffect
import kotlin.reflect.KClass

/**
 * Executor for CantAttackOrBlockTargetEffect.
 * "Target creature can't attack or block this turn."
 *
 * Creates two floating effects (SetCantAttack + SetCantBlock) on the targeted creature.
 * Used by Briber's Purse and similar effects.
 */
class CantAttackOrBlockTargetExecutor : EffectExecutor<CantAttackOrBlockTargetEffect> {

    override val effectType: KClass<CantAttackOrBlockTargetEffect> = CantAttackOrBlockTargetEffect::class

    override fun execute(
        state: GameState,
        effect: CantAttackOrBlockTargetEffect,
        context: EffectContext
    ): ExecutionResult {
        val entityId = TargetResolutionUtils.resolveTarget(effect.target, context)
            ?: return ExecutionResult.success(state)
        val container = state.getEntity(entityId) ?: return ExecutionResult.success(state)
        container.get<CardComponent>() ?: return ExecutionResult.success(state)

        val affectedEntities = setOf(entityId)
        val sourceName = context.sourceId?.let { state.getEntity(it)?.get<CardComponent>()?.name }

        val cantAttackEffect = ActiveFloatingEffect(
            id = EntityId.generate(),
            effect = FloatingEffectData(
                layer = Layer.ABILITY,
                sublayer = null,
                modification = SerializableModification.SetCantAttack,
                affectedEntities = affectedEntities
            ),
            duration = effect.duration,
            sourceId = context.sourceId,
            sourceName = sourceName,
            controllerId = context.controllerId,
            timestamp = System.currentTimeMillis()
        )

        val cantBlockEffect = ActiveFloatingEffect(
            id = EntityId.generate(),
            effect = FloatingEffectData(
                layer = Layer.ABILITY,
                sublayer = null,
                modification = SerializableModification.SetCantBlock,
                affectedEntities = affectedEntities
            ),
            duration = effect.duration,
            sourceId = context.sourceId,
            sourceName = sourceName,
            controllerId = context.controllerId,
            timestamp = System.currentTimeMillis()
        )

        val newState = state.copy(
            floatingEffects = state.floatingEffects + cantAttackEffect + cantBlockEffect
        )

        return ExecutionResult.success(newState)
    }
}
