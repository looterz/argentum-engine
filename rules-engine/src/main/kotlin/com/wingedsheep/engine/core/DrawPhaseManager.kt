package com.wingedsheep.engine.core

import com.wingedsheep.engine.handlers.DecisionHandler
import com.wingedsheep.engine.handlers.EffectContext
import com.wingedsheep.engine.handlers.effects.drawing.DrawReplacementShieldConsumer
import com.wingedsheep.engine.mechanics.StateBasedActionChecker
import com.wingedsheep.engine.mechanics.mana.ManaSolver
import com.wingedsheep.engine.state.GameState
import com.wingedsheep.engine.state.ZoneKey
import com.wingedsheep.engine.state.components.battlefield.ReplacementEffectSourceComponent
import com.wingedsheep.engine.state.components.battlefield.LinkedExileComponent
import com.wingedsheep.engine.state.components.identity.CardComponent
import com.wingedsheep.engine.state.components.player.CardsDrawnThisTurnComponent
import com.wingedsheep.engine.state.components.player.LossReason
import com.wingedsheep.engine.state.components.player.PlayerLostComponent
import com.wingedsheep.sdk.core.Step
import com.wingedsheep.sdk.core.Zone
import com.wingedsheep.sdk.model.EntityId
import com.wingedsheep.sdk.scripting.effects.Effect
import com.wingedsheep.sdk.scripting.AbilityCost
import com.wingedsheep.sdk.scripting.ReplaceDrawWithEffect
import com.wingedsheep.sdk.scripting.RevealFirstDrawEachTurn

/**
 * Handles draw step execution and all draw-related mechanics including
 * draw replacement effects, reveal-on-draw, and prompt-on-draw abilities.
 */
