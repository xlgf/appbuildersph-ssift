package com.screenshotbutler.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.screenshotbutler.ai.ShotSearch
import com.screenshotbutler.data.Shot

/** Thumbnail + snippet + date. Words from [query] are highlighted in the accent color. */
@Composable
fun ShotRow(
    shot: Shot,
    query: String? = null,
    title: String? = null,
    onClick: () -> Unit,
    trailing: (@Composable () -> Unit)? = null,
) {
    val accent = MaterialTheme.colorScheme.secondary
    Column {
    Row(Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
        AsyncImage(
            model = shot.contentUri,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.size(width = 48.dp, height = 96.dp)
                .border(BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)),
        )
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            title?.let { Text(it, style = MaterialTheme.typography.bodyLarge) }
            Text(
                // Low-text screenshots (photos, memes) show what the image labeler saw instead.
                snippet(if (shot.text.isBlank() && shot.labels.isNotBlank()) "Image: ${shot.labels}" else shot.text, query, accent),
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(4.dp))
            Text(formatShort(shot.takenAt), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        trailing?.invoke()
    }
    ThinRule()
    }
}

private fun snippet(text: String, query: String?, accent: androidx.compose.ui.graphics.Color): AnnotatedString {
    val flat = text.replace('\n', ' ')
    val terms = query?.let { ShotSearch.terms(it) }.orEmpty()
    val first = terms.mapNotNull { t -> flat.indexOf(t, ignoreCase = true).takeIf { it >= 0 } }.minOrNull() ?: 0
    val start = (first - 30).coerceAtLeast(0)
    val window = flat.substring(start, (start + 160).coerceAtMost(flat.length))
    return buildAnnotatedString {
        append(window)
        terms.forEach { term ->
            var i = window.indexOf(term, ignoreCase = true)
            while (i >= 0) {
                addStyle(SpanStyle(color = accent), i, i + term.length)
                i = window.indexOf(term, i + term.length, ignoreCase = true)
            }
        }
    }
}
