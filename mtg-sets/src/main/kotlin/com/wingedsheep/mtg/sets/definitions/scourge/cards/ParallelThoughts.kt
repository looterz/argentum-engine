package com.wingedsheep.mtg.sets.definitions.scourge.cards

import com.wingedsheep.sdk.model.Rarity
import com.wingedsheep.sdk.dsl.EffectPatterns
import com.wingedsheep.sdk.dsl.Effects
import com.wingedsheep.sdk.dsl.Triggers
import com.wingedsheep.sdk.dsl.card
import com.wingedsheep.sdk.scripting.ReplaceDrawWithEffect

// Oracle errata: Original text used "remove from the game" (now "exile").
// Rulings:
// - If you have fewer than 7 cards in your library, exile them all.
// - You can choose to take the top card from the pile even if the pile is empty.
// - You can't look at the face-down cards.
val ParallelThoughts = card("Parallel Thoughts") {
    manaCost = "{3}{U}{U}"
    typeLine = "Enchantment"
    oracleText = "When this enchantment enters, search your library for seven cards, exile them " +
        "in a face-down pile, and shuffle that pile. Then shuffle your library.\n" +
        "If you would draw a card, you may instead put the top card of the pile you exiled into your hand."

    triggeredAbility {
        trigger = Triggers.EntersBattlefield
        effect = EffectPatterns.searchAndExileLinked(count = 7)
    }

    replacementEffect(
        ReplaceDrawWithEffect(
            replacementEffect = Effects.TakeFromLinkedExile(),
            optional = true
        )
    )

    metadata {
        rarity = Rarity.RARE
        collectorNumber = "44"
        artist = "Ben Thompson"
        imageUri = "https://cards.scryfall.io/normal/front/d/9/d913c541-a8fb-4383-bbab-988be3e0f5d5.jpg?1562535276"
    }
}
