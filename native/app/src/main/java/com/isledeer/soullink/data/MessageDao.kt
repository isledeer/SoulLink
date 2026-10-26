package com.isledeer.soullink.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface MessageDao {

    /** 按时间正序观察整个会话；Compose 侧直接 collect 即可 */
    @Query("SELECT * FROM messages WHERE conversationId = :conversationId ORDER BY createdAt ASC")
    fun observeConversation(conversationId: String = MessageEntity.DEFAULT_CONVERSATION): Flow<List<MessageEntity>>

    /** 一次性读取（启动时拉历史） */
    @Query("SELECT * FROM messages WHERE conversationId = :conversationId ORDER BY createdAt ASC")
    suspend fun loadConversation(conversationId: String = MessageEntity.DEFAULT_CONVERSATION): List<MessageEntity>

    /** upsert：流式结束后写最终版本，重发时覆盖同一条 */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(message: MessageEntity)

    @Query("DELETE FROM messages WHERE conversationId = :conversationId")
    suspend fun clearConversation(conversationId: String = MessageEntity.DEFAULT_CONVERSATION)
}