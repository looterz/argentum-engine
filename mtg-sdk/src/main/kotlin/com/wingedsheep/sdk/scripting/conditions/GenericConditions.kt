package com.wingedsheep.sdk.scripting.conditions

import com.wingedsheep.sdk.core.Zone
import com.wingedsheep.sdk.scripting.GameObjectFilter
import com.wingedsheep.sdk.scripting.references.Player
import com.wingedsheep.sdk.scripting.text.TextReplacer
import com.wingedsheep.sdk.scripting.values.DynamicAmount
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

// =============================================================================
// Generic Condition Primitives
// =============================================================================

/**
 * Comparison operators for [Compare] conditions.
 */
@Serializable
enum class ComparisonOperator {
    LT, LTE, EQ, NEQ, GT, GTE;

    val symbol: String
        get() = when (this) {
            LT -> "<"
            LTE -> "<="
            EQ -> "=="
            NEQ -> "!="
            GT -> ">"
            GTE -> ">="
        }
}

/**
 * Generic numeric comparison condition.
 * Evaluates two [DynamicAmount] sides and compares them with [operator].
 *
 * Examples:
 * ```kotlin
 * // Life at most 5
 * Compare(LifeTotal(Player.You), ComparisonOperator.LTE, Fixed(5))
 *
 * // More life than opponent
 * Compare(LifeTotal(Player.You), ComparisonOperator.GT, LifeTotal(Player.Opponent))
 * ```
 */
@SerialName("Compare")
@Serializable
data class Compare(
    val left: DynamicAmount,
    val operator: ComparisonOperator,
    val right: DynamicAmount
) : Condition {
    override val description: String = "${left.description} ${operator.symbol} ${right.description}"
    override fun applyTextReplacement(replacer: TextReplacer): Condition {
        val newLeft = left.applyTextReplacement(replacer)
        val newRight = right.applyTextReplacement(replacer)
        return if (newLeft !== left || newRight !== right) copy(left = newLeft, right = newRight) else this
    }
}

/**
 * Generic zone-presence condition.
 * Checks whether any matching object exists in a player's zone.
 *
 * Examples:
 * ```kotlin
 * // You control a creature
 * Exists(Player.You, Zone.BATTLEFIELD, GameObjectFilter.Creature)
 *
 * // Empty hand (negate = true means "no objects exist")
 * Exists(Player.You, Zone.HAND, negate = true)
 * ```
 */
@SerialName("Exists")
@Serializable
data class Exists(
    val player: Player,
    val zone: Zone,
    val filter: GameObjectFilter = GameObjectFilter.Any,
    val negate: Boolean = false,
    /** When true, exclude the source entity from the search (for "another" wording). */
    val excludeSelf: Boolean = false
) : Condition {
    override val description: String = buildString {
        if (negate) {
            append("if there are no ")
            if (excludeSelf) append("other ")
            append(filter.description.ifEmpty { "cards" })
            append(" in ")
        } else {
            append("if ")
            if (excludeSelf) append("another ") else append("a ")
            append(filter.description.ifEmpty { "card" })
            append(" is in ")
        }
        append(player.possessive)
        append(" ")
        append(zone.displayName.removePrefix("a ").removePrefix("the "))
    }
    override fun applyTextReplacement(replacer: TextReplacer): Condition {
        val newFilter = filter.applyTextReplacement(replacer)
        return if (newFilter !== filter) copy(filter = newFilter) else this
    }
}
