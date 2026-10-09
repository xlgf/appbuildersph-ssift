package com.screenshotbutler.index

import android.os.SystemClock
import android.util.Log
import com.screenshotbutler.data.AppSettings
import com.screenshotbutler.data.ScreenshotSource
import com.screenshotbutler.data.Shot
import com.screenshotbutler.data.ShotDao
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.yield

data class IndexProgress(val done: Int = 0, val total: Int = 0, val running: Boolean = false)

/** OCR + tag every screenshot that isn't in the index yet. Cheap enough to run on app open. */
class Indexer(
    private val source: ScreenshotSource,
    private val ocr: OcrEngine,
    private val labeler: ImageLabeler,
    private val dao: ShotDao,
    private val settings: AppSettings,
) {
    private val _progress = MutableStateFlow(IndexProgress())
    val progress: StateFlow<IndexProgress> = _progress.asStateFlow()
    private val lock = Mutex()

    suspend fun indexNew() = lock.withLock {
        withContext(Dispatchers.Default) {
            retagIfRulesChanged()
            val known = dao.knownIds().toHashSet()
            val onDevice = source.list()
            // Screenshots deleted from the gallery (or outside the current folder) leave the index too,
            // so search never returns a screenshot that no longer exists.
            val gone = known - onDevice.mapTo(HashSet()) { it.id }
            if (gone.isNotEmpty()) gone.chunked(DELETE_CHUNK).forEach { dao.delete(it) }
            val todo = onDevice.filter { it.id !in known }
            _progress.value = IndexProgress(0, todo.size, running = todo.isNotEmpty())

            todo.chunked(BATCH).forEach { batch ->
                val started = SystemClock.elapsedRealtime()
                // A few at once: ML Kit leaves cores idle while the next image decodes. Real phones hold
                // thousands of screenshots, so one at a time took the better part of an hour.
                val shots = batch.chunked(PARALLEL).flatMap { group -> coroutineScope { group.map { ref -> async {
                    try {
                        val text = ocr.read(ref.uri)
                        // Little text to read: describe the picture instead (photos, memes, maps).
                        val labels = if (ImageLabeler.needsLabels(text)) labeler.label(ref.uri) else emptyList()
                        val tags = tagsFor(text, labels.isNotEmpty())
                        Shot(
                            mediaId = ref.id,
                            uri = ref.uri.toString(),
                            takenAt = ref.takenAt,
                            text = text,
                            tags = tags.joinToString(" "),
                            sensitive = SensitiveDetector.detect(text).joinToString(" "),
                            labels = labels.joinToString(", "),
                        )
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        // Stored with no text, so a broken image isn't retried on every app open.
                        Log.w(TAG, "OCR failed for ${ref.uri}", e)
                        Shot(mediaId = ref.id, uri = ref.uri.toString(), takenAt = ref.takenAt, text = "", tags = "")
                    }
                } }.awaitAll() } }
                dao.upsert(shots)
                Log.d(TAG, "indexed ${batch.size} in ${SystemClock.elapsedRealtime() - started} ms (${_progress.value.done + batch.size}/${todo.size})")
                _progress.value = _progress.value.copy(done = _progress.value.done + batch.size)
                yield() // keep the UI responsive between batches
            }
            _progress.value = _progress.value.copy(running = false)
        }
    }

    private fun tagsFor(text: String, hasLabels: Boolean): List<String> =
        Tagger.tag(text) + if (hasLabels) listOf("image") else emptyList()

    /** When the Tagger rules change, re-tag what's already indexed from the stored text: no OCR needed. */
    private suspend fun retagIfRulesChanged() {
        if (settings.tagVersion == Tagger.VERSION) return
        val shots = dao.all()
        shots.forEach { shot ->
            val tags = tagsFor(shot.text, shot.labels.isNotBlank()).joinToString(" ")
            if (tags != shot.tags) dao.updateTags(shot.mediaId, tags)
        }
        Log.d(TAG, "re-tagged ${shots.size} screenshots for tag rules v${Tagger.VERSION}")
        settings.tagVersion = Tagger.VERSION
    }

    private companion object {
        const val BATCH = 12
        const val PARALLEL = 3
        const val DELETE_CHUNK = 500 // stays under SQLite's limit on query parameters
        const val TAG = "Indexer"
    }
}
