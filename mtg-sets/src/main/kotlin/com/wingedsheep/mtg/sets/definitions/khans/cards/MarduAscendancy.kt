package com.wingedsheep.mtg.sets.definitions.khans.cards

import com.wingedsheep.sdk.core.Color
import com.wingedsheep.sdk.dsl.Costs
import com.wingedsheep.sdk.dsl.EffectPatterns
import com.wingedsheep.sdk.dsl.Triggers
import com.wingedsheep.sdk.dsl.card
import com.wingedsheep.sdk.model.Rarity
import com.wingedsheep.sdk.scripting.effects.CreateTokenEffect
import com.wingedsheep.sdk.scripting.filters.unified.GroupFilter

/**
 * Mardu Ascendancy
 * {R}{W}{B}
 * Enchantment
 * Whenever a nontoken creature you control attacks, create a 1/1 red Goblin creature token
 * that's tapped and attacking.
 * Sacrifice Mardu Ascendancy: Creatures you control get +0/+3 until end of turn.
 */
val MarduAscendancy = card("Mardu Ascendancy") {
    manaCost = "{R}{W}{B}"
    typeLine = "Enchantment"
    oracleText = "Whenever a nontoken creature you control attacks, create a 1/1 red Goblin creature token that's tapped and attacking.\nSacrifice Mardu Ascendancy: Creatures you control get +0/+3 until end of turn."

    spell {}

    triggeredAbility {
        trigger = Triggers.NontokenCreatureYouControlAttacks
        effect = CreateTokenEffect(
            power = 1,
            toughness = 1,
            colors = setOf(Color.RED),
            creatureTypes = setOf("Goblin"),
            tapped = true,
            attacking = true,
            imageUri = "https://cards.scryfall.io/normal/front/e/d/ed418a8b-f158-492d-a323-6265b3175292.jpg?1562640121"
        )
    }

    activatedAbility {
        cost = Costs.SacrificeSelf
        effect = EffectPatterns.modifyStatsForAll(
            power = 0,
            toughness = 3,
            filter = GroupFilter.AllCreaturesYouControl
        )
    }

    metadata {
        rarity = Rarity.RARE
        collectorNumber = "185"
        artist = "Jason Chan"
        imageUri = "https://cards.scryfall.io/normal/front/0/2/02bc2415-b1d1-467a-9578-3948dda166cf.jpg?1562781854"
    }
}
