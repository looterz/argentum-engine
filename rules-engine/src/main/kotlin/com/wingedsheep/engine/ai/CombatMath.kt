package com.wingedsheep.engine.ai

import com.wingedsheep.engine.mechanics.layers.ProjectedState
import com.wingedsheep.engine.state.GameState
import com.wingedsheep.engine.state.components.battlefield.DamageComponent
import com.wingedsheep.engine.state.components.battlefield.TappedComponent
import com.wingedsheep.engine.state.components.identity.CardComponent
import com.wingedsheep.sdk.core.AbilityFlag
import com.wingedsheep.sdk.core.Keyword
import com.wingedsheep.sdk.model.EntityId

/**
 * Pure utility functions for combat math used by CombatAdvisor.
 *
 * All functions are stateless — they read from GameState/ProjectedState
 * and return computed values without side effects.
 */
object CombatMath {

    // ── Evasion & Blocking Checks ───────────────────────────────────────

    /**
     * Returns true if [blocker] can legally block [attacker] based on evasion keywords.
     * This is a heuristic check covering the most common evasion abilities —
     * it doesn't replicate the full BlockEvasionRules pipeline.
     */
    fun canBeBlockedBy(
        state: GameState,
        projected: ProjectedState,
        attacker: EntityId,
        blocker: EntityId
    ): Boolean {
        val aKeywords = projected.getKeywords(attacker)
        val bKeywords = projected.getKeywords(blocker)

        // Unblockable
        if (AbilityFlag.CANT_BE_BLOCKED.name in aKeywords) return false

        // Flying: only blocked by flying or reach
        if (Keyword.FLYING.name in aKeywords) {
            if (Keyword.FLYING.name !in bKeywords && Keyword.REACH.name !in bKeywords) return false
        }

        // Shadow: only blocked by shadow
        if (Keyword.SHADOW.name in aKeywords) {
            if (Keyword.SHADOW.name !in bKeywords) return false
        }

        // Horsemanship: only blocked by horsemanship
        if (Keyword.HORSEMANSHIP.name in aKeywords) {
            if (Keyword.HORSEMANSHIP.name !in bKeywords) return false
        }

        // Fear: only blocked by artifact or black creatures
        if (Keyword.FEAR.name in aKeywords) {
            val bColors = projected.getColors(blocker)
            val bTypes = projected.getTypes(blocker)
            if ("BLACK" !in bColors && "ARTIFACT" !in bTypes) return false
        }

        // Intimidate: only blocked by artifact or same-color creatures
        if (Keyword.INTIMIDATE.name in aKeywords) {
            val aColors = projected.getColors(attacker)
            val bColors = projected.getColors(blocker)
            val bTypes = projected.getTypes(blocker)
            val sharesColor = aColors.any { it in bColors }
            if (!sharesColor && "ARTIFACT" !in bTypes) return false
        }

        // Menace: requires 2+ blockers (can't be single-blocked)
        // We return true here — menace is handled at the assignment level
        // since a single canBeBlockedBy check can't express "needs 2 blockers"

        // Landwalk checks (approximate — check if opponent controls matching land)
        val attackerController = projected.getController(attacker)
        val blockerController = projected.getController(blocker)
        if (blockerController != null) {
            val defenderLands = projected.getBattlefieldControlledBy(blockerController)
            if (Keyword.SWAMPWALK.name in aKeywords && defenderLands.any { projected.hasSubtype(it, "Swamp") }) return false
            if (Keyword.FORESTWALK.name in aKeywords && defenderLands.any { projected.hasSubtype(it, "Forest") }) return false
            if (Keyword.ISLANDWALK.name in aKeywords && defenderLands.any { projected.hasSubtype(it, "Island") }) return false
            if (Keyword.MOUNTAINWALK.name in aKeywords && defenderLands.any { projected.hasSubtype(it, "Mountain") }) return false
            if (Keyword.PLAINSWALK.name in aKeywords && defenderLands.any { projected.hasSubtype(it, "Plains") }) return false
        }

        return true
    }

