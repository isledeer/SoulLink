package com.isledeer.soullink.data

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query

/**
 * 消息树节点。
 * - 每个节点代表「一轮」在树上的位置：parentId 指向它上一条。
 * - 同一 parent 下可以有多个节点（重新生成 = 新增兄弟节点）。
 * - selectIndex 只对「父节点选哪个孩子」有意义：挂在父节点上会绕，所以这里
 *   放在节点自身无意义，改为「父节点记录选中第几个孩子」由 UI 层派生。
 *
 * 说明：为了让实现简单可靠，这里采用「兄弟节点 = 变体」的经典结构：
 *   同一 parentId 下的兄弟按 createdAt 排序，第 N 个就是第 N 个变体。
 *   当前选中哪个由 ConversationEntity/selectPath 决定，退化为「取最后一个」。
 */
@Entity(tableName = "message_nodes")
data class MessageNodeEntity(
    @PrimaryKey val id: String,
    val conversationId: String,
    val parentId: String?,
    /** 当前选中的变体下标（同一 node 下 ui_messages 按 createdAt 排序） */
    val selectIndex: Int = 0,
    val createdAt: Long,
)

/**
 * 节点下的一条具体消息（一个变体）。
 * parts 用 JSON 数组存，形如：
 *   [{"type":"text","text":"..."},{"type":"image","base64":"..."}]
 */
@Entity(tableName = "ui_messages")
data class UIMessageEntity(
    @PrimaryKey val id: String,
    val nodeId: String,
    val role: String,
    val parts: String,
    val reasoning: String? = null,
    val usageTokens: Int = 0,
    val generationSpeed: Double = 0.0,
    val isError: Boolean = false,
    val isFavorite: Boolean = false,
    val createdAt: Long,
)

@Dao
interface MessageTreeDao {

    @Query("SELECT * FROM message_nodes WHERE conversationId = :conversationId ORDER BY createdAt ASC")
    suspend fun loadNodes(conversationId: String): List<MessageNodeEntity>

    @Query("SELECT * FROM ui_messages WHERE nodeId = :nodeId ORDER BY createdAt ASC")
    suspend fun loadVariants(nodeId: String): List<UIMessageEntity>

    @Query("SELECT * FROM ui_messages WHERE nodeId IN (SELECT id FROM message_nodes WHERE conversationId = :conversationId) ORDER BY createdAt ASC")
    suspend fun loadAllMessages(conversationId: String): List<UIMessageEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertNode(node: MessageNodeEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertMessage(message: UIMessageEntity)

    @Query("DELETE FROM ui_messages WHERE nodeId = :nodeId")
    suspend fun deleteVariants(nodeId: String)

    @Query("DELETE FROM message_nodes WHERE conversationId = :conversationId")
    suspend fun clearNodes(conversationId: String)

    @Query("DELETE FROM ui_messages WHERE nodeId IN (SELECT id FROM message_nodes WHERE conversationId = :conversationId)")
    suspend fun clearMessages(conversationId: String)
}