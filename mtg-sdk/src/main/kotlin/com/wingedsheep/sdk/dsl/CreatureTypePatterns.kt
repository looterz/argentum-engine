package com.wingedsheep.sdk.dsl

import com.wingedsheep.sdk.core.Keyword
import com.wingedsheep.sdk.core.Zone
import com.wingedsheep.sdk.scripting.Duration
import com.wingedsheep.sdk.scripting.GameObjectFilter
import com.wingedsheep.sdk.scripting.conditions.YouControlMostOfChosenType
import com.wingedsheep.sdk.scripting.effects.CardDestination
import com.wingedsheep.sdk.scripting.effects.CardSource
import com.wingedsheep.sdk.scripting.effects.ChooseCreatureTypeEffect
import com.wingedsheep.sdk.scripting.effects.ChooseOptionEffect
import com.wingedsheep.sdk.scripting.effects.CompositeEffect
import com.wingedsheep.sdk.scripting.effects.ConditionalEffect
import com.wingedsheep.sdk.scripting.effects.EachPlayerChoosesCreatureTypeEffect
import com.wingedsheep.sdk.scripting.effects.Effect
import com.wingedsheep.sdk.scripting.effects.ForEachInGroupEffect
import com.wingedsheep.sdk.scripting.effects.ForEachPlayerEffect
import com.wingedsheep.sdk.scripting.effects.GainControlEffect
import com.wingedsheep.sdk.scripting.effects.GatherCardsEffect
import com.wingedsheep.sdk.scripting.effects.GrantKeywordEffect
import com.wingedsheep.sdk.scripting.effects.MarkMustAttackThisTurnEffect
import com.wingedsheep.sdk.scripting.effects.ModifyStatsEffect
import com.wingedsheep.sdk.scripting.effects.MoveCollectionEffect
import com.wingedsheep.sdk.scripting.effects.OptionType
import com.wingedsheep.sdk.scripting.effects.SelectFromCollectionEffect
import com.wingedsheep.sdk.scripting.effects.SelectionMode
import com.wingedsheep.sdk.scripting.effects.SetCreatureSubtypesEffect
import com.wingedsheep.sdk.scripting.effects.TapUntapEffect
import com.wingedsheep.sdk.scripting.effects.ZonePlacement
import com.wingedsheep.sdk.scripting.filters.unified.GroupFilter
import com.wingedsheep.sdk.scripting.references.Player
import com.wingedsheep.sdk.scripting.targets.EffectTarget
import com.wingedsheep.sdk.scripting.values.DynamicAmount

/**
 * Effect patterns for creature-type-choice-based effects: choose a type then
 * apply effects based on it.
 */
object CreatureTypePatterns {

    fun chooseCreatureTypeRevealTop(): CompositeEffect = CompositeEffect(
        listOf(
            ChooseCreatureTypeEffect,
            GatherCardsEffect(
                source = CardSource.TopOfLibrary(DynamicAmount.Fixed(1)),
                storeAs = "topCard",
                revealed = true
            ),
            SelectFromCollectionEffect(
                from = "topCard",
                selection = SelectionMode.All,
                filter = GameObjectFilter.Creature,
                matchChosenCreatureType = true,
                storeSelected = "matched",
                storeRemainder = "unmatched"
            ),
            MoveCollectionEffect(
                from = "matched",
                destination = CardDestination.ToZone(Zone.HAND)
            ),
            MoveCollectionEffect(
                from = "unmatched",
                destination = CardDestination.ToZone(Zone.GRAVEYARD)
            )
        )
    )

    fun chooseCreatureTypeReturnFromGraveyard(
        count: Int
    ): CompositeEffect = CompositeEffect(
        listOf(
            GatherCardsEffect(
                source = CardSource.FromZone(Zone.GRAVEYARD, Player.You, GameObjectFilter.Creature),
                storeAs = "graveyardCreatures"
            ),
            SelectFromCollectionEffect(
                from = "graveyardCreatures",
                selection = SelectionMode.ChooseUpTo(DynamicAmount.Fixed(count)),
                matchChosenCreatureType = true,
                storeSelected = "chosen"
            ),
            MoveCollectionEffect(
                from = "chosen",
                destination = CardDestination.ToZone(Zone.HAND)
            )
        )
    )

    fun chooseCreatureTypeShuffleGraveyardIntoLibrary(): CompositeEffect = CompositeEffect(
        listOf(
            ChooseCreatureTypeEffect,
            GatherCardsEffect(
                source = CardSource.FromZone(Zone.GRAVEYARD, Player.You, GameObjectFilter.Creature),
                storeAs = "graveyardCreatures"
            ),
            SelectFromCollectionEffect(
                from = "graveyardCreatures",
                selection = SelectionMode.All,
                matchChosenCreatureType = true,
                storeSelected = "chosen"
            ),
            MoveCollectionEffect(
                from = "chosen",
                destination = CardDestination.ToZone(Zone.LIBRARY, placement = ZonePlacement.Shuffled)
            )
        )
    )

    fun chooseCreatureTypeModifyStats(
        powerModifier: DynamicAmount,
        toughnessModifier: DynamicAmount,
        duration: Duration = Duration.EndOfTurn,
        grantKeyword: Keyword? = null
    ): CompositeEffect {
        val key = "chosenCreatureType"
        val modifyStats = ModifyStatsEffect(
            powerModifier = powerModifier,
            toughnessModifier = toughnessModifier,
            target = EffectTarget.Self,
            duration = duration
        )
        val innerEffect: Effect = if (grantKeyword != null) {
            CompositeEffect(listOf(
                modifyStats,
                GrantKeywordEffect(grantKeyword.name, EffectTarget.Self, duration)
            ))
        } else {
            modifyStats
        }
        return CompositeEffect(listOf(
            ChooseOptionEffect(
                optionType = OptionType.CREATURE_TYPE,
                storeAs = key
            ),
            ForEachInGroupEffect(
                filter = GroupFilter.ChosenSubtypeCreatures(key),
                effect = innerEffect
            )
        ))
    }

