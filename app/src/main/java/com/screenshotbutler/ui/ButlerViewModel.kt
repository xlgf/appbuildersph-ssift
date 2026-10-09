package com.screenshotbutler.ui

import android.net.TrafficStats
import android.net.Uri
import android.os.Process
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.screenshotbutler.AppContainer
import com.screenshotbutler.ai.AskResult
import com.screenshotbutler.data.Shot
import com.screenshotbutler.index.Tagger
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class FolderCount(val tag: String, val label: String, val count: Int, val media: Boolean)

sealed interface AskUi {
    data object Idle : AskUi
    data class Thinking(val question: String) : AskUi
    data class Done(val question: String, val result: AskResult) : AskUi
}

class ButlerViewModel(private val c: AppContainer) : ViewModel() {

    private fun <T> Flow<T>.state(initial: T) = stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), initial)

    val total = c.dao.count().state(0)
    val digested = c.dao.digestedCount().state(0)
    val readByAi = c.dao.readByAiCount().state(0)
    val sensitiveCount = c.dao.sensitiveCount().state(0)
    val recent = c.dao.recent(12).state(emptyList())
    val progress = c.indexer.progress
    val llmState = c.llm.state

    val folders: StateFlow<List<FolderCount>> = c.dao.allTags().map { rows ->
        val counts = rows.flatMap { it.split(' ') }.filter { it.isNotBlank() }.groupingBy { it }.eachCount()
        // Only folders this phone actually has, biggest first: Home is built from these.
        Tagger.folders.map { FolderCount(it.tag, it.label, counts[it.tag] ?: 0, it.media) }
            .filter { it.count > 0 }
            .sortedByDescending { it.count }
    }.state(emptyList())

    val ticketHighlight = c.dao.byTag("ticket").map(HomeHighlights::ticket).state(null)
    val receiptHighlight = c.dao.byTag("receipt").map(HomeHighlights::receipt).state(null)

    /** Bytes this app has sent over any network since boot. With no INTERNET permission it stays 0. */
    fun bytesSent(): Long = TrafficStats.getUidTxBytes(Process.myUid()).coerceAtLeast(0)

    fun byTag(tag: String) = c.dao.byTag(tag)
    fun sensitive() = c.dao.sensitive()
    fun shot(id: Long) = c.dao.observe(id)

    fun indexNew() = c.scope.launch { c.indexer.indexNew() }

    private val _ask = MutableStateFlow<AskUi>(AskUi.Idle)
    val ask: StateFlow<AskUi> = _ask.asStateFlow()

    private var askJob: Job? = null

    /** A new question cancels the previous one, and a late result for an old question is never shown. */
    fun ask(question: String) {
        val current = _ask.value
        if (current is AskUi.Done && current.question == question) return
        if (current is AskUi.Thinking && current.question == question) return
        askJob?.cancel()
        _ask.value = AskUi.Thinking(question)
        askJob = viewModelScope.launch {
            val result = c.ask.ask(question)
            if ((_ask.value as? AskUi.Thinking)?.question == question) _ask.value = AskUi.Done(question, result)
        }
    }

    fun importModel(uri: Uri) = c.scope.launch { c.llm.import(uri) }

    fun removeFromIndex(shots: List<Shot>) = viewModelScope.launch { c.dao.delete(shots.map { it.mediaId }) }

    fun deleteIndex() = viewModelScope.launch { c.dao.deleteAll() }

    val demoMode = c.settings.demoMode

    /** Switching sources wipes the index and rebuilds it from the newly selected folder. */
    fun setDemoMode(on: Boolean) = c.scope.launch {
        if (on == c.settings.demoMode.value) return@launch
        c.settings.setDemoMode(on)
        c.dao.deleteAll()
        c.indexer.indexNew()
    }
}
