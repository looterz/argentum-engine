package com.wingedsheep.mtg.sets.definitions.khans.cards

import com.wingedsheep.sdk.core.Keyword
import com.wingedsheep.sdk.dsl.Costs
import com.wingedsheep.sdk.dsl.Effects
import com.wingedsheep.sdk.dsl.card
import com.wingedsheep.sdk.model.Rarity
import com.wingedsheep.sdk.scripting.GrantKeywordByCounter
import com.wingedsheep.sdk.scripting.TimingRule
import com.wingedsheep.sdk.scripting.targets.EffectTarget

/**
 * Mer-Ek Nightblade
 * {3}{B}
 * Creature — Orc Assassin
 * 2/3
 * Outlast {B} ({B}, {T}: Put a +1/+1 counter on this creature. Outlast only as a sorcery.)
 * Each creature you control with a +1/+1 counter on it has deathtouch.
 */
val MerEkNightblade = card("Mer-Ek Nightblade") {
    manaCost = "{3}{B}"
    typeLine = "Creature — Orc Assassin"
    power = 2
    toughness = 3
    oracleText = "Outlast {B} ({B}, {T}: Put a +1/+1 counter on this creature. Outlast only as a sorcery.)\nEach creature you control with a +1/+1 counter on it has deathtouch."

    // Outlast {B}
    activatedAbility {
        cost = Costs.Composite(Costs.Mana("{B}"), Costs.Tap)
        timing = TimingRule.SorcerySpeed
        effect = Effects.AddCounters("+1/+1", 1, EffectTarget.Self)
    }

    // Each creature you control with a +1/+1 counter on it has deathtouch.
    staticAbility { ability = GrantKeywordByCounter(Keyword.DEATHTOUCH, "+1/+1", controllerOnly = true) }

    metadata {
        rarity = Rarity.UNCOMMON
        collectorNumber = "81"
        artist = "Lucas Graciano"
        flavorText = "The blades of the Mer-Ek are as poisonous as their intentions."
        imageUri = "https://cards.scr/cleyfall.io/normal/front/a/f/afa03d63-0b91-4f4a-b655-01bd47c8536b.jpg?1562789660"
    }
}
