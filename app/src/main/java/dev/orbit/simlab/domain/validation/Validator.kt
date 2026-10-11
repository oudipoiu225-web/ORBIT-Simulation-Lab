package dev.orbit.simlab.domain.validation

import dev.orbit.simlab.domain.model.*

data class Issue(val elementId: String?, val message: String)

object Validator {
    fun check(project: Project, requireNodes: Boolean = true): List<Issue> {
        val issues = mutableListOf<Issue>()
        val allIds = project.nodes.map { it.id } + project.links.map { it.id } +
            project.rules.map { it.id } + project.inputs.map { it.id }
        if (allIds.size != allIds.toSet().size) issues += Issue(null, "شناسهٔ تکراری در پروژه")
        if (requireNodes && project.nodes.isEmpty()) issues += Issue(null, "پروژه هیچ گرهی ندارد")
        if (project.nodes.size > 2000 || project.links.size > 8000 || project.rules.size > 2000 || project.inputs.size > 2000)
            issues += Issue(null, "تعداد عناصر از محدودیت مجاز بیشتر است")
        val s = project.settings
        if (!s.duration.isFinite() || s.duration <= 0 || s.duration > 1_000_000 ||
            s.maxEvents !in 1..1_000_000 || s.maxSameTime !in 1..100_000 || s.maxHops !in 1..10_000)
            issues += Issue(null, "تنظیمات مدت یا محدودیت اجرا نامعتبر است")
        val nodes = project.nodes.associateBy { it.id }
        project.nodes.forEach { n ->
            val minCapacity = if (n.type in listOf(NodeType.PROCESSOR, NodeType.RESOURCE)) 1 else 0
            if (n.id.isBlank() || n.name.isBlank() || !n.x.isFinite() || !n.y.isFinite() ||
                !n.interval.isFinite() || !n.duration.isFinite() || !n.value.isFinite() || n.capacity < minCapacity)
                issues += Issue(n.id, "پارامتر گره ${n.name} نامعتبر است")
            if (n.type == NodeType.SOURCE && n.interval <= 0) issues += Issue(n.id, "بازهٔ ورود باید مثبت باشد")
            if (n.type in listOf(NodeType.PROCESSOR, NodeType.RESOURCE) && n.duration <= 0)
                issues += Issue(n.id, "مدت پردازش باید مثبت باشد")
        }
        project.links.forEach { link ->
            if (nodes[link.from] == null || nodes[link.to] == null) issues += Issue(link.id, "ارتباط به گره ناموجود")
            if (!link.weight.isFinite() || link.weight <= 0 || link.weight > 1_000_000) issues += Issue(link.id, "وزن ارتباط باید مثبت و محدود باشد")
            if (link.from == link.to) issues += Issue(link.id, "ارتباط گره با خودش مجاز نیست")
            if (nodes[link.to]?.type == NodeType.SOURCE) issues += Issue(link.id, "نمی‌توان به منبع ورود انتقال داد")
            if (nodes[link.from]?.type == NodeType.SINK) issues += Issue(link.id, "خروجی نهایی ارتباط خروجی ندارد")
        }
        project.inputs.forEach { input ->
            if (nodes[input.nodeId] == null || !input.time.isFinite() || input.time < 0)
                issues += Issue(input.id, "رویداد زمان‌بندی‌شده نامعتبر است")
        }
        project.rules.forEach { rule ->
            if (nodes[rule.nodeId] == null || !validPredicate(rule.condition, nodes, 0) || !validAction(rule.action, nodes))
                issues += Issue(rule.id, "شرط یا عملیات قانون ناقص یا ناسازگار است")
        }
        return issues
    }

    private fun validPredicate(p: Predicate, nodes: Map<String, Node>, depth: Int): Boolean {
        if (depth > 16) return false
        return when (p) {
            Predicate.Always -> true
            is Predicate.Compare -> nodes[p.stateId]?.type == NodeType.STATE && p.number.isFinite()
            is Predicate.And -> validPredicate(p.left, nodes, depth + 1) && validPredicate(p.right, nodes, depth + 1)
            is Predicate.Or -> validPredicate(p.left, nodes, depth + 1) && validPredicate(p.right, nodes, depth + 1)
            is Predicate.Not -> validPredicate(p.operand, nodes, depth + 1)
        }
    }

    private fun validAction(a: Action, nodes: Map<String, Node>) = when (a) {
        is Action.Adjust -> nodes[a.stateId]?.type == NodeType.STATE && a.amount.isFinite()
        is Action.Set -> nodes[a.stateId]?.type == NodeType.STATE && a.value.isFinite()
        is Action.Move -> nodes[a.destinationId]?.type !in listOf(null, NodeType.SOURCE)
        is Action.Schedule -> nodes[a.destinationId]?.type !in listOf(null, NodeType.SOURCE) && a.delay.isFinite() && a.delay >= 0
        is Action.Allocate -> nodes[a.resourceId]?.type == NodeType.RESOURCE
        is Action.Release -> nodes[a.resourceId]?.type == NodeType.RESOURCE
    }
}
