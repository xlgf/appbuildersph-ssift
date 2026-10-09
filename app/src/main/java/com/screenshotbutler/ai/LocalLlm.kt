package com.screenshotbutler.ai

import android.content.Context
import android.net.Uri
import android.util.Log
import com.google.mediapipe.tasks.genai.llminference.LlmInference
import com.google.mediapipe.tasks.genai.llminference.LlmInferenceSession
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import java.util.concurrent.TimeUnit

/**
 * Wraps the MediaPipe LLM Inference engine. One engine instance, loaded once and kept in memory;
 * calls are serialized because the engine is not safe to use from several threads at once.
 */
class LocalLlm(private val context: Context) {

    sealed interface State {
        data object Missing : State
        data object Loading : State
        data object Ready : State
        data class Failed(val message: String) : State
    }

    private val _state = MutableStateFlow<State>(State.Missing)
    val state: StateFlow<State> = _state.asStateFlow()

    private var engine: LlmInference? = null
    private val mutex = Mutex()

    private val appModel = File(context.filesDir, MODEL_NAME)

    /** App storage first; /data/local/tmp/llm is where `adb push` puts it during development. */
    private fun findModel(): File? =
        listOf(appModel, File("/data/local/tmp/llm/$MODEL_NAME"))
            .firstOrNull { it.canRead() && it.length() > 0 }

    suspend fun load() = mutex.withLock {
        if (engine != null) return@withLock
        val model = findModel() ?: run {
            _state.value = State.Missing
            return@withLock
        }
        _state.value = State.Loading
        try {
            engine = withContext(Dispatchers.IO) {
                val backends = if (TRY_GPU) listOf(LlmInference.Backend.GPU, LlmInference.Backend.CPU)
                else listOf(LlmInference.Backend.CPU)
                var lastError: Exception? = null
                backends.firstNotNullOfOrNull { backend ->
                    val started = android.os.SystemClock.elapsedRealtime()
                    try {
                        val options = LlmInference.LlmInferenceOptions.builder()
                            .setModelPath(model.absolutePath)
                            .setMaxTokens(MAX_TOKENS)
                            .setPreferredBackend(backend)
                            .build()
                        LlmInference.createFromOptions(context, options).also {
                            Log.i(TAG, "Model loaded on $backend in ${android.os.SystemClock.elapsedRealtime() - started} ms")
                        }
                    } catch (e: Exception) {
                        Log.w(TAG, "Model failed to load on $backend", e)
                        lastError = e
                        null
                    }
                } ?: throw (lastError ?: IllegalStateException("No backend could load the model"))
            }
            _state.value = State.Ready
        } catch (e: Exception) {
            Log.e(TAG, "Model failed to load", e)
            _state.value = State.Failed(e.message ?: "Model failed to load")
        }
    }

    /** Copies a model the user picked (Storage Access Framework) into app storage, then loads it. */
    suspend fun import(uri: Uri) {
        withContext(Dispatchers.IO) {
            val tmp = File(context.filesDir, "$MODEL_NAME.tmp")
            context.contentResolver.openInputStream(uri)!!.use { input ->
                tmp.outputStream().use { input.copyTo(it) }
            }
            tmp.renameTo(appModel)
        }
        // Unload the current model so the imported one is used right away, not after a restart.
        mutex.withLock {
            engine?.close()
            engine = null
        }
        load()
    }

    /**
     * Generates a reply, streaming it so it can be stopped after [maxChars] characters or as soon as it
     * starts repeating itself. Without this cap a rare runaway reply ("7777…") took over two minutes.
     */
    suspend fun generate(prompt: String, maxChars: Int): String = mutex.withLock {
        val llm = engine ?: error("Model not loaded")
        withContext(Dispatchers.Default) {
            val sessionOptions = LlmInferenceSession.LlmInferenceSessionOptions.builder()
                .setTemperature(0.2f)
                .setTopK(40)
                .build()
            val session = LlmInferenceSession.createFromOptions(llm, sessionOptions)
            val reply = StringBuffer() // appended from MediaPipe's callback thread
            try {
                session.addQueryChunk(prompt)
                val future = session.generateResponseAsync { partial, _ -> reply.append(partial) }
                var cut = false
                while (!future.isDone) {
                    if (reply.length >= maxChars || isRepeating(reply)) {
                        session.cancelGenerateResponseAsync()
                        cut = true
                        break
                    }
                    delay(POLL_MS)
                }
                // A short reply can finish before its streamed pieces arrive, so prefer the complete result;
                // the streamed text is only used when we cut the reply off ourselves.
                val complete = runCatching { future.get(CANCEL_WAIT_MS, TimeUnit.MILLISECONDS) }.getOrNull()
                (if (!cut && !complete.isNullOrEmpty()) complete else reply.toString()).take(maxChars).trim()
            } finally {
                session.close()
            }
        }
    }

    /** The same character 12+ times in a row at the end of the reply means the model is stuck in a loop. */
    private fun isRepeating(reply: CharSequence): Boolean {
        if (reply.length < REPEAT_RUN) return false
        val last = reply[reply.length - 1]
        return (1..REPEAT_RUN).all { reply[reply.length - it] == last } && last != ' '
    }

    companion object {
        const val MODEL_NAME = "model.task"
        private const val MAX_TOKENS = 2048 // prompt + answer

        /** GPU first, CPU fallback. Set false to force CPU if the GPU turns out slower or less accurate. */
        private const val TRY_GPU = true
        private const val POLL_MS = 30L
        private const val CANCEL_WAIT_MS = 2_000L
        private const val REPEAT_RUN = 12
        private const val TAG = "LocalLlm"
    }
}