    /**
     * Returns all [opponentBlockers] that can legally block [attacker].
     */
    fun getValidBlockersFor(
        state: GameState,
        projected: ProjectedState,
        attacker: EntityId,
        opponentBlockers: List<EntityId>
    ): List<EntityId> {
        return opponentBlockers.filter { canBeBlockedBy(state, projected, attacker, it) }
    }

    /**
     * Returns true if [attacker] cannot be blocked by any creature in [opponentBlockers].
     */
    fun isEvasive(
        state: GameState,
        projected: ProjectedState,
        attacker: EntityId,
        opponentBlockers: List<EntityId>
    ): Boolean {
        // Menace creatures need 2+ blockers, treat as evasive if opponent has ≤1 valid blocker
        val aKeywords = projected.getKeywords(attacker)
        val validBlockers = getValidBlockersFor(state, projected, attacker, opponentBlockers)
        if (Keyword.MENACE.name in aKeywords) return validBlockers.size <= 1
        return validBlockers.isEmpty()
    }

    // ── Combat Outcome Calculations ─────────────────────────────────────

    /**
     * Returns how much damage [attacker] would deal to the defending player
     * if blocked by [blocker]. Accounts for trample; returns 0 if no trample.
     */
    fun damageDealtThrough(
        projected: ProjectedState,
        attacker: EntityId,
        blocker: EntityId
    ): Int {
        val aPower = projected.getPower(attacker) ?: 0
        val aKeywords = projected.getKeywords(attacker)
        val bToughness = projected.getToughness(blocker) ?: 0

        if (Keyword.TRAMPLE.name !in aKeywords) return 0

        // With deathtouch + trample, only 1 damage needed per blocker
        val lethalDamage = if (Keyword.DEATHTOUCH.name in aKeywords) 1 else bToughness
        return (aPower - lethalDamage).coerceAtLeast(0)
    }

    /**
     * Returns how much damage is prevented by blocking [attacker] with [blocker].
     * For tramplers, this is only the blocker's toughness (or 1 with deathtouch).
     * For non-tramplers, this prevents all damage.
     */
    fun damagePreventedByBlock(
        projected: ProjectedState,
        attacker: EntityId,
        blocker: EntityId
    ): Int {
        val aPower = projected.getPower(attacker) ?: 0
        val aKeywords = projected.getKeywords(attacker)

        if (Keyword.TRAMPLE.name !in aKeywords) return aPower

        // With trample, only prevent damage equal to blocker toughness
        val bToughness = projected.getToughness(blocker) ?: 0
        // With deathtouch + trample, attacker only needs to assign 1 to blocker
        val lethalForBlocker = if (Keyword.DEATHTOUCH.name in aKeywords) 1 else bToughness
        return lethalForBlocker.coerceAtMost(aPower)
    }

    /**
     * Would [attacker] kill [defender] in regular combat?
     * Accounts for power, toughness, existing damage, deathtouch, and indestructible.
     */
    fun wouldKillInCombat(
        state: GameState,
        projected: ProjectedState,
        attacker: EntityId,
        defender: EntityId
    ): Boolean {
        val aPower = projected.getPower(attacker) ?: 0
        val aKeywords = projected.getKeywords(attacker)
        val dKeywords = projected.getKeywords(defender)

        if (Keyword.INDESTRUCTIBLE.name in dKeywords) return false
        if (aPower <= 0) return false
        if (Keyword.DEATHTOUCH.name in aKeywords) return true

        val dToughness = projected.getToughness(defender) ?: 0
        val existingDamage = state.getEntity(defender)?.get<DamageComponent>()?.amount ?: 0
        return aPower + existingDamage >= dToughness
    }

    /**
     * Does [blocker] survive being blocked by / blocking [attacker]?
     * Checks if the attacker's power is enough to kill the blocker.
     */
    fun survivesBlock(
        state: GameState,
        projected: ProjectedState,
        attacker: EntityId,
        blocker: EntityId
    ): Boolean {
        return !wouldKillInCombat(state, projected, attacker, blocker)
    }

