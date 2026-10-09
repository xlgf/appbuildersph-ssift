package com.screenshotbutler.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import com.screenshotbutler.ai.AskResult
import com.screenshotbutler.data.Shot
import com.screenshotbutler.ui.theme.Palette
import java.util.Locale

@Composable
fun AnswerScreen(vm: ButlerViewModel, question: String, onBack: () -> Unit, onShot: (Long) -> Unit) {
    LaunchedEffect(question) { vm.ask(question) }
    val ui by vm.ask.collectAsStateWithLifecycle()

    LazyColumn(Modifier.fillMaxSize().padding(horizontal = ScreenPadding)) {
        item { Column {
            Spacer(Modifier.height(16.dp))
            CircleButton("←", size = 48.dp, onClick = onBack)
            Spacer(Modifier.height(24.dp))
            SectionLabel("You asked")
            Spacer(Modifier.height(4.dp))
            Text(question, style = MaterialTheme.typography.headlineMedium, color = Palette.Ink)
            Spacer(Modifier.height(20.dp))
        } }

        when (val state = ui) {
            AskUi.Idle, is AskUi.Thinking -> item { Thinking() }
            is AskUi.Done -> when (val r = state.result) {
                is AskResult.Answer -> item { Answer(r, onShot) }
                is AskResult.NotSure -> {
                    item { Statement("I'm not sure. These screenshots might help.") }
                    items(r.matches.take(10), key = { it.mediaId }) { ShotRow(it, question, onClick = { onShot(it.mediaId) }) }
                    item { Footnote() }
                }
                is AskResult.ModelUnavailable -> {
                    item { Statement("${r.matches.size} screenshots match. The on-device AI isn't loaded, so here they are.") }
                    items(r.matches.take(20), key = { it.mediaId }) { ShotRow(it, question, onClick = { onShot(it.mediaId) }) }
                }
                AskResult.NoMatches -> item { Column {
                    Statement("Nothing found for ‘$question’.")
                    Spacer(Modifier.height(12.dp))
                    Text(
                        "ssift only knows what's in your screenshots. Try different words, or check that the screenshot was taken on this phone.",
                        style = MaterialTheme.typography.bodyLarge,
                    )
                    ActionText("Search again", onClick = onBack)
                } }
            }
        }
    }
}

@Composable
private fun Thinking() = Column {
    LanternGlow()
    Spacer(Modifier.height(24.dp))
    Statement("Reading your screenshots…")
    Spacer(Modifier.height(8.dp))
    Text("Running on this phone. No internet needed.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

@Composable
private fun Answer(r: AskResult.Answer, onShot: (Long) -> Unit) = Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
    val clipboard = LocalClipboardManager.current
    // The answer: big and bold in the chartreuse feature card, with Copy right there.
    BentoCard(color = Palette.Chartreuse, modifier = Modifier.fillMaxWidth()) {
        SectionLabel(if (r.read == 1) "Read 1 screenshot" else "Read ${r.read} screenshots", color = Palette.Olive)
        Spacer(Modifier.height(10.dp))
        Text(r.value, style = MaterialTheme.typography.displayLarge, color = Palette.Ink)
        Spacer(Modifier.height(16.dp))
        ActionText("Copy", container = Color.White, content = Palette.Ink) { clipboard.setText(AnnotatedString(r.value)) }
    }
    BentoCard(modifier = Modifier.fillMaxWidth()) {
        KeyValueRow("Found in", "${r.matches.size} screenshot${if (r.matches.size == 1) "" else "s"}")
        KeyValueRow("Time", String.format(Locale.US, "%.1f s", r.millis / 1000f))
        KeyValueRow("Processed", "On this phone")
    }
    r.source?.let { source ->
        BentoCard(modifier = Modifier.fillMaxWidth()) {
            SectionLabel("Source")
            Spacer(Modifier.height(10.dp))
            SourceImage(source, onShot)
        }
    }
    Text(
        "Answered on this phone. Nothing was uploaded.",
        style = MaterialTheme.typography.bodyMedium,
        color = Palette.Muted,
        modifier = Modifier.padding(vertical = 16.dp),
    )
}

@Composable
private fun SourceImage(shot: Shot, onShot: (Long) -> Unit) = AsyncImage(
    model = shot.contentUri,
    contentDescription = "Source screenshot",
    contentScale = ContentScale.FillWidth,
    modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).clickable { onShot(shot.mediaId) },
)

@Composable
private fun Statement(text: String) = Text(text, style = MaterialTheme.typography.headlineMedium, color = Palette.Ink)

@Composable
private fun Footnote() = Text(
    "Answers come only from your screenshots, read on this phone.",
    style = MaterialTheme.typography.bodyMedium,
    color = MaterialTheme.colorScheme.onSurfaceVariant,
    modifier = Modifier.padding(vertical = 24.dp),
)
