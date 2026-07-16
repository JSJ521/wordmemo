package com.wordmemo.app.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index

@Entity(
    tableName = "word_groups",
    primaryKeys = ["word_id", "group_id"],
    foreignKeys = [
        ForeignKey(
            entity = WordEntity::class,
            parentColumns = ["id"],
            childColumns = ["word_id"],
            onDelete = ForeignKey.CASCADE
        ),
        ForeignKey(
            entity = GroupEntity::class,
            parentColumns = ["id"],
            childColumns = ["group_id"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("word_id"), Index("group_id")]
)
data class WordGroupCrossRef(
    @ColumnInfo(name = "word_id")
    val wordId: Long,
    @ColumnInfo(name = "group_id")
    val groupId: Long
)