    /**
     * Does [blocker] survive first-strike damage from [attacker]?
     * If the attacker doesn't have first/double strike, the blocker always survives first-strike.
     * If the attacker has first strike and would kill the blocker, the blocker dies before dealing damage.
     */
    fun survivesFirstStrike(
        state: GameState,
        projected: ProjectedState,
        attacker: EntityId,
        blocker: EntityId
    ): Boolean {
        val aKeywords = projected.getKeywords(attacker)
        val bKeywords = projected.getKeywords(blocker)

        val attackerHasFirstStrike = Keyword.FIRST_STRIKE.name in aKeywords || Keyword.DOUBLE_STRIKE.name in aKeywords
        if (!attackerHasFirstStrike) return true

        // If blocker also has first/double strike, damage is simultaneous
        val blockerHasFirstStrike = Keyword.FIRST_STRIKE.name in bKeywords || Keyword.DOUBLE_STRIKE.name in bKeywords
        if (blockerHasFirstStrike) return true

        // Attacker deals first-strike damage first — does it kill the blocker?
        return !wouldKillInCombat(state, projected, attacker, blocker)
    }

    /**
     * Can [blocker] actually deal damage to [attacker] in combat?
     * Returns false if the blocker dies to first strike before dealing damage.
     */
    fun blockerDealsDamage(
        state: GameState,
        projected: ProjectedState,
        attacker: EntityId,
        blocker: EntityId
    ): Boolean {
        return survivesFirstStrike(state, projected, attacker, blocker)
    }

    /**
     * Effective incoming damage from [attacker], accounting for lifelink.
     * Lifelink damage is worth double: you lose life AND opponent gains life.
     */
    fun effectiveDamage(projected: ProjectedState, attacker: EntityId): Int {
        val power = projected.getPower(attacker) ?: 0
        val keywords = projected.getKeywords(attacker)
        return if (Keyword.LIFELINK.name in keywords) power * 2 else power
    }

    /**
     * Effective damage prevented by blocking [attacker] with [blocker], accounting for lifelink.
     */
    fun effectiveDamagePrevented(projected: ProjectedState, attacker: EntityId, blocker: EntityId): Int {
        val prevented = damagePreventedByBlock(projected, attacker, blocker)
        val keywords = projected.getKeywords(attacker)
        return if (Keyword.LIFELINK.name in keywords) prevented * 2 else prevented
    }

    // ── Lethal Analysis ─────────────────────────────────────────────────

    /**
     * Calculate guaranteed evasive damage — power from creatures that cannot be blocked.
     */
    fun calculateEvasiveDamage(
        state: GameState,
        projected: ProjectedState,
        attackers: List<EntityId>,
        opponentBlockers: List<EntityId>
    ): Int {
        return attackers
            .filter { isEvasive(state, projected, it, opponentBlockers) }
            .sumOf { (projected.getPower(it) ?: 0).coerceAtLeast(0) }
    }

    /**
     * Simulate optimal blocking by the opponent and return how much damage gets through.
     * Uses a greedy assignment: opponent blocks the highest-power non-evasive attackers first
     * using their highest-toughness blockers (to prevent trample overflow).
     */
    fun calculateDamageThroughOptimalBlocking(
        state: GameState,
        projected: ProjectedState,
        attackers: List<EntityId>,
        opponentBlockers: List<EntityId>
    ): Int {
        var totalDamage = 0
        val usedBlockers = mutableSetOf<EntityId>()

        // Separate evasive from blockable
        val evasive = mutableListOf<EntityId>()
        val blockable = mutableListOf<EntityId>()

        for (attacker in attackers) {
            if (isEvasive(state, projected, attacker, opponentBlockers)) {
                evasive.add(attacker)
            } else {
                blockable.add(attacker)
            }
        }

        // Evasive damage goes through unimpeded
        totalDamage += evasive.sumOf { (projected.getPower(it) ?: 0).coerceAtLeast(0) }

        // For blockable attackers: opponent assigns blockers optimally to minimize damage
        // Sort blockable attackers by power descending (opponent blocks biggest threats first)
        val sortedBlockable = blockable.sortedByDescending { projected.getPower(it) ?: 0 }

        for (attacker in sortedBlockable) {
            val aPower = projected.getPower(attacker) ?: 0
            if (aPower <= 0) continue

            val aKeywords = projected.getKeywords(attacker)
            val hasTrample = Keyword.TRAMPLE.name in aKeywords

            // Find best blocker: for tramplers, use highest-toughness blocker to absorb most damage
            // For non-tramplers, any valid blocker prevents all damage — use cheapest
            val validBlockers = getValidBlockersFor(state, projected, attacker, opponentBlockers)
                .filter { it !in usedBlockers }

            // Handle menace: needs 2 valid blockers
            if (Keyword.MENACE.name in aKeywords && validBlockers.size <= 1) {
                totalDamage += aPower
                continue
            }

            if (validBlockers.isEmpty()) {
                totalDamage += aPower
                continue
            }

            if (hasTrample) {
                // Opponent picks the highest-toughness blocker to minimize overflow
                val bestBlocker = validBlockers.maxByOrNull { projected.getToughness(it) ?: 0 }!!
                totalDamage += damageDealtThrough(projected, attacker, bestBlocker)
                usedBlockers.add(bestBlocker)
            } else {
                // Non-trampler: any blocker prevents all damage; opponent uses cheapest
                val cheapestBlocker = validBlockers.minByOrNull { creatureValue(state, projected, it) }!!
                usedBlockers.add(cheapestBlocker)
                // No damage gets through
            }
        }

        return totalDamage
    }

