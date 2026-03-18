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
import com.wingedsheep.sdk.scripting.effects.GrantCantBeBlockedExceptByColorEffect
import kotlin.reflect.KClass

/**
 * Executor for GrantCantBeBlockedExceptByColorEffect.
 * "Black creatures you control can't be blocked this turn except by black creatures."
 *
 * This creates floating effects for each creature matching the filter, marking them
 * as only blockable by creatures of the specified color. The CombatManager checks
 * for this restriction during declare blockers validation.
 */
class GrantCantBeBlockedExceptByColorExecutor : EffectExecutor<GrantCantBeBlockedExceptByColorEffect> {

    override val effectType: KClass<GrantCantBeBlockedExceptByColorEffect> = GrantCantBeBlockedExceptByColorEffect::class

    override fun execute(
        state: GameState,
        effect: GrantCantBeBlockedExceptByColorEffect,
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

        val floatingEffect = ActiveFloatingEffect(
            id = EntityId.generate(),
            effect = FloatingEffectData(
                layer = Layer.ABILITY,
                sublayer = null,
                modification = SerializableModification.CantBeBlockedExceptByColor(
                    color = effect.canOnlyBeBlockedByColor.name
                ),
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
