package dev.orbit.simlab.data.importexport

import dev.orbit.simlab.domain.model.Project
import dev.orbit.simlab.domain.validation.Validator
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.InputStream

@Serializable
data class Archive(val format: String = "orbit.simlab", val version: Int = 1,
    val projects: List<Project>)

sealed class ArchiveResult {
    data class Ok(val projects: List<Project>) : ArchiveResult()
    data class Error(val message: String) : ArchiveResult()
}

class ArchiveCodec(private val json: Json) {
    companion object { const val MAX_BYTES = 2_000_000; const val MAX_DEPTH = 32 }

    fun encode(projects: List<Project>): ByteArray {
        require(projects.isNotEmpty() && projects.size <= 100) { "تعداد پروژه‌ها نامعتبر است" }
        projects.forEach { require(Validator.check(it, requireNodes = false).isEmpty()) { "پروژهٔ نامعتبر: ${it.name}" } }
        return json.encodeToString(Archive(projects = projects)).toByteArray(Charsets.UTF_8).also {
            require(it.size <= MAX_BYTES) { "اندازهٔ فایل بیش از حد مجاز است" }
        }
    }

    fun decode(stream: InputStream): ArchiveResult {
        val bytes = try {
            val buffer = ByteArray(8192)
            val output = java.io.ByteArrayOutputStream()
            while (true) {
                val count = stream.read(buffer)
                if (count < 0) break
                if (output.size() + count > MAX_BYTES) return ArchiveResult.Error("فایل بیش از ۲ مگابایت است")
                output.write(buffer, 0, count)
            }
            output.toByteArray()
        } catch (e: java.io.IOException) { return ArchiveResult.Error("خواندن فایل ناموفق بود") }
        val text = bytes.toString(Charsets.UTF_8)
        if (!boundedDepth(text)) return ArchiveResult.Error("عمق ساختار JSON نامعتبر است")
        val archive = try { json.decodeFromString<Archive>(text) }
            catch (e: kotlinx.serialization.SerializationException) { return ArchiveResult.Error("فایل JSON خراب یا ناسازگار است") }
            catch (e: IllegalArgumentException) { return ArchiveResult.Error("ساختار فایل نامعتبر است") }
        if (archive.format != "orbit.simlab" || archive.version != 1)
            return ArchiveResult.Error("نسخه یا نوع فایل پشتیبانی نمی‌شود")
        if (archive.projects.isEmpty() || archive.projects.size > 100 ||
            archive.projects.map { it.id }.distinct().size != archive.projects.size)
            return ArchiveResult.Error("تعداد پروژه‌ها یا شناسه‌ها نامعتبر است")
        val issues = archive.projects.flatMap { Validator.check(it, requireNodes = false) }
        return if (issues.isEmpty()) ArchiveResult.Ok(archive.projects)
        else ArchiveResult.Error(issues.first().message)
    }

    private fun boundedDepth(text: String): Boolean {
        var depth = 0
        var inside = false
        var escape = false
        for (c in text) {
            if (inside) {
                if (escape) escape = false
                else if (c == '\\') escape = true
                else if (c == '"') inside = false
            } else when (c) {
                '"' -> inside = true
                '{', '[' -> { depth++; if (depth > MAX_DEPTH) return false }
                '}', ']' -> { depth--; if (depth < 0) return false }
            }
        }
        return depth == 0 && !inside
    }
}
