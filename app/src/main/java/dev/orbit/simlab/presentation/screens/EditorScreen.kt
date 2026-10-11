package dev.orbit.simlab.presentation.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import dev.orbit.simlab.domain.model.*
import dev.orbit.simlab.domain.validation.Validator
import dev.orbit.simlab.presentation.OrbitViewModel
import kotlin.math.roundToInt

@Composable
fun EditorScreen(vm: OrbitViewModel, onBack: () -> Unit, onRun: () -> Unit) {
    val project by vm.current.collectAsState()
    val p = project ?: return
    var selected by remember(p.id) { mutableStateOf<String?>(null) }
    var connectFrom by remember(p.id) { mutableStateOf<String?>(null) }
    val node = p.nodes.firstOrNull { it.id == selected }
    val issues = remember(p) { Validator.check(p) }
    Scaffold(topBar = { AppBar("ویرایشگر • ${p.name}", onBack) }) { pad ->
        Column(Modifier.fillMaxSize().padding(pad)) {
            Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(8.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                NodeType.entries.forEach { type ->
                    AssistChip(onClick = { vm.addNode(type) }, label = { Text("+ ${type.name}") })
                }
            }
            Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(onClick = vm::undo) { Text("برگشت") }
                TextButton(onClick = vm::redo) { Text("تکرار") }
                TextButton(onClick = { vm.validate() }) { Text("بررسی نمودار") }
                TextButton(onClick = { if (vm.validate()) { vm.reset(); onRun() } }) { Text("اجرا") }
                TextButton(onClick = { connectFrom = selected }, enabled = selected != null) { Text("ارتباط از گره انتخابی") }
            }
            if (connectFrom != null) Text("گره مقصد را لمس کنید؛ مبدأ: ${p.nodes.find { it.id == connectFrom }?.name}",
                Modifier.padding(horizontal = 12.dp), color = MaterialTheme.colorScheme.secondary)
            if (issues.isNotEmpty()) Text(issues.take(2).joinToString("؛ ") { it.message },
                Modifier.padding(horizontal = 12.dp), color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodySmall)
            GraphCanvas(p, selected, issues.mapNotNull { it.elementId }.toSet(), onSelect = { id ->
                val from = connectFrom
                if (from != null) { vm.link(from, id); connectFrom = null } else selected = id
            }, onMove = { id, dx, dy -> vm.updateNode(id, { it.copy(x = it.x + dx, y = it.y + dy) }, false) },
                modifier = Modifier.weight(1f).fillMaxWidth())
            if (node != null) NodePanel(node, p, vm, onDelete = { vm.deleteNode(node.id); selected = null })
            else Text("گره‌ای را انتخاب کنید؛ با دو انگشت بوم را بزرگ‌نمایی یا جابه‌جا کنید.",
                Modifier.padding(12.dp), style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
private fun GraphCanvas(p: Project, selected: String?, invalid: Set<String>, onSelect: (String) -> Unit,
    onMove: (String, Float, Float) -> Unit, modifier: Modifier = Modifier) {
    var zoom by remember(p.id) { mutableFloatStateOf(1f) }
    var pan by remember(p.id) { mutableStateOf(Offset.Zero) }
    val density = LocalDensity.current
    val unit = with(density) { 1.dp.toPx() }
    val nodes = remember(p.nodes) { p.nodes.associateBy { it.id } }
    val line = MaterialTheme.colorScheme.primary.copy(alpha = .6f)
    Box(modifier.background(MaterialTheme.colorScheme.background)) {
        Canvas(Modifier.matchParentSize().pointerInput(p.id) {
            detectTransformGestures { _, movement, scale, _ ->
                pan += movement
                zoom = (zoom * scale).coerceIn(.4f, 2.5f)
            }
        }) {
            val grid = 40f * unit * zoom
            if (grid > 14f) {
                val gridColor = line.copy(alpha = .08f)
                var x = pan.x % grid
                while (x < size.width) { drawLine(gridColor, Offset(x, 0f), Offset(x, size.height)); x += grid }
                var y = pan.y % grid
                while (y < size.height) { drawLine(gridColor, Offset(0f, y), Offset(size.width, y)); y += grid }
            }
            p.links.forEach { edge ->
                val a = nodes[edge.from] ?: return@forEach
                val b = nodes[edge.to] ?: return@forEach
                val start = Offset((a.x + 140) * unit * zoom, (a.y + 35) * unit * zoom) + pan
                val end = Offset(b.x * unit * zoom, (b.y + 35) * unit * zoom) + pan
                drawLine(if (edge.id in invalid) Color.Red else line, start, end, strokeWidth = 2.5f)
                drawCircle(line, radius = 3f, center = end)
            }
        }
        p.nodes.forEach { node ->
            Surface(
                modifier = Modifier.offset { IntOffset((node.x * unit * zoom + pan.x).roundToInt(),
                    (node.y * unit * zoom + pan.y).roundToInt()) }
                    .size(140.dp, 70.dp)
                    .graphicsLayer { scaleX = zoom; scaleY = zoom; transformOrigin = androidx.compose.ui.graphics.TransformOrigin(0f, 0f) }
                    .pointerInput(node.id, zoom) {
                        detectDragGestures { change, drag ->
                            change.consume()
                            onMove(node.id, drag.x / (unit * zoom), drag.y / (unit * zoom))
                        }
                    }
                    .clickable { onSelect(node.id) }
                    .border(2.dp, when {
                        node.id in invalid -> MaterialTheme.colorScheme.error
                        node.id == selected -> MaterialTheme.colorScheme.secondary
                        else -> MaterialTheme.colorScheme.primary
                    }, RoundedCornerShape(12.dp)),
                shape = RoundedCornerShape(12.dp), tonalElevation = 4.dp
            ) {
                Column(Modifier.padding(8.dp)) {
                    Text(node.name, maxLines = 1, overflow = TextOverflow.Ellipsis,
                        style = MaterialTheme.typography.labelLarge)
                    Text("${node.type} • ${if (node.type == NodeType.STATE) node.value else node.capacity}",
                        style = MaterialTheme.typography.labelSmall)
                }
            }
        }
    }
}

@Composable
private fun NodePanel(n: Node, p: Project, vm: OrbitViewModel, onDelete: () -> Unit) {
    var showRules by remember(n.id) { mutableStateOf(false) }
    Column(Modifier.fillMaxWidth().heightIn(max = 330.dp).verticalScroll(rememberScrollState()).padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text("${n.type} • ${n.name}", style = MaterialTheme.typography.titleSmall)
        OutlinedTextField(n.name, onValueChange = { name -> vm.updateNode(n.id, { it.copy(name = name) }) },
            label = { Text("نام گره") }, singleLine = true, modifier = Modifier.width(250.dp))
        when (n.type) {
            NodeType.SOURCE -> NumericSetting("فاصلهٔ ورود", n.interval.toString()) { value ->
                vm.updateNode(n.id, { it.copy(interval = value) })
            }
            NodeType.PROCESSOR, NodeType.RESOURCE -> {
                NumericSetting("مدت پردازش", n.duration.toString()) { value -> vm.updateNode(n.id, { it.copy(duration = value) }) }
                NumericSetting("ظرفیت", n.capacity.toString()) { value -> vm.updateNode(n.id, { it.copy(capacity = value.toInt()) }) }
            }
            NodeType.QUEUE -> NumericSetting("ظرفیت صف (۰: نامحدود)", n.capacity.toString()) { value ->
                vm.updateNode(n.id, { it.copy(capacity = value.toInt()) })
            }
            NodeType.STATE -> NumericSetting("مقدار اولیه", n.value.toString()) { value ->
                vm.updateNode(n.id, { it.copy(value = value) })
            }
            else -> Unit
        }
        Row {
            TextButton(onClick = { vm.duplicateNode(n.id) }) { Text("تکثیر") }
            TextButton(onClick = onDelete) { Text("حذف گره") }
            TextButton(onClick = { showRules = true }) { Text("قوانین") }
        }
        p.links.filter { it.from == n.id || it.to == n.id }.forEach { link ->
            TextButton(onClick = { vm.deleteLink(link.id) }) {
                Text("حذف ارتباط ${p.nodes.find { it.id == link.from }?.name} ← ${p.nodes.find { it.id == link.to }?.name}")
            }
        }
    }
    if (showRules) RulesDialog(n, p, vm, onDismiss = { showRules = false })
}

@Composable
private fun RulesDialog(node: Node, project: Project, vm: OrbitViewModel, onDismiss: () -> Unit) {
    var action by remember { mutableStateOf("انتقال") }
    var target by remember { mutableStateOf<String?>(null) }
    var state by remember { mutableStateOf<String?>(null) }
    var threshold by remember { mutableStateOf("0") }
    var amount by remember { mutableStateOf("1") }
    var priority by remember { mutableStateOf("0") }
    var condition by remember { mutableStateOf(false) }
    var op by remember { mutableStateOf(Comparison.GTE) }
    val options = listOf("انتقال", "زمان‌بندی", "افزایش متغیر", "تعیین متغیر", "تخصیص منبع", "آزادسازی منبع")
    val targets = when (action) {
        "انتقال", "زمان‌بندی" -> project.nodes.filter { it.type != NodeType.SOURCE }
        "افزایش متغیر", "تعیین متغیر" -> project.nodes.filter { it.type == NodeType.STATE }
        else -> project.nodes.filter { it.type == NodeType.RESOURCE }
    }
    AlertDialog(onDismissRequest = onDismiss, title = { Text("قوانین ${node.name}") }, text = {
        Column(Modifier.heightIn(max = 440.dp).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(6.dp)) {
            project.rules.filter { it.nodeId == node.id }.forEach { rule ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("اولویت ${rule.priority}: ${rule.action.javaClass.simpleName}", Modifier.weight(1f))
                    TextButton(onClick = { vm.deleteRule(rule.id) }) { Text("حذف") }
                }
            }
            Text("قانون جدید • اولین مسیر منطبق اجرا می‌شود")
            OptionPicker("عملیات", options, action, { action = it; target = null })
            OptionPicker("هدف", targets.map { it.id }, target.orEmpty(),
                { target = it }, { id -> targets.find { it.id == id }?.name ?: "هدف" })
            OutlinedTextField(amount, { amount = it }, label = { Text("مقدار / تأخیر") }, singleLine = true)
            OutlinedTextField(priority, { priority = it }, label = { Text("اولویت") }, singleLine = true)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Checkbox(condition, onCheckedChange = { condition = it }); Text("شرط مقایسهٔ متغیر")
            }
            if (condition) {
                val states = project.nodes.filter { it.type == NodeType.STATE }
                OptionPicker("متغیر شرط", states.map { it.id }, state.orEmpty(), { state = it },
                    { id -> states.find { it.id == id }?.name ?: "متغیر" })
                OptionPicker("عملگر", Comparison.entries.map { it.name }, op.name, { op = Comparison.valueOf(it) })
                OutlinedTextField(threshold, { threshold = it }, label = { Text("آستانه") })
            }
        }
    }, confirmButton = {
        TextButton(onClick = {
            val id = target ?: return@TextButton
            val value = amount.toDoubleOrNull() ?: return@TextButton
            val p = priority.toIntOrNull() ?: return@TextButton
            val pred = if (condition) Predicate.Compare(state ?: return@TextButton, op,
                threshold.toDoubleOrNull() ?: return@TextButton) else Predicate.Always
            val operation = when (action) {
                "انتقال" -> Action.Move(id)
                "زمان‌بندی" -> Action.Schedule(id, value)
                "افزایش متغیر" -> Action.Adjust(id, value)
                "تعیین متغیر" -> Action.Set(id, value)
                "تخصیص منبع" -> Action.Allocate(id)
                else -> Action.Release(id)
            }
            vm.addRule(Rule(nodeId = node.id, priority = p, condition = pred, action = operation))
            onDismiss()
        }) { Text("افزودن") }
    }, dismissButton = { TextButton(onClick = onDismiss) { Text("بستن") } })
}

@Composable
private fun OptionPicker(label: String, options: List<String>, selected: String,
    onSelect: (String) -> Unit, display: (String) -> String = { it }) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        OutlinedButton(onClick = { expanded = true }) { Text("$label: ${display(selected)}") }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            options.forEach { option -> DropdownMenuItem(text = { Text(display(option)) }, onClick = {
                onSelect(option); expanded = false
            }) }
        }
    }
}
