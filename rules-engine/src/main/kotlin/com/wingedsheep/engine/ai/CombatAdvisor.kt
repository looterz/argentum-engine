package com.wingedsheep.engine.ai

import com.wingedsheep.engine.ai.evaluation.BoardEvaluator
import com.wingedsheep.engine.ai.evaluation.BoardPresence
import com.wingedsheep.engine.core.DeclareAttackers
import com.wingedsheep.engine.core.DeclareBlockers
import com.wingedsheep.engine.core.GameAction
import com.wingedsheep.engine.legalactions.LegalAction
import com.wingedsheep.engine.mechanics.layers.ProjectedState
import com.wingedsheep.engine.state.GameState
import com.wingedsheep.engine.state.components.battlefield.TappedComponent
import com.wingedsheep.engine.state.components.identity.CardComponent
import com.wingedsheep.engine.state.components.identity.LifeTotalComponent
import com.wingedsheep.sdk.core.Keyword
import com.wingedsheep.sdk.model.EntityId

/**
 * Specialized advisor for attack and block decisions.
 *
 * Uses pure heuristic analysis (NOT simulation) because the simulator can't
 * resolve through combat phases — it would see "creatures tapped, no damage
 * dealt" and always prefer not attacking.
 */
class CombatAdvisor(
    private val simulator: GameSimulator,
    private val evaluator: BoardEvaluator
) {
    /**
     * Build a DeclareAttackers action choosing which creatures to send in.
     */
    fun chooseAttackers(
        state: GameState,
        legalAction: LegalAction,
        playerId: EntityId
    ): GameAction {
        val projected = state.projectedState
        val validAttackers = legalAction.validAttackers ?: emptyList()
        val defendingPlayers = legalAction.validAttackTargets ?: emptyList()

        if (validAttackers.isEmpty() || defendingPlayers.isEmpty()) {
            return DeclareAttackers(playerId, emptyMap())
        }

        val opponentId = state.getOpponent(playerId) ?: defendingPlayers.first()
        val opponentLife = state.getEntity(opponentId)?.get<LifeTotalComponent>()?.life ?: 20
        val opponentCreatures = CombatMath.getOpponentUntappedCreatures(state, projected, opponentId)

        // Identify planeswalker targets
        val planeswalkerTargets = defendingPlayers.filter { it != opponentId && projected.isPlaneswalker(it) }

        // ── Lethal check: only alpha-strike if damage actually gets through ──
        if (isLethalAttack(state, projected, validAttackers, opponentCreatures, opponentLife)) {
            return DeclareAttackers(playerId, validAttackers.associateWith { opponentId })
        }

        // ── Race clock: determine aggression level ──
        val myLife = state.getEntity(playerId)?.get<LifeTotalComponent>()?.life ?: 20
        val myTurnsToKill = CombatMath.turnsToKill(state, projected, playerId, opponentId, opponentCreatures)
        val theirBlockers = opponentCreatures
        val myCreatures = projected.getBattlefieldControlledBy(playerId)
            .filter { projected.isCreature(it) && state.getEntity(it)?.has<TappedComponent>() != true }
        val theirTurnsToKill = CombatMath.turnsToKill(state, projected, opponentId, playerId, myCreatures)
        val isAheadOnClock = myTurnsToKill < theirTurnsToKill

        // ── Combat trick estimation ──
        val (trickPowerBonus, trickToughnessBonus) = CombatMath.estimateCombatTrickBonus(state, projected, opponentId)

        // ── Defense budget: estimate crackback danger ──
        val opponentAllCreatures = projected.getBattlefieldControlledBy(opponentId)
            .filter { projected.isCreature(it) }
        val opponentCrackbackPower = opponentAllCreatures
            .sumOf { (projected.getPower(it) ?: 0).coerceAtLeast(0) }
        val crackbackIsLethal = opponentCrackbackPower >= myLife

        val attackerMap = mutableMapOf<EntityId, EntityId>()
        for (entityId in validAttackers) {
            val target = chooseAttackTarget(
                state, projected, entityId, opponentId, opponentCreatures,
                opponentLife, planeswalkerTargets, isAheadOnClock, crackbackIsLethal,
                trickPowerBonus, trickToughnessBonus
            )
            if (target != null) {
                attackerMap[entityId] = target
            }
        }

        // ── Defense budget: if crackback is lethal, hold back enough blockers ──
        if (crackbackIsLethal && attackerMap.isNotEmpty()) {
            retainDefenders(state, projected, playerId, opponentId, opponentAllCreatures, attackerMap, myLife)
        }

        return DeclareAttackers(playerId, attackerMap)
    }

    /**
     * Build a DeclareBlockers action choosing which creatures block which attackers.
     */
    fun chooseBlockers(
        state: GameState,
        legalAction: LegalAction,
        playerId: EntityId
    ): GameAction {
        val projected = state.projectedState
        val validBlockers = legalAction.validBlockers ?: emptyList()
        val mandatory = legalAction.mandatoryBlockerAssignments ?: emptyMap()

        if (validBlockers.isEmpty()) {
            return DeclareBlockers(playerId, emptyMap())
        }

        val attackers = getAttackingCreatures(state)
        if (attackers.isEmpty()) {
            return DeclareBlockers(playerId, emptyMap())
        }

        val myLife = state.getEntity(playerId)?.get<LifeTotalComponent>()?.life ?: 20
        // Use effective damage (lifelink counts double) for lethal check
        val incomingEffectiveDamage = attackers.sumOf { CombatMath.effectiveDamage(projected, it) }
        val incomingRawDamage = attackers.sumOf { (projected.getPower(it) ?: 0).coerceAtLeast(0) }
        val isLethal = incomingRawDamage >= myLife

        val blockerMap = mutableMapOf<EntityId, List<EntityId>>()

        // Handle mandatory blockers first
        val assignedBlockers = mutableSetOf<EntityId>()
        val blockedAttackers = mutableSetOf<EntityId>()
        for ((blockerId, mustBlockAttackers) in mandatory) {
            if (mustBlockAttackers.isNotEmpty()) {
                blockerMap[blockerId] = listOf(mustBlockAttackers.first())
                assignedBlockers.add(blockerId)
                blockedAttackers.add(mustBlockAttackers.first())
            }
        }

        if (isLethal) {
            assignBlocksForSurvival(state, projected, validBlockers, attackers, assignedBlockers, blockedAttackers, blockerMap)
        } else {
            assignBlocksForProfit(state, projected, validBlockers, attackers, assignedBlockers, blockedAttackers, blockerMap)
        }

        return DeclareBlockers(playerId, blockerMap)
    }

    // ── Lethal Analysis ─────────────────────────────────────────────────

    /**
     * Check if attacking with all creatures would be lethal even through optimal blocking.
     */
    private fun isLethalAttack(
        state: GameState,
        projected: ProjectedState,
        attackers: List<EntityId>,
        opponentBlockers: List<EntityId>,
        opponentLife: Int
    ): Boolean {
        val totalPower = attackers.sumOf { (projected.getPower(it) ?: 0).coerceAtLeast(0) }
        if (totalPower < opponentLife) return false

        // Guaranteed evasive damage
        val evasiveDamage = CombatMath.calculateEvasiveDamage(state, projected, attackers, opponentBlockers)
        if (evasiveDamage >= opponentLife) return true

        // Full simulation of optimal blocking
        val damageThrough = CombatMath.calculateDamageThroughOptimalBlocking(
            state, projected, attackers, opponentBlockers
        )
        return damageThrough >= opponentLife
    }

    // ── Attack decision ─────────────────────────────────────────────────

    /**
     * Decide whether a creature should attack and what to target.
     * Returns the target EntityId (player or planeswalker) or null to not attack.
     */
    private fun chooseAttackTarget(
        state: GameState,
        projected: ProjectedState,
        entityId: EntityId,
        opponentId: EntityId,
        opponentCreatures: List<EntityId>,
        opponentLife: Int,
        planeswalkerTargets: List<EntityId>,
        isAheadOnClock: Boolean,
        crackbackIsLethal: Boolean,
        trickPowerBonus: Int,
        trickToughnessBonus: Int
    ): EntityId? {
        val power = projected.getPower(entityId) ?: 0
        val toughness = projected.getToughness(entityId) ?: 0
        val keywords = projected.getKeywords(entityId)
        val myValue = CombatMath.creatureValue(state, projected, entityId)

        if (power <= 0) return null

        val isEvasive = CombatMath.isEvasive(state, projected, entityId, opponentCreatures)

        // ── Always attack: no downside ──
        if (Keyword.VIGILANCE.name in keywords) {
            return chooseBestTarget(state, projected, entityId, opponentId, planeswalkerTargets, isEvasive)
        }
        if (Keyword.INDESTRUCTIBLE.name in keywords) {
            return chooseBestTarget(state, projected, entityId, opponentId, planeswalkerTargets, isEvasive)
        }

        // ── No blockers: always attack ──
        if (opponentCreatures.isEmpty()) {
            return chooseBestTarget(state, projected, entityId, opponentId, planeswalkerTargets, isEvasive)
        }

        // ── Evasive: always attack ──
        if (isEvasive) {
            return chooseBestTarget(state, projected, entityId, opponentId, planeswalkerTargets, true)
        }

        // ── Deathtouch: always trades up ──
        if (Keyword.DEATHTOUCH.name in keywords) return opponentId

        // ── First/double strike: contextual — only if we have an advantage ──
        if (Keyword.FIRST_STRIKE.name in keywords || Keyword.DOUBLE_STRIKE.name in keywords) {
            val validBlockers = CombatMath.getValidBlockersFor(state, projected, entityId, opponentCreatures)
            // Attack if no blocker can kill us, or we can kill any blocker before it hits back
            val bestBlocker = validBlockers.minByOrNull { CombatMath.creatureValue(state, projected, it) }
            if (bestBlocker == null) return opponentId
            val blockerKillsUs = CombatMath.wouldKillInCombat(state, projected, bestBlocker, entityId)
            if (!blockerKillsUs) return opponentId
            // We have first strike — do we kill the blocker before it hits us?
            val weKillBlockerFirst = CombatMath.wouldKillInCombat(state, projected, entityId, bestBlocker)
            if (weKillBlockerFirst) return opponentId
            // First strike doesn't save us against this blocker — fall through to normal evaluation
        }

        // ── Low life opponent: be aggressive ──
        if (opponentLife <= 8 && !crackbackIsLethal) return opponentId

        // ── Ahead on race clock: be aggressive ──
        if (isAheadOnClock && !crackbackIsLethal) return opponentId

        // ── Core heuristic: attack if expected value is positive ──
        val validBlockers = CombatMath.getValidBlockersFor(state, projected, entityId, opponentCreatures)
        val bestBlocker = findCheapestEffectiveBlocker(state, projected, entityId, validBlockers, trickToughnessBonus)

        if (bestBlocker != null) {
            val bPower = (projected.getPower(bestBlocker) ?: 0) + trickPowerBonus
            val bToughness = (projected.getToughness(bestBlocker) ?: 0) + trickToughnessBonus
            val bKeywords = projected.getKeywords(bestBlocker)
            val canKillUs = bPower >= toughness || Keyword.DEATHTOUCH.name in bKeywords
            val weKillThem = power >= bToughness || Keyword.DEATHTOUCH.name in keywords
            val blockerValue = CombatMath.creatureValue(state, projected, bestBlocker)

            if (!canKillUs) {
                // They can't kill us → always attack
                return opponentId
            }

            if (weKillThem && blockerValue >= myValue * 0.8) {
                // Good trade
                return opponentId
            }

            // They can kill us and it's a bad trade — check board state
            val myCreatureCount = projected.getBattlefieldControlledBy(
                state.turnOrder.find { it != opponentId } ?: return null
            ).count { projected.isCreature(it) }
            if (myCreatureCount >= 3 && !crackbackIsLethal) return opponentId

            // Bad trade and crackback is dangerous — don't attack
            return null
        }

        // No blocker can kill us → attack
        return opponentId
    }

    /**
     * Choose the best attack target: player or a planeswalker.
     */
    private fun chooseBestTarget(
        state: GameState,
        projected: ProjectedState,
        attacker: EntityId,
        opponentId: EntityId,
        planeswalkerTargets: List<EntityId>,
        isEvasive: Boolean
    ): EntityId {
        if (planeswalkerTargets.isEmpty()) return opponentId

        // Only redirect evasive creatures to planeswalkers — ground creatures should pressure life total
        if (!isEvasive) return opponentId

        val power = projected.getPower(attacker) ?: 0

        // Find planeswalkers where our damage is meaningful (can kill or reduce significantly)
        val bestPw = planeswalkerTargets.maxByOrNull { pwId ->
            val loyalty = projected.getPower(pwId) ?: 0 // Loyalty is stored as power for PWs
            val pwValue = CombatMath.creatureValue(state, projected, pwId)
            // Prefer killing planeswalkers we can one-shot, or high-value ones
            if (power >= loyalty) pwValue + 5.0 else pwValue
        }

        if (bestPw != null) {
            val loyalty = projected.getPower(bestPw) ?: 0
            val pwValue = CombatMath.creatureValue(state, projected, bestPw)
            // Attack planeswalker if we can kill it, or if it's very valuable
            if (power >= loyalty || pwValue >= 6.0) return bestPw
        }

        return opponentId
    }

    /**
     * Find the opponent's cheapest creature that can kill our attacker, factoring in combat tricks.
     */
    private fun findCheapestEffectiveBlocker(
        state: GameState,
        projected: ProjectedState,
        attacker: EntityId,
        validBlockers: List<EntityId>,
        trickToughnessBonus: Int
    ): EntityId? {
        val toughness = projected.getToughness(attacker) ?: 0
        val keywords = projected.getKeywords(attacker)

        return validBlockers.filter { blocker ->
            val bPower = projected.getPower(blocker) ?: 0
            val bKeywords = projected.getKeywords(blocker)
            // Can this blocker kill us (accounting for possible combat trick pumping it)?
            val canKillUs = (bPower >= toughness) || Keyword.DEATHTOUCH.name in bKeywords
            // Even without enough power now, a trick might pump it enough
            val trickCouldKillUs = (bPower + trickToughnessBonus) >= toughness
            canKillUs || trickCouldKillUs
        }.minByOrNull { CombatMath.creatureValue(state, projected, it) }
    }

    /**
     * Remove attackers from the map to retain enough blockers for defense against crackback.
     */
    private fun retainDefenders(
        state: GameState,
        projected: ProjectedState,
        playerId: EntityId,
        opponentId: EntityId,
        opponentCreatures: List<EntityId>,
        attackerMap: MutableMap<EntityId, EntityId>,
        myLife: Int
    ) {
        // Estimate how much damage gets through if we block with remaining (non-attacking) creatures
        val myAllCreatures = projected.getBattlefieldControlledBy(playerId)
            .filter { projected.isCreature(it) }
        val nonAttackers = myAllCreatures.filter { it !in attackerMap }

        // How much crackback damage gets through with current defenders?
        val crackbackThrough = CombatMath.calculateDamageThroughOptimalBlocking(
            state, projected, opponentCreatures, nonAttackers
        )

        if (crackbackThrough < myLife) return // we're safe

        // Pull back attackers starting with the least valuable until we survive crackback
        // Keep evasive attackers (they're most likely to deal damage) and vigilance creatures
        val pullBackCandidates = attackerMap.keys
            .filter {
                Keyword.VIGILANCE.name !in projected.getKeywords(it) &&
                    !CombatMath.isEvasive(state, projected, it, CombatMath.getOpponentUntappedCreatures(state, projected, opponentId))
            }
            .sortedBy { CombatMath.creatureValue(state, projected, it) }

        for (candidate in pullBackCandidates) {
            attackerMap.remove(candidate)
            // Recalculate
            val updatedNonAttackers = myAllCreatures.filter { it !in attackerMap }
            val updatedCrackback = CombatMath.calculateDamageThroughOptimalBlocking(
                state, projected, opponentCreatures, updatedNonAttackers
            )
            if (updatedCrackback < myLife) break
        }
    }

    // ── Blocking strategies ─────────────────────────────────────────────

    /**
     * Survival mode: minimize total damage taken using a damage-reduction matrix.
     * Accounts for trample and lifelink.
     */
    private fun assignBlocksForSurvival(
        state: GameState,
        projected: ProjectedState,
        validBlockers: List<EntityId>,
        attackers: List<EntityId>,
        assignedBlockers: MutableSet<EntityId>,
        blockedAttackers: MutableSet<EntityId>,
        blockerMap: MutableMap<EntityId, List<EntityId>>
    ) {
        val available = validBlockers.filter { it !in assignedBlockers }.toMutableList()
        val unblocked = attackers.filter { it !in blockedAttackers }.toMutableList()

        // Build a damage-reduction matrix: for each (attacker, blocker) pair,
        // compute how much effective damage is prevented by this assignment.
        // Greedily pick the pair with highest reduction.
        while (available.isNotEmpty() && unblocked.isNotEmpty()) {
            var bestAttacker: EntityId? = null
            var bestBlocker: EntityId? = null
            var bestReduction = 0

            for (attacker in unblocked) {
                val aPower = projected.getPower(attacker) ?: 0
                if (aPower <= 0) continue

                for (blocker in available) {
                    val reduction = CombatMath.effectiveDamagePrevented(projected, attacker, blocker)
                    if (reduction > bestReduction) {
                        bestReduction = reduction
                        bestAttacker = attacker
                        bestBlocker = blocker
                    }
                }
            }

            if (bestAttacker == null || bestBlocker == null || bestReduction <= 0) break

            blockerMap[bestBlocker] = listOf(bestAttacker)
            assignedBlockers.add(bestBlocker)
            blockedAttackers.add(bestAttacker)
            available.remove(bestBlocker)
            unblocked.remove(bestAttacker)
        }
    }

    /**
     * Profit mode: look for favorable trades, accounting for first strike and lifelink.
     */
    private fun assignBlocksForProfit(
        state: GameState,
        projected: ProjectedState,
        validBlockers: List<EntityId>,
        attackers: List<EntityId>,
        assignedBlockers: MutableSet<EntityId>,
        blockedAttackers: MutableSet<EntityId>,
        blockerMap: MutableMap<EntityId, List<EntityId>>
    ) {
        // Sort attackers by effective damage descending — block lifelink creatures first
        val unblocked = attackers
            .filter { it !in blockedAttackers }
            .sortedByDescending { CombatMath.effectiveDamage(projected, it) }

        for (attacker in unblocked) {
            val aPower = projected.getPower(attacker) ?: 0
            val aToughness = projected.getToughness(attacker) ?: 0
            val aKeywords = projected.getKeywords(attacker)
            val aHasDeathtouch = Keyword.DEATHTOUCH.name in aKeywords
            val aHasFirstStrike = Keyword.FIRST_STRIKE.name in aKeywords || Keyword.DOUBLE_STRIKE.name in aKeywords
            val attackerValue = CombatMath.creatureValue(state, projected, attacker)

            // ── Try single blocker first ──
            val singleBlocker = validBlockers
                .filter { it !in assignedBlockers }
                .filter { blockerId ->
                    val bPower = projected.getPower(blockerId) ?: 0
                    val bToughness = projected.getToughness(blockerId) ?: 0
                    val bKeywords = projected.getKeywords(blockerId)
                    val bHasDeathtouch = Keyword.DEATHTOUCH.name in bKeywords
                    val blockerValue = CombatMath.creatureValue(state, projected, blockerId)

                    val weKillThem = bPower >= aToughness || bHasDeathtouch
                    val weSurvive = bToughness > aPower && !aHasDeathtouch

                    // First strike check: if attacker has first strike and blocker doesn't,
                    // blocker dies before dealing damage — can't kill the attacker
                    val blockerDealsDamage = CombatMath.blockerDealsDamage(state, projected, attacker, blockerId)
                    val effectivelyKillThem = weKillThem && blockerDealsDamage

                    (effectivelyKillThem && weSurvive) || (effectivelyKillThem && attackerValue > blockerValue)
                }
                .minByOrNull { CombatMath.creatureValue(state, projected, it) }

            if (singleBlocker != null) {
                blockerMap[singleBlocker] = listOf(attacker)
                assignedBlockers.add(singleBlocker)
                blockedAttackers.add(attacker)
                continue
            }

            // ── Try gang-block ──
            if (aHasDeathtouch) continue // double-blocking into deathtouch loses both creatures

            val available = validBlockers.filter { it !in assignedBlockers }
            if (available.size < 2) continue

            val gangBlock = findProfitableGangBlock(state, projected, attacker, aPower, aToughness, aHasFirstStrike, attackerValue, available)
            if (gangBlock != null) {
                for (blocker in gangBlock) {
                    blockerMap[blocker] = listOf(attacker)
                    assignedBlockers.add(blocker)
                }
                blockedAttackers.add(attacker)
            }
        }
    }

    /**
     * Find two creatures that can gang-block an attacker profitably.
     *
     * Tier 1: Both blockers survive, kill attacker — best case.
     * Tier 2: One blocker dies, kill attacker — acceptable if attacker is much more valuable.
     * First strike: if attacker has first strike, it may kill one blocker before both deal damage.
     */
    private fun findProfitableGangBlock(
        state: GameState,
        projected: ProjectedState,
        attacker: EntityId,
        aPower: Int,
        aToughness: Int,
        aHasFirstStrike: Boolean,
        attackerValue: Double,
        available: List<EntityId>
    ): List<EntityId>? {
        val sorted = available.sortedBy { CombatMath.creatureValue(state, projected, it) }

        var bestTier2: List<EntityId>? = null
        var bestTier2Cost = Double.MAX_VALUE

        for (i in sorted.indices) {
            for (j in i + 1 until sorted.size) {
                val b1 = sorted[i]
                val b2 = sorted[j]
                val p1 = projected.getPower(b1) ?: 0
                val p2 = projected.getPower(b2) ?: 0
                val t1 = projected.getToughness(b1) ?: 0
                val t2 = projected.getToughness(b2) ?: 0

                val combinedPower = p1 + p2
                if (combinedPower < aToughness) continue

                // With first strike: attacker kills one blocker first.
                // The surviving blocker must have enough power alone to kill the attacker.
                if (aHasFirstStrike) {
                    // Attacker kills the weaker one (lower toughness) first
                    val b1SurvivesFS = !CombatMath.wouldKillInCombat(state, projected, attacker, b1)
                    val b2SurvivesFS = !CombatMath.wouldKillInCombat(state, projected, attacker, b2)
                    // Both need to survive first strike to deal damage together
                    if (!b1SurvivesFS || !b2SurvivesFS) continue
                }

                val bothSurvive = t1 > aPower && t2 > aPower
                val totalBlockerValue = CombatMath.creatureValue(state, projected, b1) + CombatMath.creatureValue(state, projected, b2)

                if (bothSurvive) {
                    // Tier 1: both survive, kill attacker
                    if (attackerValue >= totalBlockerValue * 0.4) {
                        return listOf(b1, b2)
                    }
                } else {
                    // Tier 2: one dies — acceptable if attacker is much more valuable
                    val dyingBlocker = if (t1 <= aPower) b1 else b2
                    val dyingValue = CombatMath.creatureValue(state, projected, dyingBlocker)
                    if (attackerValue > dyingValue * 1.5 && dyingValue < bestTier2Cost) {
                        bestTier2 = listOf(b1, b2)
                        bestTier2Cost = dyingValue
                    }
                }
            }
        }

        return bestTier2
    }

    // ── Helpers ──────────────────────────────────────────────────────────

    private fun getAttackingCreatures(state: GameState): List<EntityId> {
        return state.getBattlefield().filter { entityId ->
            state.getEntity(entityId)?.has<com.wingedsheep.engine.state.components.combat.AttackingComponent>() == true
        }
    }
}
