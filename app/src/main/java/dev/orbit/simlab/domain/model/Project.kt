package dev.orbit.simlab.domain.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import java.util.UUID

@Serializable
enum class NodeType { SOURCE, QUEUE, PROCESSOR, RESOURCE, ROUTER, SINK, STATE }

@Serializable
data class Node(
    val id: String = UUID.randomUUID().toString(), val name: String, val type: NodeType,
    val x: Float = 0f, val y: Float = 0f, val interval: Double = 1.0,
    val duration: Double = 1.0, val capacity: Int = 1, val value: Double = 0.0
)

@Serializable
data class Link(val id: String = UUID.randomUUID().toString(), val from: String, val to: String, val weight: Double = 1.0)

@Serializable
data class Settings(val duration: Double = 30.0, val seed: Long = 42,
    val maxEvents: Int = 100_000, val maxSameTime: Int = 10_000, val maxHops: Int = 100)

@Serializable
sealed class Predicate {
    @Serializable @SerialName("always") data object Always : Predicate()
    @Serializable @SerialName("compare") data class Compare(val stateId: String, val operator: Comparison, val number: Double) : Predicate()
    @Serializable @SerialName("and") data class And(val left: Predicate, val right: Predicate) : Predicate()
    @Serializable @SerialName("or") data class Or(val left: Predicate, val right: Predicate) : Predicate()
    @Serializable @SerialName("not") data class Not(val operand: Predicate) : Predicate()
}

@Serializable
enum class Comparison { LT, LTE, EQ, GTE, GT, NE }

@Serializable
sealed class Action {
    @Serializable @SerialName("adjust") data class Adjust(val stateId: String, val amount: Double) : Action()
    @Serializable @SerialName("set") data class Set(val stateId: String, val value: Double) : Action()
    @Serializable @SerialName("move") data class Move(val destinationId: String) : Action()
    @Serializable @SerialName("schedule") data class Schedule(val destinationId: String, val delay: Double) : Action()
    @Serializable @SerialName("allocate") data class Allocate(val resourceId: String) : Action()
    @Serializable @SerialName("release") data class Release(val resourceId: String) : Action()
}

@Serializable
data class Rule(val id: String = UUID.randomUUID().toString(), val nodeId: String,
    val priority: Int = 0, val enabled: Boolean = true,
    val condition: Predicate = Predicate.Always, val action: Action)

@Serializable
data class ScheduledInput(val id: String = UUID.randomUUID().toString(), val nodeId: String, val time: Double)

@Serializable
data class RunResult(val id: String = UUID.randomUUID().toString(), val at: Long = System.currentTimeMillis(),
    val clock: Double, val generated: Int, val completed: Int, val lost: Int,
    val meanQueue: Double?, val maxQueue: Int, val meanWait: Double?,
    val meanProcessing: Double?, val resourceUtilization: Double?, val throughput: Double?,
    val events: Int, val status: String)

@Serializable
data class Project(val id: String = UUID.randomUUID().toString(), val name: String,
    val description: String = "", val settings: Settings = Settings(),
    val nodes: List<Node> = emptyList(), val links: List<Link> = emptyList(),
    val rules: List<Rule> = emptyList(), val inputs: List<ScheduledInput> = emptyList(),
    val history: List<RunResult> = emptyList())

object SampleProject {
    fun emergency(): Project {
        val source = Node(name = "ورود بیماران (آزمایشی)", type = NodeType.SOURCE, x = 40f, y = 190f, interval = 2.0)
        val queue = Node(name = "صف انتظار", type = NodeType.QUEUE, x = 250f, y = 190f, capacity = 0)
        val a = Node(name = "تیم رسیدگی ۱", type = NodeType.RESOURCE, x = 460f, y = 70f, duration = 3.0)
        val b = Node(name = "تیم رسیدگی ۲", type = NodeType.RESOURCE, x = 460f, y = 310f, duration = 3.0)
        val sink = Node(name = "پایان رسیدگی", type = NodeType.SINK, x = 700f, y = 190f)
        return Project(name = "شبیه‌ساز صف اورژانس",
            description = "داده‌ها ساختگی‌اند؛ برای آموزش و آزمون موتور، نه تصمیم پزشکی.",
            nodes = listOf(source, queue, a, b, sink),
            links = listOf(Link(from = source.id, to = queue.id), Link(from = queue.id, to = a.id),
                Link(from = queue.id, to = b.id), Link(from = a.id, to = sink.id), Link(from = b.id, to = sink.id)))
    }
}
