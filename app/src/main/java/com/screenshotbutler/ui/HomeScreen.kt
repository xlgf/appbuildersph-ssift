package com.screenshotbutler.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowForward
import androidx.compose.material.icons.outlined.AutoStories
import androidx.compose.material.icons.outlined.ChatBubbleOutline
import androidx.compose.material.icons.outlined.Event
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.Groups
import androidx.compose.material.icons.outlined.Key
import androidx.compose.material.icons.outlined.Password
import androidx.compose.material.icons.outlined.Place
import androidx.compose.material.icons.outlined.Restaurant
import androidx.compose.material.icons.outlined.SportsEsports
import androidx.compose.material.icons.outlined.ConfirmationNumber
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.Menu
import androidx.compose.material.icons.outlined.ReceiptLong
import androidx.compose.material.icons.outlined.Wifi
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import com.screenshotbutler.ai.LocalLlm
import com.screenshotbutler.ui.theme.Palette

private val CardHeight = 140.dp
private val Gap = 8.dp

/**
 * Home, per the Stitch mockup: menu + privacy pill, the question, a search pill, then a bento grid whose
 * cards say something (next event, latest receipt) rather than just counting. Each card has one pointed
 * corner facing the middle of the grid.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun HomeScreen(
    vm: ButlerViewModel,
    onAsk: (String) -> Unit,
    onFolder: (String) -> Unit,
    onShot: (Long) -> Unit,
    onSettings: () -> Unit,
) {
    val total by vm.total.collectAsStateWithLifecycle()
    val folders by vm.folders.collectAsStateWithLifecycle()
    val sensitive by vm.sensitiveCount.collectAsStateWithLifecycle()
    val recent by vm.recent.collectAsStateWithLifecycle()
    val progress by vm.progress.collectAsStateWithLifecycle()
    val llm by vm.llmState.collectAsStateWithLifecycle()
    val readByAi by vm.readByAi.collectAsStateWithLifecycle()
    val ticket by vm.ticketHighlight.collectAsStateWithLifecycle()
    val receipt by vm.receiptHighlight.collectAsStateWithLifecycle()

    Box(Modifier.fillMaxSize()) {
        LazyColumn(Modifier.fillMaxSize().padding(horizontal = ScreenPadding), verticalArrangement = Arrangement.spacedBy(Gap)) {
            item { Column {
                Spacer(Modifier.height(12.dp))
                // The "0 bytes sent" privacy proof lives at the top of Settings.
                IconCircle(Icons.Outlined.Menu, "Settings", size = 64.dp, onClick = onSettings)
                Spacer(Modifier.height(32.dp))
                SectionLabel(if (total == 0) "NO SCREENSHOTS READ YET" else "$total SCREENSHOTS, ALL ON THIS PHONE")
                Spacer(Modifier.height(6.dp))
                Text("What are you looking for?", style = MaterialTheme.typography.headlineLarge, color = Palette.Ink)
                Spacer(Modifier.height(20.dp))
                SearchPill(onAsk)
                if (progress.running) {
                    Spacer(Modifier.height(Gap))
                    BentoCard {
                        SectionLabel("Reading ${progress.done} of ${progress.total}")
                        Spacer(Modifier.height(10.dp))
                        LinearProgressIndicator(
                            progress = { if (progress.total == 0) 0f else progress.done / progress.total.toFloat() },
                            color = Palette.CoralDeep, trackColor = Palette.CardAlt,
                            modifier = Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp)),
                        )
                    }
                }
                ModelStatus(llm, vm)
                Spacer(Modifier.height(12.dp))
            } }

            // Folders come from this phone's screenshots: biggest first, empty ones hidden. Info folders fill the
            // bento grid; media (games, comics, social, photos) gets its own row, since Ask doesn't answer from it.
            val info = folders.filter { !it.media }
            val media = folders.filter { it.media }
            fun special(tag: String): Highlight? = when (tag) {
                "ticket" -> ticket
                "receipt" -> receipt
                else -> null
            }
            fun line(f: FolderCount) = special(f.tag) ?: Highlight("${f.count} ", folderNoun(f.tag, f.count))

            info.firstOrNull()?.let { big -> item {
                Row(horizontalArrangement = Arrangement.spacedBy(Gap)) {
                    BentoCard(Modifier.weight(1f).height(CardHeight * 2 + Gap), pointed = Corner.BottomEnd, onClick = { onFolder(big.tag) }) {
                        Icon(folderIcon(big.tag), null, tint = Palette.Ink, modifier = Modifier.size(28.dp))
                        Spacer(Modifier.height(28.dp))
                        SectionLabel(big.label)
                        Spacer(Modifier.height(6.dp))
                        CardLine(special(big.tag) ?: Highlight("Your ", big.label.lowercase()), size = 22)
                        Spacer(Modifier.weight(1f))
                        Text("${big.count} ${folderNoun(big.tag, big.count)}", style = MaterialTheme.typography.bodyMedium, color = Palette.Muted)
                    }
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(Gap)) {
                        val second = info.getOrNull(1)
                        if (second != null) SmallCard(folderIcon(second.tag), second.label, line(second), Corner.BottomStart) { onFolder(second.tag) }
                        else NotesCard(readByAi, Corner.BottomStart, onClick = onSettings)
                        info.getOrNull(2)?.let { f -> SmallCard(folderIcon(f.tag), f.label, line(f), Corner.TopStart) { onFolder(f.tag) } }
                    }
                }
            } }

            // Second row: the fourth info folder next to the AI's parsed notes.
            if (info.size >= 2) item {
                Row(horizontalArrangement = Arrangement.spacedBy(Gap)) {
                    info.getOrNull(3)?.let { f -> SmallCard(folderIcon(f.tag), f.label, line(f), Corner.TopEnd, Modifier.weight(1f)) { onFolder(f.tag) } }
                    NotesCard(readByAi, Corner.TopStart, Modifier.weight(1f), onClick = onSettings)
                }
            }

            if (sensitive > 0) item { SensitiveCard(sensitive) { onFolder("sensitive") } }

            if (info.size > 4) item {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(Gap), verticalArrangement = Arrangement.spacedBy(Gap), modifier = Modifier.padding(top = 4.dp)) {
                    info.drop(4).forEach { f -> FolderPill(f) { onFolder(f.tag) } }
                }
            }

            if (media.isNotEmpty()) item { Column {
                Spacer(Modifier.height(16.dp))
                SectionLabel("Media")
                Spacer(Modifier.height(10.dp))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(Gap), verticalArrangement = Arrangement.spacedBy(Gap)) {
                    media.forEach { f -> FolderPill(f) { onFolder(f.tag) } }
                }
            } }

            if (recent.isNotEmpty()) item { Column {
                Spacer(Modifier.height(16.dp))
                SectionLabel("Recent")
                Spacer(Modifier.height(10.dp))
                LazyRow(horizontalArrangement = Arrangement.spacedBy(Gap)) {
                    items(recent, key = { it.mediaId }) { shot ->
                        Column(Modifier.width(92.dp).clickable { onShot(shot.mediaId) }) {
                            AsyncImage(
                                model = shot.contentUri, contentDescription = null, contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxWidth().aspectRatio(9f / 19.5f).clip(RoundedCornerShape(16.dp)),
                            )
                            Spacer(Modifier.height(6.dp))
                            Text(formatShort(shot.takenAt), style = MaterialTheme.typography.bodyMedium, color = Palette.Body)
                        }
                    }
                }
                Spacer(Modifier.height(24.dp))
            } }
        }
    }
}

@Composable
private fun NotesCard(readByAi: Int, pointed: Corner, modifier: Modifier = Modifier, onClick: () -> Unit) =
    SmallCard(Icons.Outlined.Description, "Notes", Highlight("$readByAi items ", "parsed"), pointed, modifier, onClick = onClick)

@Composable
private fun FolderPill(f: FolderCount, onClick: () -> Unit) = Row(
    Modifier.clip(PillShape).background(Palette.Card).clickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 10.dp),
    verticalAlignment = Alignment.CenterVertically,
) {
    Icon(folderIcon(f.tag), null, tint = Palette.Body, modifier = Modifier.size(18.dp))
    Spacer(Modifier.width(8.dp))
    Text("${f.label}  ${f.count}", style = MaterialTheme.typography.labelLarge, color = Palette.Body)
}

private fun folderIcon(tag: String): ImageVector = when (tag) {
    "ticket" -> Icons.Outlined.ConfirmationNumber
    "receipt" -> Icons.Outlined.ReceiptLong
    "schedule" -> Icons.Outlined.Event
    "account" -> Icons.Outlined.Key
    "wifi" -> Icons.Outlined.Wifi
    "code" -> Icons.Outlined.Password
    "address" -> Icons.Outlined.Place
    "chat" -> Icons.Outlined.ChatBubbleOutline
    "recipe" -> Icons.Outlined.Restaurant
    "social" -> Icons.Outlined.Groups
    "game" -> Icons.Outlined.SportsEsports
    "comic" -> Icons.Outlined.AutoStories
    "image" -> Icons.Outlined.Image
    else -> Icons.Outlined.Folder
}

/** What one item in a folder is called on its card: "3 networks", "12 logins". */
private fun folderNoun(tag: String, count: Int): String {
    val (one, many) = when (tag) {
        "ticket" -> "ticket" to "tickets"
        "receipt" -> "receipt" to "receipts"
        "schedule" -> "date" to "dates"
        "account" -> "login" to "logins"
        "wifi" -> "network" to "networks"
        "code" -> "code" to "codes"
        "address" -> "address" to "addresses"
        "chat" -> "chat" to "chats"
        "recipe" -> "recipe" to "recipes"
        "social" -> "post" to "posts"
        "game" -> "screen" to "screens"
        "comic" -> "page" to "pages"
        "image" -> "photo" to "photos"
        else -> "screenshot" to "screenshots"
    }
    return if (count == 1) one else many
}

