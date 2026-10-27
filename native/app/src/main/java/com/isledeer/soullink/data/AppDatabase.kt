package com.isledeer.soullink.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [
        AssistantEntity::class,
        ConversationEntity::class,
        MessageNodeEntity::class,
        UIMessageEntity::class,
        GameSessionEntity::class,
        GameEventEntity::class,
    ],
    // v5：游戏账本（game_sessions / game_events）。破坏性迁移，旧数据清空。
    version = 5,
    exportSchema = false,
)
abstract class AppDatabase : RoomDatabase() {

    abstract fun assistantDao(): AssistantDao
    abstract fun conversationDao(): ConversationDao
    abstract fun messageTreeDao(): MessageTreeDao
    abstract fun gameSessionDao(): GameSessionDao
    abstract fun gameEventDao(): GameEventDao

    companion object {
        @Volatile
        private var instance: AppDatabase? = null

        fun get(context: Context): AppDatabase =
            instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "soullink.db",
                )
                    .fallbackToDestructiveMigration()
                    .build()
                    .also { instance = it }
            }
    }
}