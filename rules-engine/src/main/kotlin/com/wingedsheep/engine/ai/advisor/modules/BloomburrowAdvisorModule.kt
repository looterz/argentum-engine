package com.wingedsheep.engine.ai.advisor.modules

import com.wingedsheep.engine.ai.advisor.*
import com.wingedsheep.engine.ai.evaluation.BoardPresence
import com.wingedsheep.engine.core.*
import com.wingedsheep.engine.state.components.battlefield.SummoningSicknessComponent
import com.wingedsheep.engine.state.components.battlefield.TappedComponent
import com.wingedsheep.engine.state.components.identity.CardComponent
import com.wingedsheep.engine.state.components.identity.LifeTotalComponent
import com.wingedsheep.engine.state.GameState
import com.wingedsheep.engine.mechanics.layers.ProjectedState
import com.wingedsheep.sdk.core.Keyword
import com.wingedsheep.sdk.core.Phase
import com.wingedsheep.sdk.core.Step
import com.wingedsheep.sdk.core.Zone
import com.wingedsheep.sdk.model.EntityId

/**
 * AI advisors for Bloomburrow (BLB) cards.
 *
 * Organized by archetype rather than individual cards — many cards share
 * the same strategic pattern (e.g., all combat tricks should be held for combat).
 */
class BloomburrowAdvisorModule : CardAdvisorModule {
    override fun register(registry: CardAdvisorRegistry) {
        registry.register(CombatTrickAdvisor)
        registry.register(InstantRemovalAdvisor)
        registry.register(CounterspellAdvisor)
        registry.register(BoardWipeAdvisor)
        registry.register(GiftRemovalAdvisor)
        registry.register(GiftBoardWipeAdvisor)
        registry.register(GiftCombatTrickAdvisor)
        registry.register(GiftCounterspellAdvisor)
        registry.register(GiftCardDrawAdvisor)
        registry.register(GiftProtectionAdvisor)
        registry.register(GiftBounceAdvisor)
    }
}

// ═════════════════════════════════════════════════════════════════════════════
// Shared Utilities
// ═════════════════════════════════════════════════════════════════════════════

/** True when the game is in a combat step where tricks are most effective. */
private fun isCombatStep(state: GameState): Boolean =
    state.step.phase == Phase.COMBAT && state.step != Step.END_COMBAT

/** True when it's the AI's own main phase. */
private fun isOwnMainPhase(state: GameState, playerId: EntityId): Boolean =
    state.activePlayerId == playerId && state.step.isMainPhase

/** True when it's the opponent's turn. */
private fun isOpponentsTurn(state: GameState, playerId: EntityId): Boolean =
    state.activePlayerId != playerId

/** Sum of creature board value for a player. */
private fun creatureBoardValue(state: GameState, projected: ProjectedState, playerId: EntityId): Double {
    return projected.getBattlefieldControlledBy(playerId).sumOf { entityId ->
        val card = state.getEntity(entityId)?.get<CardComponent>() ?: return@sumOf 0.0
        if (projected.isCreature(entityId)) {
            BoardPresence.permanentValue(state, projected, entityId, card)
        } else 0.0
    }
}

/** Count of creatures a player controls. */
private fun creatureCount(projected: ProjectedState, playerId: EntityId): Int =
    projected.getBattlefieldControlledBy(playerId).count { projected.isCreature(it) }

/** Whether the player has untapped creatures (potential blockers / attack threats). */
private fun hasUntappedCreatures(state: GameState, projected: ProjectedState, playerId: EntityId): Boolean =
    projected.getBattlefieldControlledBy(playerId).any { entityId ->
        projected.isCreature(entityId) && state.getEntity(entityId)?.get<TappedComponent>() == null
    }

/** Get player's life total. */
private fun lifeTotal(state: GameState, playerId: EntityId): Int =
    state.getEntity(playerId)?.get<LifeTotalComponent>()?.life ?: 0

