package com.screenshotbutler.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.screenshotbutler.ai.LocalLlm
import com.screenshotbutler.work.DigestWorker

@Composable
fun SettingsScreen(vm: ButlerViewModel, onBack: () -> Unit) {
    val context = LocalContext.current
    val total by vm.total.collectAsStateWithLifecycle()
    val digested by vm.digested.collectAsStateWithLifecycle()
    val readByAi by vm.readByAi.collectAsStateWithLifecycle()
    val llm by vm.llmState.collectAsStateWithLifecycle()
    val demoMode by vm.demoMode.collectAsStateWithLifecycle()
    var confirmDelete by remember { mutableStateOf(false) }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri -> uri?.let(vm::importModel) }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = ScreenPadding)) {
        Spacer(Modifier.height(24.dp))
        CircleButton("←", size = 48.dp, onClick = onBack)
        Spacer(Modifier.height(8.dp))
        Text("Settings", style = MaterialTheme.typography.headlineMedium)
        Spacer(Modifier.height(12.dp))
        DoubleRule()
        Spacer(Modifier.height(24.dp))
        PrivacyLabel(vm.bytesSent())
        Spacer(Modifier.height(8.dp))
        Text("${vm.bytesSent()} bytes sent since this phone started. ssift has no internet permission.", style = MaterialTheme.typography.headlineMedium)

        Section("Library")
        Row(Modifier.fillMaxWidth().padding(vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Demo mode", style = MaterialTheme.typography.bodyLarge)
                Text(
                    if (demoMode) "Only reads Screenshots/ButlerDemo. Your own screenshots are left out."
                    else "Reads all your screenshots.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Switch(
                checked = demoMode,
                onCheckedChange = { vm.setDemoMode(it) },
                colors = SwitchDefaults.colors(checkedTrackColor = MaterialTheme.colorScheme.tertiary),
            )
        }
        ThinRule()
        KeyValueRow("Indexed", "$total screenshots")
        // "Checked" includes screenshots skipped for having nothing to extract; only real reads count as AI.
        KeyValueRow("Checked by AI", "$digested of $total")
        KeyValueRow("Facts found in", "$readByAi screenshots")
        ActionText("Re-scan for new screenshots") { vm.indexNew() }

        Section("Charging digest")
        Text(
            "While the phone charges, the on-device AI reads each screenshot to pull out key facts and spot sensitive ones.",
            style = MaterialTheme.typography.bodyLarge,
        )
        ActionText("Digest now (for testing)") { DigestWorker.runNow(context) }

        Section("AI")
        KeyValueRow(
            "Model",
            when (val s = llm) {
                LocalLlm.State.Ready -> "Loaded, on this phone"
                LocalLlm.State.Loading -> "Loading…"
                LocalLlm.State.Missing -> "Not installed"
                is LocalLlm.State.Failed -> "Failed: ${s.message}"
            },
        )
        ActionText("Import model file") { picker.launch(arrayOf("*/*")) }

        Section("Privacy")
        KeyValueRow("Photo access", "Screenshots only")
        Text(
            "Delete my index",
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.clickable { confirmDelete = true }.padding(vertical = 12.dp),
        )
        Spacer(Modifier.height(32.dp))
    }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Delete the index?") },
            text = { Text("All extracted text and facts are removed from this phone. Your screenshots are not touched. ssift builds a fresh index the next time you open it.") },
            confirmButton = { TextButton(onClick = { vm.deleteIndex(); confirmDelete = false }) { Text("Delete") } },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Cancel") } },
        )
    }
}

@Composable
private fun Section(title: String) = Column {
    Spacer(Modifier.height(32.dp))
    HeavyRule()
    Spacer(Modifier.height(12.dp))
    SectionLabel(title)
}
