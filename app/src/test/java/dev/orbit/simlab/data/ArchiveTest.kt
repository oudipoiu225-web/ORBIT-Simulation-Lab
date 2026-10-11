package dev.orbit.simlab.data

import dev.orbit.simlab.data.importexport.Archive
import dev.orbit.simlab.data.importexport.ArchiveCodec
import dev.orbit.simlab.data.importexport.ArchiveResult
import dev.orbit.simlab.domain.model.SampleProject
import kotlinx.serialization.json.Json
import kotlinx.serialization.encodeToString
import org.junit.Assert.*
import org.junit.Test
import java.io.ByteArrayInputStream

class ArchiveTest {
    private val json = Json { encodeDefaults = true; classDiscriminator = "kind" }
    private val codec = ArchiveCodec(json)
    private fun parse(text: String) = codec.decode(ByteArrayInputStream(text.toByteArray()))

    @Test fun roundTripAndBackup() {
        val p = SampleProject.emergency()
        val bytes = codec.encode(listOf(p, p.copy(id = "other")))
        val decoded = codec.decode(ByteArrayInputStream(bytes))
        assertTrue(decoded is ArchiveResult.Ok)
        assertEquals(listOf(p, p.copy(id = "other")), (decoded as ArchiveResult.Ok).projects)
    }

    @Test fun corruptFileAndUnknownVersion() {
        assertTrue(parse("{garbage") is ArchiveResult.Error)
        val text = json.encodeToString(Archive(projects = listOf(SampleProject.emergency())))
        assertTrue(parse(text.replace("\"version\":1", "\"version\":99")) is ArchiveResult.Error)
    }

    @Test fun oversizedAndDeepFiles() {
        assertTrue(codec.decode(ByteArrayInputStream(ByteArray(ArchiveCodec.MAX_BYTES + 1))) is ArchiveResult.Error)
        assertTrue(parse("[".repeat(ArchiveCodec.MAX_DEPTH + 1) + "]".repeat(ArchiveCodec.MAX_DEPTH + 1)) is ArchiveResult.Error)
    }

    @Test fun duplicateAndInvalidData() {
        val project = SampleProject.emergency()
        val broken = project.copy(nodes = project.nodes + project.nodes.first())
        assertThrows(IllegalArgumentException::class.java) { codec.encode(listOf(broken)) }
        assertTrue(parse(json.encodeToString(Archive(projects = listOf(broken)))) is ArchiveResult.Error)
        val text = json.encodeToString(Archive(projects = listOf(project)))
        assertTrue(parse(text.replaceFirst("\"interval\":2.0", "\"interval\":-2.0")) is ArchiveResult.Error)
    }
}
