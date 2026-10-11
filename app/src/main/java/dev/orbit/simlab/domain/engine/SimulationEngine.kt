package dev.orbit.simlab.domain.engine

import dev.orbit.simlab.domain.model.*
import dev.orbit.simlab.domain.validation.Validator
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.yield
import kotlin.random.Random

sealed class StepResult {
    data class Progress(val snapshot: Snapshot) : StepResult()
    data class Finished(val result: RunResult) : StepResult()
    data class Failed(val message: String, val snapshot: Snapshot) : StepResult()
}

data class Snapshot(val clock: Double, val events: Int, val generated: Int, val completed: Int,
    val lost: Int, val queues: Map<String, Int>, val busy: Map<String, Int>, val states: Map<String, Double>)

interface SimulationEngine {
    fun step(): StepResult
    fun snapshot(): Snapshot
    suspend fun run(onStep: (Snapshot) -> Unit = {}): StepResult
}

/** Independent from Android and wall clock. One step consumes exactly one priority-queue event. */
class DiscreteEngine(private val project: Project) : SimulationEngine {
    private val errors = Validator.check(project)
    private val nodes = project.nodes.associateBy { it.id }
    private val outgoing = project.links.groupBy { it.from }
    private val inbound = project.links.groupBy { it.to }
    private val rules = project.rules.filter { it.enabled }.groupBy { it.nodeId }
        .mapValues { (_, list) -> list.sortedWith(compareByDescending<Rule> { it.priority }.thenBy { it.id }) }
    private val random = Random(project.settings.seed)
    private val heap = EventQueue()
    private val queues = mutableMapOf<String, ArrayDeque<Pair<Item, Double>>>()
    private val busy = mutableMapOf<String, Int>()
    private val reservations = mutableMapOf<String, MutableSet<Long>>()
    private val states = project.nodes.filter { it.type == NodeType.STATE }.associate { it.id to it.value }.toMutableMap()
    private val queueArea = mutableMapOf<String, Double>()
    private val queuePeak = mutableMapOf<String, Int>()
    private val busyArea = mutableMapOf<String, Double>()
    private var serial = 0L
    private var itemSerial = 0L
    private var clock = 0.0
    private var lastEventTime = Double.NaN
    private var sameTimeCount = 0
    private var events = 0
    private var generated = 0
    private var completed = 0
    private var lost = 0
    private var waitSum = 0.0
    private var served = 0
    private var processSum = 0.0
    private var processed = 0
    private var terminal: StepResult? = null

    init {
        if (errors.isEmpty()) {
            project.nodes.filter { it.type == NodeType.SOURCE }.forEach { schedule(0.0, Kind.SOURCE, it.id) }
            project.inputs.forEach { schedule(it.time, Kind.INPUT, it.nodeId) }
        }
    }

    private fun schedule(at: Double, kind: Kind, nodeId: String, item: Item? = null) {
        if (!at.isFinite() || at < clock) throw EngineFailure("زمان رویداد نامعتبر است")
        if (at <= project.settings.duration) heap.add(Event(at, serial++, kind, nodeId, item))
    }

    override fun snapshot() = Snapshot(clock, events, generated, completed, lost,
        queues.mapValues { it.value.size }, busy.toMap(), states.toMap())

    override fun step(): StepResult {
        terminal?.let { return it }
        if (errors.isNotEmpty()) return fail(errors.joinToString("؛ ") { it.message })
        if (heap.size > project.settings.maxEvents * 2L) return fail("محدودیت حافظهٔ صف رویدادها")
        val next = heap.peek() ?: return StepResult.Finished(result("پایان")).also { terminal = it }
        if (events >= project.settings.maxEvents) return fail("محدودیت تعداد رویدادها")
        if (next.time == lastEventTime && sameTimeCount >= project.settings.maxSameTime)
            return fail("محدودیت رویدادهای هم‌زمان")
        val previous = snapshot()
        return try {
            val e = heap.next()!!
            accumulate(e.time - clock)
            clock = e.time
            if (clock == lastEventTime) sameTimeCount++ else { lastEventTime = clock; sameTimeCount = 1 }
            events++
            when (e.kind) {
                Kind.SOURCE -> source(e)
                Kind.INPUT -> source(e, recurring = false)
                Kind.ARRIVAL -> arrival(e.nodeId, e.item ?: throw EngineFailure("آیتم موجود نیست"))
                Kind.FINISH -> finish(e.nodeId, e.item ?: throw EngineFailure("آیتم موجود نیست"))
            }
            StepResult.Progress(snapshot())
        } catch (e: EngineFailure) { fail(e.message ?: "خطای موتور", previous) }
    }

