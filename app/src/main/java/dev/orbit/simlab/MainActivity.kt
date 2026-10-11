package dev.orbit.simlab

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import dev.orbit.simlab.presentation.OrbitViewModel
import dev.orbit.simlab.presentation.screens.*
import dev.orbit.simlab.presentation.theme.OrbitTheme

class MainActivity : ComponentActivity() {
    private var pendingExport: ByteArray? = null
    private val createFile = registerForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        if (uri != null) {
            try {
                val output = contentResolver.openOutputStream(uri)
                    ?: throw java.io.IOException("cannot open destination")
                output.use { out -> out.write(pendingExport ?: throw java.io.IOException("missing archive")) }
            } catch (_: java.io.IOException) { exportFailure.value = "نوشتن فایل ناموفق بود" }
        }
        pendingExport = null
    }
    private var importHandler: ((android.net.Uri) -> Unit)? = null
    private val openFile = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) importHandler?.invoke(uri)
    }
    private val exportFailure = mutableStateOf<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            OrbitTheme {
                Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    OrbitApp(onExport = { bytes ->
                        pendingExport = bytes
                        createFile.launch("orbit-${System.currentTimeMillis()}.orbit.json")
                    }, onImport = { handler ->
                        importHandler = handler
                        openFile.launch(arrayOf("application/json", "*/*"))
                    }, failure = exportFailure.value, clearFailure = { exportFailure.value = null })
                }
            }
        }
    }
}

@Composable
private fun OrbitApp(
    onExport: (ByteArray) -> Unit,
    onImport: ((android.net.Uri) -> Unit) -> Unit,
    failure: String?, clearFailure: () -> Unit
) {
    val nav = rememberNavController()
    val vm: OrbitViewModel = viewModel()
    val resolver = androidx.compose.ui.platform.LocalContext.current.contentResolver
    NavHost(navController = nav, startDestination = "projects") {
        composable("projects") { ProjectsScreen(vm, onOpen = { nav.navigate("dashboard") },
            onBackups = { nav.navigate("backups") }, onSettings = { nav.navigate("settings") }) }
        composable("dashboard") { DashboardScreen(vm, onEditor = { nav.navigate("editor") },
            onRun = { nav.navigate("run") }, onHistory = { nav.navigate("history") },
            onBack = { nav.popBackStack() }) }
        composable("editor") { EditorScreen(vm, onBack = { nav.popBackStack() },
            onRun = { nav.navigate("run") }) }
        composable("run") { RunScreen(vm, onResults = { nav.navigate("results") }, onBack = { nav.popBackStack() }) }
        composable("results") { ResultsScreen(vm, onBack = { nav.popBackStack() }) }
        composable("history") { HistoryScreen(vm, onBack = { nav.popBackStack() }) }
        composable("backups") { BackupsScreen(vm,
            onExport = onExport,
            onImport = { onImport { uri -> vm.importBackup { resolver.openInputStream(uri) } } },
            onBack = { nav.popBackStack() }) }
        composable("settings") { SettingsScreen(onBack = { nav.popBackStack() }) }
    }
    val message by vm.notice.collectAsState()
    if (message != null || failure != null) {
        androidx.compose.material3.AlertDialog(onDismissRequest = { vm.dismiss(); clearFailure() },
            title = { androidx.compose.material3.Text("پیام ORBIT") },
            text = { androidx.compose.material3.Text(failure ?: message.orEmpty()) },
            confirmButton = { androidx.compose.material3.TextButton(onClick = { vm.dismiss(); clearFailure() }) {
                androidx.compose.material3.Text("بستن")
            } })
    }
}
