package com.wingedsheep.gameserver.scenarios

import com.wingedsheep.engine.core.CastSpell
import com.wingedsheep.engine.core.PaymentStrategy
import com.wingedsheep.engine.core.TurnFaceUp
import com.wingedsheep.engine.state.components.battlefield.CountersComponent
import com.wingedsheep.engine.state.components.identity.CardComponent
import com.wingedsheep.engine.state.components.identity.FaceDownComponent
import com.wingedsheep.gameserver.ScenarioTestBase
import com.wingedsheep.sdk.core.CounterType
import com.wingedsheep.sdk.core.Phase
import com.wingedsheep.sdk.core.Step
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe

/**
 * Scenario tests for Hooded Hydra.
 *
 * Hooded Hydra {X}{G}{G}
 * Creature — Snake Hydra (0/0)
 * Hooded Hydra enters the battlefield with X +1/+1 counters on it.
 * When Hooded Hydra dies, create a 1/1 green Snake creature token for each +1/+1 counter on it.
 * Morph {3}{G}{G}
 * As Hooded Hydra is turned face up, put five +1/+1 counters on it.
 */
class HoodedHydraScenarioTest : ScenarioTestBase() {

    init {
        context("Hooded Hydra enters with X counters") {

            test("cast with X=3 enters with 3 +1/+1 counters") {
                val game = scenario()
                    .withPlayers("Player1", "Player2")
                    .withCardInHand(1, "Hooded Hydra")
                    .withLandsOnBattlefield(1, "Forest", 5) // {3}{G}{G} = X=3
                    .withCardInLibrary(1, "Forest")
                    .withCardInLibrary(2, "Forest")
                    .withActivePlayer(1)
                    .inPhase(Phase.PRECOMBAT_MAIN, Step.PRECOMBAT_MAIN)
                    .build()

                // Cast Hooded Hydra with X=3
                val cardId = game.findCardsInHand(1, "Hooded Hydra").first()
                val result = game.execute(
                    CastSpell(
                        playerId = game.player1Id,
                        cardId = cardId,
                        xValue = 3,
                        paymentStrategy = PaymentStrategy.AutoPay
                    )
                )
                result.error shouldBe null

                // Resolve the spell
                game.resolveStack()

                // Hooded Hydra should be on the battlefield with 3 +1/+1 counters
                val hydraId = game.findPermanent("Hooded Hydra")
                hydraId shouldNotBe null

                val counters = game.state.getEntity(hydraId!!)?.get<CountersComponent>()
                counters shouldNotBe null
                counters!!.getCount(CounterType.PLUS_ONE_PLUS_ONE) shouldBe 3
            }
        }

        context("Hooded Hydra morph") {

            test("turning face up puts 5 +1/+1 counters on it") {
                val game = scenario()
                    .withPlayers("Player1", "Player2")
                    .withCardInHand(1, "Hooded Hydra")
                    .withLandsOnBattlefield(1, "Forest", 8) // {3} face-down + {3}{G}{G} to turn up
                    .withCardInLibrary(1, "Forest")
                    .withCardInLibrary(2, "Forest")
                    .withActivePlayer(1)
                    .inPhase(Phase.PRECOMBAT_MAIN, Step.PRECOMBAT_MAIN)
                    .build()

                // Cast Hooded Hydra face-down
                val cardId = game.findCardsInHand(1, "Hooded Hydra").first()
                val castResult = game.execute(
                    CastSpell(
                        playerId = game.player1Id,
                        cardId = cardId,
                        castFaceDown = true,
                        paymentStrategy = PaymentStrategy.AutoPay
                    )
                )
                castResult.error shouldBe null

                // Resolve the face-down spell
                game.resolveStack()

                // Find the face-down creature
                val faceDownId = game.state.getBattlefield().find { entityId ->
                    game.state.getEntity(entityId)?.has<FaceDownComponent>() == true
                }
                faceDownId shouldNotBe null

                // Turn face up by paying morph cost {3}{G}{G}
                val turnUpResult = game.execute(TurnFaceUp(game.player1Id, faceDownId!!))
                turnUpResult.error shouldBe null

                // Should now be face up with 5 +1/+1 counters
                val entity = game.state.getEntity(faceDownId)
                entity?.has<FaceDownComponent>() shouldBe false
                entity?.get<CardComponent>()?.name shouldBe "Hooded Hydra"

                val counters = entity?.get<CountersComponent>()
                counters shouldNotBe null
                counters!!.getCount(CounterType.PLUS_ONE_PLUS_ONE) shouldBe 5
            }
        }

        context("Hooded Hydra death trigger") {

            test("creates Snake tokens equal to +1/+1 counters when it dies") {
                val game = scenario()
                    .withPlayers("Player1", "Player2")
                    .withCardOnBattlefield(1, "Hooded Hydra")
                    .withLandsOnBattlefield(1, "Forest", 3) // for counters
                    .withCardInHand(2, "Smite the Monstrous") // destroy creature with power 4+
                    .withLandsOnBattlefield(2, "Plains", 4)
                    .withCardInLibrary(1, "Forest")
                    .withCardInLibrary(2, "Forest")
                    .withActivePlayer(2)
                    .inPhase(Phase.PRECOMBAT_MAIN, Step.PRECOMBAT_MAIN)
                    .build()

                // Hooded Hydra enters as 0/0 from withCardOnBattlefield — it needs counters
                // Manually add counters to simulate it having entered properly
                val hydraId = game.findPermanent("Hooded Hydra")
                hydraId shouldNotBe null

                // Add 5 +1/+1 counters to simulate it being a 5/5
                game.state = game.state.updateEntity(hydraId!!) { c ->
                    val counters = c.get<CountersComponent>() ?: CountersComponent()
                    c.with(counters.withAdded(CounterType.PLUS_ONE_PLUS_ONE, 5))
                }

                // Destroy with Smite the Monstrous (targets power 4+)
                game.castSpell(2, "Smite the Monstrous", hydraId)
                game.resolveStack()

                // The death trigger should be on the stack — resolve it
                game.resolveStack()

                // Hooded Hydra should be in the graveyard
                game.isInGraveyard(1, "Hooded Hydra") shouldBe true

                // 5 Snake tokens should be on the battlefield
                val snakes = game.findAllPermanents("Snake Token")
                snakes.size shouldBe 5
            }
        }
    }
}