    // ── Race Clock ──────────────────────────────────────────────────────

    /**
     * Estimate how many turns until [attacker] can kill [defender] with evasive creatures.
     * Returns Int.MAX_VALUE if no evasive damage is available.
     */
    fun turnsToKill(
        state: GameState,
        projected: ProjectedState,
        attackerPlayer: EntityId,
        defenderPlayer: EntityId,
        opponentBlockers: List<EntityId>
    ): Int {
        val defenderLife = state.getEntity(defenderPlayer)
            ?.get<com.wingedsheep.engine.state.components.identity.LifeTotalComponent>()?.life ?: 20
        val myCreatures = projected.getBattlefieldControlledBy(attackerPlayer)
            .filter { projected.isCreature(it) && !state.getEntity(it)!!.has<TappedComponent>() }
        val evasiveDamage = calculateEvasiveDamage(state, projected, myCreatures, opponentBlockers)
        if (evasiveDamage <= 0) return Int.MAX_VALUE
        return (defenderLife + evasiveDamage - 1) / evasiveDamage // ceiling division
    }

    // ── Combat Trick Estimation ─────────────────────────────────────────

    /**
     * Estimate the potential pump bonus an opponent could apply based on untapped mana.
     * Returns a (power bonus, toughness bonus) pair.
     */
    fun estimateCombatTrickBonus(
        state: GameState,
        projected: ProjectedState,
        opponentId: EntityId
    ): Pair<Int, Int> {
        val untappedLands = projected.getBattlefieldControlledBy(opponentId).count { entityId ->
            val card = state.getEntity(entityId)?.get<CardComponent>()
            card != null && card.isLand && state.getEntity(entityId)?.has<TappedComponent>() != true
        }
        val cardsInHand = state.getHand(opponentId).size

        if (cardsInHand == 0) return 0 to 0

        return when {
            untappedLands >= 3 -> 3 to 3
            untappedLands >= 1 -> 2 to 2
            else -> 0 to 0
        }
    }

    // ── Helpers ──────────────────────────────────────────────────────────

    fun creatureValue(state: GameState, projected: ProjectedState, entityId: EntityId): Double {
        val card = state.getEntity(entityId)?.get<CardComponent>() ?: return 0.0
        return com.wingedsheep.engine.ai.evaluation.BoardPresence.permanentValue(state, projected, entityId, card)
    }

    /**
     * Get opponent's untapped creatures that could block.
     */
    fun getOpponentUntappedCreatures(
        state: GameState,
        projected: ProjectedState,
        opponentId: EntityId
    ): List<EntityId> {
        return projected.getBattlefieldControlledBy(opponentId).filter { entityId ->
            projected.isCreature(entityId) &&
                state.getEntity(entityId)?.has<TappedComponent>() != true
        }
    }
}
