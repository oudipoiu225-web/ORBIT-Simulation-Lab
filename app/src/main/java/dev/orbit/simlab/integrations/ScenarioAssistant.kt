package dev.orbit.simlab.integrations

import dev.orbit.simlab.domain.model.Project

/** Optional boundary only; external proposals must be validated before saving. */
interface ScenarioAssistant {
    suspend fun suggest(project: Project): Project
}
