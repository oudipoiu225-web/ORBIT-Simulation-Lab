package dev.orbit.simlab.data

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.orbit.simlab.data.local.OrbitDatabase
import dev.orbit.simlab.data.repository.RoomProjects
import dev.orbit.simlab.domain.model.SampleProject
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class RoomProjectsTest {
    private val json = Json { encodeDefaults = true; classDiscriminator = "kind" }

    @Test fun createUpdateDeleteAndRelations() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val db = Room.inMemoryDatabaseBuilder(context, OrbitDatabase::class.java).build()
        try {
            val repo = RoomProjects(db, json)
            val original = SampleProject.emergency()
            repo.save(original)
            assertEquals(original, repo.get(original.id))
            assertEquals(5, db.projects().nodes(original.id).size)
            val updated = original.copy(name = "changed", links = original.links.dropLast(1))
            repo.save(updated)
            assertEquals(updated, repo.get(original.id))
            assertEquals(4, db.projects().links(original.id).size)
            repo.remove(original.id)
            assertNull(repo.get(original.id))
            assertEquals(0, db.projects().nodes(original.id).size)
        } finally { db.close() }
    }

    @Test fun transactionRollsBackOnDuplicateChildId() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val db = Room.inMemoryDatabaseBuilder(context, OrbitDatabase::class.java).build()
        try {
            val repo = RoomProjects(db, json)
            val p = SampleProject.emergency()
            repo.save(p)
            val invalid = p.copy(name = "must roll back", nodes = p.nodes + p.nodes.first())
            try { repo.save(invalid); fail("duplicate child ID should fail") }
            catch (_: android.database.sqlite.SQLiteConstraintException) { }
            assertEquals(p, repo.get(p.id))
        } finally { db.close() }
    }

    @Test fun persistsAcrossDatabaseReopen() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val file = "orbit-instrumented-${System.nanoTime()}"
        val p = SampleProject.emergency()
        try {
            Room.databaseBuilder(context, OrbitDatabase::class.java, file).build().use { db ->
                RoomProjects(db, json).save(p)
            }
            Room.databaseBuilder(context, OrbitDatabase::class.java, file).build().use { db ->
                assertEquals(p, RoomProjects(db, json).get(p.id))
            }
        } finally { context.deleteDatabase(file) }
    }
}