// ═════════════════════════════════════════════════════════════════════════════
// Combat Tricks — hold for combat, don't waste on main phase
// ═════════════════════════════════════════════════════════════════════════════

/**
 * Covers all instant-speed pump spells. The generic AI often casts these during
 * main phase because the 1-ply simulation sees "bigger creature = better board."
 * In practice these should almost always be held for combat.
 */
object CombatTrickAdvisor : CardAdvisor {
    override val cardNames = setOf(
        // Pure pump / protection instants
        "Shore Up",
        "High Stride",
        "Overprotect",
        "Mabel's Mettle",
        "Scales of Shale",
        "Might of the Meek",
        "Rabbit Response",
        "Valley Rally",
        "Rabid Gnaw",
        // Flash aura (combat trick)
        "Feather of Flight",
    )

    override fun evaluateCast(context: CastContext): Double? {
        val state = context.state
        val playerId = context.playerId

        // During combat: let default simulation decide (it works well here)
        if (isCombatStep(state)) return null

        // On opponent's turn outside combat: small penalty but allow it
        // (could be protecting from removal)
        if (isOpponentsTurn(state, playerId) && !isCombatStep(state)) {
            return context.defaultScore - 1.0
        }

        // Own main phase: heavily penalize — hold the trick for combat
        if (isOwnMainPhase(state, playerId)) {
            return context.passScore - 1.0
        }

        return null
    }
}

// ═════════════════════════════════════════════════════════════════════════════
// Instant Removal — prefer opponent's turn or combat
// ═════════════════════════════════════════════════════════════════════════════

/**
 * Instant-speed removal should be held for the opponent's turn or combat
 * rather than cast proactively during main phase. Sorcery-speed removal
 * doesn't need an advisor since it can only be cast at sorcery speed anyway.
 */
object InstantRemovalAdvisor : CardAdvisor {
    override val cardNames = setOf(
        "Sonar Strike",
        "Repel Calamity",
        "Take Out the Trash",
        "Early Winter",
        "Conduct Electricity",
        "Polliwallop",
    )

    override fun evaluateCast(context: CastContext): Double? {
        val state = context.state
        val playerId = context.playerId

        // During combat or opponent's turn: let simulation decide
        if (isCombatStep(state) || isOpponentsTurn(state, playerId)) return null

        // Own main phase: penalize. The default simulation sees
        // "creature gone = good" but misses the timing advantage
        // of removing an attacker mid-combat.
        if (isOwnMainPhase(state, playerId)) {
            return context.defaultScore - 3.0
        }

        return null
    }
}

// ═════════════════════════════════════════════════════════════════════════════
// Counterspells — hold for opponent's turn, never cast proactively
// ═════════════════════════════════════════════════════════════════════════════

/**
 * Counterspells must be held for the opponent's spells. The AI sometimes
 * sees the "draw 2" mode on Spellgyre during its own turn and casts it,
 * wasting a potential counter.
 */
object CounterspellAdvisor : CardAdvisor {
    override val cardNames = setOf(
        "Dazzling Denial",
        "Spellgyre",
    )

    override fun evaluateCast(context: CastContext): Double? {
        val state = context.state
        val playerId = context.playerId

        // Opponent's turn with something on the stack: let simulation decide
        if (isOpponentsTurn(state, playerId) && state.stack.isNotEmpty()) return null

        // Own turn: heavily penalize — hold it
        if (state.activePlayerId == playerId) {
            return context.passScore - 2.0
        }

        // Opponent's turn but nothing to counter: still hold it
        if (state.stack.isEmpty()) {
            return context.passScore - 1.0
        }

        return null
    }
}

// ═════════════════════════════════════════════════════════════════════════════
// Board Wipes — only cast when behind on board
// ═════════════════════════════════════════════════════════════════════════════

/**
 * Board wipes destroy our own creatures too. Only worthwhile when the
 * opponent's board is significantly more valuable than ours.
 */
object BoardWipeAdvisor : CardAdvisor {
    override val cardNames = setOf(
        "Starfall Invocation",
        "Wildfire Howl",
    )

