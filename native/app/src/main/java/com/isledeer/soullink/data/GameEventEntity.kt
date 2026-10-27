package com.isledeer.soullink.data

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Index
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query

/**
 * 账本事件（append-only）。
 * 关键：messageId 指向「消息变体」；派生状态只认当前选中路径上的消息。
 */
@Entity(
    tableName = "game_events",
    indices = [Index("sessionId"), Index("messageId"), Index("nodeId")],
)
data class GameEventEntity(
    @PrimaryKey val id: String,
    val sessionId: String,
    val conversationId: String,
    val messageId: String,
    val nodeId: String,
    val seq: Long,
    val kind: String,
    val targetId: String? = null,
    val payload: String = "{}",
    val source: String = "TOOL",
    val createdAt: Long,
)

@Dao
interface GameEventDao {

    @Query("SELECT * FROM game_events WHERE sessionId = :sessionId ORDER BY seq ASC")
    suspend fun forSession(sessionId: String): List<GameEventEntity>

    @Query("SELECT * FROM game_events WHERE messageId = :messageId")
    suspend fun forMessage(messageId: String): List<GameEventEntity>

    @Query("DELETE FROM game_events WHERE messageId = :messageId")
    suspend fun deleteForMessage(messageId: String)

    @Query("DELETE FROM game_events WHERE nodeId = :nodeId")
    suspend fun deleteForNode(nodeId: String)

    @Query("DELETE FROM game_events WHERE conversationId = :conversationId")
    suspend fun deleteForConversation(conversationId: String)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(events: List<GameEventEntity>)
}
