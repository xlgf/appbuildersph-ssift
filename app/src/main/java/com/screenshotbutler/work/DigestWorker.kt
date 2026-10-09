package com.screenshotbutler.work

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.ServiceInfo
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.ForegroundInfo
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.screenshotbutler.ButlerApp
import kotlinx.coroutines.delay
import java.util.concurrent.TimeUnit

/**
 * Lets the on-device LLM read every screenshot. The periodic run waits for the charger and pauses between
 * batches to keep the phone cool; "Digest now" runs as a foreground job (no 10-minute background limit)
 * with no pauses.
 */
class DigestWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val now = inputData.getBoolean(KEY_NOW, false)
        if (now) setForeground(foregroundInfo())

        val digester = (applicationContext as ButlerApp).container.digester
        while (!isStopped) {
            val processed = digester.digestBatch(BATCH)
            if (processed == 0) break
            if (!now) delay(PAUSE_MS) // overnight run: let the phone cool between batches
        }
        return Result.success()
    }

    override suspend fun getForegroundInfo(): ForegroundInfo = foregroundInfo()

    private fun foregroundInfo(): ForegroundInfo {
        val manager = applicationContext.getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL, "Reading screenshots", NotificationManager.IMPORTANCE_LOW)
        )
        val notification = NotificationCompat.Builder(applicationContext, CHANNEL)
            .setSmallIcon(android.R.drawable.ic_popup_sync)
            .setContentTitle("Reading your screenshots")
            .setContentText("On this phone. Nothing is uploaded.")
            .setOngoing(true)
            .build()
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ForegroundInfo(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        } else {
            ForegroundInfo(NOTIFICATION_ID, notification)
        }
    }

    companion object {
        private const val BATCH = 5
        private const val PAUSE_MS = 2_000L
        private const val PERIODIC = "digest-periodic"
        private const val NOW = "digest-now"
        private const val KEY_NOW = "now"
        private const val CHANNEL = "digest"
        private const val NOTIFICATION_ID = 42

        /** Runs hourly whenever the phone is charging and the battery isn't low. */
        fun schedule(context: Context) {
            val constraints = Constraints.Builder()
                .setRequiresCharging(true)
                .setRequiresBatteryNotLow(true)
                .build()
            val request = PeriodicWorkRequestBuilder<DigestWorker>(1, TimeUnit.HOURS)
                .setConstraints(constraints)
                .build()
            WorkManager.getInstance(context)
                .enqueueUniquePeriodicWork(PERIODIC, ExistingPeriodicWorkPolicy.KEEP, request)
        }

        /** Demo/dev shortcut from Settings: digest now, in the foreground, charger or not. */
        fun runNow(context: Context) {
            val request = OneTimeWorkRequestBuilder<DigestWorker>()
                .setInputData(workDataOf(KEY_NOW to true))
                .build()
            WorkManager.getInstance(context).enqueueUniqueWork(NOW, ExistingWorkPolicy.KEEP, request)
        }
    }
}
