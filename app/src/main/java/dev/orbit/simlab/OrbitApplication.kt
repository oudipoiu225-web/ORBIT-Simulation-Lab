package dev.orbit.simlab

import android.app.Application
import androidx.room.Room
import dev.orbit.simlab.data.importexport.ArchiveCodec
import dev.orbit.simlab.data.local.OrbitDatabase
import dev.orbit.simlab.data.repository.RoomProjects
import kotlinx.serialization.json.Json

class OrbitApplication : Application() {
    lateinit var container: AppContainer
        private set
    override fun onCreate() {
        super.onCreate()
        val json = Json { encodeDefaults = true; classDiscriminator = "kind" }
        val db = Room.databaseBuilder(this, OrbitDatabase::class.java, "orbit.db").build()
        container = AppContainer(RoomProjects(db, json), ArchiveCodec(json))
    }
}

class AppContainer(val projects: RoomProjects, val archives: ArchiveCodec)
