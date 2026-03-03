package com.wingedsheep.engine.handlers.effects.permanent

import com.wingedsheep.engine.handlers.DecisionHandler
import com.wingedsheep.engine.handlers.DynamicAmountEvaluator
import com.wingedsheep.engine.handlers.effects.EffectExecutor
import com.wingedsheep.engine.handlers.effects.ExecutorModule
import com.wingedsheep.engine.registry.CardRegistry

/**
 * Module providing all permanent-related effect executors.
 */
class PermanentExecutors(
    private val decisionHandler: DecisionHandler = DecisionHandler(),
    private val amountEvaluator: DynamicAmountEvaluator = DynamicAmountEvaluator(),
    private val cardRegistry: CardRegistry? = null
) : ExecutorModule {
    override fun executors(): List<EffectExecutor<*>> = listOf(
        TapUntapExecutor(),
        TapTargetCreaturesExecutor(),
        ModifyStatsExecutor(amountEvaluator),
        GrantKeywordUntilEndOfTurnExecutor(),
        RemoveKeywordUntilEndOfTurnExecutor(),
        AddCountersExecutor(),
        RemoveCountersExecutor(),
        ChooseColorProtectionExecutor(decisionHandler),
        ChooseColorProtectionTargetExecutor(decisionHandler),
        ChangeCreatureTypeTextExecutor(decisionHandler),
        GainControlExecutor(),
        GainControlByActivePlayerExecutor(),
        GainControlByMostOfSubtypeExecutor(),
        GiveControlToTargetPlayerExecutor(),
        TurnFaceDownExecutor(),
        TurnFaceUpExecutor(cardRegistry),
        GrantTriggeredAbilityUntilEndOfTurnExecutor(),
        GrantActivatedAbilityUntilEndOfTurnExecutor(),
        GrantToEnchantedCreatureTypeGroupExecutor(),
        BecomeCreatureTypeExecutor(),
        ChooseCreatureTypeModifyStatsExecutor(amountEvaluator),
        BecomeChosenTypeAllCreaturesExecutor(),
        SetGroupCreatureSubtypesExecutor(),
        SetCreatureSubtypesExecutor(),
        ChangeGroupColorExecutor(),
        GrantActivatedAbilityToGroupExecutor(),
        ChooseCreatureTypeUntapExecutor(),
        ChooseCreatureTypeGainControlExecutor(),
        AnimateLandExecutor(),
        SetBasePowerExecutor(amountEvaluator),
        DistributeCountersFromSelfExecutor()
    )
}