    override fun evaluateCast(context: CastContext): Double? {
        val state = context.state
        val playerId = context.playerId
        val opponentId = state.getOpponent(playerId) ?: return null
        val projected = context.projected

        val myBoardValue = creatureBoardValue(state, projected, playerId)
        val oppBoardValue = creatureBoardValue(state, projected, opponentId)

        // Ahead or even on board: don't wipe
        if (oppBoardValue <= myBoardValue * 1.2) {
            return context.passScore - 2.0
        }

        // Behind on board: bonus scales with how far behind we are
        val deficit = oppBoardValue - myBoardValue
        return context.defaultScore + deficit * 0.3
    }
}

// ════════════════════════════════════════════════════════════════════════��════
// Gift Cards — evaluate whether the enhanced effect is worth the gift
//
// Gift cards are modal: mode 1 = base effect, mode 2 = enhanced effect but
// opponent draws a card (or gets a Food token). The AI needs to weigh the
// upgrade against giving the opponent card advantage.
// ═════════════════════════════════════════════════════════════════════════════

/**
 * Gift removal spells (Nocturnal Hunger, Parting Gust).
 *
 * Mode 1: removal with a downside (lose life, temporary exile).
 * Mode 2 (gift): clean removal but opponent gets a token/card.
 *
 * Generally prefer the gift mode when the target is high-value (worth
 * more than a card), and the non-gift mode for small threats.
 */
object GiftRemovalAdvisor : CardAdvisor {
    override val cardNames = setOf(
        "Nocturnal Hunger",
        "Parting Gust",
    )

    override fun respondToDecision(context: AdvisorDecisionContext): DecisionResponse? {
        val decision = context.decision as? ChooseModeDecision ?: return null
        if (decision.modes.size != 2) return null

        val state = context.state
        val playerId = context.playerId
        val opponentId = state.getOpponent(playerId) ?: return null

        // Evaluate opponent's board — if they have strong creatures,
        // the permanent removal of gift mode is worth the card they draw
        val oppCreatureCount = creatureCount(context.projected, opponentId)
        val myLife = lifeTotal(state, playerId)

        // For Nocturnal Hunger: mode 1 = destroy + lose 2 life, mode 2 = gift + destroy (no life loss)
        // For Parting Gust: mode 1 = temporary exile, mode 2 = gift + permanent exile
        val preferGift = when (context.sourceCardName) {
            "Nocturnal Hunger" -> myLife <= 8  // avoid life loss when low
            "Parting Gust" -> true  // permanent exile almost always better than temporary
            else -> false
        }

        val modeIndex = if (preferGift) 1 else 0
        val available = decision.modes.filter { it.available }
        val chosen = available.getOrNull(modeIndex) ?: available.first()
        return ModesChosenResponse(decision.id, listOf(chosen.index))
    }
}

/**
 * Gift board wipes (Starfall Invocation, Wildfire Howl).
 *
 * Mode 2 (gift) on Starfall Invocation lets you return a creature from your
 * graveyard after the wipe — very powerful when behind.
 * Mode 2 (gift) on Wildfire Howl adds a targeted damage + the opponent draws.
 *
 * Prefer gift mode when far behind (the extra value outweighs the card),
 * use base mode when the wipe alone is sufficient.
 */
object GiftBoardWipeAdvisor : CardAdvisor {
    override val cardNames = setOf(
        "Starfall Invocation",
        "Wildfire Howl",
    )

    override fun respondToDecision(context: AdvisorDecisionContext): DecisionResponse? {
        val decision = context.decision as? ChooseModeDecision ?: return null
        if (decision.modes.size != 2) return null

        val state = context.state
        val playerId = context.playerId
        val opponentId = state.getOpponent(playerId) ?: return null

        val myBoardValue = creatureBoardValue(state, context.projected, playerId)
        val oppBoardValue = creatureBoardValue(state, context.projected, opponentId)
        val deficit = oppBoardValue - myBoardValue

        // Gift mode when far behind — the extra value is worth the card
        val preferGift = deficit > 6.0

        val modeIndex = if (preferGift) 1 else 0
        val available = decision.modes.filter { it.available }
        val chosen = available.getOrNull(modeIndex) ?: available.first()
        return ModesChosenResponse(decision.id, listOf(chosen.index))
    }
}

