package com.wingedsheep.mtg.sets.definitions.khans.cards

import com.wingedsheep.sdk.dsl.Costs
import com.wingedsheep.sdk.dsl.Targets
import com.wingedsheep.sdk.dsl.card
import com.wingedsheep.sdk.model.Rarity
import com.wingedsheep.sdk.scripting.ModifyStats
import com.wingedsheep.sdk.scripting.effects.RegenerateEffect
import com.wingedsheep.sdk.scripting.targets.EffectTarget

/**
 * Molting Snakeskin
 * {B}
 * Enchantment — Aura
 * Enchant creature
 * Enchanted creature gets +2/+0 and has "{2}{B}: Regenerate this creature."
 */
val MoltingSnakeskin = card("Molting Snakeskin") {
    manaCost = "{B}"
    typeLine = "Enchantment — Aura"
    oracleText = "Enchant creature\nEnchanted creature gets +2/+0 and has \"{2}{B}: Regenerate this creature.\""

    auraTarget = Targets.Creature

    staticAbility {
        ability = ModifyStats(2, 0)
    }

    activatedAbility {
        cost = Costs.Mana("{2}{B}")
        effect = RegenerateEffect(EffectTarget.EnchantedCreature)
    }

    metadata {
        rarity = Rarity.COMMON
        collectorNumber = "80"
        artist = "YW Tang"
        flavorText = "Flesh wounds are meaningless to those who discard their flesh so readily."
        imageUri = "https://cards.scryfall.io/normal/front/b/7/b781cbcf-dbba-41c1-be02-2396b824a217.jpg?1562792454"
    }
}
