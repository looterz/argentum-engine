package com.wingedsheep.engine.handlers.continuations

import com.wingedsheep.engine.core.ContinuationFrame
import com.wingedsheep.engine.core.DecisionResponse
import com.wingedsheep.engine.core.ExecutionResult
import com.wingedsheep.engine.state.GameState
import kotlin.reflect.KClass

/**
 * Registry that maps continuation frame types to their resumers.
 *
 * This implements the Strategy pattern, allowing each continuation frame type
 * to have its own dedicated resumer while providing a unified dispatch mechanism.
 *
 * The registry uses a map-based dispatch system with modular sub-registries
 * for each category of continuations, reducing merge conflicts and enabling
 * dynamic resumer registration.
 */
class ContinuationResumerRegistry {
    private val resumers = mutableMapOf<KClass<out ContinuationFrame>, ContinuationResumer<*>>()

    /**
     * Register all resumers from a module.
     */
    fun registerModule(module: ContinuationResumerModule) {
        module.resumers().forEach { resumer ->
            resumers[resumer.frameType] = resumer
        }
    }

    /**
     * Register a single resumer.
     */
    fun <T : ContinuationFrame> register(resumer: ContinuationResumer<T>) {
        resumers[resumer.frameType] = resumer
    }

    /**
     * Resume a continuation using the appropriate resumer.
     *
     * @param state The game state after popping the continuation
     * @param continuation The continuation frame to resume
     * @param response The player's decision response
     * @param checkForMore Callback to check for more continuations on the stack
     * @return The execution result with new state and events
     */
    @Suppress("UNCHECKED_CAST")
    fun resume(
        state: GameState,
        continuation: ContinuationFrame,
        response: DecisionResponse,
        checkForMore: CheckForMore
    ): ExecutionResult {
        val resumer = resumers[continuation::class] as? ContinuationResumer<ContinuationFrame>
            ?: return ExecutionResult.error(state, "No resumer registered for continuation type: ${continuation::class.simpleName}")
        return resumer.resume(state, continuation, response, checkForMore)
    }

    /**
     * Returns the number of registered resumers.
     */
    fun resumerCount(): Int = resumers.size
}
