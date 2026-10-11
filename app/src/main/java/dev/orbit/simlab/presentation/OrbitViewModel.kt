package dev.orbit.simlab.presentation

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import dev.orbit.simlab.OrbitApplication
import dev.orbit.simlab.data.importexport.ArchiveResult
import dev.orbit.simlab.domain.engine.*
import dev.orbit.simlab.domain.model.*
import dev.orbit.simlab.domain.validation.Validator
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.InputStream
import java.util.UUID

class OrbitViewModel(app: Application) : AndroidViewModel(app) {
    private val repo = (app as OrbitApplication).container.projects
    private val codec = (app as OrbitApplication).container.archives
    private val _projects = MutableStateFlow<List<Project>>(emptyList())
    val projects: StateFlow<List<Project>> = _projects.asStateFlow()
    private val _current = MutableStateFlow<Project?>(null)
    val current: StateFlow<Project?> = _current.asStateFlow()
    private val _snapshot = MutableStateFlow<Snapshot?>(null)
    val snapshot: StateFlow<Snapshot?> = _snapshot.asStateFlow()
    private val _result = MutableStateFlow<RunResult?>(null)
    val result: StateFlow<RunResult?> = _result.asStateFlow()
    private val _notice = MutableStateFlow<String?>(null)
    val notice: StateFlow<String?> = _notice.asStateFlow()
    private val _running = MutableStateFlow(false)
    val running: StateFlow<Boolean> = _running.asStateFlow()
    private var engine: DiscreteEngine? = null
    private var runJob: Job? = null
    private var saveJob: Job? = null
    private var pausedByUser = false
    private val undo = ArrayDeque<Project>()
    private val redo = ArrayDeque<Project>()

    init {
        viewModelScope.launch {
            try {
                if (withContext(Dispatchers.IO) { repo.count() } == 0) {
                    withContext(Dispatchers.IO) { repo.save(SampleProject.emergency()) }
                }
                repo.observe().collect { _projects.value = it }
            } catch (e: Exception) { _notice.value = "خواندن پروژه‌ها ناموفق بود: ${e.localizedMessage}" }
        }
    }

    fun dismiss() { _notice.value = null }

    fun open(id: String, after: () -> Unit) {
        viewModelScope.launch {
            saveJob?.join()
            try {
                val p = withContext(Dispatchers.IO) { repo.get(id) }
                if (p == null) { _notice.value = "پروژه پیدا نشد"; return@launch }
                runJob?.cancel(); engine = null; _snapshot.value = null; _result.value = p.history.lastOrNull()
                undo.clear(); redo.clear(); _current.value = p; after()
            } catch (e: Exception) { _notice.value = "بارگذاری پروژه ناموفق بود: ${e.localizedMessage}" }
        }
    }

    fun create(name: String, after: () -> Unit) {
        if (name.isBlank()) { _notice.value = "نام پروژه را وارد کنید"; return }
        viewModelScope.launch {
            try {
                saveJob?.join()
                val p = Project(name = name.trim())
                withContext(Dispatchers.IO) { repo.save(p) }
                _current.value = p; _result.value = null; engine = null; undo.clear(); redo.clear(); after()
            } catch (e: Exception) { _notice.value = "ذخیرهٔ پروژه ناموفق بود: ${e.localizedMessage}" }
        }
    }

    fun duplicate(id: String) {
        viewModelScope.launch {
            try {
                val p = withContext(Dispatchers.IO) { repo.get(id) } ?: return@launch
                withContext(Dispatchers.IO) { repo.save(p.copy(id = UUID.randomUUID().toString(), name = "${p.name} (رونوشت)", history = emptyList())) }
            } catch (e: Exception) { _notice.value = "تکثیر پروژه ناموفق بود: ${e.localizedMessage}" }
        }
    }

    fun deleteProject(id: String) {
        viewModelScope.launch {
            try {
                saveJob?.join()
                withContext(Dispatchers.IO) { repo.remove(id) }
                if (_current.value?.id == id) _current.value = null
            } catch (e: Exception) { _notice.value = "حذف پروژه ناموفق بود: ${e.localizedMessage}" }
        }
    }

    fun edit(change: (Project) -> Project) = edit(change, true)

    fun edit(change: (Project) -> Project, recordUndo: Boolean) {
        val before = _current.value ?: return
        val after = change(before)
        if (after == before) return
        if (_running.value) pause()
        if (recordUndo) { undo.addLast(before); if (undo.size > 50) undo.removeFirst(); redo.clear() }
        _current.value = after
        engine = null; _snapshot.value = null; _result.value = null
        scheduleSave()
    }

    fun undo() {
        val previous = undo.removeLastOrNull() ?: return
        _current.value?.let { redo.addLast(it) }
        _current.value = previous; engine = null; _result.value = null; scheduleSave()
    }

    fun redo() {
        val next = redo.removeLastOrNull() ?: return
        _current.value?.let { undo.addLast(it) }
        _current.value = next; engine = null; _result.value = null; scheduleSave()
    }

    private fun scheduleSave() {
        saveJob?.cancel()
        val projectToSave = _current.value ?: return
        saveJob = viewModelScope.launch {
            kotlinx.coroutines.delay(400)
            try { withContext(Dispatchers.IO) { repo.save(projectToSave) } }
            catch (e: Exception) { _notice.value = "ذخیرهٔ خودکار ناموفق بود: ${e.localizedMessage}" }
        }
    }

