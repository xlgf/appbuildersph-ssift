package com.screenshotbutler.index

import android.content.Context
import android.net.Uri
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.label.ImageLabeling
import com.google.mlkit.vision.label.defaults.ImageLabelerOptions
import kotlinx.coroutines.tasks.await

/**
 * On-device image labeling (bundled ML Kit model, ~400 everyday categories) for screenshots
 * that OCR can't describe: photos, memes, maps. Gives them searchable words like "food" or "dog".
 */
class ImageLabeler(private val context: Context) {
    private val labeler = ImageLabeling.getClient(
        ImageLabelerOptions.Builder().setConfidenceThreshold(MIN_CONFIDENCE).build()
    )

    suspend fun label(uri: Uri): List<String> {
        val image = InputImage.fromFilePath(context, uri)
        return labeler.process(image).await()
            .sortedByDescending { it.confidence }
            .take(MAX_LABELS)
            .map { it.text.lowercase() }
    }

    companion object {
        private const val MIN_CONFIDENCE = 0.7f
        private const val MAX_LABELS = 5

        /** Fewer letters than this and OCR has little to go on, so the image gets labeled too. */
        private const val TEXT_LETTERS_THRESHOLD = 40

        fun needsLabels(ocrText: String): Boolean = ocrText.count { it.isLetter() } < TEXT_LETTERS_THRESHOLD
    }
}