    private fun fail(message: String, safeSnapshot: Snapshot = snapshot()): StepResult =
        StepResult.Failed(message, safeSnapshot).also { terminal = it }

    override suspend fun run(onStep: (Snapshot) -> Unit): StepResult {
        while (true) {
            currentCoroutineContext().ensureActive()
            when (val s = step()) {
                is StepResult.Progress -> if (s.snapshot.events % 100 == 0) { onStep(s.snapshot); yield() }
                else -> { onStep(snapshot()); return s }
            }
        }
    }

    private fun source(e: Event, recurring: Boolean = true) {
        val n = nodes[e.nodeId] ?: throw EngineFailure("گره موجود نیست")
        val item = Item(++itemSerial, clock)
        generated++
        if (n.type == NodeType.SOURCE) {
            route(n.id, item)
            if (recurring) schedule(clock + n.interval, Kind.SOURCE, n.id)
        } else arrival(n.id, item)
    }

    private fun arrival(id: String, item: Item) {
        val n = nodes[id] ?: throw EngineFailure("گره موجود نیست")
        if (item.hops > project.settings.maxHops) throw EngineFailure("محدودیت تکرار در چرخه")
        var redirect: Pair<String, Double>? = null
        for (rule in rules[id].orEmpty()) {
            if (!matches(rule.condition)) continue
            when (val a = rule.action) {
                is Action.Adjust -> states[a.stateId] = checked((states[a.stateId] ?: 0.0) + a.amount)
                is Action.Set -> states[a.stateId] = a.value
                is Action.Move -> redirect = a.destinationId to 0.0
                is Action.Schedule -> redirect = a.destinationId to a.delay
                is Action.Allocate -> {
                    val held = reservations.getOrPut(a.resourceId) { mutableSetOf() }
                    val occupied = (busy[a.resourceId] ?: 0) + held.size
                    if (occupied >= nodes.getValue(a.resourceId).capacity && item.id !in held) {
                        lost++; releaseAll(item); return
                    }
                    reservations.getValue(a.resourceId).add(item.id)
                }
                is Action.Release -> reservations[a.resourceId]?.remove(item.id)
            }
            if (redirect != null) break // First matching routing rule wins.
        }
        if (redirect != null) {
            val (target, delay) = redirect
            if (target != id) reservations[id]?.remove(item.id)
            schedule(clock + delay, Kind.ARRIVAL, target, item.copy(hops = item.hops + 1))
            return
        }
        when (n.type) {
            NodeType.SOURCE -> throw EngineFailure("انتقال به گره ورودی مجاز نیست")
            NodeType.SINK -> { completed++; releaseAll(item) }
            NodeType.STATE, NodeType.ROUTER -> route(n.id, item)
            NodeType.QUEUE -> {
                val q = queues.getOrPut(id) { ArrayDeque() }
                if (n.capacity > 0 && q.size >= n.capacity) { lost++; releaseAll(item); return }
                q.addLast(item to clock)
                queuePeak[id] = maxOf(queuePeak[id] ?: 0, q.size)
                drain(id)
            }
            NodeType.PROCESSOR, NodeType.RESOURCE -> if (!start(n, item)) { lost++; releaseAll(item) }
        }
    }

    private fun releaseAll(item: Item) { reservations.values.forEach { it.remove(item.id) } }
    private fun checked(number: Double): Double = number.takeIf { it.isFinite() }
        ?: throw EngineFailure("مقدار متغیر از محدودهٔ معتبر خارج شد")

    private fun matches(p: Predicate): Boolean = when (p) {
        Predicate.Always -> true
        is Predicate.Compare -> {
            val v = states[p.stateId] ?: throw EngineFailure("متغیر وضعیت موجود نیست")
            when (p.operator) {
                Comparison.LT -> v < p.number
                Comparison.LTE -> v <= p.number
                Comparison.EQ -> v == p.number
                Comparison.GTE -> v >= p.number
                Comparison.GT -> v > p.number
                Comparison.NE -> v != p.number
            }
        }
        is Predicate.And -> matches(p.left) && matches(p.right)
        is Predicate.Or -> matches(p.left) || matches(p.right)
        is Predicate.Not -> !matches(p.operand)
    }

