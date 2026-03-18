package com.wingedsheep.engine.handlers.effects.combat

import com.wingedsheep.engine.core.DecisionContext
import com.wingedsheep.engine.core.ExecutionResult
import com.wingedsheep.engine.core.PreventDamageFromChosenSourceContinuation
import com.wingedsheep.engine.core.SelectCardsDecision
import com.wingedsheep.engine.handlers.DynamicAmountEvaluator
import com.wingedsheep.engine.handlers.EffectContext
import com.wingedsheep.engine.handlers.effects.EffectExecutor
import com.wingedsheep.engine.handlers.effects.TargetResolutionUtils
import com.wingedsheep.engine.handlers.effects.DamageUtils
import com.wingedsheep.engine.handlers.effects.ZoneMovementUtils
import com.wingedsheep.engine.handlers.effects.ReplacementEffectUtils
import com.wingedsheep.engine.handlers.effects.BattlefieldFilterUtils
import com.wingedsheep.engine.state.GameState
import com.wingedsheep.engine.state.components.identity.CardComponent
import com.wingedsheep.sdk.model.EntityId
import com.wingedsheep.sdk.scripting.effects.PreventNextDamageFromChosenSourceEffect
import java.util.UUID
import kotlin.reflect.KClass

/**
 * Executor for PreventNextDamageFromChosenSourceEffect.
 *
 * Resolves the target, then presents the controller with a choice of all game objects
 * that could be damage sources (permanents on the battlefield + spells on the stack).
 * After the source is chosen, a continuation resumer creates the prevention shield.
 */
class PreventNextDamageFromChosenSourceExecutor(
    private val amountEvaluator: DynamicAmountEvaluator
) : EffectExecutor<PreventNextDamageFromChosenSourceEffect> {

    override val effectType: KClass<PreventNextDamageFromChosenSourceEffect> =
        PreventNextDamageFromChosenSourceEffect::class

    override fun execute(
        state: GameState,
        effect: PreventNextDamageFromChosenSourceEffect,
        context: EffectContext
    ): ExecutionResult {
        val controllerId = context.controllerId
        val targetId = TargetResolutionUtils.resolveTarget(effect.target, context)
            ?: return ExecutionResult.error(state, "Could not resolve target for PreventNextDamageFromChosenSourceEffect")

        val amount = amountEvaluator.evaluate(state, effect.amount, context)
        if (amount <= 0) return ExecutionResult.success(state)

        // Gather all possible sources: permanents on battlefield + spells on stack
        val sourceIds = mutableListOf<EntityId>()

        for (entityId in state.getBattlefield()) {
            if (state.getEntity(entityId)?.get<CardComponent>() != null) {
                sourceIds.add(entityId)
            }
        }

        for (entityId in state.stack) {
            if (state.getEntity(entityId)?.get<CardComponent>() != null) {
                sourceIds.add(entityId)
            }
        }

        if (sourceIds.isEmpty()) {
            return ExecutionResult.success(state)
        }

        val decisionId = UUID.randomUUID().toString()

        val decision = SelectCardsDecision(
            id = decisionId,
            playerId = controllerId,
            prompt = "Choose a source of damage",
            context = DecisionContext(
                sourceId = context.sourceId,
                sourceName = context.sourceId?.let { state.getEntity(it)?.get<CardComponent>()?.name }
            ),
            options = sourceIds,
            minSelections = 1,
            maxSelections = 1,
            useTargetingUI = true
        )

        val continuation = PreventDamageFromChosenSourceContinuation(
            decisionId = decisionId,
            controllerId = controllerId,
            targetId = targetId,
            amount = amount,
            sourceId = context.sourceId,
            sourceName = context.sourceId?.let { state.getEntity(it)?.get<CardComponent>()?.name }
        )

        val newState = state.withPendingDecision(decision).pushContinuation(continuation)
        return ExecutionResult.paused(newState, decision)
    }
}
