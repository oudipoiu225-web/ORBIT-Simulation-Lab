package dev.orbit.simlab.domain.repository

import dev.orbit.simlab.domain.model.Project
import kotlinx.coroutines.flow.Flow

interface ProjectRepository {
    fun observe(): Flow<List<Project>>
    suspend fun get(id: String): Project?
    suspend fun save(project: Project)
    suspend fun remove(id: String)
}
