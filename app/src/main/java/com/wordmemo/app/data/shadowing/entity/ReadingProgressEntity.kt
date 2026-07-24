package com.wordmemo.app.data.shadowing.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * 阅读进度持久化。记录 EPUB / TXT 精听阅读的最后位置。
 *
 * @param bookKey 书籍唯一标识（文件名 hash）
 * @param chapterIndex 当前章节索引
 * @param sentenceIndex 当前句子索引
 * @param updatedAt 更新时间戳
 */
@Entity(tableName = "reading_progress")
data class ReadingProgressEntity(
    @PrimaryKey val bookKey: String,
    val chapterIndex: Int = 0,
    val sentenceIndex: Int = 0,
    val updatedAt: Long = System.currentTimeMillis()
)
