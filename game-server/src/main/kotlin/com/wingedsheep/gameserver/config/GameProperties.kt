package com.wingedsheep.gameserver.config

import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties(prefix = "game")
data class GameProperties(
    val handSmoother: HandSmootherProperties = HandSmootherProperties(),
    val sets: SetsProperties = SetsProperties(),
    val admin: AdminProperties = AdminProperties(),
    val ai: AiProperties = AiProperties()
)

data class HandSmootherProperties(
    val enabled: Boolean = true,
    val candidates: Int = 3
)

data class SetsProperties(
    val onslaughtEnabled: Boolean = true,
    val scourgeEnabled: Boolean = true,
    val legionsEnabled: Boolean = true,
    val khansEnabled: Boolean = true,
    val dominariaEnabled: Boolean = false
)

data class AdminProperties(
    val password: String = ""
)

data class AiProperties(
    val enabled: Boolean = false,
    val openRouterApiKey: String = "",
    val model: String = "stepfun/step-3.5-flash:free",
    val deckbuildingModel: String = "",
    val maxRetries: Int = 2,
    val timeoutMs: Long = 10000,
    val thinkingDelayMs: Long = 500
) {
    /** Returns the model to use for deckbuilding — falls back to the gameplay model if not set. */
    val effectiveDeckbuildingModel: String get() = deckbuildingModel.ifBlank { model }
}