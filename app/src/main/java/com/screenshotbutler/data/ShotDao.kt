package com.screenshotbutler.data

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface ShotDao {
    @Upsert
    suspend fun upsert(shots: List<Shot>)

    @Query("SELECT mediaId FROM shots")
    suspend fun knownIds(): List<Long>

    @Query("SELECT COUNT(*) FROM shots")
    fun count(): Flow<Int>

    @Query("SELECT COUNT(*) FROM shots")
    suspend fun total(): Int

    /** Screenshots the digest has handled: read by the LLM, skipped as nothing-to-extract, or failed. */
    @Query("SELECT COUNT(*) FROM shots WHERE digest IS NOT NULL")
    fun digestedCount(): Flow<Int>

    /** Screenshots the LLM actually read and pulled facts from. */
    @Query("SELECT COUNT(*) FROM shots WHERE digest IS NOT NULL AND digest != '{}' AND digest NOT LIKE '%\"skipped\"%'")
    fun readByAiCount(): Flow<Int>

    @Query("SELECT tags FROM shots")
    fun allTags(): Flow<List<String>>

    @Query("SELECT * FROM shots ORDER BY takenAt DESC LIMIT :limit")
    fun recent(limit: Int): Flow<List<Shot>>

    @Query("SELECT * FROM shots WHERE (' ' || tags || ' ') LIKE '% ' || :tag || ' %' ORDER BY takenAt DESC")
    fun byTag(tag: String): Flow<List<Shot>>

    @Query("SELECT * FROM shots WHERE sensitive != '' ORDER BY takenAt DESC")
    fun sensitive(): Flow<List<Shot>>

    @Query("SELECT COUNT(*) FROM shots WHERE sensitive != ''")
    fun sensitiveCount(): Flow<Int>

    // No LIMIT: an unordered LIMIT dropped the right screenshot once a phone had hundreds of matches.
    // Search scores every match (and counts word frequency from them), which stays cheap at a few thousand rows.
    @Query(
        """SELECT shots.* FROM shots
           JOIN shots_fts ON shots.rowid = shots_fts.rowid
           WHERE shots_fts MATCH :match"""
    )
    suspend fun search(match: String): List<Shot>

    @Query("SELECT * FROM shots WHERE mediaId = :id")
    fun observe(id: Long): Flow<Shot?>

    @Query("SELECT * FROM shots WHERE mediaId IN (:ids)")
    suspend fun getAll(ids: List<Long>): List<Shot>

    @Query("SELECT * FROM shots WHERE digest IS NULL ORDER BY takenAt DESC LIMIT :limit")
    suspend fun undigested(limit: Int): List<Shot>

    @Query("UPDATE shots SET digest = :digest, tags = :tags, sensitive = :sensitive WHERE mediaId = :id")
    suspend fun updateDigest(id: Long, digest: String, tags: String, sensitive: String)

    @Query("SELECT * FROM shots")
    suspend fun all(): List<Shot>

    @Query("UPDATE shots SET tags = :tags WHERE mediaId = :id")
    suspend fun updateTags(id: Long, tags: String)

    @Query("DELETE FROM shots WHERE mediaId IN (:ids)")
    suspend fun delete(ids: List<Long>)

    @Query("DELETE FROM shots")
    suspend fun deleteAll()
}
