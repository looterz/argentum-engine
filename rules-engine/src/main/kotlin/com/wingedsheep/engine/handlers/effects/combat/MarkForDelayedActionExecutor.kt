package com.wingedsheep.engine.handlers.effects.combat

import com.wingedsheep.engine.core.ExecutionResult
import com.wingedsheep.engine.handlers.EffectContext
import com.wingedsheep.engine.handlers.effects.EffectExecutor
import com.wingedsheep.engine.handlers.effects.TargetResolutionUtils.resolveTarget
import com.wingedsheep.engine.state.GameState
import com.wingedsheep.engine.state.components.combat.MarkedForDestructionAtEndOfCombatComponent
import com.wingedsheep.engine.state.components.combat.MarkedForSacrificeAtEndOfCombatComponent
import com.wingedsheep.sdk.scripting.effects.DelayedAction
import com.wingedsheep.sdk.scripting.effects.MarkForDelayedActionEffect
import kotlin.reflect.KClass

/**
 * Executor for MarkForDelayedActionEffect.
 * Marks a permanent for destruction or sacrifice at end of combat by adding
 * the appropriate marker component.
 *
 * The actual destruction/sacrifice is processed by [TurnManager] when the END_COMBAT step begins.
 */
class MarkForDelayedActionExecutor : EffectExecutor<MarkForDelayedActionEffect> {

    override val effectType: KClass<MarkForDelayedActionEffect> = MarkForDelayedActionEffect::class

    override fun execute(
        state: GameState,
        effect: MarkForDelayedActionEffect,
        context: EffectContext
    ): ExecutionResult {
        val targetId = resolveTarget(effect.target, context)
            ?: return ExecutionResult.success(state)

        // Verify the target is still on the battlefield
        if (!state.getBattlefield().contains(targetId)) {
            return ExecutionResult.success(state)
        }

        val component = when (effect.action) {
            DelayedAction.DESTROY -> MarkedForDestructionAtEndOfCombatComponent
            DelayedAction.SACRIFICE -> MarkedForSacrificeAtEndOfCombatComponent
        }

        val newState = state.updateEntity(targetId) { container ->
            container.with(component)
        }

        return ExecutionResult.success(newState)
    }
}