class DrawPhaseManager(
    private val cardRegistry: com.wingedsheep.engine.registry.CardRegistry?,
    private val decisionHandler: DecisionHandler,
    private val effectExecutor: ((GameState, Effect, EffectContext) -> ExecutionResult)?
) {

    /**
     * Perform the draw step (active player draws a card).
     * - Skip draw on first turn for first player (standard rule)
     */
    fun performDrawStep(state: GameState): ExecutionResult {
        val activePlayer = state.activePlayerId
            ?: return ExecutionResult.error(state, "No active player")

        // Skip draw on first turn for first player
        val isFirstTurnFirstPlayer = state.turnNumber == 1 && activePlayer == state.turnOrder.first()
        if (isFirstTurnFirstPlayer) {
            return ExecutionResult.success(
                state.withPriority(activePlayer),
                listOf(StepChangedEvent(Step.DRAW))
            )
        }

        // Check for "prompt on draw" abilities (e.g., Words of Wind)
        val promptResult = checkPromptOnDraw(state, activePlayer, 1, isDrawStep = true)
        if (promptResult != null) {
            return promptResult
        }

        // Draw a card
        val drawResult = drawCards(state, activePlayer, 1)
        if (!drawResult.isSuccess) {
            return drawResult
        }

        // Give priority to active player
        val newState = drawResult.newState.withPriority(activePlayer)
        return ExecutionResult.success(newState, drawResult.events + StepChangedEvent(Step.DRAW))
    }

    /**
     * Draw cards for a player.
     */
    fun drawCards(state: GameState, playerId: EntityId, count: Int): ExecutionResult {
        var newState = state
        val events = mutableListOf<GameEvent>()
        val drawnCards = mutableListOf<EntityId>()

        val libraryKey = ZoneKey(playerId, Zone.LIBRARY)
        val handKey = ZoneKey(playerId, Zone.HAND)

        val shieldConsumer = effectExecutor?.let { DrawReplacementShieldConsumer(it) }

        for (i in 0 until count) {
            // Check for unified draw replacement shields (Words of Worship/Wind/War/Waste/Wilding)
            if (shieldConsumer != null) {
                val shieldResult = shieldConsumer.consumeShield(
                    state = newState,
                    playerId = playerId,
                    remainingDraws = count - i - 1,
                    drawnCardsSoFar = drawnCards.toList(),
                    eventsSoFar = events.toList(),
                    isDrawStep = true
                )
                if (shieldResult != null) {
                    when (shieldResult) {
                        is DrawReplacementShieldConsumer.ConsumeResult.Paused -> {
                            // Emit CardsDrawnEvent for cards drawn before this shield was hit
                            val allEvents = events.toMutableList()
                            if (drawnCards.isNotEmpty()) {
                                val cardNames = drawnCards.map { newState.getEntity(it)?.get<CardComponent>()?.name ?: "Card" }
                                allEvents.add(0, CardsDrawnEvent(playerId, drawnCards.size, drawnCards.toList(), cardNames))
                            }
                            return ExecutionResult.paused(
                                shieldResult.result.state,
                                shieldResult.result.pendingDecision!!,
                                allEvents + shieldResult.result.events
                            )
                        }
                        is DrawReplacementShieldConsumer.ConsumeResult.Synchronous -> {
                            newState = shieldResult.state
                            events.addAll(shieldResult.events)
                            continue
                        }
                    }
                }
            }

            // Check for static draw replacement effects (e.g., Parallel Thoughts)
            val staticResult = checkStaticDrawReplacement(newState, playerId, count - i, drawnCards.toList())
            if (staticResult != null) {
                if (drawnCards.isNotEmpty()) {
                    val cardNames = drawnCards.map { newState.getEntity(it)?.get<CardComponent>()?.name ?: "Card" }
                    val allEvents = mutableListOf<GameEvent>(
                        CardsDrawnEvent(playerId, drawnCards.size, drawnCards.toList(), cardNames)
                    )
                    allEvents.addAll(events)
                    allEvents.addAll(staticResult.events)
                    return ExecutionResult.paused(
                        staticResult.state,
                        staticResult.pendingDecision!!,
                        allEvents
                    )
                }
                return staticResult
            }

            val library = newState.getZone(libraryKey)
            if (library.isEmpty()) {
                // Player tries to draw from empty library - they lose (Rule 704.5c)
                newState = newState.updateEntity(playerId) { container ->
                    container.with(PlayerLostComponent(LossReason.EMPTY_LIBRARY))
                }
                events.add(DrawFailedEvent(playerId, "Library is empty"))
                return ExecutionResult.success(newState, events)
            }

            // Draw from top of library
            val cardId = library.first()
            newState = newState.removeFromZone(libraryKey, cardId)
            newState = newState.addToZone(handKey, cardId)
            drawnCards.add(cardId)

            // Track draw count and check for reveal-first-draw effects
            val drawCountBefore = newState.getEntity(playerId)?.get<CardsDrawnThisTurnComponent>()?.count ?: 0
            newState = newState.updateEntity(playerId) { container ->
                container.with(CardsDrawnThisTurnComponent(count = drawCountBefore + 1))
            }
            if (drawCountBefore == 0) {
                val revealEvent = checkRevealFirstDraw(newState, playerId, cardId)
                if (revealEvent != null) {
                    events.add(revealEvent)
                }
            }
        }

        if (drawnCards.isNotEmpty()) {
            val cardNames = drawnCards.map { newState.getEntity(it)?.get<CardComponent>()?.name ?: "Card" }
            events.add(CardsDrawnEvent(playerId, drawnCards.size, drawnCards, cardNames))
        }
        return ExecutionResult.success(newState, events)
    }

    /**
     * Check if a drawn card should be revealed due to RevealFirstDrawEachTurn static abilities.
     * Returns a CardRevealedFromDrawEvent if any permanent controlled by this player has the ability.
     * Only called when this is the first draw of the turn (drawCountBefore == 0).
     */
    private fun checkRevealFirstDraw(
        state: GameState,
        playerId: EntityId,
        drawnCardId: EntityId
    ): CardRevealedFromDrawEvent? {
        if (cardRegistry == null) return null

        // Check if any permanent controlled by this player has RevealFirstDrawEachTurn
        val projected = state.projectedState
        val hasRevealAbility = projected.getBattlefieldControlledBy(playerId).any { permanentId ->
            val card = state.getEntity(permanentId)?.get<CardComponent>() ?: return@any false
            val cardDef = cardRegistry.getCard(card.cardDefinitionId) ?: return@any false
            cardDef.script.staticAbilities.any { it is RevealFirstDrawEachTurn }
        }

        if (!hasRevealAbility) return null

        val drawnCard = state.getEntity(drawnCardId)?.get<CardComponent>() ?: return null
        return CardRevealedFromDrawEvent(
            playerId = playerId,
            cardEntityId = drawnCardId,
            cardName = drawnCard.name,
            isCreature = drawnCard.typeLine.isCreature
        )
    }

    /**
     * Check if a player controls a permanent with an optional static draw replacement effect
     * (e.g., Parallel Thoughts). If so, present a yes/no decision.
     */
    internal fun checkStaticDrawReplacement(
        state: GameState,
        playerId: EntityId,
        drawCount: Int,
        drawnCardsSoFar: List<EntityId> = emptyList()
    ): ExecutionResult? {
        if (cardRegistry == null) return null

        val projected = state.projectedState
        val controlledPermanents = projected.getBattlefieldControlledBy(playerId)

        for (permanentId in controlledPermanents) {
            val container = state.getEntity(permanentId) ?: continue
            val replacementSource = container.get<ReplacementEffectSourceComponent>()
                ?: continue

            for (re in replacementSource.replacementEffects) {
                if (re !is ReplaceDrawWithEffect) continue
                if (!re.optional) continue

                val card = container.get<CardComponent>() ?: continue
                val linkedExile = container.get<LinkedExileComponent>()
                val pileCount = linkedExile?.exiledIds?.size ?: 0

                val decisionId = java.util.UUID.randomUUID().toString()
                val prompt = "Use ${card.name}? Put the top card of the exiled pile ($pileCount cards remaining) into your hand instead of drawing?"

                val decision = YesNoDecision(
                    id = decisionId,
                    playerId = playerId,
                    prompt = prompt,
                    context = DecisionContext(
                        sourceId = permanentId,
                        sourceName = card.name,
                        phase = DecisionPhase.RESOLUTION
                    )
                )

                val continuation = StaticDrawReplacementContinuation(
                    decisionId = decisionId,
                    drawingPlayerId = playerId,
                    sourceId = permanentId,
                    sourceName = card.name,
                    replacementEffect = re.replacementEffect,
                    drawCount = drawCount,
                    isDrawStep = true,
                    drawnCardsSoFar = drawnCardsSoFar
                )

                val stateWithDecision = state.withPendingDecision(decision)
                val stateWithContinuation = stateWithDecision.pushContinuation(continuation)

                return ExecutionResult.paused(
                    stateWithContinuation,
                    decision,
                    listOf(
                        DecisionRequestedEvent(
                            decisionId = decisionId,
                            playerId = playerId,
                            decisionType = "YES_NO",
                            prompt = decision.prompt
                        )
                    )
                )
            }
        }

        return null
    }

    /**
     * Check if a player has a "prompt on draw" activated ability that they can afford.
     * If so, present a mana source selection decision and pause.
     * Returns null if no prompt is needed.
     */
    internal fun checkPromptOnDraw(
        state: GameState,
        playerId: EntityId,
        drawCount: Int,
        isDrawStep: Boolean,
        declinedSourceIds: List<EntityId> = emptyList()
    ): ExecutionResult? {
        if (cardRegistry == null) return null

        // Scan the player's battlefield for permanents with promptOnDraw activated abilities
        val projected = state.projectedState
        val controlledPermanents = projected.getBattlefieldControlledBy(playerId)

        for (permanentId in controlledPermanents) {
            if (permanentId in declinedSourceIds) continue
            val container = state.getEntity(permanentId) ?: continue
            val card = container.get<CardComponent>() ?: continue
            val cardDef = cardRegistry.getCard(card.cardDefinitionId) ?: continue

            for (ability in cardDef.script.activatedAbilities) {
                if (!ability.promptOnDraw) continue

                // Check if the ability has a mana cost
                val manaCost = when (val cost = ability.cost) {
                    is AbilityCost.Mana -> cost.cost
                    is AbilityCost.Composite -> {
                        cost.costs.filterIsInstance<AbilityCost.Mana>()
                            .firstOrNull()?.cost
                    }
                    else -> null
                } ?: continue

                // Check if the player can afford it
                val manaSolver = ManaSolver(cardRegistry)
                if (!manaSolver.canPay(state, playerId, manaCost)) continue

                // Find available mana sources for the UI
                val sources = manaSolver.findAvailableManaSources(state, playerId)
                val sourceOptions = sources.map { source ->
                    ManaSourceOption(
                        entityId = source.entityId,
                        name = source.name,
                        producesColors = source.producesColors,
                        producesColorless = source.producesColorless
                    )
                }

                // Get auto-pay suggestion
                val solution = manaSolver.solve(state, playerId, manaCost)
                val autoPaySuggestion = solution?.sources?.map { it.entityId } ?: emptyList()

                // Create mana source selection decision with decline option
                val decisionId = java.util.UUID.randomUUID().toString()
                val decision = SelectManaSourcesDecision(
                    id = decisionId,
                    playerId = playerId,
                    prompt = "Pay ${manaCost} to activate ${card.name}?",
                    context = DecisionContext(
                        sourceId = permanentId,
                        sourceName = card.name,
                        phase = DecisionPhase.RESOLUTION
                    ),
                    availableSources = sourceOptions,
                    requiredCost = manaCost.toString(),
                    autoPaySuggestion = autoPaySuggestion,
                    canDecline = true
                )

                val continuation = DrawReplacementActivationContinuation(
                    decisionId = decisionId,
                    drawingPlayerId = playerId,
                    sourceId = permanentId,
                    sourceName = card.name,
                    abilityEffect = ability.effect,
                    manaCost = manaCost.toString(),
                    drawCount = drawCount,
                    isDrawStep = isDrawStep,
                    targetRequirements = ability.targetRequirements,
                    declinedSourceIds = declinedSourceIds
                )

                val stateWithDecision = state.withPendingDecision(decision)
                val stateWithContinuation = stateWithDecision.pushContinuation(continuation)

                return ExecutionResult.paused(
                    stateWithContinuation,
                    decision,
                    listOf(
                        DecisionRequestedEvent(
                            decisionId = decisionId,
                            playerId = playerId,
                            decisionType = "SELECT_MANA_SOURCES",
                            prompt = decision.prompt
                        )
                    )
                )
            }
        }

        return null
    }
}
