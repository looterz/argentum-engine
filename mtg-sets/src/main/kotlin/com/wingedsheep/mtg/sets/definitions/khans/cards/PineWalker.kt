package com.wingedsheep.mtg.sets.definitions.khans.cards

import com.wingedsheep.sdk.dsl.Effects
import com.wingedsheep.sdk.dsl.Triggers
import com.wingedsheep.sdk.dsl.card
import com.wingedsheep.sdk.model.Rarity
import com.wingedsheep.sdk.scripting.targets.EffectTarget

/**
 * Pine Walker
 * {3}{G}{G}
 * Creature — Elemental
 * 5/5
 * Morph {4}{G}
 * Whenever Pine Walker or another creature you control is turned face up, untap that creature.
 */
val PineWalker = card("Pine Walker") {
    manaCost = "{3}{G}{G}"
    typeLine = "Creature — Elemental"
    power = 5
    toughness = 5
    oracleText = "Morph {4}{G} (You may cast this card face down as a 2/2 creature for {3}. Turn it face up any time for its morph cost.)\nWhenever Pine Walker or another creature you control is turned face up, untap that creature."

    morph = "{4}{G}"

    triggeredAbility {
        trigger = Triggers.CreatureYouControlTurnedFaceUp
        effect = Effects.Untap(EffectTarget.TriggeringEntity)
    }

    metadata {
        rarity = Rarity.UNCOMMON
        collectorNumber = "149"
        artist = "Viktor Titov"
        flavorText = "Its roots reach deep enough to tap into the ancient magic that suffuses Tarkir."
        imageUri = "https://cards.scryfall.io/normal/front/a/a/aab1f439-39c4-47ed-a5ab-da448e3275db.jpg?1562791541"
    }
}
