package dev.orbit.simlab.presentation.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import dev.orbit.simlab.domain.model.Settings
import dev.orbit.simlab.presentation.OrbitViewModel

@Composable
fun DashboardScreen(vm: OrbitViewModel, onEditor: () -> Unit, onRun: () -> Unit,
    onHistory: () -> Unit, onBack: () -> Unit) {
    val project by vm.current.collectAsState()
    val p = project
    Scaffold(topBar = { AppBar(p?.name ?: "پروژه", onBack) }) { pad ->
        if (p == null) return@Scaffold
        Column(Modifier.fillMaxSize().padding(pad).verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Text(p.description.ifEmpty { "توصیف پروژه را از این بخش ویرایش کنید." })
            OutlinedTextField(p.name, onValueChange = { text -> vm.edit { it.copy(name = text) } },
                label = { Text("نام پروژه") }, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(p.description, onValueChange = { text -> vm.edit { it.copy(description = text) } },
                label = { Text("توضیحات") }, modifier = Modifier.fillMaxWidth())
            Text("${p.nodes.size} گره • ${p.links.size} ارتباط • ${p.rules.size} قانون")
            Text("تنظیمات آزمایش", style = MaterialTheme.typography.titleMedium)
            NumericSetting("مدت شبیه‌سازی", p.settings.duration.toString()) { number ->
                vm.edit { it.copy(settings = it.settings.copy(duration = number)) }
            }
            NumericSetting("بذر تصادفی", p.settings.seed.toString()) { number ->
                vm.edit { it.copy(settings = it.settings.copy(seed = number.toLong())) }
            }
            NumericSetting("حداکثر رویداد", p.settings.maxEvents.toString()) { number ->
                vm.edit { it.copy(settings = it.settings.copy(maxEvents = number.toInt())) }
            }
            Button(onClick = onEditor, modifier = Modifier.fillMaxWidth()) { Text("ویرایش نمودار و قوانین") }
            Button(onClick = { if (vm.validate()) { vm.reset(); onRun() } }, modifier = Modifier.fillMaxWidth()) { Text("اجرای آزمایش") }
            OutlinedButton(onClick = onHistory, modifier = Modifier.fillMaxWidth()) { Text("تاریخچهٔ اجرا") }
        }
    }
}

@Composable
fun NumericSetting(label: String, value: String, update: (Double) -> Unit) {
    var text by remember(label, value) { mutableStateOf(value) }
    Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
        OutlinedTextField(text, onValueChange = { text = it }, label = { Text(label) },
            singleLine = true, modifier = Modifier.weight(1f))
        TextButton(onClick = { text.toDoubleOrNull()?.takeIf(Double::isFinite)?.let(update) },
            enabled = text.toDoubleOrNull()?.isFinite() == true) { Text("ثبت") }
    }
}
