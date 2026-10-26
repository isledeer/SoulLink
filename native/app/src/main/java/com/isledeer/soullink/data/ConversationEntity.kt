package com.isledeer.soullink.data

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

/**
 * 会话（存档位）。v3 起必须绑定 assistantId：每次新建对话都关联一个助手。
 * systemPrompt / maxTokens / temperature 是「对话级覆盖」，优先级最高。
 */
@Entity(tableName = "conversations")
data class ConversationEntity(
    @PrimaryKey val id: String,
    val title: String,
    val assistantId: String = AssistantEntity.DEFAULT_ID,
    val systemPrompt: String = "",
    val maxTokens: Int = 0,
    val temperature: Double = -1.0,
    val createdAt: Long,
    val updatedAt: Long,
) {
    /** 0 / 负数 = 未覆盖，回落到助手或全局默认 */
    val hasMaxTokensOverride: Boolean get() = maxTokens > 0
    val hasTemperatureOverride: Boolean get() = temperature >= 0.0
}

@Dao
interface ConversationDao {

    @Query("SELECT * FROM conversations ORDER BY updatedAt DESC")
    fun observeAll(): Flow<List<ConversationEntity>>

    @Query("SELECT * FROM conversations ORDER BY updatedAt DESC")
    suspend fun loadAll(): List<ConversationEntity>

    @Query("SELECT * FROM conversations WHERE id = :id")
    suspend fun findById(id: String): ConversationEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(conversation: ConversationEntity)

    @Query("DELETE FROM conversations WHERE id = :id")
    suspend fun delete(id: String)

    @Query("UPDATE conversations SET title = :title, updatedAt = :updatedAt WHERE id = :id")
    suspend fun rename(id: String, title: String, updatedAt: Long)
}