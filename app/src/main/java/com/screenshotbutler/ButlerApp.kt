package com.screenshotbutler

import android.app.Application
import android.content.Context
import com.screenshotbutler.ai.AskEngine
import com.screenshotbutler.ai.Digester
import com.screenshotbutler.ai.LocalLlm
import com.screenshotbutler.ai.ShotSearch
import com.screenshotbutler.data.AppSettings
import com.screenshotbutler.data.ButlerDatabase
import com.screenshotbutler.data.ScreenshotSource
import com.screenshotbutler.index.ImageLabeler
import com.screenshotbutler.index.Indexer
import com.screenshotbutler.index.OcrEngine
import com.screenshotbutler.work.DigestWorker
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class ButlerApp : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
        DigestWorker.schedule(this)
        // Load the model at launch so the first question has no cold start.
        container.scope.launch { container.llm.load() }
    }
}

/** Manual dependency wiring; small enough that a DI framework isn't worth it. */
class AppContainer(context: Context) {
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    val db = ButlerDatabase.create(context)
    val dao = db.shots()
    val settings = AppSettings(context)
    val llm = LocalLlm(context)
    val indexer = Indexer(ScreenshotSource(context, settings), OcrEngine(context), ImageLabeler(context), dao, settings)
    val search = ShotSearch(dao)
    val ask = AskEngine(search, llm)
    val digester = Digester(dao, llm)
}
