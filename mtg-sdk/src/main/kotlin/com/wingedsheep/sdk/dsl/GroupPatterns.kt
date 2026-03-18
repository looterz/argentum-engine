package com.wingedsheep.sdk.dsl

import com.wingedsheep.sdk.core.Keyword
import com.wingedsheep.sdk.core.Zone
import com.wingedsheep.sdk.scripting.Duration
import com.wingedsheep.sdk.scripting.GameObjectFilter
import com.wingedsheep.sdk.scripting.effects.CardDestination
import com.wingedsheep.sdk.scripting.effects.CardSource
import com.wingedsheep.sdk.scripting.effects.CompositeEffect
import com.wingedsheep.sdk.scripting.effects.DealDamageEffect
import com.wingedsheep.sdk.scripting.effects.ForEachInGroupEffect
import com.wingedsheep.sdk.scripting.effects.GainControlEffect
import com.wingedsheep.sdk.scripting.effects.GatherCardsEffect
import com.wingedsheep.sdk.scripting.effects.GrantKeywordEffect
import com.wingedsheep.sdk.scripting.effects.ModifyStatsEffect
import com.wingedsheep.sdk.scripting.effects.MoveCollectionEffect
import com.wingedsheep.sdk.scripting.effects.MoveToZoneEffect
import com.wingedsheep.sdk.scripting.effects.MoveType
import com.wingedsheep.sdk.scripting.effects.RemoveKeywordEffect
import com.wingedsheep.sdk.scripting.effects.TapUntapEffect
import com.wingedsheep.sdk.scripting.filters.unified.GroupFilter
import com.wingedsheep.sdk.scripting.targets.EffectTarget
import com.wingedsheep.sdk.scripting.values.DynamicAmount

/**
 * Effect patterns for bulk operations on filtered groups of permanents:
 * tap/untap, return, destroy, grant/remove keywords, modify stats, deal damage,
 * and gain control.
 */
object GroupPatterns {

    fun untapGroup(filter: GroupFilter = GroupFilter.AllCreatures): ForEachInGroupEffect =
        ForEachInGroupEffect(
            filter = filter,
            effect = TapUntapEffect(EffectTarget.Self, tap = false)
        )

    fun tapAll(filter: GroupFilter): ForEachInGroupEffect =
        ForEachInGroupEffect(
            filter = filter,
            effect = TapUntapEffect(EffectTarget.Self, tap = true)
        )

    fun returnAllToHand(filter: GroupFilter): CompositeEffect = CompositeEffect(listOf(
        GatherCardsEffect(
            source = CardSource.BattlefieldMatching(
                filter = filter.baseFilter,
                excludeSelf = filter.excludeSelf
            ),
            storeAs = "returnAllToHand_gathered"
        ),
        MoveCollectionEffect(
            from = "returnAllToHand_gathered",
            destination = CardDestination.ToZone(Zone.HAND)
        )
    ))

    fun destroyAll(filter: GroupFilter, noRegenerate: Boolean = false): ForEachInGroupEffect =
        ForEachInGroupEffect(
            filter = filter,
            effect = MoveToZoneEffect(EffectTarget.Self, Zone.GRAVEYARD, byDestruction = true),
            noRegenerate = noRegenerate
        )

    fun destroyAllPipeline(
        filter: GameObjectFilter,
        noRegenerate: Boolean = false,
        storeDestroyedAs: String? = null
    ): CompositeEffect = CompositeEffect(listOf(
        GatherCardsEffect(
            source = CardSource.BattlefieldMatching(filter = filter),
            storeAs = "destroyAll_gathered"
        ),
        MoveCollectionEffect(
            from = "destroyAll_gathered",
            destination = CardDestination.ToZone(Zone.GRAVEYARD),
            moveType = MoveType.Destroy,
            noRegenerate = noRegenerate,
            storeMovedAs = storeDestroyedAs
        )
    ))

    fun destroyAllAndAttachedPipeline(
        filter: GameObjectFilter,
        noRegenerate: Boolean = false
    ): CompositeEffect = CompositeEffect(listOf(
        GatherCardsEffect(
            source = CardSource.BattlefieldMatching(filter = filter, includeAttachments = true),
            storeAs = "destroyAllAttached_gathered"
        ),
        MoveCollectionEffect(
            from = "destroyAllAttached_gathered",
            destination = CardDestination.ToZone(Zone.GRAVEYARD),
            moveType = MoveType.Destroy,
            noRegenerate = noRegenerate
        )
    ))

    fun grantKeywordToAll(
        keyword: Keyword,
        filter: GroupFilter,
        duration: Duration = Duration.EndOfTurn
    ): ForEachInGroupEffect =
        ForEachInGroupEffect(
            filter = filter,
            effect = GrantKeywordEffect(keyword.name, EffectTarget.Self, duration)
        )

    fun removeKeywordFromAll(
        keyword: Keyword,
        filter: GroupFilter,
        duration: Duration = Duration.EndOfTurn
    ): ForEachInGroupEffect =
        ForEachInGroupEffect(
            filter = filter,
            effect = RemoveKeywordEffect(keyword.name, EffectTarget.Self, duration)
        )

    fun modifyStatsForAll(
        power: Int,
        toughness: Int,
        filter: GroupFilter,
        duration: Duration = Duration.EndOfTurn
    ): ForEachInGroupEffect =
        ForEachInGroupEffect(
            filter = filter,
            effect = ModifyStatsEffect(power, toughness, EffectTarget.Self, duration)
        )

    fun modifyStatsForAll(
        power: DynamicAmount,
        toughness: DynamicAmount,
        filter: GroupFilter,
        duration: Duration = Duration.EndOfTurn
    ): ForEachInGroupEffect =
        ForEachInGroupEffect(
            filter = filter,
            effect = ModifyStatsEffect(power, toughness, EffectTarget.Self, duration)
        )

    fun dealDamageToAll(amount: Int, filter: GroupFilter): ForEachInGroupEffect =
        ForEachInGroupEffect(
            filter = filter,
            effect = DealDamageEffect(amount, EffectTarget.Self)
        )

    fun dealDamageToAll(amount: DynamicAmount, filter: GroupFilter): ForEachInGroupEffect =
        ForEachInGroupEffect(
            filter = filter,
            effect = DealDamageEffect(amount, EffectTarget.Self)
        )

    fun gainControlOfGroup(filter: GroupFilter = GroupFilter.AllCreatures, duration: Duration = Duration.EndOfTurn): ForEachInGroupEffect =
        ForEachInGroupEffect(
            filter = filter,
            effect = GainControlEffect(EffectTarget.Self, duration)
        )
}
