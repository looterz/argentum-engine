package com.wingedsheep.engine.handlers.continuations

import com.wingedsheep.engine.core.ContinuationFrame
import com.wingedsheep.engine.core.DecisionResponse
import com.wingedsheep.engine.core.ExecutionResult
import com.wingedsheep.engine.state.GameState
import kotlin.reflect.KClass

/**
 * Interface for continuation resumers.
 *
 * Each concrete continuation frame type has a corresponding resumer that handles
 * resumption logic after a player decision. This follows the Strategy pattern,
 * allowing continuation handling to be modular and testable.
 *
 * @param T The specific continuation frame type this resumer handles
 */
interface ContinuationResumer<T : ContinuationFrame> {
    /**
     * The continuation frame type this resumer handles.
     * Used for automatic registration in the resumer registry.
     */
    val frameType: KClass<T>

    /**
     * Resume execution after a decision is submitted.
     *
     * @param state The game state after popping the continuation
     * @param continuation The continuation frame describing what to resume
     * @param response The player's decision response
     * @param checkForMore Callback to check for more continuations on the stack
     * @return The execution result with new state and events
     */
    fun resume(
        state: GameState,
        continuation: T,
        response: DecisionResponse,
        checkForMore: CheckForMore
    ): ExecutionResult
}

/**
 * Factory function to create a [ContinuationResumer] from a method reference.
 *
 * Reduces boilerplate when implementing [ContinuationResumerModule.resumers].
 */
fun <T : ContinuationFrame> resumer(
    type: KClass<T>,
    handler: (GameState, T, DecisionResponse, CheckForMore) -> ExecutionResult
): ContinuationResumer<T> = object : ContinuationResumer<T> {
    override val frameType: KClass<T> = type
    override fun resume(
        state: GameState,
        continuation: T,
        response: DecisionResponse,
        checkForMore: CheckForMore
    ): ExecutionResult = handler(state, continuation, response, checkForMore)
}
