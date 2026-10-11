package dev.orbit.simlab.presentation.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import dev.orbit.simlab.domain.model.RunResult
import dev.orbit.simlab.presentation.OrbitViewModel
import java.util.Locale

@Composable
fun RunScreen(vm: OrbitViewModel, onResults: () -> Unit, onBack: () -> Unit) {
    val p by vm.current.collectAsState()
    val snapshot by vm.snapshot.collectAsState()
    val result by vm.result.collectAsState()
    val running by vm.running.collectAsState()
    Scaffold(topBar = { AppBar("اجرای ${p?.name.orEmpty()}", onBack) }) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("ساعت مجازی: ${snapshot?.clock?.format() ?: "۰"} / ${p?.settings?.duration}")
            Text("رویدادهای پردازش‌شده: ${snapshot?.events ?: 0}")
            Text("تولید: ${snapshot?.generated ?: 0}  •  تکمیل: ${snapshot?.completed ?: 0}  •  ازدست‌رفته: ${snapshot?.lost ?: 0}")
            p?.nodes?.forEach { node ->
                val length = snapshot?.queues?.get(node.id)
                val busy = snapshot?.busy?.get(node.id)
                val state = snapshot?.states?.get(node.id)
                if (length != null || busy != null || state != null)
                    Text("${node.name}: ${length?.let { "صف $it" } ?: busy?.let { "مشغول $it" } ?: "وضعیت $state"}")
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = { vm.step() }, enabled = !running && result == null) { Text("گام بعد") }
                Button(onClick = { if (running) vm.pause() else vm.run() }, enabled = result == null) {
                    Text(if (running) "توقف" else "ادامه / اجرا")
                }
            }
            OutlinedButton(onClick = vm::reset) { Text("شروع از ابتدا") }
            if (result != null) {
                Text("اجرا پایان یافت؛ نتایج از موتور محاسبه و ذخیره شده‌اند.")
                Button(onClick = onResults) { Text("نمایش نتیجه") }
            }
        }
    }
}

@Composable
fun ResultsScreen(vm: OrbitViewModel, onBack: () -> Unit) {
    val result by vm.result.collectAsState()
    Scaffold(topBar = { AppBar("نتایج آزمایش", onBack) }) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)) {
            if (result == null) Text("هنوز آزمایش تکمیل‌شده‌ای وجود ندارد.")
            else MetricPanel(result!!)
        }
    }
}

@Composable
fun HistoryScreen(vm: OrbitViewModel, onBack: () -> Unit) {
    val project by vm.current.collectAsState()
    Scaffold(topBar = { AppBar("تاریخچهٔ اجراها", onBack) }) { padding ->
        val history = project?.history.orEmpty().reversed()
        if (history.isEmpty()) Text("هنوز اجرایی ذخیره نشده است.", Modifier.padding(padding).padding(16.dp))
        LazyColumn(Modifier.padding(padding), contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)) {
            items(history, key = { it.id }) { run -> Card {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("${java.text.DateFormat.getDateTimeInstance().format(java.util.Date(run.at))} • ${run.status}")
                    MetricPanel(run)
                }
            } }
        }
    }
}

private fun Double.format() = String.format(Locale.US, "%.2f", this)

@Composable
private fun MetricPanel(r: RunResult) {
    Metric("تولیدشده", r.generated.toString())
    Metric("تکمیل‌شده", r.completed.toString())
    Metric("ردشده / ازدست‌رفته", r.lost.toString())
    Metric("میانگین طول صف", r.meanQueue?.format())
    Metric("بیشینهٔ طول صف", r.maxQueue.toString())
    Metric("میانگین انتظار", r.meanWait?.format())
    Metric("میانگین پردازش", r.meanProcessing?.format())
    Metric("استفاده از منابع", r.resourceUtilization?.let { "${(it * 100).format()}٪" })
    Metric("نرخ خروجی در واحد زمان", r.throughput?.format())
    Metric("تعداد رویدادها", r.events.toString())
}

@Composable
private fun Metric(label: String, value: String?) {
    Card(Modifier.fillMaxWidth()) { Row(Modifier.padding(12.dp).fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label); Text(value ?: "دادهٔ کافی نیست", color = MaterialTheme.colorScheme.primary)
    } }
}
