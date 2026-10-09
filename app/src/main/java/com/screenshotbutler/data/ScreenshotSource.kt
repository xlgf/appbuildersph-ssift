package com.screenshotbutler.data

import android.content.ContentUris
import android.content.Context
import android.net.Uri
import android.provider.MediaStore
import java.text.SimpleDateFormat
import java.util.Locale

data class ScreenshotRef(val id: Long, val uri: Uri, val takenAt: Long)

/**
 * Lists the device's screenshots through MediaStore. Reads nothing but metadata.
 * In demo mode only the ButlerDemo subfolder is listed, so personal screenshots stay out of the demo.
 */
class ScreenshotSource(private val context: Context, private val settings: AppSettings) {

    fun list(): List<ScreenshotRef> {
        val collection = MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL)
        val projection = arrayOf(
            MediaStore.Images.Media._ID,
            MediaStore.Images.Media.DATE_TAKEN,
            MediaStore.Images.Media.DATE_ADDED,
            MediaStore.Images.Media.DISPLAY_NAME,
        )
        // Most OEMs save to Pictures/Screenshots or DCIM/Screenshots.
        // Demo mode reads only the demo folder; otherwise the demo folder is left out, so its staged
        // screenshots ("$85.00 sent to Alex") never mix with the user's own on Home.
        val path = MediaStore.Images.Media.RELATIVE_PATH
        val demoFolder = "%Screenshots/${AppSettings.DEMO_FOLDER}%"
        val (selection, args) =
            if (settings.demoMode.value) "$path LIKE ?" to arrayOf(demoFolder)
            else "$path LIKE ? AND $path NOT LIKE ?" to arrayOf("%Screenshots%", demoFolder)

        val result = mutableListOf<ScreenshotRef>()
        context.contentResolver.query(collection, projection, selection, args, null)?.use { cursor ->
            val idCol = cursor.getColumnIndexOrThrow(MediaStore.Images.Media._ID)
            val takenCol = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DATE_TAKEN)
            val addedCol = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DATE_ADDED)
            val nameCol = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DISPLAY_NAME)
            while (cursor.moveToNext()) {
                val id = cursor.getLong(idCol)
                result += ScreenshotRef(
                    id = id,
                    uri = ContentUris.withAppendedId(collection, id),
                    takenAt = takenAt(cursor.getLong(takenCol), cursor.getString(nameCol), cursor.getLong(addedCol)),
                )
            }
        }
        val newestFirst = result.sortedByDescending { it.takenAt }
        val limit = settings.realLimit
        return if (!settings.demoMode.value && limit > 0) newestFirst.take(limit) else newestFirst
    }

    /**
     * When the screenshot was taken: MediaStore's DATE_TAKEN, else the time in the file name
     * ("Screenshot_20261007-152000…", which copied or restored files keep), and only then the time the
     * file was added to this phone.
     */
    private fun takenAt(dateTaken: Long, name: String?, dateAddedSeconds: Long): Long {
        if (dateTaken > 0) return dateTaken
        FILE_DATE.find(name.orEmpty())?.let { m ->
            runCatching { FILE_DATE_FORMAT.parse(m.groupValues[1] + m.groupValues[2])?.time }.getOrNull()?.let { return it }
        }
        return dateAddedSeconds * 1000
    }

    private companion object {
        val FILE_DATE = Regex("""(\d{8})[-_](\d{6})""")
        val FILE_DATE_FORMAT = SimpleDateFormat("yyyyMMddHHmmss", Locale.US)
    }
}
