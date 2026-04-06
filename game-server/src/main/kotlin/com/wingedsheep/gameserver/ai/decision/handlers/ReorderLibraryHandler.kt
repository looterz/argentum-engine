package com.wingedsheep.gameserver.ai.decision.handlers

import com.wingedsheep.engine.core.DecisionResponse
import com.wingedsheep.engine.core.OrderedResponse
import com.wingedsheep.engine.core.ReorderLibraryDecision
import com.wingedsheep.gameserver.ai.AiResponseParser
import com.wingedsheep.gameserver.ai.GameStateFormatter
import com.wingedsheep.gameserver.ai.decision.AiDecisionHandler
import com.wingedsheep.gameserver.dto.ClientGameState
import com.wingedsheep.sdk.model.EntityId
import kotlin.reflect.KClass

class ReorderLibraryHandler : AiDecisionHandler<ReorderLibraryDecision> {
    override val decisionType: KClass<ReorderLibraryDecision> = ReorderLibraryDecision::class

    override fun autoResolve(decision: ReorderLibraryDecision): DecisionResponse {
        throw UnsupportedOperationException()
    }

    override fun format(
        sb: StringBuilder,
        decision: ReorderLibraryDecision,
        state: ClientGameState,
        labels: Map<EntityId, String>
    ) {
        sb.appendLine("Reorder cards on top of library (first = top):")
        for ((j, eid) in decision.cards.withIndex()) {
            val info = decision.cardInfo[eid]
            val name = info?.name ?: "Unknown"
            sb.appendLine("  [${GameStateFormatter.actionLetter(j)}] $name")
        }
        sb.appendLine("Reply with the order (e.g., \"B, A, C\").")
    }

    override fun parse(
        response: String,
        decision: ReorderLibraryDecision,
        state: ClientGameState,
        parser: AiResponseParser
    ): DecisionResponse? {
        val ordering = parser.parseOrdering(response, decision.cards.size) ?: return null
        return OrderedResponse(decisionId = decision.id, orderedObjects = ordering.map { decision.cards[it] })
    }

    override fun heuristic(decision: ReorderLibraryDecision, state: ClientGameState): DecisionResponse {
        return OrderedResponse(decisionId = decision.id, orderedObjects = decision.cards)
    }
}
