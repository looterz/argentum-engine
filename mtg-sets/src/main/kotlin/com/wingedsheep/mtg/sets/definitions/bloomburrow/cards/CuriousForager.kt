package com.wingedsheep.mtg.sets.definitions.bloomburrow.cards

import com.wingedsheep.sdk.core.Zone
import com.wingedsheep.sdk.dsl.Effects
import com.wingedsheep.sdk.dsl.Triggers
import com.wingedsheep.sdk.dsl.card
import com.wingedsheep.sdk.model.Rarity
import com.wingedsheep.sdk.scripting.GameObjectFilter
import com.wingedsheep.sdk.scripting.effects.CardDestination
import com.wingedsheep.sdk.scripting.effects.CardSource
import com.wingedsheep.sdk.scripting.effects.CompositeEffect
import com.wingedsheep.sdk.scripting.effects.GatherCardsEffect
import com.wingedsheep.sdk.scripting.effects.ModalEffect
import com.wingedsheep.sdk.scripting.effects.Mode
import com.wingedsheep.sdk.scripting.effects.MoveCollectionEffect
import com.wingedsheep.sdk.scripting.effects.ReflexiveTriggerEffect
import com.wingedsheep.sdk.scripting.effects.SacrificeEffect
import com.wingedsheep.sdk.scripting.effects.SelectFromCollectionEffect
import com.wingedsheep.sdk.scripting.effects.SelectionMode
import com.wingedsheep.sdk.scripting.filters.unified.TargetFilter
import com.wingedsheep.sdk.scripting.references.Player
import com.wingedsheep.sdk.scripting.targets.EffectTarget
import com.wingedsheep.sdk.scripting.targets.TargetObject
import com.wingedsheep.sdk.scripting.values.DynamicAmount

/**
 * Curious Forager
 * {2}{G}
 * Creature — Squirrel Druid
 * 3/2
 *
 * When this creature enters, you may forage. When you do, return target permanent
 * card from your graveyard to your hand.
 * (To forage, exile three cards from your graveyard or sacrifice a Food.)
 *
 * Modeled as a reflexive trigger: the action is forage (modal: exile 3 or sacrifice Food),
 * optional, and the reflexive effect targets a permanent card in your graveyard to return to hand.
 */
val CuriousForager = card("Curious Forager") {
    manaCost = "{2}{G}"
    typeLine = "Creature — Squirrel Druid"
    power = 3
    toughness = 2
    oracleText = "When this creature enters, you may forage. When you do, return target permanent card from your graveyard to your hand. (To forage, exile three cards from your graveyard or sacrifice a Food.)"

    triggeredAbility {
        trigger = Triggers.EntersBattlefield
        val permanentInGY = target(
            "permanent card in your graveyard",
            TargetObject(filter = TargetFilter(GameObjectFilter.Permanent.ownedByYou(), zone = Zone.GRAVEYARD))
        )
        effect = ReflexiveTriggerEffect(
            action = ModalEffect.chooseOne(
                // Mode 1: Exile 3 cards from your graveyard
                Mode.noTarget(
                    CompositeEffect(
                        listOf(
                            GatherCardsEffect(
                                source = CardSource.FromZone(Zone.GRAVEYARD, Player.You),
                                storeAs = "graveCards"
                            ),
                            SelectFromCollectionEffect(
                                from = "graveCards",
                                selection = SelectionMode.ChooseExactly(DynamicAmount.Fixed(3)),
                                storeSelected = "exileCards",
                                prompt = "Choose 3 cards from your graveyard to exile (forage)"
                            ),
                            MoveCollectionEffect(
                                from = "exileCards",
                                destination = CardDestination.ToZone(Zone.EXILE)
                            )
                        )
                    ),
                    "Exile three cards from your graveyard"
                ),
                // Mode 2: Sacrifice a Food
                Mode.noTarget(
                    SacrificeEffect(
                        filter = GameObjectFilter.Any.withSubtype("Food"),
                        count = 1
                    ),
                    "Sacrifice a Food"
                )
            ),
            optional = true,
            reflexiveEffect = Effects.ReturnToHand(permanentInGY)
        )
    }

    metadata {
        rarity = Rarity.UNCOMMON
        collectorNumber = "169"
        artist = "Mariah Tekulve"
        flavorText = "The bones of Calamity Beasts are sometimes buried in caches where their power can grow quietly."
        imageUri = "https://cards.scryfall.io/normal/front/6/4/64653b4a-e139-45f9-a915-ab49afb6b795.jpg?1721426795"
    }
}
