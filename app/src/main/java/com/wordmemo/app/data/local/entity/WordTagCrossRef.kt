package com.wordmemo.app.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index

@Entity(
    tableName = "word_tags",
    primaryKeys = ["word_id", "tag_id"],
    foreignKeys = [
        ForeignKey(
            entity = WordEntity::class,
            parentColumns = ["id"],
            childColumns = ["word_id"],
            onDelete = ForeignKey.CASCADE
        ),
        ForeignKey(
            entity = TagEntity::class,
            parentColumns = ["id"],
            childColumns = ["tag_id"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("word_id"), Index("tag_id")]
)
data class WordTagCrossRef(
    @ColumnInfo(name = "word_id")
    val wordId: Long,
    @ColumnInfo(name = "tag_id")
    val tagId: Long
)
