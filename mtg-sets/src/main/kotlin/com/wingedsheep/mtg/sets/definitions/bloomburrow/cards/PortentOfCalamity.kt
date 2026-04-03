package com.wingedsheep.mtg.sets.definitions.bloomburrow.cards

import com.wingedsheep.sdk.core.Zone
import com.wingedsheep.sdk.dsl.card
import com.wingedsheep.sdk.model.Rarity
import com.wingedsheep.sdk.scripting.effects.CardDestination
import com.wingedsheep.sdk.scripting.effects.CardSource
import com.wingedsheep.sdk.scripting.effects.CompositeEffect
import com.wingedsheep.sdk.scripting.effects.GatherCardsEffect
import com.wingedsheep.sdk.scripting.effects.MoveCollectionEffect
import com.wingedsheep.sdk.scripting.effects.SelectFromCollectionEffect
import com.wingedsheep.sdk.scripting.effects.SelectionMode
import com.wingedsheep.sdk.scripting.values.DynamicAmount

/**
 * Portent of Calamity
 * {X}{U}
 * Sorcery
 *
 * Reveal the top X cards of your library. For each card type, you may exile a card
 * of that type from among them. Put the rest into your graveyard. You may cast a spell
 * from among the exiled cards without paying its mana cost if you exiled four or more
 * cards this way. Then put the rest of the exiled cards into your hand.
 *
 * Implementation note: The "one per card type" constraint is presented as a prompt but
 * not enforced by the engine (player/AI selects freely up to 9 cards). The conditional
 * free-cast-if-4+ is simplified: all selected cards go to hand instead of exile with
 * free cast permission. A full implementation would need a new ConditionalOnCollectionSize
 * effect and mid-resolution casting support.
 */
val PortentOfCalamity = card("Portent of Calamity") {
    manaCost = "{X}{U}"
    typeLine = "Sorcery"
    oracleText = "Reveal the top X cards of your library. For each card type, you may exile a card of that type from among them. Put the rest into your graveyard. You may cast a spell from among the exiled cards without paying its mana cost if you exiled four or more cards this way. Then put the rest of the exiled cards into your hand."

    spell {
        effect = CompositeEffect(
            listOf(
                // Reveal top X cards
                GatherCardsEffect(
                    source = CardSource.TopOfLibrary(DynamicAmount.XValue),
                    storeAs = "revealed"
                ),
                // For each card type, you may exile a card of that type
                // Simplified: player chooses any cards to keep (up to 9 = max card types)
                SelectFromCollectionEffect(
                    from = "revealed",
                    selection = SelectionMode.ChooseUpTo(DynamicAmount.Fixed(9)),
                    storeSelected = "kept",
                    storeRemainder = "rest",
                    prompt = "Choose cards to put into your hand (one per card type). The rest go to your graveyard.",
                    showAllCards = true,
                    selectedLabel = "Hand",
                    remainderLabel = "Graveyard"
                ),
                // Put unchosen cards into graveyard
                MoveCollectionEffect(
                    from = "rest",
                    destination = CardDestination.ToZone(Zone.GRAVEYARD)
                ),
                // Put chosen cards into hand
                // TODO: Full implementation should exile chosen cards, then if 4+ exiled,
                // allow casting one for free before putting the rest in hand
                MoveCollectionEffect(
                    from = "kept",
                    destination = CardDestination.ToZone(Zone.HAND)
                )
            )
        )
    }

    metadata {
        rarity = Rarity.RARE
        collectorNumber = "66"
        artist = "Sam Guay"
        imageUri = "https://cards.scryfall.io/normal/front/8/5/8599e2dd-9164-4da3-814f-adccef3b9497.jpg?1721426215"

        ruling("2024-07-26", "The card types that can appear on cards you reveal are artifact, battle, creature, enchantment, instant, kindred, land, planeswalker, and sorcery.")
        ruling("2024-07-26", "You choose which spell to cast (if any) as Portent of Calamity resolves. If you choose to cast a spell this way, you do so as part of the resolution of Portent of Calamity.")
        ruling("2024-07-26", "If you cast a spell \"without paying its mana cost,\" you can't choose to cast it for any alternative costs. You can, however, pay additional costs.")
    }
}
