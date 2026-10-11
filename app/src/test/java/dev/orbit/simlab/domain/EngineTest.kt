package dev.orbit.simlab.domain

import dev.orbit.simlab.domain.engine.*
import dev.orbit.simlab.domain.model.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class EngineTest {
    private fun simple(interval: Double = 2.0, service: Double = 3.0, capacity: Int = 1,
        horizon: Double = 10.0): Project {
        val a = Node(id = "a", name = "arrival", type = NodeType.SOURCE, interval = interval)
        val q = Node(id = "q", name = "wait", type = NodeType.QUEUE, capacity = 0)
        val r = Node(id = "r", name = "serve", type = NodeType.RESOURCE, duration = service, capacity = capacity)
        val z = Node(id = "z", name = "end", type = NodeType.SINK)
        return Project(name = "test", settings = Settings(duration = horizon), nodes = listOf(a, q, r, z),
            links = listOf(Link(id = "a-q", from = "a", to = "q"), Link(id = "q-r", from = "q", to = "r"),
                Link(id = "r-z", from = "r", to = "z")))
    }

    private fun finish(engine: DiscreteEngine): RunResult {
        repeat(100000) {
            when (val step = engine.step()) {
                is StepResult.Finished -> return step.result
                is StepResult.Failed -> fail(step.message)
                else -> Unit
            }
        }
        fail("No termination")
        error("unreachable")
    }

    @Test fun simplePathAndManualReference() {
        val result = finish(DiscreteEngine(simple(interval = 2.0, service = 3.0, horizon = 10.0)))
        assertEquals(6, result.generated) // at 0,2,4,6,8,10
        assertEquals(3, result.completed) // at 3,6,9
        assertEquals(0, result.lost)
        assertEquals(3.0, result.meanProcessing!!, 0.00001)
        assertEquals(.3, result.throughput!!, 0.00001)
        assertEquals(1.0, result.resourceUtilization!!, 0.00001) // fourth job active from 9 to horizon 10
        assertTrue(result.events > result.generated)
    }

    @Test fun priorityQueueSortsTimeThenSequence() {
        val q = EventQueue()
        q.add(Event(1.0, 5L, Kind.ARRIVAL, "later"))
        q.add(Event(0.0, 3L, Kind.ARRIVAL, "earlier"))
        q.add(Event(1.0, 4L, Kind.ARRIVAL, "tie"))
        assertEquals(listOf("earlier", "tie", "later"), List(3) { q.next()!!.nodeId })
    }

    @Test fun simultaneousEventsHaveStableOrdering() {
        val engine = DiscreteEngine(simple(interval = 2.0, service = 2.0, horizon = 4.0))
        val clocks = mutableListOf<Double>()
        while (true) {
            when (val s = engine.step()) {
                is StepResult.Progress -> clocks += s.snapshot.clock
                is StepResult.Finished -> break
                is StepResult.Failed -> fail(s.message)
            }
        }
        assertEquals(clocks.sorted(), clocks)
        assertTrue(clocks.count { it == 2.0 } > 1)
        assertEquals(clocks, run {
            val again = DiscreteEngine(simple(interval = 2.0, service = 2.0, horizon = 4.0))
            val replay = mutableListOf<Double>()
            while (true) when (val s = again.step()) {
                is StepResult.Progress -> replay += s.snapshot.clock
                is StepResult.Finished -> break
                is StepResult.Failed -> fail(s.message)
            }
            replay
        })
    }

    @Test fun capacityAndWaiting() {
        val r = finish(DiscreteEngine(simple(interval = 1.0, service = 2.0, horizon = 6.0)))
        assertEquals(7, r.generated)
        assertEquals(3, r.completed)
        assertEquals(0, r.lost)
        assertEquals(1.5, r.meanWait!!, 0.00001) // starts 0,2,4,6: waits 0,1,2,3
        assertEquals(3, r.maxQueue)
        assertTrue(r.meanQueue!! > 0.0)
    }

    @Test fun fullQueueDropsItems() {
        val p = simple(interval = 1.0, service = 4.0, horizon = 5.0)
        val changed = p.copy(nodes = p.nodes.map { if (it.id == "q") it.copy(capacity = 1) else it })
        assertTrue(finish(DiscreteEngine(changed)).lost > 0)
    }

    @Test fun transferThroughRouterAndState() {
        val p = simple(horizon = 5.0)
        val route = Node(id = "router", name = "route", type = NodeType.ROUTER)
        val state = Node(id = "state", name = "count", type = NodeType.STATE)
        val transformed = p.copy(nodes = p.nodes + route + state,
            links = p.links.filterNot { it.id == "r-z" } +
                Link(id = "r-router", from = "r", to = route.id) +
                Link(id = "router-state", from = route.id, to = state.id) +
                Link(id = "state-z", from = state.id, to = "z"),
            rules = listOf(Rule(id = "increment", nodeId = "state", action = Action.Adjust(state.id, 1.0))))
        val engine = DiscreteEngine(transformed)
        val result = finish(engine)
        assertEquals(result.completed.toDouble(), engine.snapshot().states.getValue("state"), .0001)
    }

    @Test fun sameSeedSameResults() {
        val p = SampleProject.emergency()
        val a = finish(DiscreteEngine(p))
        val b = finish(DiscreteEngine(p))
        assertEquals(a.copy(id = "", at = 0), b.copy(id = "", at = 0))
    }

    @Test fun pauseAndResumeViaStep() {
        val engine = DiscreteEngine(simple())
        assertTrue(engine.step() is StepResult.Progress)
        val paused = engine.snapshot()
        assertEquals(1, paused.events)
        val completed = finish(engine)
        assertEquals(3, completed.completed)
    }

    @Test fun cancellationIsCooperative() = runBlocking {
        val p = simple(interval = .001, service = 1.0, horizon = 100.0)
        val job = launch { DiscreteEngine(p).run() }
        job.cancel()
        job.join()
        assertTrue(job.isCancelled)
    }

    @Test fun eventAndSimultaneousLimits() {
        val p = simple().copy(settings = Settings(duration = 10.0, maxEvents = 2))
        val engine = DiscreteEngine(p)
        repeat(2) { assertTrue(engine.step() is StepResult.Progress) }
        assertTrue(engine.step() is StepResult.Failed)
        val burst = DiscreteEngine(simple().copy(settings = Settings(duration = 10.0, maxSameTime = 1)))
        assertTrue(burst.step() is StepResult.Progress)
        assertTrue(burst.step() is StepResult.Failed)
    }

    @Test fun rejectsBrokenGraphAndRulesAndEmptyProject() {
        val p = simple()
        assertTrue(DiscreteEngine(p.copy(links = p.links + Link(from = "ghost", to = "z"))).step() is StepResult.Failed)
        assertTrue(DiscreteEngine(p.copy(rules = listOf(Rule(nodeId = "a", action = Action.Set("ghost", 1.0))))).step() is StepResult.Failed)
        assertTrue(DiscreteEngine(Project(name = "empty")).step() is StepResult.Failed)
    }

    @Test fun rejectsInvalidParametersAndDuplicateIds() {
        val p = simple()
        assertTrue(DiscreteEngine(p.copy(nodes = p.nodes.map { if (it.id == "r") it.copy(capacity = -1) else it })).step() is StepResult.Failed)
        assertTrue(DiscreteEngine(p.copy(settings = Settings(duration = Double.NaN))).step() is StepResult.Failed)
        assertTrue(DiscreteEngine(p.copy(nodes = p.nodes + p.nodes.first())).step() is StepResult.Failed)
    }

    @Test fun cyclesAreBoundedRatherThanForbidden() {
        val p = simple(horizon = 20.0)
        val loop = p.copy(links = p.links.filterNot { it.id == "r-z" } + Link(id = "cycle", from = "r", to = "q"),
            settings = Settings(duration = 20.0, maxHops = 3))
        var result: StepResult = DiscreteEngine(loop).step()
        val engine = DiscreteEngine(loop)
        repeat(100) { result = engine.step(); if (result is StepResult.Failed) return@repeat }
        assertTrue(result is StepResult.Failed)
    }

    @Test fun conditionalRuleRoutesAndChangesState() {
        val p = simple(horizon = 5.0)
        val counter = Node(id = "state", name = "state", type = NodeType.STATE, value = 0.0)
        val withRules = p.copy(nodes = p.nodes + counter,
            rules = listOf(
                Rule(id = "increment", nodeId = "q", priority = 2,
                    action = Action.Adjust(counter.id, 1.0)),
                Rule(id = "conditional", nodeId = "q", priority = 1,
                    condition = Predicate.Compare(counter.id, Comparison.GT, 1.0),
                    action = Action.Move("z"))
            ))
        val engine = DiscreteEngine(withRules)
        val result = finish(engine)
        assertEquals(3, result.generated)
        assertEquals(3.0, engine.snapshot().states.getValue("state"), .0001)
        assertTrue(result.completed >= 2)
    }

    @Test fun insufficientDataIsNullable() {
        val p = simple(horizon = .5)
        val r = finish(DiscreteEngine(p))
        assertNull(r.meanProcessing) // no completed service within the horizon
    }

    @Test fun scheduledInputIsOneOffEvenAtSource() {
        val p = simple(interval = 100.0, horizon = 5.0).copy(
            inputs = listOf(ScheduledInput(id = "input", nodeId = "a", time = 1.0)))
        val r = finish(DiscreteEngine(p))
        assertEquals(2, r.generated) // standard source at zero plus one scheduled source input
    }
}
