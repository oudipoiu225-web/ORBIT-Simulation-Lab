package dev.orbit.simlab.presentation.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import dev.orbit.simlab.domain.model.Project
import dev.orbit.simlab.presentation.OrbitViewModel

@Composable
fun ProjectsScreen(vm: OrbitViewModel, onOpen: () -> Unit, onBackups: () -> Unit, onSettings: () -> Unit) {
    val projects by vm.projects.collectAsState()
    var creating by remember { mutableStateOf(false) }
    var name by remember { mutableStateOf("") }
    var delete by remember { mutableStateOf<Project?>(null) }
    Scaffold(topBar = { AppBar("ORBIT • پروژه‌ها") }, floatingActionButton = {
        FloatingActionButton(onClick = { creating = true }) {
            Text("+")
        }
    }) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).padding(horizontal = 16.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(onClick = onBackups) { Text("پشتیبان / ورود و خروج") }
                TextButton(onClick = onSettings) { Text("تنظیمات") }
            }
            if (projects.isEmpty()) Text("هنوز پروژه‌ای ذخیره نشده است.", Modifier.padding(24.dp))
            LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp), contentPadding = PaddingValues(bottom = 90.dp)) {
                items(projects, key = { it.id }) { p ->
                    Card(shape = RoundedCornerShape(16.dp), modifier = Modifier.fillMaxWidth().clickable { vm.open(p.id, onOpen) }) {
                        Column(Modifier.padding(16.dp)) {
                            Text(p.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                            Text(p.description.ifBlank { "${p.nodes.size} گره • ${p.links.size} ارتباط" }, style = MaterialTheme.typography.bodySmall)
                            Row {
                                TextButton(onClick = { vm.open(p.id, onOpen) }) { Text("بازکردن") }
                                TextButton(onClick = { vm.duplicate(p.id) }) { Text("تکثیر") }
                                TextButton(onClick = { delete = p }) { Text("حذف") }
                            }
                        }
                    }
                }
            }
        }
    }
    if (creating) AlertDialog(onDismissRequest = { creating = false }, title = { Text("پروژهٔ جدید") },
        text = { OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("نام") }, singleLine = true) },
        confirmButton = { TextButton(onClick = { creating = false; vm.create(name, onOpen); name = "" }) { Text("ساخت") } },
        dismissButton = { TextButton(onClick = { creating = false }) { Text("انصراف") } })
    delete?.let { p ->
        AlertDialog(onDismissRequest = { delete = null }, title = { Text("حذف پروژه") },
            text = { Text("«${p.name}» و تاریخچهٔ آن حذف شوند؟") },
            confirmButton = { TextButton(onClick = { vm.deleteProject(p.id); delete = null }) { Text("حذف") } },
            dismissButton = { TextButton(onClick = { delete = null }) { Text("انصراف") } })
    }
}

@Composable
fun AppBar(title: String, onBack: (() -> Unit)? = null) {
    Surface(color = MaterialTheme.colorScheme.surface) {
        Row(Modifier.fillMaxWidth().heightIn(min = 56.dp).padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            if (onBack != null) TextButton(onClick = onBack) { Text("‹ بازگشت") }
            Text(title, Modifier.padding(top = 15.dp), style = MaterialTheme.typography.titleLarge)
        }
    }
}
