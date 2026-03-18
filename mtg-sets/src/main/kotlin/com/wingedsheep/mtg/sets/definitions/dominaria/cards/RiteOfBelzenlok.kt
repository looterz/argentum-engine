package com.wingedsheep.mtg.sets.definitions.dominaria.cards

import com.wingedsheep.sdk.core.Color
import com.wingedsheep.sdk.core.Keyword
import com.wingedsheep.sdk.dsl.Effects
import com.wingedsheep.sdk.dsl.Triggers
import com.wingedsheep.sdk.dsl.card
import com.wingedsheep.sdk.model.Rarity
import com.wingedsheep.sdk.scripting.GameObjectFilter
import com.wingedsheep.sdk.scripting.TriggeredAbility
import com.wingedsheep.sdk.scripting.costs.PayCost
import com.wingedsheep.sdk.scripting.effects.CreateTokenEffect
import com.wingedsheep.sdk.scripting.effects.PayOrSufferEffect
import com.wingedsheep.sdk.scripting.targets.EffectTarget

/**
 * Rite of Belzenlok
 * {2}{B}{B}
 * Enchantment — Saga
 *
 * (As this Saga enters and after your draw step, add a lore counter. Sacrifice after III.)
 * I, II — Create two 0/1 black Cleric creature tokens.
 * III — Create a 6/6 black Demon creature token with flying, trample, and "At the beginning
 *        of your upkeep, sacrifice another creature. If you can't, this creature deals 6
 *        damage to you."
 */
val RiteOfBelzenlok = card("Rite of Belzenlok") {
    manaCost = "{2}{B}{B}"
    typeLine = "Enchantment — Saga"
    oracleText = "(As this Saga enters and after your draw step, add a lore counter. Sacrifice after III.)\n" +
        "I, II — Create two 0/1 black Cleric creature tokens.\n" +
        "III — Create a 6/6 black Demon creature token with flying, trample, and \"At the beginning of your upkeep, sacrifice another creature. If you can't, this creature deals 6 damage to you.\""

    sagaChapter(1) {
        effect = Effects.CreateToken(
            count = 2,
            power = 0,
            toughness = 1,
            colors = setOf(Color.BLACK),
            creatureTypes = setOf("Cleric")
        )
    }

    sagaChapter(2) {
        effect = Effects.CreateToken(
            count = 2,
            power = 0,
            toughness = 1,
            colors = setOf(Color.BLACK),
            creatureTypes = setOf("Cleric")
        )
    }

    sagaChapter(3) {
        effect = CreateTokenEffect(
            power = 6,
            toughness = 6,
            colors = setOf(Color.BLACK),
            creatureTypes = setOf("Demon"),
            keywords = setOf(Keyword.FLYING, Keyword.TRAMPLE),
            triggeredAbilities = listOf(
                TriggeredAbility.create(
                    trigger = Triggers.YourUpkeep.event,
                    binding = Triggers.YourUpkeep.binding,
                    effect = PayOrSufferEffect(
                        cost = PayCost.Sacrifice(GameObjectFilter.Creature),
                        suffer = Effects.DealDamage(6, EffectTarget.Controller)
                    )
                )
            )
        )
    }

    metadata {
        rarity = Rarity.RARE
        collectorNumber = "102"
        artist = "Seb McKinnon"
        imageUri = "https://cards.scryfall.io/normal/front/1/e/1ed7cca3-79be-44ca-bf10-2ae1c0835ed1.jpg?1577142986"
    }
}
