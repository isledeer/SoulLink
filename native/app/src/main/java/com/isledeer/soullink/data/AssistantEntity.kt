package com.isledeer.soullink.data

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import kotlinx.coroutines.flow.Flow
import org.json.JSONArray
import org.json.JSONObject

/**
 * 助手 = 一个「智能体」的完整配置（对标 RikkaHub 的 Assistant）。
 *
 * 列表/映射类字段统一以 JSON 字符串落库（Room 不需要 TypeConverter）：
 *   tags / localTools / mcpServers / enabledSkills → JSON 数组
 *   customHeaders / customBodies                  → JSON 对象
 * 读取时用下面几个扩展函数转成 Kotlin 集合。
 */
@Entity(tableName = "assistants")
data class AssistantEntity(
    @PrimaryKey val id: String,
    val name: String,
    val avatar: String? = null,
    val tags: String = "[]",
    val chatModelId: String? = null,
    val temperature: Float? = null,
    val topP: Float? = null,
    val maxTokens: Int? = null,
    val systemPrompt: String = "",
    val messageTemplate: String? = null,
    /** 0 = 不限制 */
    val contextMessageLimit: Int = 0,
    val enableMemory: Boolean = false,
    val localTools: String = "[]",
    val mcpServers: String = "[]",
    val enabledSkills: String = "[]",
    val customHeaders: String = "{}",
    val customBodies: String = "{}",
    val workspaceId: String? = null,
    val enableWebSearch: Boolean = false,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
) {
    fun tagList(): List<String> = parseStringArray(tags)
    fun toolList(): List<String> = parseStringArray(localTools)
    fun mcpList(): List<String> = parseStringArray(mcpServers)
    fun skillList(): List<String> = parseStringArray(enabledSkills)
    fun headerMap(): Map<String, String> = parseStringMap(customHeaders)
    fun bodyMap(): Map<String, String> = parseStringMap(customBodies)

    companion object {
        const val DEFAULT_ID = "assistant-default"

        fun defaultAssistant(): AssistantEntity = AssistantEntity(
            id = DEFAULT_ID,
            name = "通用助手",
            avatar = "🦌",
            systemPrompt = "你是 SoulLink 的助手，回答准确、简洁、直接。",
            tags = "[\"通用\"]",
        )

        fun parseStringArray(raw: String?): List<String> = runCatching {
            val array = JSONArray(raw ?: "[]")
            (0 until array.length()).mapNotNull { i -> array.optString(i).takeIf { it.isNotBlank() } }
        }.getOrElse { emptyList() }

        fun parseStringMap(raw: String?): Map<String, String> = runCatching {
            val obj = JSONObject(raw ?: "{}")
            obj.keys().asSequence().associateWith { key -> obj.optString(key) }
        }.getOrElse { emptyMap() }
    }
}

@Dao
interface AssistantDao {

    @Query("SELECT * FROM assistants ORDER BY updatedAt DESC")
    fun observeAll(): Flow<List<AssistantEntity>>

    @Query("SELECT * FROM assistants ORDER BY updatedAt DESC")
    suspend fun loadAll(): List<AssistantEntity>

    @Query("SELECT * FROM assistants WHERE id = :id")
    suspend fun findById(id: String): AssistantEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(assistant: AssistantEntity)

    @Query("DELETE FROM assistants WHERE id = :id")
    suspend fun delete(id: String)
}