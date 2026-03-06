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
 * Abzan Falconer
 * {2}{W}
 * Creature — Human Soldier
 * 2/3
 * Outlast {W} ({W}, {T}: Put a +1/+1 counter on this creature. Outlast only as a sorcery.)
 * Each creature you control with a +1/+1 counter on it has flying.
 */
val AbzanFalconer = card("Abzan Falconer") {
    manaCost = "{2}{W}"
    typeLine = "Creature — Human Soldier"
    power = 2
    toughness = 3
    oracleText = "Outlast {W} ({W}, {T}: Put a +1/+1 counter on this creature. Outlast only as a sorcery.)\nEach creature you control with a +1/+1 counter on it has flying."

    // Outlast {W}
    activatedAbility {
        cost = Costs.Composite(Costs.Mana("{W}"), Costs.Tap)
        timing = TimingRule.SorcerySpeed
        effect = Effects.AddCounters("+1/+1", 1, EffectTarget.Self)
    }

    // Each creature you control with a +1/+1 counter on it has flying.
    staticAbility { ability = GrantKeywordByCounter(Keyword.FLYING, "+1/+1", controllerOnly = true) }

    metadata {
        rarity = Rarity.UNCOMMON
        collectorNumber = "2"
        artist = "Steven Belledin"
        flavorText = "\"Bows are useless in the shifting desert sands. A falcon can pursue prey wherever it goes.\""
        imageUri = "https://cards.scryfall.io/normal/front/b/5/b5428992-2108-4067-9e2c-4b0d0d3a663c.jpg?1562791708"
    }
}
