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
import com.wingedsheep.sdk.scripting.effects.GrantAttackBlockTaxPerCreatureTypeEffect
import kotlin.reflect.KClass

/**
 * Executor for GrantAttackBlockTaxPerCreatureTypeEffect.
 * Creates a floating effect that marks a creature as having a combat tax
 * based on the count of a specific creature type on the battlefield.
 */
class GrantAttackBlockTaxPerCreatureTypeExecutor : EffectExecutor<GrantAttackBlockTaxPerCreatureTypeEffect> {

    override val effectType: KClass<GrantAttackBlockTaxPerCreatureTypeEffect> =
        GrantAttackBlockTaxPerCreatureTypeEffect::class

    override fun execute(
        state: GameState,
        effect: GrantAttackBlockTaxPerCreatureTypeEffect,
        context: EffectContext
    ): ExecutionResult {
        val entityId = TargetResolutionUtils.resolveTarget(effect.target, context)
            ?: return ExecutionResult.success(state)
        val container = state.getEntity(entityId) ?: return ExecutionResult.success(state)
        container.get<CardComponent>() ?: return ExecutionResult.success(state)

        val floatingEffect = ActiveFloatingEffect(
            id = EntityId.generate(),
            effect = FloatingEffectData(
                layer = Layer.ABILITY,
                sublayer = null,
                modification = SerializableModification.AttackBlockTaxPerCreatureType(
                    creatureType = effect.creatureType,
                    manaCostPer = effect.manaCostPer
                ),
                affectedEntities = setOf(entityId)
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
