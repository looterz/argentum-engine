package com.wingedsheep.mtg.sets.definitions.khans.cards

import com.wingedsheep.sdk.dsl.card
import com.wingedsheep.sdk.model.Rarity
import com.wingedsheep.sdk.scripting.effects.DividedDamageEffect
import com.wingedsheep.sdk.scripting.targets.AnyTarget

/**
 * Arc Lightning
 * {2}{R}
 * Sorcery
 * Arc Lightning deals 3 damage divided as you choose among one, two, or three targets.
 */
val ArcLightning = card("Arc Lightning") {
    manaCost = "{2}{R}"
    typeLine = "Sorcery"
    oracleText = "Arc Lightning deals 3 damage divided as you choose among one, two, or three targets."

    spell {
        target = AnyTarget(count = 3, minCount = 1)
        effect = DividedDamageEffect(
            totalDamage = 3,
            minTargets = 1,
            maxTargets = 3
        )
    }

    metadata {
        rarity = Rarity.UNCOMMON
        collectorNumber = "97"
        artist = "Seb McKinnon"
        flavorText = "Lightning burns its own path."
        imageUri = "https://cards.scryfall.io/normal/front/3/5/35c7c392-6782-40c8-bb24-6aad24f14660.jpg?1562784760"
    }
}
