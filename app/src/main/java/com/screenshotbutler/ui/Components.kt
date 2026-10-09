package com.screenshotbutler.ui

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.screenshotbutler.ui.theme.Palette
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

val ScreenPadding = 20.dp
val CardShape = RoundedCornerShape(28.dp)
val PillShape = RoundedCornerShape(50)

/** Soft divider. (The bento design has no heavy rules; the name is kept so screens read the same.) */
@Composable
fun HeavyRule(modifier: Modifier = Modifier) = Spacer(modifier.height(4.dp))

@Composable
fun ThinRule(modifier: Modifier = Modifier) =
    Box(modifier.fillMaxWidth().height(1.dp).background(MaterialTheme.colorScheme.outlineVariant))

/** Space under a screen title (the bento design separates by space, not rules). */
@Composable
fun DoubleRule() = Spacer(Modifier.height(4.dp))

/** Small coral label above a headline or card title ("Tickets", "Answer"). */
@Composable
fun SectionLabel(text: String, modifier: Modifier = Modifier, color: Color = MaterialTheme.colorScheme.primary) = Text(
    text, // shown as written: re-casing broke names like "Wi-Fi"
    style = MaterialTheme.typography.labelSmall,
    color = color,
    modifier = modifier,
)

/** Which corner of a bento card is pointed (the design's cards have one sharp corner facing the group's center). */
enum class Corner { TopStart, TopEnd, BottomEnd, BottomStart }

fun bentoShape(pointed: Corner?, radius: Dp = 28.dp, point: Dp = 6.dp) = RoundedCornerShape(
    topStart = if (pointed == Corner.TopStart) point else radius,
    topEnd = if (pointed == Corner.TopEnd) point else radius,
    bottomEnd = if (pointed == Corner.BottomEnd) point else radius,
    bottomStart = if (pointed == Corner.BottomStart) point else radius,
)

/** Off-white rounded card, the bento building block. [pointed] makes one corner sharp. */
@Composable
fun BentoCard(
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.surface,
    pointed: Corner? = null,
    onClick: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) = Column(
    modifier
        .clip(bentoShape(pointed))
        .background(color)
        .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
        .padding(20.dp),
    content = content,
)

/** Round white button with a single glyph, like the design's back / arrow / plus buttons. */
@Composable
fun CircleButton(glyph: String, modifier: Modifier = Modifier, size: Dp = 52.dp, onClick: () -> Unit) = Box(
    modifier.size(size).clip(CircleShape).background(Color.White).clickable(onClick = onClick),
    contentAlignment = Alignment.Center,
) { Text(glyph, style = MaterialTheme.typography.titleMedium, color = Palette.Ink) }

/** Pill action ("Copy", "Review"): coral by default, as in the design's primary button. */
@Composable
fun ActionText(
    text: String,
    modifier: Modifier = Modifier,
    container: Color = MaterialTheme.colorScheme.primaryContainer,
    content: Color = Color.White,
    onClick: () -> Unit,
) = Text(
    text,
    style = MaterialTheme.typography.labelLarge,
    color = content,
    modifier = modifier
        .padding(vertical = 6.dp)
        .clip(PillShape)
        .background(container)
        .clickable(onClick = onClick)
        .padding(horizontal = 18.dp, vertical = 10.dp),
)

@Composable
fun PrivacyLabel(bytesSent: Long) = Row(
    Modifier.clip(PillShape).background(MaterialTheme.colorScheme.surface).padding(horizontal = 14.dp, vertical = 8.dp),
    verticalAlignment = Alignment.CenterVertically,
) {
    Box(Modifier.size(8.dp).background(Palette.Olive, CircleShape))
    Spacer(Modifier.width(8.dp))
    Text("On this phone · $bytesSent bytes sent", style = MaterialTheme.typography.labelSmall, color = Palette.Body)
}

@Composable
fun KeyValueRow(key: String, value: String, action: (@Composable () -> Unit)? = null) {
    Row(Modifier.fillMaxWidth().padding(vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(key, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(0.35f))
        Text(value, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold, color = Palette.Body, maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(0.65f))
        action?.invoke()
    }
    ThinRule()
}

/** Pulsing coral-and-chartreuse blob shown while the on-device model is thinking. */
@Composable
fun LanternGlow(size: Dp = 96.dp) {
    val pulse by rememberInfiniteTransition(label = "thinking").animateFloat(
        initialValue = 0.55f, targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(900), RepeatMode.Reverse), label = "pulse",
    )
    Box(
        Modifier.size(size).alpha(pulse).background(
            Brush.radialGradient(listOf(Palette.Chartreuse, Palette.Coral, Palette.Coral.copy(alpha = 0f))),
            CircleShape,
        )
    )
}

/** Small shape per folder, in the design system's accent colors. */
@Composable
fun FolderSymbol(tag: String, size: Dp = 14.dp) {
    val (color, shape) = folderStyle(tag)
    Box(Modifier.size(size).background(color, shape))
}

private fun folderStyle(tag: String): Pair<Color, Shape> = when (tag) {
    "ticket" -> Palette.Coral to CircleShape
    "receipt" -> Palette.CoralDeep to RoundedCornerShape(25)
    "chat" -> Palette.Indigo to RoundedCornerShape(50, 50, 50, 0)
    "code" -> Palette.Olive to RoundedCornerShape(0, 50, 0, 50)
    "wifi" -> Palette.Indigo to RoundedCornerShape(50, 50, 0, 0)
    "address" -> Palette.Olive to RoundedCornerShape(50, 0, 50, 0)
    "recipe" -> Palette.Chartreuse to RoundedCornerShape(0, 0, 50, 50)
    "image" -> Palette.Lilac to RoundedCornerShape(25)
    "schedule" -> Palette.Coral to RoundedCornerShape(50, 0, 50, 0)
    "account" -> Palette.Indigo to RoundedCornerShape(0, 50, 50, 50)
    "social" -> Palette.Lilac to CircleShape
    "game" -> Palette.Chartreuse to RoundedCornerShape(25)
    "comic" -> Palette.Lilac to RoundedCornerShape(50, 50, 0, 50)
    "sensitive" -> Palette.Danger to RoundedCornerShape(25)
    else -> Palette.Muted to CircleShape
}

private val shortDate = SimpleDateFormat("MMM d", Locale.getDefault())
private val longDate = SimpleDateFormat("MMM d, HH:mm", Locale.getDefault())
fun formatShort(millis: Long): String = shortDate.format(Date(millis))
fun formatLong(millis: Long): String = longDate.format(Date(millis))
