package dev.orbit.simlab.data.repository

import androidx.room.withTransaction
import dev.orbit.simlab.data.local.*
import dev.orbit.simlab.domain.model.*
import dev.orbit.simlab.domain.repository.ProjectRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.encodeToString
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json

class RoomProjects(private val db: OrbitDatabase, private val json: Json) : ProjectRepository {
    private val dao get() = db.projects()
    suspend fun count(): Int = dao.count()

    override fun observe(): Flow<List<Project>> = dao.observeRows().map { rows -> rows.mapNotNull { get(it.id) } }

    override suspend fun get(id: String): Project? = db.withTransaction {
        val row = dao.project(id) ?: return@withTransaction null
        Project(id = row.id, name = row.name, description = row.description,
            settings = json.decodeFromString<Settings>(row.settingsJson),
            nodes = dao.nodes(id).map { json.decodeFromString<Node>(it.json) },
            links = dao.links(id).map { json.decodeFromString<Link>(it.json) },
            rules = dao.rules(id).map { json.decodeFromString<Rule>(it.json) },
            inputs = dao.inputs(id).map { json.decodeFromString<ScheduledInput>(it.json) },
            history = dao.runs(id).map { json.decodeFromString<RunResult>(it.json) })
    }

    override suspend fun save(project: Project) = db.withTransaction {
        // Replace children atomically. An exception rolls back the parent and all children.
        dao.upsert(ProjectRow(project.id, project.name, project.description,
            json.encodeToString(project.settings), System.currentTimeMillis()))
        dao.clearLinks(project.id); dao.clearRules(project.id); dao.clearInputs(project.id)
        dao.clearRuns(project.id); dao.clearNodes(project.id)
        dao.nodes(project.nodes.map { NodeRow(project.id, it.id, json.encodeToString(it)) })
        dao.links(project.links.map { LinkRow(project.id, it.id, json.encodeToString(it)) })
        dao.rules(project.rules.map { RuleRow(project.id, it.id, json.encodeToString(it)) })
        dao.inputs(project.inputs.map { InputRow(project.id, it.id, json.encodeToString(it)) })
        dao.runs(project.history.map { RunRow(project.id, it.id, json.encodeToString(it)) })
    }

    override suspend fun remove(id: String) { dao.remove(id) }
}
