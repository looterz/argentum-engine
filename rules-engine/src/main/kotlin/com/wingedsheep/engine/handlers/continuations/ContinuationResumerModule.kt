package com.wingedsheep.engine.handlers.continuations

/**
 * Interface for grouping related continuation resumers into modules.
 *
 * Each module represents a category of continuations (e.g., combat, mana payment, library)
 * and provides all resumers for that category. This enables modular organization
 * and reduces merge conflicts when adding new continuation types.
 */
interface ContinuationResumerModule {
    /**
     * Returns all resumers provided by this module.
     */
    fun resumers(): List<ContinuationResumer<*>>
}
