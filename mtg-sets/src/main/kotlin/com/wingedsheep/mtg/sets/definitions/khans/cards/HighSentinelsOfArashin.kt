package com.wingedsheep.mtg.sets.definitions.khans.cards

import com.wingedsheep.sdk.core.Keyword
import com.wingedsheep.sdk.dsl.Costs
import com.wingedsheep.sdk.dsl.Targets
import com.wingedsheep.sdk.dsl.card
import com.wingedsheep.sdk.model.Rarity
import com.wingedsheep.sdk.scripting.GameObjectFilter
import com.wingedsheep.sdk.scripting.GrantDynamicStatsEffect
import com.wingedsheep.sdk.scripting.StaticTarget
import com.wingedsheep.sdk.scripting.effects.AddCountersEffect
import com.wingedsheep.sdk.scripting.references.Player
import com.wingedsheep.sdk.scripting.values.DynamicAmount

/**
 * High Sentinels of Arashin
 * {3}{W}
 * Creature — Bird Soldier
 * 3/4
 * Flying
 * High Sentinels of Arashin gets +1/+1 for each other creature you control with a +1/+1 counter on it.
 * {3}{W}: Put a +1/+1 counter on target creature.
 */
val HighSentinelsOfArashin = card("High Sentinels of Arashin") {
    manaCost = "{3}{W}"
    typeLine = "Creature — Bird Soldier"
    power = 3
    toughness = 4
    oracleText = "Flying\nHigh Sentinels of Arashin gets +1/+1 for each other creature you control with a +1/+1 counter on it.\n{3}{W}: Put a +1/+1 counter on target creature."

    keywords(Keyword.FLYING)

    // Gets +1/+1 for each other creature you control with a +1/+1 counter on it
    staticAbility {
        ability = GrantDynamicStatsEffect(
            target = StaticTarget.SourceCreature,
            powerBonus = DynamicAmount.AggregateBattlefield(
                player = Player.You,
                filter = GameObjectFilter.Creature.withCounter("+1/+1"),
                excludeSelf = true
            ),
            toughnessBonus = DynamicAmount.AggregateBattlefield(
                player = Player.You,
                filter = GameObjectFilter.Creature.withCounter("+1/+1"),
                excludeSelf = true
            )
        )
    }

    // {3}{W}: Put a +1/+1 counter on target creature
    activatedAbility {
        cost = Costs.Mana("{3}{W}")
        val t = target("target creature", Targets.Creature)
        effect = AddCountersEffect(
            counterType = "+1/+1",
            count = 1,
            target = t
        )
    }

    metadata {
        rarity = Rarity.RARE
        collectorNumber = "189"
        artist = "James Ryman"
        flavorText = "\"My lance was forged in the flames of the First Hatching.\""
        imageUri = "https://cards.scryfall.io/normal/front/a/5/a5a06587-f2a8-4b0c-8a17-da384c522e8e.jpg?1562791296"
    }
}
