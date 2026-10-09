package com.screenshotbutler.ai

import android.os.SystemClock
import android.util.Log
import com.screenshotbutler.data.ShotDao
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.json.JSONObject

/**
 * The charging digest: the LLM reads each screenshot once and stores structured facts.
 * Runs from [com.screenshotbutler.work.DigestWorker] only while the phone charges (or on "Digest now").
 */
class Digester(private val dao: ShotDao, private val llm: LocalLlm) {

    // The overnight run and "Digest now" can overlap; one batch at a time means neither repeats the other's work.
    private val batchLock = Mutex()

    /** Digests up to [limit] screenshots. Returns how many were processed (0 = nothing left). */
    suspend fun digestBatch(limit: Int): Int = batchLock.withLock {
        llm.load()
        if (llm.state.value != LocalLlm.State.Ready) return 0

        val batch = dao.undigested(limit)
        for (shot in batch) {
            // Nothing for the LLM to add (memes, photos, casual chats): record it as skipped, no model call.
            if (!Prompts.needsDigest(shot)) {
                dao.updateDigest(shot.mediaId, SKIPPED, shot.tags, shot.sensitive)
                Log.d(TAG, "skipped ${shot.mediaId}")
                continue
            }

            val started = SystemClock.elapsedRealtime()
            val prompt = Prompts.digest(shot)
            val json = try {
                val raw = llm.generate(prompt, maxChars = Prompts.DIGEST_MAX_REPLY_CHARS)
                Log.d(TAG, "digest ${shot.mediaId}: ${prompt.length} chars in, ${SystemClock.elapsedRealtime() - started} ms, reply: $raw")
                // The prompt prefills "{", but Gemma often repeats it; only add it back when it's missing.
                val text = raw.trimStart()
                parse(if (text.startsWith(Prompts.DIGEST_PREFILL)) text else Prompts.DIGEST_PREFILL + text)
            } catch (e: CancellationException) {
                throw e // the worker was stopped: leave this screenshot undigested so it is retried
            } catch (e: Exception) {
                Log.w(TAG, "Digest failed for ${shot.mediaId}", e)
                null
            }

            val reasons = shot.sensitiveList.toMutableSet()
            // The 1B model over-flags (it called a party invite sensitive), so its flag only counts when it
            // also pulled out a password, card, code or ID value.
            if (json != null && sensitive(json) && reasons.isEmpty() && hasSecretValue(json)) reasons += "ai"
            // Store "{}" on failure so a bad screenshot isn't retried forever. Folders come from rules only.
            dao.updateDigest(
                id = shot.mediaId,
                digest = json?.toString() ?: "{}",
                tags = shot.tags,
                sensitive = reasons.joinToString(" "),
            )
        }
        return batch.size
    }

    /**
     * Small models wrap JSON in prose, stop before the last "}", or get cut off by the reply cap.
     * Take everything from the first "{", drop a dangling unfinished value, and close what is still open.
     */
    private fun parse(raw: String): JSONObject? {
        val start = raw.indexOf('{')
        if (start < 0) return null
        val body = raw.substring(start)
        return try {
            JSONObject(body.substring(0, body.lastIndexOf('}') + 1))
        } catch (e: Exception) {
            runCatching { JSONObject(repair(body)) }.getOrNull()
        }
    }

    private fun repair(json: String): String {
        // Pass 1: find the end of the last complete value or the last comma, where it's safe to cut.
        var inString = false
        var escaped = false
        var lastSafe = 0
        json.forEachIndexed { i, c ->
            when {
                escaped -> escaped = false
                c == '\\' && inString -> escaped = true
                c == '"' -> { inString = !inString; if (!inString) lastSafe = i + 1 }
                inString -> Unit
                c == '}' || c == ']' || c.isLetterOrDigit() -> lastSafe = i + 1
                c == ',' -> lastSafe = i
            }
        }
        var cut = json.substring(0, lastSafe).trimEnd().trimEnd(',')
        // A key with no value yet ("…, \"notes\"" or "…, \"notes\":") is dropped back to the previous comma.
        if (cut.endsWith(":") || Regex(""",\s*"[^"]*"$""").containsMatchIn(cut)) {
            cut = cut.substring(0, cut.lastIndexOf(',').coerceAtLeast(0)).trimEnd()
        }
        // Pass 2: close whatever is still open at the cut.
        val open = ArrayDeque<Char>()
        inString = false; escaped = false
        for (c in cut) {
            when {
                escaped -> escaped = false
                c == '\\' && inString -> escaped = true
                c == '"' -> inString = !inString
                inString -> Unit
                c == '{' -> open.addLast('}')
                c == '[' -> open.addLast(']')
                c == '}' || c == ']' -> open.removeLastOrNull()
            }
        }
        return cut + open.reversed().joinToString("")
    }

    companion object {
        private const val TAG = "Digester"
        const val SKIPPED = """{"skipped":true}"""

        // Long keys are current; short keys (s, v, x) come from an earlier prompt version.
        fun summary(json: JSONObject): String = json.optString("s").ifBlank { json.optString("summary") }
        fun values(json: JSONObject): JSONObject? = json.optJSONObject("v") ?: json.optJSONObject("values")
        fun sensitive(json: JSONObject): Boolean = json.optBoolean("x", json.optBoolean("sensitive"))

        private val SECRET_KEY = Regex("""pass|pw|pin|card|cvv|code|passport|ssn|\bid\b|licen""", RegexOption.IGNORE_CASE)
        fun hasSecretValue(json: JSONObject): Boolean =
            values(json)?.keys()?.asSequence()?.any { SECRET_KEY.containsMatchIn(it) } == true
    }
}
