package com.isledeer.soullink.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [ConversationEntity::class, MessageEntity::class],
    // v2：新增会话表；messages 增加多模态与统计字段
    version = 2,
    exportSchema = false,
)
abstract class AppDatabase : RoomDatabase() {

    abstract fun conversationDao(): ConversationDao
    abstract fun messageDao(): MessageDao

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
                    // 仍在早期迭代，结构变就直接重建；有真实用户数据前不写 Migration
                    .fallbackToDestructiveMigration()
                    .build()
                    .also { instance = it }
            }
    }
}