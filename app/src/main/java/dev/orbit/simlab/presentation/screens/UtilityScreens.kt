package dev.orbit.simlab.presentation.screens

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import dev.orbit.simlab.presentation.OrbitViewModel

@Composable
fun BackupsScreen(vm: OrbitViewModel, onExport: (ByteArray) -> Unit, onImport: () -> Unit, onBack: () -> Unit) {
    val projects by vm.projects.collectAsState()
    Scaffold(topBar = { AppBar("پشتیبان و فایل‌ها", onBack) }) { padding ->
        Column(Modifier.padding(padding).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("فایل‌های JSON با انتخاب‌گر فایل اندروید ذخیره و باز می‌شوند. نسخهٔ پشتیبان شامل همهٔ پروژه‌های فعلی است.")
            Button(onClick = { vm.exportBackup(true, onExport) }, enabled = projects.isNotEmpty()) { Text("پشتیبان همهٔ پروژه‌ها") }
            OutlinedButton(onClick = onImport) { Text("واردکردن / بازیابی") }
            Text("هنگام ورود، پروژه‌ها با شناسهٔ جدید ساخته می‌شوند؛ هیچ پروژهٔ موجودی بازنویسی نمی‌شود.")
        }
    }
}

@Composable
fun SettingsScreen(onBack: () -> Unit) {
    Scaffold(topBar = { AppBar("تنظیمات", onBack) }) { pad ->
        Column(Modifier.padding(pad).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("ORBIT Simulation Lab • نسخهٔ ۰.۱.۰", style = MaterialTheme.typography.titleMedium)
            Text("موتور آفلاین است و به حساب کاربری یا اینترنت نیاز ندارد.")
            Text("اتصال AI نصب نشده و غیرفعال است.")
            Text("پوستهٔ روشن یا تیره مطابق تنظیمات دستگاه انتخاب می‌شود.")
        }
    }
}
