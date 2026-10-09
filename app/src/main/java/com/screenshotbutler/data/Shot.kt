package com.screenshotbutler.data

import android.net.Uri
import androidx.room.Entity
import androidx.room.Fts4
import androidx.room.PrimaryKey

/** One screenshot and everything Butler has learned about it. */
@Entity(tableName = "shots")
data class Shot(
    @PrimaryKey val mediaId: Long,     // MediaStore id; also the SQLite rowid
    val uri: String,
    val takenAt: Long,                 // epoch millis
    val text: String,                  // OCR output
    val tags: String,                  // space-separated, e.g. "ticket wifi"
    val sensitive: String = "",        // space-separated reasons, e.g. "card password"
    val digest: String? = null,        // JSON from the charging digest, null until digested
    val labels: String = "",           // image labels for low-text screenshots, e.g. "food dog"
) {
    val contentUri: Uri get() = Uri.parse(uri)
    val tagList: List<String> get() = tags.split(' ').filter { it.isNotBlank() }
    val sensitiveList: List<String> get() = sensitive.split(' ').filter { it.isNotBlank() }
    val labelList: List<String> get() = labels.split(',').map { it.trim() }.filter { it.isNotBlank() }
}

/** Full-text index over OCR text, folder tags and image labels, kept in sync by Room triggers. */
@Fts4(contentEntity = Shot::class)
@Entity(tableName = "shots_fts")
data class ShotFts(val text: String, val tags: String, val labels: String)
