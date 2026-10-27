package com.isledeer.soullink.data

import org.json.JSONArray
import org.json.JSONObject

/** 一个「轮次」：节点 + 当前选中的变体消息 */
data class ResolvedTurn(
    val node: MessageNodeEntity,
    val message: UIMessageEntity,
    val variantCount: Int,
    val variantIndex: Int,
)

/** parts 编解码（text / image） */
object MessageParts {

    fun encode(text: String, imageBase64: String?, choices: List<String> = emptyList()): String {
        val array = JSONArray()
        if (text.isNotBlank()) {
            array.put(JSONObject().put("type", "text").put("text", text))
        }
        if (!imageBase64.isNullOrBlank()) {
            array.put(JSONObject().put("type", "image").put("base64", imageBase64))
        }
        if (choices.isNotEmpty()) {
            val items = JSONArray()
            choices.forEach { items.put(it) }
            array.put(JSONObject().put("type", "choices").put("items", items))
        }
        return array.toString()
    }

    fun text(parts: String): String = runCatching {
        val array = JSONArray(parts)
        buildString {
            for (i in 0 until array.length()) {
                val obj = array.optJSONObject(i) ?: continue
                if (obj.optString("type") == "text") append(obj.optString("text"))
            }
        }
    }.getOrElse { "" }

    fun image(parts: String): String? = runCatching {
        val array = JSONArray(parts)
        var found: String? = null
        for (i in 0 until array.length()) {
            val obj = array.optJSONObject(i) ?: continue
            if (obj.optString("type") == "image") {
                val data = obj.optString("base64")
                if (data.isNotBlank()) {
                    found = data
                    break
                }
            }
        }
        found
    }.getOrNull()

    /** 读取 ABCD 选项（没有就是空列表） */
    fun choices(parts: String): List<String> = runCatching {
        val array = JSONArray(parts)
        val list = mutableListOf<String>()
        for (i in 0 until array.length()) {
            val obj = array.optJSONObject(i) ?: continue
            if (obj.optString("type") != "choices") continue
            val items = obj.optJSONArray("items") ?: continue
            for (j in 0 until items.length()) {
                val text = items.optString(j)
                if (text.isNotBlank()) list.add(text)
            }
        }
        list
    }.getOrElse { emptyList() }
}

/**
 * 消息树仓库。
 *
 * 结构：node 链式串联（parentId），node 下挂多个变体（ui_messages）。
 * 重新生成 = 给同一个 node 追加变体 + 选中新变体，历史变体不丢。
 */
class MessageTreeRepository(private val dao: MessageTreeDao) {

    suspend fun loadPath(conversationId: String): List<ResolvedTurn> {
        val nodes = dao.loadNodes(conversationId)
        if (nodes.isEmpty()) return emptyList()

        val byParent = nodes.groupBy { it.parentId }
        var current = byParent[null].orEmpty().maxByOrNull { it.createdAt } ?: return emptyList()

        val turns = mutableListOf<ResolvedTurn>()
        while (true) {
            val variants = dao.loadVariants(current.id).sortedBy { it.createdAt }
            val index = current.selectIndex.coerceIn(0, (variants.size - 1).coerceAtLeast(0))
            variants.getOrNull(index)?.let { selected ->
                turns.add(
                    ResolvedTurn(
                        node = current,
                        message = selected,
                        variantCount = variants.size,
                        variantIndex = index,
                    )
                )
            }
            current = byParent[current.id].orEmpty().maxByOrNull { it.createdAt } ?: break
        }
        return turns
    }

    /** 追加一轮（新节点）。返回节点 id */
    suspend fun appendTurn(
        conversationId: String,
        parentId: String?,
        role: String,
        parts: String,
        reasoning: String?,
        isError: Boolean = false,
    ): Pair<String, String> {
        val now = System.currentTimeMillis()
        val nodeId = "n-$now-${(1000..9999).random()}"
        val messageId = "m-$now-${(1000..9999).random()}"

        dao.upsertNode(
            MessageNodeEntity(
                id = nodeId,
                conversationId = conversationId,
                parentId = parentId,
                selectIndex = 0,
                createdAt = now,
            )
        )
        dao.upsertMessage(
            UIMessageEntity(
                id = messageId,
                nodeId = nodeId,
                role = role,
                parts = parts,
                reasoning = reasoning,
                isError = isError,
                createdAt = now,
            )
        )
        return nodeId to messageId
    }

    /** 追加变体并选中它（重新生成用） */
    suspend fun appendVariant(
        node: MessageNodeEntity,
        role: String,
        parts: String,
        reasoning: String?,
        isError: Boolean = false,
    ): UIMessageEntity {
        val now = System.currentTimeMillis()
        val existing = dao.loadVariants(node.id)
        val message = UIMessageEntity(
            id = "m-$now-${(1000..9999).random()}",
            nodeId = node.id,
            role = role,
            parts = parts,
            reasoning = reasoning,
            isError = isError,
            createdAt = now,
        )
        dao.upsertMessage(message)
        dao.upsertNode(node.copy(selectIndex = existing.size))
        return message
    }

    suspend fun updateMessage(message: UIMessageEntity) = dao.upsertMessage(message)

    suspend fun selectVariant(node: MessageNodeEntity, index: Int) {
        dao.upsertNode(node.copy(selectIndex = index))
    }

    suspend fun deleteNode(nodeId: String) {
        dao.deleteVariants(nodeId)
    }

    suspend fun clear(conversationId: String) {
        dao.clearMessages(conversationId)
        dao.clearNodes(conversationId)
    }
}