    private fun route(id: String, item: Item) {
        val links = outgoing[id].orEmpty().sortedBy { it.id }
        if (links.isEmpty()) { lost++; releaseAll(item); return }
        val total = links.sumOf { it.weight }
        val pick = random.nextDouble() * total
        var cumulative = 0.0
        val chosen = links.firstOrNull { link ->
            cumulative += link.weight
            pick < cumulative
        } ?: links.last()
        schedule(clock, Kind.ARRIVAL, chosen.to, item.copy(hops = item.hops + 1))
    }

    private fun start(node: Node, item: Item): Boolean {
        val owned = reservations[node.id]?.contains(item.id) == true
        val occupied = (busy[node.id] ?: 0) + (reservations[node.id]?.size ?: 0)
        if (occupied >= node.capacity && !owned) return false
        if (owned) reservations[node.id]?.remove(item.id)
        busy[node.id] = (busy[node.id] ?: 0) + 1
        schedule(clock + node.duration, Kind.FINISH, node.id, item)
        return true
    }

    private fun drain(queueId: String) {
        val q = queues[queueId] ?: return
        val options = outgoing[queueId].orEmpty().sortedBy { it.id }
        while (q.isNotEmpty()) {
            val link = options.firstOrNull { link ->
                val n = nodes[link.to]
                n != null && n.type in listOf(NodeType.PROCESSOR, NodeType.RESOURCE) &&
                    (busy[n.id] ?: 0) + (reservations[n.id]?.size ?: 0) < n.capacity
            } ?: options.firstOrNull { nodes[it.to]?.type !in listOf(NodeType.PROCESSOR, NodeType.RESOURCE) } ?: break
            val (item, entered) = q.first()
            val target = nodes.getValue(link.to)
            if (target.type in listOf(NodeType.PROCESSOR, NodeType.RESOURCE)) {
                // Reserve the free slot until the arrival event executes, so entry rules
                // still run and simultaneous arrivals cannot steal the same capacity.
                reservations.getOrPut(target.id) { mutableSetOf() }.add(item.id)
                schedule(clock, Kind.ARRIVAL, target.id, item.copy(hops = item.hops + 1))
                q.removeFirst()
            } else {
                q.removeFirst()
                schedule(clock, Kind.ARRIVAL, target.id, item.copy(hops = item.hops + 1))
            }
            served++
            waitSum += clock - entered
        }
    }

    private fun finish(id: String, item: Item) {
        val n = nodes.getValue(id)
        busy[id] = (busy[id] ?: 0) - 1
        processSum += n.duration
        processed++
        reservations[id]?.remove(item.id)
        route(id, item)
        inbound[id].orEmpty().filter { nodes[it.from]?.type == NodeType.QUEUE }.forEach { drain(it.from) }
    }

    private fun accumulate(dt: Double) {
        queues.forEach { (id, q) -> queueArea[id] = (queueArea[id] ?: 0.0) + q.size * dt }
        busy.forEach { (id, count) -> busyArea[id] = (busyArea[id] ?: 0.0) + count * dt }
    }

    private fun result(status: String): RunResult {
        val remainder = (project.settings.duration - clock).coerceAtLeast(0.0)
        val queueNodes = nodes.values.filter { it.type == NodeType.QUEUE }
        val queueTotal = queueNodes.sumOf { (queueArea[it.id] ?: 0.0) +
            (queues[it.id]?.size ?: 0).toDouble() * remainder }
        val resources = nodes.values.filter { it.type == NodeType.RESOURCE }
        val utilization = if (resources.isEmpty()) null else {
            val active = resources.sumOf { (busyArea[it.id] ?: 0.0) +
                (busy[it.id] ?: 0).toDouble() * remainder }
            active / (resources.sumOf { it.capacity }.toDouble() * project.settings.duration)
        }
        return RunResult(clock = clock, generated = generated, completed = completed, lost = lost,
            meanQueue = if (queueNodes.isEmpty()) null else queueTotal / (project.settings.duration * queueNodes.size),
            maxQueue = queuePeak.values.maxOrNull() ?: 0,
            meanWait = if (served == 0) null else waitSum / served,
            meanProcessing = if (processed == 0) null else processSum / processed,
            resourceUtilization = utilization,
            throughput = completed / project.settings.duration, events = events, status = status)
    }
}

private class EngineFailure(message: String) : RuntimeException(message)