    fun chooseCreatureTypeUntap(): CompositeEffect = CompositeEffect(
        listOf(
            ChooseOptionEffect(
                optionType = OptionType.CREATURE_TYPE,
                storeAs = "chosenType"
            ),
            ForEachInGroupEffect(
                filter = GroupFilter(
                    baseFilter = GameObjectFilter.Creature.withSubtypeFromVariable("chosenType")
                ),
                effect = TapUntapEffect(EffectTarget.Self, tap = false)
            )
        )
    )

    fun becomeChosenTypeAllCreatures(
        excludedTypes: List<String> = emptyList(),
        controllerOnly: Boolean = false,
        duration: Duration = Duration.EndOfTurn
    ): CompositeEffect {
        val key = "chosenCreatureType"
        val filter = if (controllerOnly) GroupFilter.AllCreaturesYouControl else GroupFilter.AllCreatures
        return CompositeEffect(listOf(
            ChooseOptionEffect(
                optionType = OptionType.CREATURE_TYPE,
                storeAs = key,
                excludedOptions = excludedTypes
            ),
            ForEachInGroupEffect(
                filter = filter,
                effect = SetCreatureSubtypesEffect(
                    fromChosenValueKey = key,
                    target = EffectTarget.Self,
                    duration = duration
                )
            )
        ))
    }

    fun chooseCreatureTypeGainControl(
        duration: Duration = Duration.Permanent
    ): CompositeEffect {
        val key = "chosenCreatureType"
        return CompositeEffect(listOf(
            ChooseOptionEffect(
                optionType = OptionType.CREATURE_TYPE,
                storeAs = key
            ),
            ConditionalEffect(
                condition = YouControlMostOfChosenType(key),
                effect = ForEachInGroupEffect(
                    filter = GroupFilter.ChosenSubtypeCreatures(key),
                    effect = GainControlEffect(
                        target = EffectTarget.Self,
                        duration = duration
                    )
                )
            )
        ))
    }

    fun chooseCreatureTypeMustAttack(): CompositeEffect {
        val key = "chosenCreatureType"
        return CompositeEffect(listOf(
            ChooseOptionEffect(
                optionType = OptionType.CREATURE_TYPE,
                storeAs = key
            ),
            ForEachInGroupEffect(
                filter = GroupFilter.ChosenSubtypeCreatures(key),
                effect = MarkMustAttackThisTurnEffect(
                    target = EffectTarget.Self
                )
            )
        ))
    }

    fun patriarchsBidding(): CompositeEffect = CompositeEffect(
        listOf(
            EachPlayerChoosesCreatureTypeEffect(storeAs = "biddingTypes"),
            ForEachPlayerEffect(
                players = Player.ActivePlayerFirst,
                effects = listOf(
                    GatherCardsEffect(
                        source = CardSource.FromZone(
                            zone = Zone.GRAVEYARD,
                            player = Player.You,
                            filter = GameObjectFilter.Creature.withSubtypeInStoredList("biddingTypes")
                        ),
                        storeAs = "toReturn"
                    ),
                    MoveCollectionEffect(
                        from = "toReturn",
                        destination = CardDestination.ToZone(Zone.BATTLEFIELD)
                    )
                )
            )
        )
    )

    fun destroyAllExceptStoredSubtypes(
        noRegenerate: Boolean = false,
        exceptSubtypesFromStored: String
    ): CompositeEffect = CompositeEffect(listOf(
        GatherCardsEffect(
            source = CardSource.BattlefieldMatching(filter = GameObjectFilter.Creature),
            storeAs = "destroyAll_gathered"
        ),
        com.wingedsheep.sdk.scripting.effects.FilterCollectionEffect(
            from = "destroyAll_gathered",
            filter = com.wingedsheep.sdk.scripting.effects.CollectionFilter.ExcludeSubtypesFromStored(exceptSubtypesFromStored),
            storeMatching = "destroyAll_filtered"
        ),
        MoveCollectionEffect(
            from = "destroyAll_filtered",
            destination = CardDestination.ToZone(Zone.GRAVEYARD),
            moveType = com.wingedsheep.sdk.scripting.effects.MoveType.Destroy,
            noRegenerate = noRegenerate
        )
    ))

    fun destroyAllSharingTypeWithSacrificed(
        noRegenerate: Boolean = false
    ): CompositeEffect = CompositeEffect(listOf(
        GatherCardsEffect(
            source = CardSource.BattlefieldMatching(filter = GameObjectFilter.Creature),
            storeAs = "sharingType_gathered"
        ),
        com.wingedsheep.sdk.scripting.effects.FilterCollectionEffect(
            from = "sharingType_gathered",
            filter = com.wingedsheep.sdk.scripting.effects.CollectionFilter.SharesSubtypeWithSacrificed,
            storeMatching = "sharingType_filtered"
        ),
        MoveCollectionEffect(
            from = "sharingType_filtered",
            destination = CardDestination.ToZone(Zone.GRAVEYARD),
            moveType = com.wingedsheep.sdk.scripting.effects.MoveType.Destroy,
            noRegenerate = noRegenerate
        )
    ))
}
