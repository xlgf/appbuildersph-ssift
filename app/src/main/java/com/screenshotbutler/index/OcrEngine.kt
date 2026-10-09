package com.screenshotbutler.index

import android.content.Context
import android.net.Uri
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlinx.coroutines.tasks.await

/** On-device OCR with the bundled ML Kit model. */
class OcrEngine(private val context: Context) {
    private val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)

    suspend fun read(uri: Uri): String {
        val image = InputImage.fromFilePath(context, uri)
        return recognizer.process(image).await().text
    }
}
