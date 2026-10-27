package com.isledeer.soullink.net

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/**
 * 联网搜索：bing / bocha / tavily / exa 四个引擎的统一调用。
 * 搜索是增强不是阻断：任何失败都返回 null，由调用方静默跳过。
 */
class SearchService(
    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(25, TimeUnit.SECONDS)
        .build(),
) {

    companion object {
        val ENGINES = listOf("bing" to "必应", "bocha" to "博查", "tavily" to "Tavily", "exa" to "Exa")
        private val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()
    }

    suspend fun search(engine: String, apiKey: String, query: String): String? {
        if (query.isBlank() || apiKey.isBlank()) return null
        return runCatching {
            when (engine) {
                "bing" -> searchBing(apiKey, query)
                "bocha" -> searchBocha(apiKey, query)
                "tavily" -> searchTavily(apiKey, query)
                "exa" -> searchExa(apiKey, query)
                else -> null
            }
        }.getOrNull()
    }

    private suspend fun searchBing(key: String, query: String): String? = withContext(Dispatchers.IO) {
        val url = "https://api.bing.microsoft.com/v7.0/search?q=${urlEncode(query)}&count=5&mkt=zh-CN"
        val request = Request.Builder()
            .url(url)
            .addHeader("Ocp-Apim-Subscription-Key", key)
            .get()
            .build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) return@withContext null
            val root = runCatching { JSONObject(response.body?.string().orEmpty()) }.getOrNull()
                ?: return@withContext null
            val pages = root.optJSONObject("webPages")?.optJSONArray("value")
                ?: return@withContext null
            formatResults(pages, "name", "snippet", "url")
        }
    }

    private suspend fun searchBocha(key: String, query: String): String? = withContext(Dispatchers.IO) {
        val body = JSONObject().put("query", query).put("summary", true).put("count", 5)
        val request = Request.Builder()
            .url("https://api.bochaai.com/v1/web-search")
            .addHeader("Content-Type", "application/json")
            .addHeader("Authorization", "Bearer $key")
            .post(body.toString().toRequestBody(JSON_MEDIA_TYPE))
            .build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) return@withContext null
            val root = runCatching { JSONObject(response.body?.string().orEmpty()) }.getOrNull()
                ?: return@withContext null
            val pages = root.optJSONObject("data")?.optJSONObject("webPages")?.optJSONArray("value")
                ?: return@withContext null
            formatResults(pages, "name", "summary", "url")
        }
    }

    private suspend fun searchTavily(key: String, query: String): String? = withContext(Dispatchers.IO) {
        val body = JSONObject().put("api_key", key).put("query", query).put("max_results", 5)
        val request = Request.Builder()
            .url("https://api.tavily.com/search")
            .addHeader("Content-Type", "application/json")
            .post(body.toString().toRequestBody(JSON_MEDIA_TYPE))
            .build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) return@withContext null
            val root = runCatching { JSONObject(response.body?.string().orEmpty()) }.getOrNull()
                ?: return@withContext null
            val results = root.optJSONArray("results") ?: return@withContext null
            formatResults(results, "title", "content", "url")
        }
    }

    private suspend fun searchExa(key: String, query: String): String? = withContext(Dispatchers.IO) {
        val body = JSONObject()
            .put("query", query)
            .put("numResults", 5)
            .put("contents", JSONObject().put("text", true))
        val request = Request.Builder()
            .url("https://api.exa.ai/search")
            .addHeader("Content-Type", "application/json")
            .addHeader("x-api-key", key)
            .post(body.toString().toRequestBody(JSON_MEDIA_TYPE))
            .build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) return@withContext null
            val root = runCatching { JSONObject(response.body?.string().orEmpty()) }.getOrNull()
                ?: return@withContext null
            val results = root.optJSONArray("results") ?: return@withContext null
            formatResults(results, "title", "text", "url")
        }
    }

    private fun formatResults(array: JSONArray, titleKey: String, bodyKey: String, urlKey: String): String {
        val lines = mutableListOf<String>()
        for (i in 0 until array.length()) {
            val item = array.optJSONObject(i) ?: continue
            val title = item.optString(titleKey)
            val snippet = item.optString(bodyKey).take(200)
            val url = item.optString(urlKey)
            if (title.isBlank() && snippet.isBlank()) continue
            lines.add("- $title")
            if (snippet.isNotBlank()) lines.add("  $snippet")
            if (url.isNotBlank()) lines.add("  $url")
        }
        return if (lines.isEmpty()) "（无结果）" else lines.joinToString("\n")
    }

    private fun urlEncode(text: String): String = java.net.URLEncoder.encode(text, "UTF-8")
}