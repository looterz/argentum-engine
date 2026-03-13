package com.wingedsheep.mtg.sets.definitions.dominaria.cards

import com.wingedsheep.sdk.core.Color
import com.wingedsheep.sdk.core.Keyword
import com.wingedsheep.sdk.core.ManaCost
import com.wingedsheep.sdk.dsl.Triggers
import com.wingedsheep.sdk.dsl.card
import com.wingedsheep.sdk.model.Rarity
import com.wingedsheep.sdk.scripting.KeywordAbility
import com.wingedsheep.sdk.scripting.conditions.WasKicked
import com.wingedsheep.sdk.scripting.effects.ConditionalEffect
import com.wingedsheep.sdk.scripting.effects.CreateTokenEffect

/**
 * Josu Vess, Lich Knight
 * {2}{B}{B}
 * Legendary Creature — Zombie Knight
 * 4/5
 * Kicker {5}{B}
 * Menace
 * When Josu Vess enters, if it was kicked, create eight 2/2 black
 * Zombie Knight creature tokens with menace.
 */
val JosuVessLichKnight = card("Josu Vess, Lich Knight") {
    manaCost = "{2}{B}{B}"
    typeLine = "Legendary Creature — Zombie Knight"
    power = 4
    toughness = 5
    oracleText = "Kicker {5}{B}\nMenace\nWhen Josu Vess, Lich Knight enters, if it was kicked, create eight 2/2 black Zombie Knight creature tokens with menace."

    keywordAbility(KeywordAbility.Kicker(ManaCost.parse("{5}{B}")))

    keywords(Keyword.MENACE)

    triggeredAbility {
        trigger = Triggers.EntersBattlefield
        effect = ConditionalEffect(
            condition = WasKicked,
            effect = CreateTokenEffect(
                count = 8,
                power = 2,
                toughness = 2,
                colors = setOf(Color.BLACK),
                creatureTypes = setOf("Zombie", "Knight"),
                keywords = setOf(Keyword.MENACE)
            )
        )
    }

    metadata {
        rarity = Rarity.RARE
        collectorNumber = "95"
        artist = "Tyler Jacobson"
        imageUri = "https://cards.scryfall.io/normal/front/6/e/6ed6d088-db82-4648-a109-0e3fa1421847.jpg?1562737477"
    }
}