/**
 * Gift combat tricks (Crumb and Get It, Valley Rally).
 *
 * Mode 2 (gift) gives a Food token to opponent but enhances the trick
 * (indestructible, first strike). Prefer gift when the creature would
 * die without it, skip gift when the base pump is enough to win combat.
 */
object GiftCombatTrickAdvisor : CardAdvisor {
    override val cardNames = setOf(
        "Crumb and Get It",
        "Valley Rally",
    )

    override fun evaluateCast(context: CastContext): Double? {
        // Same timing logic as regular combat tricks
        return CombatTrickAdvisor.evaluateCast(context)
    }

    override fun respondToDecision(context: AdvisorDecisionContext): DecisionResponse? {
        val decision = context.decision as? ChooseModeDecision ?: return null
        if (decision.modes.size != 2) return null

        // Simulate both modes and pick the better outcome
        val available = decision.modes.filter { it.available }
        if (available.size <= 1) {
            return ModesChosenResponse(decision.id, listOf(available.first().index))
        }

        val scores = available.map { mode ->
            val result = context.simulator.simulateDecision(
                context.state,
                ModesChosenResponse(decision.id, listOf(mode.index))
            )
            mode to context.evaluator.evaluate(result.state, result.state.projectedState, context.playerId)
        }
        val best = scores.maxBy { it.second }
        return ModesChosenResponse(decision.id, listOf(best.first.index))
    }
}

/**
 * Gift counterspell (Long River's Pull).
 *
 * Mode 1: counter creature spell only. Mode 2 (gift): counter any spell.
 * Always use gift mode when countering a non-creature spell (only option).
 * For creature spells, prefer non-gift mode to avoid giving opponent a card.
 */
object GiftCounterspellAdvisor : CardAdvisor {
    override val cardNames = setOf(
        "Long River's Pull",
    )

    override fun evaluateCast(context: CastContext): Double? {
        // Same hold-for-opponent's-turn logic as regular counterspells
        return CounterspellAdvisor.evaluateCast(context)
    }

    override fun respondToDecision(context: AdvisorDecisionContext): DecisionResponse? {
        val decision = context.decision as? ChooseModeDecision ?: return null
        if (decision.modes.size != 2) return null

        val available = decision.modes.filter { it.available }
        if (available.size <= 1) {
            return ModesChosenResponse(decision.id, listOf(available.first().index))
        }

        // Mode 1 (index 0) counters creature spells only — cheaper/no gift
        // Mode 2 (index 1) counters any spell but gifts opponent a card
        // Default to non-gift mode. The engine will only offer mode 1 if the
        // target is a creature spell, so if we're here with both available,
        // the target is a creature spell and mode 1 is preferred.
        val chosen = available.first()
        return ModesChosenResponse(decision.id, listOf(chosen.index))
    }
}

/**
 * Gift card draw (Mind Spiral, Sazacap's Brew).
 *
 * These are sorcery-speed so timing isn't the issue — the question is
 * whether giving the opponent a card/token is worth the enhanced effect.
 * Generally prefer gift mode when ahead on board (can afford to give
 * opponent resources) or when the upgrade is large.
 */
object GiftCardDrawAdvisor : CardAdvisor {
    override val cardNames = setOf(
        "Mind Spiral",
        "Sazacap's Brew",
    )

