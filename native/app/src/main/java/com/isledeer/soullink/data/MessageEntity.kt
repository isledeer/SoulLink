package com.isledeer.soullink.data

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

/**
 * 消息实体。
 *
 * v2 新增：imageBase64（多模态）、generationSpeed（tok/s）、tokenCount、
 * isFavorite（收藏）。schema 变了但仍在早期，直接 destructive migration。
 */
@Entity(tableName = "messages")
data class MessageEntity(
    @PrimaryKey val id: String,
    val conversationId: String,
    val role: String,
    val content: String,
    val reasoning: String?,
    val imageBase64: String? = null,
    val generationSpeed: Double = 0.0,
    val tokenCount: Int = 0,
    val isFavorite: Boolean = false,
    val createdAt: Long,
) {
    companion object {
        /** 默认会话 id（旧版本遗留的常量，保留以兼容任何残留引用） */
        const val DEFAULT_CONVERSATION = "default"
    }
}

@Dao
interface MessageDao {

    @Query("SELECT * FROM messages WHERE conversationId = :conversationId ORDER BY createdAt ASC")
    fun observeConversation(conversationId: String): Flow<List<MessageEntity>>

    @Query("SELECT * FROM messages WHERE conversationId = :conversationId ORDER BY createdAt ASC")
    suspend fun loadConversation(conversationId: String): List<MessageEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(message: MessageEntity)

    @Query("DELETE FROM messages WHERE id = :id")
    suspend fun deleteById(id: String)

    @Query("DELETE FROM messages WHERE conversationId = :conversationId")
    suspend fun clearConversation(conversationId: String)

    @Query("UPDATE messages SET isFavorite = :favorite WHERE id = :id")
    suspend fun setFavorite(id: String, favorite: Boolean)
}