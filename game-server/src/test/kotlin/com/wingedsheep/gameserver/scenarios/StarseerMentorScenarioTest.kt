package com.wingedsheep.gameserver.scenarios

import com.wingedsheep.engine.core.ChooseOptionDecision
import com.wingedsheep.engine.core.OptionChosenResponse
import com.wingedsheep.engine.core.SelectCardsDecision
import com.wingedsheep.engine.state.components.player.LifeGainedThisTurnComponent
import com.wingedsheep.gameserver.ScenarioTestBase
import com.wingedsheep.sdk.core.Phase
import com.wingedsheep.sdk.core.Step
import io.kotest.assertions.withClue
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.kotest.matchers.types.shouldBeInstanceOf

/**
 * Scenario tests for Starseer Mentor.
 *
 * Starseer Mentor {3}{W}{B}
 * Creature — Bat Warlock
 * 3/5
 *
 * Flying, vigilance
 * At the beginning of your end step, if you gained or lost life this turn,
 * target opponent loses 3 life unless they sacrifice a nonland permanent of
 * their choice or discard a card.
 */
class StarseerMentorScenarioTest : ScenarioTestBase() {

    private fun markLifeGained(game: TestGame) {
        game.state = game.state.updateEntity(game.player1Id) { it.with(LifeGainedThisTurnComponent) }
    }

    /** Advance from postcombat main to end step by passing priority until we get there. */
    private fun advanceToEndStep(game: TestGame) {
        var iterations = 0
        while (game.state.step != Step.END && game.state.pendingDecision == null && iterations++ < 10) {
            game.passPriority()
        }
    }

    init {
        context("Starseer Mentor — PayOrSuffer Choice") {

            test("opponent chooses to sacrifice a nonland permanent") {
                val game = scenario()
                    .withPlayers("Player", "Opponent")
                    .withCardOnBattlefield(1, "Starseer Mentor")
                    .withCardOnBattlefield(2, "Glory Seeker")
                    .withCardInLibrary(1, "Plains")
                    .withCardInLibrary(2, "Plains")
                    .withActivePlayer(1)
                    .inPhase(Phase.POSTCOMBAT_MAIN, Step.POSTCOMBAT_MAIN)
                    .build()

                markLifeGained(game)
                advanceToEndStep(game)

                // Trigger should have fired — resolve it from the stack
                withClue("Stack should have the trigger: step=${game.state.step}, stack=${game.state.stack.size}") {
                    game.state.stack.size shouldBe 1
                }
                game.resolveStack()

                // Opponent gets a ChooseOptionDecision
                val decision = game.getPendingDecision()
                withClue("Should have a pending decision after resolving Starseer Mentor trigger") {
                    decision shouldNotBe null
                }
                decision.shouldBeInstanceOf<ChooseOptionDecision>()
                decision.playerId shouldBe game.player2Id

                // Choose to sacrifice (first option)
                game.submitDecision(OptionChosenResponse(decision.id, 0))

                // Now opponent must select a nonland permanent to sacrifice
                val sacrificeDecision = game.getPendingDecision()
                sacrificeDecision.shouldBeInstanceOf<SelectCardsDecision>()

                val glorySeeker = game.findPermanent("Glory Seeker")!!
                game.selectCards(listOf(glorySeeker))

                // Glory Seeker should be gone
                game.findPermanent("Glory Seeker") shouldBe null
                // Opponent should still have 20 life
                game.getLifeTotal(2) shouldBe 20
            }

            test("opponent chooses to discard a card") {
                val game = scenario()
                    .withPlayers("Player", "Opponent")
                    .withCardOnBattlefield(1, "Starseer Mentor")
                    .withCardOnBattlefield(2, "Glory Seeker") // nonland permanent so all 3 options show
                    .withCardInHand(2, "Plains")
                    .withCardInLibrary(1, "Plains")
                    .withCardInLibrary(2, "Plains")
                    .withActivePlayer(1)
                    .inPhase(Phase.POSTCOMBAT_MAIN, Step.POSTCOMBAT_MAIN)
                    .build()

                markLifeGained(game)
                advanceToEndStep(game)
                game.resolveStack()

                val decision = game.getPendingDecision()
                decision.shouldBeInstanceOf<ChooseOptionDecision>()

                // Choose to discard (second option — index 1)
                game.submitDecision(OptionChosenResponse(decision.id, 1))

                // Opponent must select a card to discard
                val discardDecision = game.getPendingDecision()
                discardDecision.shouldBeInstanceOf<SelectCardsDecision>()

                val handCards = game.findCardsInHand(2, "Plains")
                game.selectCards(listOf(handCards.first()))

                // Opponent should still have 20 life
                game.getLifeTotal(2) shouldBe 20
                // Hand should be empty
                game.handSize(2) shouldBe 0
            }

            test("opponent chooses to lose 3 life") {
                val game = scenario()
                    .withPlayers("Player", "Opponent")
                    .withCardOnBattlefield(1, "Starseer Mentor")
                    .withCardOnBattlefield(2, "Glory Seeker")
                    .withCardInHand(2, "Plains")
                    .withCardInLibrary(1, "Plains")
                    .withCardInLibrary(2, "Plains")
                    .withActivePlayer(1)
                    .inPhase(Phase.POSTCOMBAT_MAIN, Step.POSTCOMBAT_MAIN)
                    .build()

                markLifeGained(game)
                advanceToEndStep(game)
                game.resolveStack()

                val decision = game.getPendingDecision()
                decision.shouldBeInstanceOf<ChooseOptionDecision>()

                // Choose to lose 3 life (third option — index 2)
                game.submitDecision(OptionChosenResponse(decision.id, 2))

                // Opponent should lose 3 life
                game.getLifeTotal(2) shouldBe 17
                // Still have their permanent and card
                game.findPermanent("Glory Seeker") shouldNotBe null
                game.handSize(2) shouldBe 1
            }

            test("trigger does not fire if no life was gained or lost") {
                val game = scenario()
                    .withPlayers("Player", "Opponent")
                    .withCardOnBattlefield(1, "Starseer Mentor")
                    .withCardInLibrary(1, "Plains")
                    .withCardInLibrary(2, "Plains")
                    .withActivePlayer(1)
                    .inPhase(Phase.POSTCOMBAT_MAIN, Step.POSTCOMBAT_MAIN)
                    .build()

                // Don't mark life gained — advance to end step
                advanceToEndStep(game)

                // No trigger should fire — stack should be empty
                game.state.stack.size shouldBe 0
                game.getPendingDecision() shouldBe null
            }
        }
    }
}
