package com.wingedsheep.mtg.sets.definitions.dominaria.cards

import com.wingedsheep.sdk.dsl.card
import com.wingedsheep.sdk.model.Rarity

/**
 * Corrosive Ooze
 * {1}{G}
 * Creature — Ooze
 * 2/2
 * Whenever this creature blocks or becomes blocked by an equipped creature,
 * destroy all Equipment attached to that creature at end of combat.
 *
 * Note: The triggered ability is not yet implemented. It requires new infrastructure:
 * - Combat partner detection (identifying which creature this blocked / was blocked by)
 * - Equipment status checking on combat partners
 * - Delayed end-of-combat equipment destruction effect
 * The creature body (2/2 for {1}{G}) is correct.
 */
val CorrosiveOoze = card("Corrosive Ooze") {
    manaCost = "{1}{G}"
    typeLine = "Creature — Ooze"
    power = 2
    toughness = 2
    oracleText = "Whenever Corrosive Ooze blocks or becomes blocked by an equipped creature, destroy all Equipment attached to that creature at end of combat."

    // TODO: Triggered ability requires combat partner + equipment destruction infrastructure

    metadata {
        rarity = Rarity.COMMON
        collectorNumber = "158"
        artist = "Daniel Ljunggren"
        flavorText = "Nothing tastes finer to an ooze than a priceless family heirloom."
        imageUri = "https://cards.scryfall.io/normal/front/d/1/d13deb9c-5985-4355-8716-ee1c7b54b8e2.jpg?1562743360"
    }
}
