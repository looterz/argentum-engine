package com.wingedsheep.sdk.core

import kotlinx.serialization.Serializable

/**
 * Types of counters that can be placed on permanents.
 */
@Serializable
enum class CounterType {
    PLUS_ONE_PLUS_ONE,
    MINUS_ONE_MINUS_ONE,
    LOYALTY,
    CHARGE,
    GEM,
    POISON,
    SILVER,
    GOLD,
    PLAGUE,
    TRAP,
    DEPLETION,
    EGG,
    LORE,
    AIM,
    STUN,
    FINALITY,
    SUPPLY
}
