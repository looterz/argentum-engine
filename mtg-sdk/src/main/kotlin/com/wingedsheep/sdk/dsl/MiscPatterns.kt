package com.wingedsheep.sdk.dsl

import com.wingedsheep.sdk.core.Zone
import com.wingedsheep.sdk.scripting.GameObjectFilter
import com.wingedsheep.sdk.scripting.effects.CardSource
import com.wingedsheep.sdk.scripting.effects.CardDestination
import com.wingedsheep.sdk.scripting.effects.CompositeEffect
import com.wingedsheep.sdk.scripting.effects.DealDamageEffect
import com.wingedsheep.sdk.scripting.effects.Effect
import com.wingedsheep.sdk.scripting.effects.ForEachPlayerEffect
import com.wingedsheep.sdk.scripting.effects.GainLifeEffect
import com.wingedsheep.sdk.scripting.effects.GatherCardsEffect
import com.wingedsheep.sdk.scripting.effects.MoveCollectionEffect
import com.wingedsheep.sdk.scripting.effects.OptionalCostEffect
import com.wingedsheep.sdk.scripting.effects.ReflexiveTriggerEffect
import com.wingedsheep.sdk.scripting.effects.SacrificeEffect
import com.wingedsheep.sdk.scripting.effects.SelectFromCollectionEffect
import com.wingedsheep.sdk.scripting.effects.SelectionMode
import com.wingedsheep.sdk.scripting.effects.ShuffleLibraryEffect
import com.wingedsheep.sdk.scripting.effects.StoreCountEffect
import com.wingedsheep.sdk.scripting.effects.StoreResultEffect
import com.wingedsheep.sdk.scripting.references.Player
import com.wingedsheep.sdk.scripting.targets.EffectTarget
import com.wingedsheep.sdk.scripting.values.DynamicAmount
import com.wingedsheep.sdk.scripting.values.EffectVariable

/**
 * Small utility effect patterns that don't fit a larger domain category:
 * optional costs, sacrifice, reflexive triggers, store/reference, sequence,
 * drain, and multi-player utility effects.
 */
object MiscPatterns {

    fun mayPay(cost: Effect, effect: Effect): OptionalCostEffect =
        OptionalCostEffect(cost, effect)

    fun mayPayOrElse(cost: Effect, ifPaid: Effect, ifNotPaid: Effect): OptionalCostEffect =
        OptionalCostEffect(cost, ifPaid, ifNotPaid)

    fun sacrificeFor(
        filter: GameObjectFilter,
        countName: String,
        thenEffect: Effect
    ): CompositeEffect = CompositeEffect(
        listOf(
            StoreCountEffect(
                effect = SacrificeEffect(filter, any = true),
                storeAs = EffectVariable.Count(countName)
            ),
            thenEffect
        )
    )

    fun sacrifice(
        filter: GameObjectFilter,
        count: Int = 1,
        then: Effect
    ): CompositeEffect = CompositeEffect(
        listOf(
            SacrificeEffect(filter, count),
            then
        )
    )

    fun reflexiveTrigger(
        action: Effect,
        whenYouDo: Effect,
        optional: Boolean = true
    ): ReflexiveTriggerEffect = ReflexiveTriggerEffect(action, optional, whenYouDo)

    fun storeEntity(effect: Effect, `as`: String): StoreResultEffect =
        StoreResultEffect(effect, EffectVariable.EntityRef(`as`))

    fun storeCount(effect: Effect, `as`: String): StoreCountEffect =
        StoreCountEffect(effect, EffectVariable.Count(`as`))

    fun sequence(vararg effects: Effect): CompositeEffect =
        CompositeEffect(effects.toList())

    fun drain(amount: Int, target: EffectTarget): CompositeEffect = CompositeEffect(
        listOf(
            DealDamageEffect(amount, target),
            GainLifeEffect(amount, EffectTarget.Controller)
        )
    )

    fun eachPlayerReturnsPermanentToHand(): ForEachPlayerEffect = ForEachPlayerEffect(
        players = Player.ActivePlayerFirst,
        effects = listOf(
            GatherCardsEffect(
                source = CardSource.ControlledPermanents(Player.You),
                storeAs = "permanents"
            ),
            SelectFromCollectionEffect(
                from = "permanents",
                selection = SelectionMode.ChooseExactly(DynamicAmount.Fixed(1)),
                storeSelected = "chosen",
                prompt = "Choose a permanent to return to its owner's hand"
            ),
            MoveCollectionEffect(
                from = "chosen",
                destination = CardDestination.ToZone(Zone.HAND, Player.You)
            )
        )
    )

    fun eachPlayerSearchesLibrary(
        filter: GameObjectFilter,
        count: DynamicAmount
    ): ForEachPlayerEffect = ForEachPlayerEffect(
        players = Player.Each,
        effects = listOf(
            GatherCardsEffect(
                source = CardSource.FromZone(Zone.LIBRARY, Player.You, filter),
                storeAs = "searchable"
            ),
            SelectFromCollectionEffect(
                from = "searchable",
                selection = SelectionMode.ChooseUpTo(count),
                storeSelected = "found"
            ),
            MoveCollectionEffect(
                from = "found",
                destination = CardDestination.ToZone(Zone.HAND),
                revealed = true
            ),
            ShuffleLibraryEffect()
        )
    )
}
