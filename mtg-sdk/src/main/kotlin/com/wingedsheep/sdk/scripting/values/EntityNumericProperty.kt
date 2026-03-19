package com.wingedsheep.sdk.scripting.values

import com.wingedsheep.sdk.scripting.events.CounterTypeFilter
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * A numeric property that can be read from an entity.
 *
 * Richer than [CardNumericProperty] — handles parameterized properties (counter type)
 * and entity-specific properties (blocker count, attachment count) that only make sense
 * when reading from a specific entity rather than aggregating over a group.
 */
@Serializable
sealed interface EntityNumericProperty {
    val description: String

    @SerialName("Power")
    @Serializable
    data object Power : EntityNumericProperty {
        override val description: String = "power"
    }

    @SerialName("Toughness")
    @Serializable
    data object Toughness : EntityNumericProperty {
        override val description: String = "toughness"
    }

    @SerialName("ManaValue")
    @Serializable
    data object ManaValue : EntityNumericProperty {
        override val description: String = "mana value"
    }

    @SerialName("CounterCount")
    @Serializable
    data class CounterCount(val counterType: CounterTypeFilter) : EntityNumericProperty {
        override val description: String = "the number of ${counterType.description} counters"
    }

    @SerialName("AttachmentCount")
    @Serializable
    data object AttachmentCount : EntityNumericProperty {
        override val description: String = "the number of Auras and Equipment attached"
    }

    @SerialName("BlockerCount")
    @Serializable
    data object BlockerCount : EntityNumericProperty {
        override val description: String = "the number of creatures blocking"
    }
}
