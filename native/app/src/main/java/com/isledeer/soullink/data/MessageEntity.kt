package com.isledeer.soullink.data

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * 消息落盘实体。
 *
 * 刻意保留 reasoning（思维链）：角色扮演里「模型想过什么」往往比正文更值钱，
 * 而且重放/编辑重发时要靠它还原上下文。
 *
 * conversationId 现在固定 "default"，等支持多存档时直接当外键用，不用改表结构。
 */
@Entity(tableName = "messages")
data class MessageEntity(
    @PrimaryKey val id: String,
    val conversationId: String = DEFAULT_CONVERSATION,
    val role: String,
    val content: String,
    val reasoning: String?,
    val createdAt: Long,
) {
    companion object {
        const val DEFAULT_CONVERSATION = "default"
    }
}