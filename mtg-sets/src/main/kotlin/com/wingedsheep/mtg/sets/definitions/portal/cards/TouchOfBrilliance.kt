package com.wingedsheep.mtg.sets.definitions.portal.cards

import com.wingedsheep.sdk.dsl.Effects
import com.wingedsheep.sdk.dsl.card
import com.wingedsheep.sdk.model.Rarity

/**
 * Touch of Brilliance
 * {3}{U}
 * Sorcery
 * Draw two cards.
 */
val TouchOfBrilliance = card("Touch of Brilliance") {
    manaCost = "{3}{U}"
    typeLine = "Sorcery"

    spell {
        effect = Effects.DrawCards(2)
    }

    metadata {
        rarity = Rarity.COMMON
        collectorNumber = "76"
        artist = "Douglas Shuler"
        imageUri = "https://cards.scryfall.io/normal/front/1/9/196474ce-e28e-48f0-b407-dc5535adf1b6.jpg"
    }
}
