package com.wingedsheep.engine.legalactions.utils

import com.wingedsheep.engine.registry.CardRegistry
import com.wingedsheep.engine.state.GameState
import com.wingedsheep.engine.state.components.identity.CardComponent
import com.wingedsheep.sdk.model.EntityId
import com.wingedsheep.sdk.scripting.GrantAdditionalLandDrop

/**
 * Utility for calculating additional land drops from static abilities.
 */
object LandDropUtils {

    /**
     * Count additional land drops granted by [GrantAdditionalLandDrop] static abilities
     * on permanents controlled by the given player. Multiple sources are additive.
     */
    fun getAdditionalLandDrops(state: GameState, playerId: EntityId, cardRegistry: CardRegistry): Int {
        var bonus = 0
        for (entityId in state.getBattlefield(playerId)) {
            val card = state.getEntity(entityId)?.get<CardComponent>() ?: continue
            val cardDef = cardRegistry.getCard(card.cardDefinitionId) ?: continue
            for (ability in cardDef.script.staticAbilities) {
                if (ability is GrantAdditionalLandDrop) {
                    bonus += ability.count
                }
            }
        }
        return bonus
    }
}
