package com.wingedsheep.engine.handlers.effects.permanent

import com.wingedsheep.engine.core.ExecutionResult
import com.wingedsheep.engine.handlers.EffectContext
import com.wingedsheep.engine.handlers.effects.EffectExecutor
import com.wingedsheep.engine.handlers.effects.TargetResolutionUtils.resolveTarget
import com.wingedsheep.engine.mechanics.layers.ActiveFloatingEffect
import com.wingedsheep.engine.mechanics.layers.FloatingEffectData
import com.wingedsheep.engine.mechanics.layers.Layer
import com.wingedsheep.engine.mechanics.layers.SerializableModification
import com.wingedsheep.engine.mechanics.layers.Sublayer
import com.wingedsheep.engine.state.GameState
import com.wingedsheep.engine.state.components.identity.CardComponent
import com.wingedsheep.sdk.model.EntityId
import com.wingedsheep.sdk.scripting.effects.BecomeCreatureEffect
import kotlin.reflect.KClass

/**
 * Executor for BecomeCreatureEffect.
 * Turns a permanent into a creature by creating floating effects across multiple layers.
 *
 * Used for Sarkhan, the Dragonspeaker's +1 and similar "becomes a creature" effects.
 * Creates all floating effects atomically to avoid validation issues with intermediate states.
 */
class BecomeCreatureExecutor : EffectExecutor<BecomeCreatureEffect> {

    override val effectType: KClass<BecomeCreatureEffect> = BecomeCreatureEffect::class

    override fun execute(
        state: GameState,
        effect: BecomeCreatureEffect,
        context: EffectContext
    ): ExecutionResult {
        val targetId = resolveTarget(effect.target, context)
            ?: return ExecutionResult.success(state)

        // Verify the target is still on the battlefield
        if (targetId !in state.getBattlefield()) {
            return ExecutionResult.success(state)
        }

        val controllerId = context.controllerId
            ?: return ExecutionResult.error(state, "No controller for BecomeCreature effect")

        val affectedEntities = setOf(targetId)
        val timestamp = System.currentTimeMillis()
        val sourceName = context.sourceId?.let { state.getEntity(it)?.get<CardComponent>()?.name }

        val floatingEffects = mutableListOf<ActiveFloatingEffect>()

        // Layer 4 (TYPE): Add CREATURE type
        floatingEffects.add(createFloatingEffect(
            layer = Layer.TYPE,
            modification = SerializableModification.AddType("CREATURE"),
            affectedEntities = affectedEntities,
            duration = effect.duration,
            sourceId = context.sourceId,
            sourceName = sourceName,
            controllerId = controllerId,
            timestamp = timestamp
        ))

        // Layer 4 (TYPE): Remove specified types (e.g., PLANESWALKER)
        for (type in effect.removeTypes) {
            floatingEffects.add(createFloatingEffect(
                layer = Layer.TYPE,
                modification = SerializableModification.RemoveType(type),
                affectedEntities = affectedEntities,
                duration = effect.duration,
                sourceId = context.sourceId,
                sourceName = sourceName,
                controllerId = controllerId,
                timestamp = timestamp
            ))
        }

        // Layer 4 (TYPE): Set creature subtypes
        if (effect.creatureTypes.isNotEmpty()) {
            floatingEffects.add(createFloatingEffect(
                layer = Layer.TYPE,
                modification = SerializableModification.SetCreatureSubtypes(effect.creatureTypes),
                affectedEntities = affectedEntities,
                duration = effect.duration,
                sourceId = context.sourceId,
                sourceName = sourceName,
                controllerId = controllerId,
                timestamp = timestamp
            ))
        }

        // Layer 5 (COLOR): Change color if specified
        if (effect.colors != null) {
            floatingEffects.add(createFloatingEffect(
                layer = Layer.COLOR,
                modification = SerializableModification.ChangeColor(effect.colors!!),
                affectedEntities = affectedEntities,
                duration = effect.duration,
                sourceId = context.sourceId,
                sourceName = sourceName,
                controllerId = controllerId,
                timestamp = timestamp
            ))
        }

        // Layer 6 (ABILITY): Grant keywords
        for (keyword in effect.keywords) {
            floatingEffects.add(createFloatingEffect(
                layer = Layer.ABILITY,
                modification = SerializableModification.GrantKeyword(keyword.name),
                affectedEntities = affectedEntities,
                duration = effect.duration,
                sourceId = context.sourceId,
                sourceName = sourceName,
                controllerId = controllerId,
                timestamp = timestamp
            ))
        }

        // Layer 7b (POWER_TOUGHNESS, SET_VALUES): Set base P/T
        floatingEffects.add(createFloatingEffect(
            layer = Layer.POWER_TOUGHNESS,
            sublayer = Sublayer.SET_VALUES,
            modification = SerializableModification.SetPowerToughness(effect.power, effect.toughness),
            affectedEntities = affectedEntities,
            duration = effect.duration,
            sourceId = context.sourceId,
            sourceName = sourceName,
            controllerId = controllerId,
            timestamp = timestamp
        ))

        val newState = state.copy(
            floatingEffects = state.floatingEffects + floatingEffects
        )

        return ExecutionResult.success(newState)
    }

    private fun createFloatingEffect(
        layer: Layer,
        modification: SerializableModification,
        affectedEntities: Set<EntityId>,
        duration: com.wingedsheep.sdk.scripting.Duration,
        sourceId: EntityId?,
        sourceName: String?,
        controllerId: EntityId,
        timestamp: Long,
        sublayer: Sublayer? = null
    ): ActiveFloatingEffect = ActiveFloatingEffect(
        id = EntityId.generate(),
        effect = FloatingEffectData(
            layer = layer,
            sublayer = sublayer,
            modification = modification,
            affectedEntities = affectedEntities
        ),
        duration = duration,
        sourceId = sourceId,
        sourceName = sourceName,
        controllerId = controllerId,
        timestamp = timestamp
    )
}