    fun addNode(type: NodeType) = edit { p ->
        p.copy(nodes = p.nodes + Node(name = type.name, type = type,
            x = 100f + (p.nodes.size % 5) * 90, y = 90f + (p.nodes.size % 7) * 75))
    }

    fun updateNode(id: String, change: (Node) -> Node, recordUndo: Boolean = true) =
        edit({ p -> p.copy(nodes = p.nodes.map { if (it.id == id) change(it) else it }) }, recordUndo)

    fun deleteNode(id: String) = edit { p -> p.copy(nodes = p.nodes.filterNot { it.id == id },
        links = p.links.filterNot { it.from == id || it.to == id }, rules = p.rules.filterNot { it.nodeId == id }) }

    fun duplicateNode(id: String) = edit { p ->
        val node = p.nodes.firstOrNull { it.id == id } ?: return@edit p
        p.copy(nodes = p.nodes + node.copy(id = UUID.randomUUID().toString(), name = "${node.name} (رونوشت)", x = node.x + 30, y = node.y + 30))
    }

    fun link(from: String, to: String) {
        val p = _current.value ?: return
        if (from == to || p.links.any { it.from == from && it.to == to } ||
            p.nodes.none { it.id == to && it.type != NodeType.SOURCE } ||
            p.nodes.none { it.id == from && it.type != NodeType.SINK }) {
            _notice.value = "این ارتباط مجاز نیست یا قبلاً وجود دارد"; return
        }
        edit { it.copy(links = it.links + Link(from = from, to = to)) }
    }

    fun deleteLink(id: String) = edit { it.copy(links = it.links.filterNot { link -> link.id == id }) }
    fun addRule(rule: Rule) = edit { it.copy(rules = it.rules + rule) }
    fun deleteRule(id: String) = edit { it.copy(rules = it.rules.filterNot { rule -> rule.id == id }) }

    fun validate(): Boolean {
        val issues = _current.value?.let { Validator.check(it) }.orEmpty()
        if (issues.isNotEmpty()) _notice.value = issues.take(3).joinToString("؛ ") { it.message }
        return issues.isEmpty()
    }

    private fun prepare(): DiscreteEngine? {
        val p = _current.value ?: return null
        if (!validate()) return null
        return engine ?: DiscreteEngine(p).also { engine = it; _snapshot.value = it.snapshot() }
    }

    fun step() {
        if (_running.value) return
        val e = prepare() ?: return
        consume(e.step())
    }

    fun run() {
        if (_running.value) return
        val e = prepare() ?: return
        pausedByUser = false
        _running.value = true
        runJob = viewModelScope.launch {
            try {
                // If the previous run was stopped while Default was still finishing a step,
                // wait for its cancellation before touching the same engine again.
                val previous = priorRun
                previous?.join()
                val outcome = withContext(Dispatchers.Default) {
                    e.run { snap -> _snapshot.value = snap }
                }
                consume(outcome)
            } catch (e: CancellationException) {
                if (!pausedByUser) _notice.value = "اجرای شبیه‌سازی لغو شد"
            }
            finally { _running.value = false }
        }
    }

    private var priorRun: Job? = null
    fun pause() {
        pausedByUser = true
        priorRun = runJob?.also { it.cancel() }
        runJob = null
        _running.value = false
    }
    fun reset() { pause(); engine = null; _snapshot.value = null; _result.value = null }

    private fun consume(step: StepResult) {
        when (step) {
            is StepResult.Progress -> _snapshot.value = step.snapshot
            is StepResult.Failed -> { _snapshot.value = step.snapshot; _notice.value = step.message }
            is StepResult.Finished -> {
                _result.value = step.result
                _current.value = _current.value?.let { p -> p.copy(history = (p.history + step.result).takeLast(100)) }
                scheduleSave()
            }
        }
    }

    fun exportBackup(all: Boolean, onReady: (ByteArray) -> Unit) {
        viewModelScope.launch {
            try {
                saveJob?.join()
                val items = if (all) _projects.value else listOfNotNull(_current.value)
                val bytes = withContext(Dispatchers.Default) { codec.encode(items) }
                onReady(bytes)
            } catch (e: Exception) { _notice.value = "صادرکردن ناموفق بود: ${e.localizedMessage}" }
        }
    }

    fun importBackup(open: () -> InputStream?) {
        viewModelScope.launch {
            try {
                val result: ArchiveResult = withContext(Dispatchers.IO) { open()?.use(codec::decode) ?: ArchiveResult.Error("فایل قابل خواندن نیست") }
                when (result) {
                    is ArchiveResult.Error -> _notice.value = result.message
                    is ArchiveResult.Ok -> {
                        withContext(Dispatchers.IO) {
                            result.projects.forEach { p ->
                                // Copy to new IDs: importing never overwrites an existing project.
                                repo.save(p.copy(id = UUID.randomUUID().toString(), name = "${p.name} (واردشده)"))
                            }
                        }
                        _notice.value = "${result.projects.size} پروژه وارد شد"
                    }
                }
            } catch (e: Exception) { _notice.value = "ورود پروژه ناموفق بود: ${e.localizedMessage}" }
        }
    }
}
