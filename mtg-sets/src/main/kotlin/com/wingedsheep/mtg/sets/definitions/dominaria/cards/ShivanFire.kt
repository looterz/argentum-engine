package com.wingedsheep.mtg.sets.definitions.dominaria.cards

import com.wingedsheep.sdk.dsl.Targets
import com.wingedsheep.sdk.dsl.card
import com.wingedsheep.sdk.model.Rarity
import com.wingedsheep.sdk.core.ManaCost
import com.wingedsheep.sdk.scripting.KeywordAbility
import com.wingedsheep.sdk.scripting.conditions.WasKicked
import com.wingedsheep.sdk.scripting.effects.ConditionalEffect
import com.wingedsheep.sdk.scripting.effects.DealDamageEffect

/**
 * Shivan Fire
 * {R}
 * Instant
 * Kicker {4}
 * Shivan Fire deals 2 damage to target creature or planeswalker.
 * If this spell was kicked, it deals 4 damage instead.
 */
val ShivanFire = card("Shivan Fire") {
    manaCost = "{R}"
    typeLine = "Instant"
    oracleText = "Kicker {4}\nShivan Fire deals 2 damage to target creature or planeswalker. If this spell was kicked, it deals 4 damage instead."

    keywordAbility(KeywordAbility.Kicker(ManaCost.parse("{4}")))

    spell {
        val t = target("target", Targets.CreatureOrPlaneswalker)
        effect = ConditionalEffect(
            condition = WasKicked,
            effect = DealDamageEffect(4, t),
            elseEffect = DealDamageEffect(2, t)
        )
    }

    metadata {
        rarity = Rarity.COMMON
        collectorNumber = "175"
        artist = "Svetlin Velinov"
        flavorText = "The Keldons didn't come to Dominaria for a vacation."
        imageUri = "https://cards.scryfall.io/normal/front/2/1/21b0d542-f54f-4b2c-a2c7-aeee85ed3542.jpg?1562732478"
    }
}
