package dev.orbit.simlab.data.local

import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.RoomDatabase
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "projects")
data class ProjectRow(@PrimaryKey val id: String, val name: String, val description: String,
    val settingsJson: String, val modifiedAt: Long)

@Entity(tableName = "nodes", foreignKeys = [ForeignKey(entity = ProjectRow::class,
    parentColumns = ["id"], childColumns = ["projectId"], onDelete = ForeignKey.CASCADE)],
    indices = [Index("projectId")], primaryKeys = ["projectId", "id"])
data class NodeRow(val projectId: String, val id: String, val json: String)

@Entity(tableName = "links", foreignKeys = [ForeignKey(entity = ProjectRow::class,
    parentColumns = ["id"], childColumns = ["projectId"], onDelete = ForeignKey.CASCADE)],
    indices = [Index("projectId")], primaryKeys = ["projectId", "id"])
data class LinkRow(val projectId: String, val id: String, val json: String)

@Entity(tableName = "rules", foreignKeys = [ForeignKey(entity = ProjectRow::class,
    parentColumns = ["id"], childColumns = ["projectId"], onDelete = ForeignKey.CASCADE)],
    indices = [Index("projectId")], primaryKeys = ["projectId", "id"])
data class RuleRow(val projectId: String, val id: String, val json: String)

@Entity(tableName = "inputs", foreignKeys = [ForeignKey(entity = ProjectRow::class,
    parentColumns = ["id"], childColumns = ["projectId"], onDelete = ForeignKey.CASCADE)],
    indices = [Index("projectId")], primaryKeys = ["projectId", "id"])
data class InputRow(val projectId: String, val id: String, val json: String)

@Entity(tableName = "runs", foreignKeys = [ForeignKey(entity = ProjectRow::class,
    parentColumns = ["id"], childColumns = ["projectId"], onDelete = ForeignKey.CASCADE)],
    indices = [Index("projectId")], primaryKeys = ["projectId", "id"])
data class RunRow(val projectId: String, val id: String, val json: String)

@Dao
interface ProjectDao {
    @Query("SELECT * FROM projects ORDER BY modifiedAt DESC") fun observeRows(): Flow<List<ProjectRow>>
    @Query("SELECT * FROM projects ORDER BY modifiedAt DESC") suspend fun listRows(): List<ProjectRow>
    @Query("SELECT * FROM projects WHERE id=:id") suspend fun project(id: String): ProjectRow?
    @Query("SELECT COUNT(*) FROM projects") suspend fun count(): Int
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun upsert(row: ProjectRow)
    @Query("DELETE FROM projects WHERE id=:id") suspend fun remove(id: String)
    @Query("DELETE FROM nodes WHERE projectId=:id") suspend fun clearNodes(id: String)
    @Query("DELETE FROM links WHERE projectId=:id") suspend fun clearLinks(id: String)
    @Query("DELETE FROM rules WHERE projectId=:id") suspend fun clearRules(id: String)
    @Query("DELETE FROM inputs WHERE projectId=:id") suspend fun clearInputs(id: String)
    @Query("DELETE FROM runs WHERE projectId=:id") suspend fun clearRuns(id: String)
    @Insert suspend fun nodes(rows: List<NodeRow>)
    @Insert suspend fun links(rows: List<LinkRow>)
    @Insert suspend fun rules(rows: List<RuleRow>)
    @Insert suspend fun inputs(rows: List<InputRow>)
    @Insert suspend fun runs(rows: List<RunRow>)
    @Query("SELECT * FROM nodes WHERE projectId=:id ORDER BY rowid") suspend fun nodes(id: String): List<NodeRow>
    @Query("SELECT * FROM links WHERE projectId=:id ORDER BY rowid") suspend fun links(id: String): List<LinkRow>
    @Query("SELECT * FROM rules WHERE projectId=:id ORDER BY rowid") suspend fun rules(id: String): List<RuleRow>
    @Query("SELECT * FROM inputs WHERE projectId=:id ORDER BY rowid") suspend fun inputs(id: String): List<InputRow>
    @Query("SELECT * FROM runs WHERE projectId=:id ORDER BY rowid") suspend fun runs(id: String): List<RunRow>
}

@Database(entities = [ProjectRow::class, NodeRow::class, LinkRow::class, RuleRow::class,
    InputRow::class, RunRow::class], version = 1, exportSchema = false)
abstract class OrbitDatabase : RoomDatabase() { abstract fun projects(): ProjectDao }
