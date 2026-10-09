package com.screenshotbutler.ui

import android.app.Activity
import android.os.Build
import android.provider.MediaStore
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.screenshotbutler.index.SensitiveDetector
import com.screenshotbutler.index.Tagger

@Composable
private fun ListHeader(tag: String, title: String, count: Int, onBack: () -> Unit) = Column {
    Spacer(Modifier.height(24.dp))
    CircleButton("←", size = 48.dp, onClick = onBack)
    Spacer(Modifier.height(12.dp))
    Row(verticalAlignment = Alignment.CenterVertically) {
        FolderSymbol(tag, size = 28.dp)
        Spacer(Modifier.width(12.dp))
        Text(title, style = MaterialTheme.typography.headlineMedium, modifier = Modifier.weight(1f))
        Text("$count", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
    }
    Spacer(Modifier.height(12.dp))
    DoubleRule()
    Spacer(Modifier.height(8.dp))
}

@Composable
fun FolderScreen(vm: ButlerViewModel, tag: String, onBack: () -> Unit, onShot: (Long) -> Unit) {
    val shots by remember(tag) { vm.byTag(tag) }.collectAsStateWithLifecycle(emptyList())
    val label = Tagger.folders.firstOrNull { it.tag == tag }?.label ?: tag

    LazyColumn(Modifier.fillMaxSize().padding(horizontal = ScreenPadding)) {
        item { ListHeader(tag, label, shots.size, onBack) }
        items(shots, key = { it.mediaId }) { ShotRow(it, onClick = { onShot(it.mediaId) }) }
    }
}

@Composable
fun SensitiveScreen(vm: ButlerViewModel, onBack: () -> Unit, onShot: (Long) -> Unit) {
    val shots by remember { vm.sensitive() }.collectAsStateWithLifecycle(emptyList())
    val context = LocalContext.current
    val deleter = rememberLauncherForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK) vm.removeFromIndex(shots)
    }

    LazyColumn(Modifier.fillMaxSize().padding(horizontal = ScreenPadding)) {
        item { Column {
            ListHeader("sensitive", "Sensitive", shots.size, onBack)
            Text("${shots.size} screenshots expose private details.", style = MaterialTheme.typography.headlineMedium)
            Spacer(Modifier.height(8.dp))
            Text(
                "Found on this phone. Nothing was uploaded to find them.",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(16.dp))
            HeavyRule()
        } }
        items(shots, key = { it.mediaId }) { shot ->
            val found = shot.sensitiveList.joinToString(", ") { SensitiveDetector.labels[it] ?: it }
            ShotRow(shot, title = found, onClick = { onShot(shot.mediaId) })
        }
        if (shots.isNotEmpty() && Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) item {
            Button(
                onClick = {
                    val request = MediaStore.createDeleteRequest(context.contentResolver, shots.map { it.contentUri })
                    deleter.launch(IntentSenderRequest.Builder(request.intentSender).build())
                },
                shape = PillShape,
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary),
                modifier = Modifier.fillMaxWidth().padding(vertical = 24.dp).height(56.dp),
            ) { Text("Delete ${shots.size} screenshots  →", fontWeight = FontWeight.SemiBold) }
        }
    }
}
