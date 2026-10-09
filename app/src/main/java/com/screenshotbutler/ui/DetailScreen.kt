package com.screenshotbutler.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import com.screenshotbutler.ai.Digester
import com.screenshotbutler.index.SensitiveDetector
import org.json.JSONObject

@Composable
fun DetailScreen(vm: ButlerViewModel, id: Long, onBack: () -> Unit) {
    val shot by remember(id) { vm.shot(id) }.collectAsStateWithLifecycle(null)
    val s = shot ?: return
    val clipboard = LocalClipboardManager.current
    var showText by remember { mutableStateOf(false) }

    val digest = remember(s.digest) { s.digest?.let { runCatching { JSONObject(it) }.getOrNull() } }
    val values = remember(digest) {
        digest?.let { Digester.values(it) }?.let { obj -> obj.keys().asSequence().map { it to obj.optString(it) }.toList() }.orEmpty()
    }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = ScreenPadding)) {
        Spacer(Modifier.height(24.dp))
        CircleButton("←", size = 48.dp, onClick = onBack)
        Spacer(Modifier.height(8.dp))
        Text(formatLong(s.takenAt), style = MaterialTheme.typography.headlineMedium)
        Spacer(Modifier.height(16.dp))
        AsyncImage(
            model = s.contentUri,
            contentDescription = "Screenshot",
            contentScale = ContentScale.FillWidth,
            modifier = Modifier.fillMaxWidth().border(BorderStroke(1.dp, MaterialTheme.colorScheme.outline)),
        )
        Spacer(Modifier.height(24.dp))
        HeavyRule()
        Spacer(Modifier.height(12.dp))

        digest?.let { Digester.summary(it) }?.takeIf { it.isNotBlank() }?.let {
            Text(it, style = MaterialTheme.typography.bodyLarge)
            Spacer(Modifier.height(12.dp))
        }
        if (values.isNotEmpty()) {
            SectionLabel("Found in this screenshot")
            values.forEach { (key, value) ->
                KeyValueRow(key.replaceFirstChar { it.uppercase() }, value) {
                    ActionText("Copy") { clipboard.setText(AnnotatedString(value)) }
                }
            }
        }
        if (s.sensitiveList.isNotEmpty()) {
            KeyValueRow("Sensitive", s.sensitiveList.joinToString(", ") { SensitiveDetector.labels[it] ?: it })
        }
        if (s.labelList.isNotEmpty()) {
            KeyValueRow("Image shows", s.labelList.joinToString(", "))
        }
        KeyValueRow("Folders", s.tagList.joinToString(", ").ifBlank { "None" })
        Text(
            when {
                digest == null -> "Not yet read by the AI. It will be, next time the phone charges."
                digest.optBoolean("skipped") -> "Nothing here for the AI to pull out, so it was skipped."
                else -> "Read by on-device AI while charging."
            },
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(vertical = 12.dp),
        )

        ActionText(if (showText) "Hide text" else "Show all text") { showText = !showText }
        if (showText) {
            Text(s.text, style = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace))
        }
        Spacer(Modifier.height(32.dp))
    }
}
