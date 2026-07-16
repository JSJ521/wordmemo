package com.wordmemo.app.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.wordmemo.app.data.local.entity.TagEntity
import com.wordmemo.app.data.local.entity.WordTagCrossRef

@Dao
interface TagDao {
    @Query("SELECT * FROM tags ORDER BY name ASC")
    suspend fun getAll(): List<TagEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(tag: TagEntity): Long

    @Query("DELETE FROM tags WHERE id = :id")
    suspend fun delete(id: Long)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun assignTagToWord(crossRef: WordTagCrossRef)

    @Query("DELETE FROM word_tags WHERE word_id = :wordId AND tag_id = :tagId")
    suspend fun removeTagFromWord(wordId: Long, tagId: Long)

    @Query("SELECT t.* FROM tags t INNER JOIN word_tags wt ON t.id = wt.tag_id WHERE wt.word_id = :wordId")
    suspend fun getTagsForWord(wordId: Long): List<TagEntity>
}