    override fun respondToDecision(context: AdvisorDecisionContext): DecisionResponse? {
        val decision = context.decision as? ChooseModeDecision ?: return null
        if (decision.modes.size != 2) return null

        // Simulate both modes — card draw decisions are hard to heuristic
        val available = decision.modes.filter { it.available }
        if (available.size <= 1) {
            return ModesChosenResponse(decision.id, listOf(available.first().index))
        }

        val scores = available.map { mode ->
            val result = context.simulator.simulateDecision(
                context.state,
                ModesChosenResponse(decision.id, listOf(mode.index))
            )
            mode to context.evaluator.evaluate(result.state, result.state.projectedState, context.playerId)
        }

        // Slightly penalize gift mode (opponent gets a card) since simulation
        // can't fully account for the future value of that card
        val adjusted = scores.map { (mode, score) ->
            val penalty = if (mode.index == 1) 1.5 else 0.0
            mode to (score - penalty)
        }
        val best = adjusted.maxBy { it.second }
        return ModesChosenResponse(decision.id, listOf(best.first.index))
    }
}

/**
 * Gift protection spell (Dawn's Truce).
 *
 * Mode 1: hexproof for your creatures until end of turn.
 * Mode 2 (gift): hexproof + indestructible but opponent draws.
 *
 * Prefer gift mode when facing lethal or a board wipe — indestructible
 * is a huge upgrade. Otherwise use base mode to deny opponent cards.
 */
object GiftProtectionAdvisor : CardAdvisor {
    override val cardNames = setOf(
        "Dawn's Truce",
    )

    override fun evaluateCast(context: CastContext): Double? {
        // Protection should be held for opponent's turn (reactive)
        val state = context.state
        val playerId = context.playerId

        if (isOpponentsTurn(state, playerId)) return null

        // Own turn: penalize (hold for opponent's removal/combat)
        if (isOwnMainPhase(state, playerId)) {
            return context.passScore - 2.0
        }

        return null
    }

    override fun respondToDecision(context: AdvisorDecisionContext): DecisionResponse? {
        val decision = context.decision as? ChooseModeDecision ?: return null
        if (decision.modes.size != 2) return null

        val available = decision.modes.filter { it.available }
        if (available.size <= 1) {
            return ModesChosenResponse(decision.id, listOf(available.first().index))
        }

        val state = context.state
        val playerId = context.playerId
        val myCreatureValue = creatureBoardValue(state, context.projected, playerId)

        // If we have significant board presence, indestructible is very valuable
        // (protects more total value) — worth giving opponent a card
        val preferGift = myCreatureValue > 8.0
        val modeIndex = if (preferGift) 1 else 0
        val chosen = available.getOrNull(modeIndex) ?: available.first()
        return ModesChosenResponse(decision.id, listOf(chosen.index))
    }
}

/**
 * Gift bounce spells (Into the Flood Maw, Run Away Together).
 *
 * Mode 1: bounce creature. Mode 2 (gift): bounce nonland permanent + token.
 * Prefer gift when targeting a high-value non-creature permanent.
 */
object GiftBounceAdvisor : CardAdvisor {
    override val cardNames = setOf(
        "Into the Flood Maw",
    )

    override fun respondToDecision(context: AdvisorDecisionContext): DecisionResponse? {
        val decision = context.decision as? ChooseModeDecision ?: return null
        if (decision.modes.size != 2) return null

        val available = decision.modes.filter { it.available }
        if (available.size <= 1) {
            return ModesChosenResponse(decision.id, listOf(available.first().index))
        }

        // Simulate both — the wider targeting of gift mode (nonland permanent
        // vs creature) might hit something more valuable
        val scores = available.map { mode ->
            val result = context.simulator.simulateDecision(
                context.state,
                ModesChosenResponse(decision.id, listOf(mode.index))
            )
            mode to context.evaluator.evaluate(result.state, result.state.projectedState, context.playerId)
        }
        // Small penalty for gift mode
        val adjusted = scores.map { (mode, score) ->
            val penalty = if (mode.index == 1) 1.0 else 0.0
            mode to (score - penalty)
        }
        val best = adjusted.maxBy { it.second }
        return ModesChosenResponse(decision.id, listOf(best.first.index))
    }
}
