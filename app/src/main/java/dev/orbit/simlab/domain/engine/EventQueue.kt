package dev.orbit.simlab.domain.engine

import java.util.PriorityQueue

internal enum class Kind { SOURCE, INPUT, ARRIVAL, FINISH }
internal data class Event(val time: Double, val sequence: Long, val kind: Kind,
    val nodeId: String, val item: Item? = null)
internal data class Item(val id: Long, val created: Double, val hops: Int = 0)

/** FIFO at equal virtual times: insertion sequence is the sole tie breaker. */
internal class EventQueue {
    private val heap = PriorityQueue(compareBy<Event> { it.time }.thenBy { it.sequence })
    val size: Int get() = heap.size
    fun add(event: Event) { heap.add(event) }
    fun next(): Event? = heap.poll()
    fun peek(): Event? = heap.peek()
}