@Composable
private fun SmallCard(
    icon: ImageVector,
    label: String,
    line: Highlight,
    pointed: Corner,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) = BentoCard(modifier.fillMaxWidth().height(CardHeight), pointed = pointed, onClick = onClick) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, null, tint = Palette.Ink, modifier = Modifier.size(26.dp))
        Spacer(Modifier.weight(1f))
        SectionLabel(label)
    }
    Spacer(Modifier.weight(1f))
    CardLine(line, size = 18)
}

/** Card text with one bold part, light weight otherwise. */
@Composable
private fun CardLine(h: Highlight, size: Int) = Text(
    buildAnnotatedString {
        append(h.before)
        withStyle(SpanStyle(fontWeight = FontWeight.Bold)) { append(h.bold) }
        append(h.after)
    },
    style = MaterialTheme.typography.headlineMedium.copy(fontSize = size.sp, lineHeight = (size + 6).sp),
    color = Palette.Ink,
    maxLines = 3,
)

@Composable
private fun SensitiveCard(count: Int, onClick: () -> Unit) {
    Box(Modifier.fillMaxWidth().clip(bentoShape(Corner.TopEnd)).background(Palette.Indigo).clickable(onClick = onClick)) {
        // Lilac squiggle, as in the design.
        Canvas(Modifier.matchParentSize()) {
            val w = size.width; val h = size.height
            val path = Path().apply {
                moveTo(w * 0.42f, h * 1.05f)
                cubicTo(w * 0.50f, h * 0.55f, w * 0.78f, h * 0.95f, w * 0.70f, h * 0.35f)
                cubicTo(w * 0.64f, h * -0.05f, w * 0.52f, h * 0.30f, w * 0.62f, h * 0.55f)
                cubicTo(w * 0.70f, h * 0.80f, w * 0.80f, h * 0.70f, w * 0.78f, h * 1.05f)
            }
            drawPath(path, Palette.Lilac.copy(alpha = 0.55f), style = Stroke(width = 18.dp.toPx(), cap = StrokeCap.Round))
        }
        Row(Modifier.padding(horizontal = 24.dp, vertical = 26.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(
                buildAnnotatedString {
                    append("$count screenshots show ")
                    withStyle(SpanStyle(fontWeight = FontWeight.Bold)) { append("private details") }
                },
                style = MaterialTheme.typography.headlineMedium.copy(fontSize = 20.sp, lineHeight = 26.sp),
                color = Color.White,
                modifier = Modifier.weight(1f),
            )
            Spacer(Modifier.width(12.dp))
            Box(Modifier.size(46.dp).clip(CircleShape).background(Color.White.copy(alpha = 0.22f)), contentAlignment = Alignment.Center) {
                Icon(Icons.AutoMirrored.Outlined.ArrowForward, null, tint = Color.White)
            }
        }
    }
}

/** Round white button holding a line icon (menu). */
@Composable
fun IconCircle(icon: ImageVector, description: String, modifier: Modifier = Modifier, size: Dp = 56.dp, onClick: () -> Unit) = Box(
    modifier.size(size).clip(CircleShape).background(Color.White).clickable(onClick = onClick),
    contentAlignment = Alignment.Center,
) { Icon(icon, description, tint = Palette.Ink, modifier = Modifier.size(size * 0.42f)) }

@Composable
private fun SearchPill(onAsk: (String) -> Unit) {
    var query by rememberSaveable { mutableStateOf("") }
    // The box starts empty again for the next question (the asked one stays on the answer screen).
    val submit = { if (query.isNotBlank()) { onAsk(query.trim()); query = "" } }
    Row(
        Modifier.fillMaxWidth().clip(PillShape).background(Palette.Card).padding(start = 24.dp, end = 10.dp, top = 10.dp, bottom = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        BasicTextField(
            value = query,
            onValueChange = { query = it },
            singleLine = true,
            textStyle = MaterialTheme.typography.bodyLarge.copy(color = Palette.Ink, fontSize = 17.sp),
            cursorBrush = SolidColor(Palette.Coral),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            keyboardActions = KeyboardActions(onSearch = { submit() }),
            modifier = Modifier.weight(1f).padding(vertical = 14.dp),
            decorationBox = { inner ->
                if (query.isEmpty()) {
                    Text(
                        "Ask anything… e.g. wifi password at mom's",
                        style = MaterialTheme.typography.bodyLarge.copy(fontSize = 17.sp),
                        color = Palette.Muted, maxLines = 1,
                    )
                }
                inner()
            },
        )
        Spacer(Modifier.width(8.dp))
        Box(Modifier.size(56.dp).clip(CircleShape).background(Palette.Ink).clickable { submit() }, contentAlignment = Alignment.Center) {
            Icon(Icons.AutoMirrored.Outlined.ArrowForward, "Ask", tint = Color.White)
        }
    }
}

@Composable
private fun ModelStatus(state: LocalLlm.State, vm: ButlerViewModel) {
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri -> uri?.let(vm::importModel) }
    val message = when (state) {
        LocalLlm.State.Ready -> return
        LocalLlm.State.Loading -> "Loading the on-device AI…"
        LocalLlm.State.Missing -> "On-device AI model not installed. Search still works."
        is LocalLlm.State.Failed -> "On-device AI failed to load: ${state.message}"
    }
    Spacer(Modifier.height(Gap))
    BentoCard(color = Palette.CardAlt) {
        Text(message, style = MaterialTheme.typography.bodyMedium, color = Palette.Body)
        if (state !is LocalLlm.State.Loading) ActionText("Import model file") { picker.launch(arrayOf("*/*")) }
    }
}
