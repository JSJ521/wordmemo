package com.wordmemo.app.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.wordmemo.app.data.local.dao.AiContentDao
import com.wordmemo.app.data.local.dao.AppConfigDao
import com.wordmemo.app.data.local.dao.FlashcardDao
import com.wordmemo.app.data.local.dao.FsrsParamsDao
import com.wordmemo.app.data.local.dao.GroupDao
import com.wordmemo.app.data.local.dao.ReviewLogDao
import com.wordmemo.app.data.local.dao.TagDao
import com.wordmemo.app.data.local.dao.WordDao
import com.wordmemo.app.data.local.entity.AiMnemonicEntity
import com.wordmemo.app.data.local.entity.AiRelationEntity
import com.wordmemo.app.data.local.entity.AppConfigEntity
import com.wordmemo.app.data.local.entity.FlashcardEntity
import com.wordmemo.app.data.local.entity.FsrsParamsEntity
import com.wordmemo.app.data.local.entity.GroupEntity
import com.wordmemo.app.data.local.entity.ReviewLogEntity
import com.wordmemo.app.data.local.entity.TagEntity
import com.wordmemo.app.data.local.entity.WordEntity
import com.wordmemo.app.data.local.entity.WordGroupCrossRef
import com.wordmemo.app.data.local.entity.WordTagCrossRef

@Database(
    entities = [
        WordEntity::class,
        GroupEntity::class,
        WordGroupCrossRef::class,
        TagEntity::class,
        WordTagCrossRef::class,
        FlashcardEntity::class,
        ReviewLogEntity::class,
        FsrsParamsEntity::class,
        AiMnemonicEntity::class,
        AiRelationEntity::class,
        AppConfigEntity::class
    ],
    version = 2,
    exportSchema = false
)
abstract class WordMemoDatabase : RoomDatabase() {
    abstract fun wordDao(): WordDao
    abstract fun groupDao(): GroupDao
    abstract fun tagDao(): TagDao
    abstract fun flashcardDao(): FlashcardDao
    abstract fun reviewLogDao(): ReviewLogDao
    abstract fun fsrsParamsDao(): FsrsParamsDao
    abstract fun aiContentDao(): AiContentDao
    abstract fun appConfigDao(): AppConfigDao

    companion object {
        @Volatile private var instance: WordMemoDatabase? = null

        fun getInstance(context: Context): WordMemoDatabase {
            return instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    WordMemoDatabase::class.java,
                    "wordmemo.db"
                ).fallbackToDestructiveMigration().build().also { instance = it }
            }
        }
    }
}
