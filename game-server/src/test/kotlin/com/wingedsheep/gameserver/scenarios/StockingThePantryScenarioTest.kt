package com.wingedsheep.gameserver.scenarios

import com.wingedsheep.gameserver.ScenarioTestBase
import com.wingedsheep.sdk.core.CounterType
import com.wingedsheep.sdk.core.Phase
import com.wingedsheep.sdk.core.Step
import io.kotest.assertions.withClue
import io.kotest.matchers.shouldBe

class StockingThePantryScenarioTest : ScenarioTestBase() {

    init {
        context("Stocking the Pantry - counter trigger from +1/+1 counter") {
            test("gains a supply counter when a creature ETBs with a +1/+1 counter") {
                // Cindering Cutthroat ETBs with +1/+1 counter if opponent lost life this turn.
                // We'll deal damage first by having player 1 attack, then cast Cindering Cutthroat.
                // Simpler approach: use Frilled Sparkshooter which has "valiant" — when it becomes
                // the target of a spell, put +1/+1 counter on it. We can target it with High Stride.
                // Actually, the simplest setup: SeedglaiveMentor ETBs with +1/+1 counter if
                // you control a creature with power >= 4.
                // Let's use Tempest Angler instead — when it enters, put +1/+1 counter on it
                // if you control 3+ other creatures. Actually that's conditional too.

                // Simplest: Valley Mightcaller gets +1/+1 counter when you cast a creature spell.
                val game = scenario()
                    .withPlayers("Player1", "Player2")
                    .withCardOnBattlefield(1, "Stocking the Pantry")
                    .withCardOnBattlefield(1, "Valley Mightcaller") // Gets +1/+1 when Frog enters
                    .withCardInHand(1, "Three Tree Scribe") // Frog Druid, costs {1}{G}
                    .withLandsOnBattlefield(1, "Forest", 2)
                    .withActivePlayer(1)
                    .inPhase(Phase.PRECOMBAT_MAIN, Step.PRECOMBAT_MAIN)
                    .build()

                // Cast Three Tree Scribe (a Frog) — Valley Mightcaller gets +1/+1 counter
                // which should trigger Stocking the Pantry to get a supply counter
                game.castSpell(1, "Three Tree Scribe")
                game.resolveStack()

                val pantryId = game.findPermanent("Stocking the Pantry")!!
                val clientState = game.getClientState(1)
                val pantryInfo = clientState.cards[pantryId]!!

                withClue("Stocking the Pantry should have a supply counter") {
                    pantryInfo.counters.getOrDefault(CounterType.SUPPLY, 0) shouldBe 1
                }
            }
        }
    }
}
