package cn.com.omnimind.bot.mem0

import cn.com.omnimind.baselib.util.OmniLog
import com.google.gson.Gson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import java.net.SocketTimeoutException
import java.util.Locale
import java.util.concurrent.TimeUnit
import kotlin.math.min

object Mem0Client {
    private const val TAG = "[Mem0Client]"
    private const val CONNECT_TIMEOUT_SECONDS = 20L
    private const val IO_TIMEOUT_SECONDS = 90L
    private const val CALL_TIMEOUT_SECONDS = 95L

    private val gson = Gson()
    private val jsonMediaType = "application/json; charset=utf-8".toMediaType()
    private val client = OkHttpClient.Builder()
        .connectTimeout(CONNECT_TIMEOUT_SECONDS, TimeUnit.SECONDS)
        .readTimeout(IO_TIMEOUT_SECONDS, TimeUnit.SECONDS)
        .writeTimeout(IO_TIMEOUT_SECONDS, TimeUnit.SECONDS)
        .callTimeout(CALL_TIMEOUT_SECONDS, TimeUnit.SECONDS)
        .build()

    private data class ParsedResponse(
        val rawBody: String,
        val payload: Any?
    )

    private class HttpStatusException(
        val code: Int,
        val responseBody: String,
        override val message: String
    ) : IOException(message)

    suspend fun configure(
        config: Mem0ResolvedConfig,
        payload: Map<String, Any?>
    ): Mem0ApiResult {
        val response = execute(
            config = config,
            method = "POST",
            pathSegments = listOf("configure"),
            body = payload
        )
        return buildApiResult(
            action = "configure",
            payload = response.payload,
            rawJson = response.rawBody
        )
    }

    suspend fun addMemory(
        config: Mem0ResolvedConfig,
        payload: Map<String, Any?>,
        runId: String
    ): Mem0ApiResult {
        val body = adaptCreateMemoryPayload(
            payload = withWriteMetadata(withIdentity(payload, config), runId),
            runId = runId
        )
        if ((body["messages"] as? List<*>)?.isEmpty() != false) {
            throw IllegalArgumentException("mem0_add 需要 memory 或 messages")
        }
        val response = execute(
            config = config,
            method = "POST",
            pathSegments = listOf("memories"),
            body = body
        )
        return buildApiResult(
            action = "add",
            payload = response.payload,
            rawJson = response.rawBody
        )
    }

    suspend fun listMemories(
        config: Mem0ResolvedConfig,
        payload: Map<String, Any?>
    ): Mem0ApiResult {
        val queryParams = withIdentity(payload, config)
        val response = execute(
            config = config,
            method = "GET",
            pathSegments = listOf("memories"),
            queryParams = queryParams
        )
        return buildApiResult(
            action = "list",
            payload = response.payload,
            rawJson = response.rawBody
        )
    }

    suspend fun getMemory(
        config: Mem0ResolvedConfig,
        memoryId: String,
        payload: Map<String, Any?>
    ): Mem0ApiResult {
        val response = execute(
            config = config,
            method = "GET",
            pathSegments = listOf("memories", memoryId),
            queryParams = withIdentity(payload, config)
        )
        return buildApiResult(
            action = "get",
            payload = response.payload,
            rawJson = response.rawBody
        )
    }

    suspend fun updateMemory(
        config: Mem0ResolvedConfig,
        memoryId: String,
        payload: Map<String, Any?>,
        runId: String
    ): Mem0ApiResult {
        val rawBody = withWriteMetadata(withIdentity(payload, config), runId)
        val body = adaptUpdateMemoryPayload(rawBody)
        return try {
            val response = execute(
                config = config,
                method = "PUT",
                pathSegments = listOf("memories", memoryId),
                queryParams = withIdentity(emptyMap(), config),
                body = body
            )
            buildApiResult(
                action = "update",
                payload = response.payload,
                rawJson = response.rawBody
            )
        } catch (e: HttpStatusException) {
            if (!shouldFallbackUpdateByAddDelete(e.code, e.message, e.responseBody)) {
                throw e
            }
            fallbackUpdateByAddDelete(
                config = config,
                memoryId = memoryId,
                normalizedUpdatePayload = body,
                runId = runId,
                cause = e.message
            )
        }
    }

    suspend fun deleteMemory(
        config: Mem0ResolvedConfig,
        memoryId: String,
        payload: Map<String, Any?>
    ): Mem0ApiResult {
        val response = execute(
            config = config,
            method = "DELETE",
            pathSegments = listOf("memories", memoryId),
            queryParams = withIdentity(payload, config)
        )
        return buildApiResult(
            action = "delete",
            payload = response.payload,
            rawJson = response.rawBody
        )
    }

    suspend fun getMemoryHistory(
        config: Mem0ResolvedConfig,
        memoryId: String,
        payload: Map<String, Any?>
    ): Mem0ApiResult {
        val response = execute(
            config = config,
            method = "GET",
            pathSegments = listOf("memories", memoryId, "history"),
            queryParams = withIdentity(payload, config)
        )
        return buildApiResult(
            action = "history",
            payload = response.payload,
            rawJson = response.rawBody
        )
    }

    suspend fun search(
        config: Mem0ResolvedConfig,
        payload: Map<String, Any?>
    ): Mem0ApiResult {
        val body = withIdentity(payload, config)
        if (body["query"]?.toString().isNullOrBlank()) {
            throw IllegalArgumentException("mem0_search 需要 query")
        }
        val response = execute(
            config = config,
            method = "POST",
            pathSegments = listOf("search"),
            body = body
        )
        return buildApiResult(
            action = "search",
            payload = response.payload,
            rawJson = response.rawBody
        )
    }

    suspend fun deleteAll(
        config: Mem0ResolvedConfig,
        payload: Map<String, Any?>
    ): Mem0ApiResult {
        val scopedPayload = withIdentity(payload, config)
        val response = execute(
            config = config,
            method = "DELETE",
            pathSegments = listOf("memories"),
            queryParams = scopedPayload
        )
        return buildApiResult(
            action = "delete_all",
            payload = response.payload,
            rawJson = response.rawBody
        )
    }

    suspend fun reset(
        config: Mem0ResolvedConfig,
        payload: Map<String, Any?>
    ): Mem0ApiResult {
        val queryParams = withIdentity(payload, config).entries
            .filter { (key, value) -> value != null && (key == "user_id" || key == "agent_id" || key == "run_id") }
            .associate { it.key to it.value }
        val response = execute(
            config = config,
            method = "POST",
            pathSegments = listOf("reset"),
            queryParams = queryParams
        )
        return buildApiResult(
            action = "reset",
            payload = response.payload,
            rawJson = response.rawBody
        )
    }

    suspend fun searchWithFallback(
        config: Mem0ResolvedConfig,
        query: String,
        limit: Int = Mem0Defaults.DEFAULT_SEARCH_LIMIT
    ): Mem0InjectedContext {
        val safeLimit = limit.coerceIn(1, 10)
        return try {
            val searchResult = search(
                config = config,
                payload = mapOf(
                    "query" to query,
                    "limit" to safeLimit
                )
            )
            val items = extractMemoryItems(searchResult.payload).take(safeLimit)
            Mem0InjectedContext(
                items = items,
                source = "search",
                usedFallback = false
            )
        } catch (searchError: Exception) {
            OmniLog.w(TAG, "Mem0 search failed, fallback to list: ${searchError.message}")
            val listResult = runCatching {
                listMemories(
                    config = config,
                    payload = mapOf("limit" to Mem0Defaults.DEFAULT_LIST_FALLBACK_LIMIT)
                )
            }.getOrElse {
                OmniLog.w(TAG, "Mem0 fallback list failed: ${it.message}")
                return Mem0InjectedContext(emptyList(), "search_failed", true)
            }
            val reRanked = rerankLocally(query, extractMemoryItems(listResult.payload), safeLimit)
            Mem0InjectedContext(
                items = reRanked,
                source = "list_fallback",
                usedFallback = true
            )
        }
    }

    internal fun normalizeBaseUrl(baseUrl: String): String {
        return baseUrl.trim().trimEnd('/')
    }

    internal fun buildUrlString(
        baseUrl: String,
        pathSegments: List<String>,
        queryParams: Map<String, Any?> = emptyMap()
    ): String {
        val httpUrl = normalizeBaseUrl(baseUrl).toHttpUrlOrNull()
            ?: throw IllegalArgumentException("Mem0 Base URL 无效")
        val builder = httpUrl.newBuilder()
        pathSegments.forEach { segment ->
            segment.split("/")
                .map { it.trim() }
                .filter { it.isNotEmpty() }
                .forEach { builder.addPathSegment(it) }
        }
        queryParams.forEach { (key, rawValue) ->
            appendQueryParameter(builder, key, rawValue)
        }
        return builder.build().toString()
    }

    internal fun rerankLocally(
        query: String,
        items: List<Mem0MemoryItem>,
        limit: Int
    ): List<Mem0MemoryItem> {
        if (query.isBlank() || items.isEmpty()) return items.take(limit)
        val queryTokens = tokenize(query)
        return items.map { item ->
            val lexicalScore = computeLocalScore(queryTokens, item.text)
            val semanticScore = Mem0MemoryAdvisor.computeSimilarityScore(query, item.text)
            item to (semanticScore * 0.72 + lexicalScore * 0.28)
        }
            .filter { (_, score) -> score > 0.0 }
            .sortedWith(compareByDescending<Pair<Mem0MemoryItem, Double>> { it.second }
                .thenByDescending { it.first.updatedAt ?: it.first.createdAt ?: "" })
            .take(limit)
            .map { it.first }
    }

    private suspend fun execute(
        config: Mem0ResolvedConfig,
        method: String,
        pathSegments: List<String>,
        queryParams: Map<String, Any?> = emptyMap(),
        body: Map<String, Any?>? = null
    ): ParsedResponse = withContext(Dispatchers.IO) {
        val url = buildUrlString(config.baseUrl, pathSegments, queryParams)
        val requestBody = body?.let { gson.toJson(it).toRequestBody(jsonMediaType) }
        val request = Request.Builder()
            .url(url)
            .header("Authorization", "Bearer ${config.apiKey}")
            .header("Accept", "application/json")
            .apply {
                when (method.uppercase(Locale.US)) {
                    "GET" -> get()
                    "POST" -> post(requestBody ?: "{}".toRequestBody(jsonMediaType))
                    "PUT" -> put(requestBody ?: "{}".toRequestBody(jsonMediaType))
                    "PATCH" -> patch(requestBody ?: "{}".toRequestBody(jsonMediaType))
                    "DELETE" -> method(
                        "DELETE",
                        requestBody
                    )
                    else -> method(method.uppercase(Locale.US), requestBody)
                }
            }
            .build()

        try {
            client.newCall(request).execute().use { response ->
                val rawBody = response.body?.string().orEmpty().trim()
                if (!response.isSuccessful) {
                    throw HttpStatusException(
                        code = response.code,
                        responseBody = rawBody,
                        message = mapHttpError(response.code, rawBody)
                    )
                }
                val safeBody = if (rawBody.isBlank()) "{}" else rawBody
                val payload = parseBody(safeBody)
                ParsedResponse(
                    rawBody = safeBody,
                    payload = payload
                )
            }
        } catch (e: SocketTimeoutException) {
            throw IOException(
                "Mem0 请求超时，请检查 self-hosted Mem0 处理耗时或网络情况（当前客户端超时 ${CALL_TIMEOUT_SECONDS}s）"
            )
        }
    }

    private fun buildApiResult(
        action: String,
        payload: Any?,
        rawJson: String
    ): Mem0ApiResult {
        val preview = if (rawJson.length <= 1200) rawJson else rawJson.take(1200) + "..."
        return Mem0ApiResult(
            summaryText = buildSummary(action, payload),
            previewJson = preview,
            rawResultJson = rawJson,
            success = true,
            payload = payload
        )
    }

    private fun buildSummary(action: String, payload: Any?): String {
        val items = extractMemoryItems(payload)
        return when (action) {
            "configure" -> "Mem0 服务端配置已同步。"
            "add" -> items.firstOrNull()?.text?.take(60)?.let { "已写入记忆：$it" } ?: "已写入记忆。"
            "list" -> "共获取到 ${items.size} 条记忆。"
            "get" -> items.firstOrNull()?.text?.take(80)?.let { "记忆详情：$it" } ?: "已获取记忆详情。"
            "update" -> items.firstOrNull()?.text?.take(60)?.let { "记忆已更新：$it" } ?: "记忆已更新。"
            "delete" -> "记忆已删除。"
            "history" -> {
                val count = extractCount(payload)
                if (count > 0) "共获取到 $count 条记忆历史。" else "已获取记忆历史。"
            }
            "search" -> "检索到 ${items.size} 条相关记忆。"
            "delete_all" -> {
                val count = extractCount(payload)
                if (count > 0) "已清空 $count 条记忆。" else "已清空当前 Mem0 空间的记忆。"
            }
            "reset" -> "已重置当前 Mem0 记忆空间。"
            else -> "Mem0 请求已完成。"
        }
    }

    private fun parseBody(rawBody: String): Any? {
        return runCatching { gson.fromJson(rawBody, Any::class.java) }.getOrElse { rawBody }
    }

    private fun mapHttpError(code: Int, responseBody: String): String {
        val detail = extractErrorDetail(responseBody)
        if (detail.equals("Connection error.", ignoreCase = true)) {
            return "Mem0 服务端依赖连接失败，请检查 self-hosted Mem0 的模型、向量库或外部网络配置"
        }
        return when (code) {
            400 -> detail ?: "Mem0 请求参数错误"
            401, 403 -> detail ?: "Mem0 鉴权失败，请检查 API Key"
            404 -> detail ?: "Mem0 资源不存在"
            408, 504 -> detail ?: "Mem0 请求超时"
            409 -> detail ?: "Mem0 请求冲突"
            429 -> detail ?: "Mem0 请求过于频繁，请稍后重试"
            in 500..599 -> detail ?: "Mem0 服务暂时不可用"
            else -> detail ?: "Mem0 请求失败（HTTP $code）"
        }
    }

    private fun extractErrorDetail(rawBody: String): String? {
        val payload = parseBody(rawBody)
        val map = payload as? Map<*, *> ?: return rawBody.takeIf { it.isNotBlank() }?.take(160)
        val candidate = listOf("detail", "message", "error", "reason")
            .firstNotNullOfOrNull { key -> map[key]?.toString()?.trim()?.takeIf { it.isNotBlank() } }
        return candidate ?: rawBody.takeIf { it.isNotBlank() }?.take(160)
    }

    private fun withIdentity(
        payload: Map<String, Any?>,
        config: Mem0ResolvedConfig
    ): Map<String, Any?> {
        val result = linkedMapOf<String, Any?>()
        result.putAll(payload)
        result["user_id"] = config.userId
        result["agent_id"] = config.agentId
        return result
    }

    private fun withWriteMetadata(
        payload: Map<String, Any?>,
        runId: String
    ): Map<String, Any?> {
        val result = payload.toMutableMap()
        val metadata = mutableMapOf<String, Any?>()
        val existing = result["metadata"]
        if (existing is Map<*, *>) {
            existing.forEach { (key, value) ->
                metadata[key.toString()] = value
            }
        }
        metadata["run_id"] = runId
        result["metadata"] = metadata
        return result
    }

    internal fun adaptCreateMemoryPayload(
        payload: Map<String, Any?>,
        runId: String
    ): Map<String, Any?> {
        val result = payload.toMutableMap()
        val metadata = (result["metadata"] as? Map<*, *>)?.entries?.associate {
            it.key.toString() to it.value
        }?.toMutableMap() ?: mutableMapOf()

        val categories = when (val raw = result.remove("categories")) {
            is List<*> -> raw.mapNotNull { item ->
                item?.toString()?.trim()?.takeIf { it.isNotEmpty() }
            }
            else -> emptyList()
        }
        if (categories.isNotEmpty()) {
            metadata["categories"] = categories
        }

        val memoryText = result.remove("memory")?.toString()?.trim().orEmpty()
        val existingMessages = normalizeMessages(result["messages"])
        if (existingMessages.isNotEmpty()) {
            result["messages"] = existingMessages
        } else if (memoryText.isNotBlank()) {
            result["messages"] = listOf(
                mapOf(
                    "role" to "user",
                    "content" to memoryText
                )
            )
        }

        result["run_id"] = runId
        result["metadata"] = metadata.takeIf { it.isNotEmpty() }
        return result
    }

    internal fun adaptUpdateMemoryPayload(payload: Map<String, Any?>): Map<String, Any?> {
        val result = payload.toMutableMap()
        val metadata = (result["metadata"] as? Map<*, *>)?.entries?.associate {
            it.key.toString() to it.value
        }?.toMutableMap() ?: mutableMapOf()

        val categories = when (val raw = result.remove("categories")) {
            is List<*> -> raw.mapNotNull { item ->
                item?.toString()?.trim()?.takeIf { it.isNotEmpty() }
            }
            else -> emptyList()
        }
        if (categories.isNotEmpty()) {
            metadata["categories"] = categories
        }

        val memoryText = sequenceOf(
            result["text"]?.toString()?.trim().orEmpty(),
            result["memory"]?.toString()?.trim().orEmpty(),
            extractTextFromMessages(result["messages"])
        ).firstOrNull { it.isNotBlank() }.orEmpty()

        result.remove("memory")
        result.remove("messages")
        if (memoryText.isNotBlank()) {
            result["text"] = memoryText
        }

        result["metadata"] = metadata.takeIf { it.isNotEmpty() }

        // /memories/{id} 已经通过 path + query 绑定身份，不把 identity 字段再塞进 body。
        result.remove("user_id")
        result.remove("agent_id")
        result.remove("run_id")
        return result
    }

    internal fun shouldFallbackUpdateByAddDelete(
        code: Int?,
        message: String?,
        responseBody: String?
    ): Boolean {
        if (code != null && code !in 500..599) return false
        val merged = buildString {
            append(message.orEmpty())
            append(' ')
            append(responseBody.orEmpty())
        }.lowercase(Locale.US)
        return merged.contains("dict") && merged.contains("replace")
    }

    private suspend fun fallbackUpdateByAddDelete(
        config: Mem0ResolvedConfig,
        memoryId: String,
        normalizedUpdatePayload: Map<String, Any?>,
        runId: String,
        cause: String
    ): Mem0ApiResult {
        val text = normalizedUpdatePayload["text"]?.toString()?.trim().orEmpty()
        if (text.isBlank()) {
            throw IllegalArgumentException("mem0_update 需要 memory 或 text")
        }

        val addPayload = linkedMapOf<String, Any?>(
            "memory" to text
        )
        val metadata = normalizedUpdatePayload["metadata"] as? Map<*, *>
        if (metadata != null && metadata.isNotEmpty()) {
            addPayload["metadata"] = metadata.entries.associate { it.key.toString() to it.value }
            val categories = (metadata["categories"] as? List<*>)?.mapNotNull { item ->
                item?.toString()?.trim()?.takeIf { it.isNotEmpty() }
            } ?: emptyList()
            if (categories.isNotEmpty()) {
                addPayload["categories"] = categories
            }
        }

        val addResult = addMemory(
            config = config,
            payload = addPayload,
            runId = runId
        )
        val deleteSucceeded = runCatching {
            deleteMemory(
                config = config,
                memoryId = memoryId,
                payload = emptyMap()
            )
        }.isSuccess
        if (!deleteSucceeded) {
            OmniLog.w(
                TAG,
                "Mem0 update fallback add succeeded but delete old memory failed: $memoryId"
            )
        }

        val fallbackPayload = linkedMapOf<String, Any?>(
            "mode" to "compat_add_delete",
            "cause" to cause,
            "originalMemoryId" to memoryId,
            "deleteOldSuccess" to deleteSucceeded,
            "addResult" to addResult.payload
        )
        val rawJson = gson.toJson(fallbackPayload)
        val preview = if (rawJson.length <= 1200) rawJson else rawJson.take(1200) + "..."
        val summary = if (deleteSucceeded) {
            "记忆已更新。"
        } else {
            "记忆已更新（兼容模式：新记忆已写入，但旧记忆删除失败，可能存在重复）。"
        }
        return Mem0ApiResult(
            summaryText = summary,
            previewJson = preview,
            rawResultJson = rawJson,
            success = true,
            payload = fallbackPayload
        )
    }

    private fun normalizeMessages(raw: Any?): List<Map<String, String>> {
        return when (raw) {
            is List<*> -> raw.mapNotNull { item ->
                val map = item as? Map<*, *> ?: return@mapNotNull null
                val role = map["role"]?.toString()?.trim().orEmpty()
                val content = map["content"]?.toString()?.trim().orEmpty()
                if (role.isBlank() || content.isBlank()) {
                    return@mapNotNull null
                }
                mapOf(
                    "role" to role,
                    "content" to content
                )
            }
            else -> emptyList()
        }
    }

    private fun extractTextFromMessages(raw: Any?): String {
        return normalizeMessages(raw)
            .mapNotNull { it["content"]?.trim()?.takeIf { text -> text.isNotEmpty() } }
            .joinToString("\n")
    }

    private fun extractMemoryItems(payload: Any?): List<Mem0MemoryItem> {
        val candidates = flattenCandidates(payload)
        return candidates.mapNotNull { candidate ->
            val map = candidate as? Map<*, *> ?: return@mapNotNull null
            val id = firstString(map, "id", "memory_id", "uuid")
            val text = extractMemoryText(map)
            if (id.isBlank() && text.isBlank()) {
                return@mapNotNull null
            }
            Mem0MemoryItem(
                id = id,
                text = text,
                score = firstDouble(map, "score", "similarity", "relevance"),
                metadata = (map["metadata"] as? Map<*, *>)?.entries?.associate {
                    it.key.toString() to it.value
                } ?: emptyMap(),
                categories = ((map["categories"] as? List<*>) ?: emptyList<Any?>()).mapNotNull {
                    it?.toString()?.trim()?.takeIf { value -> value.isNotEmpty() }
                },
                createdAt = firstStringOrNull(map, "created_at", "createdAt"),
                updatedAt = firstStringOrNull(map, "updated_at", "updatedAt")
            )
        }
    }

    private fun flattenCandidates(payload: Any?): List<Any?> {
        return when (payload) {
            null -> emptyList()
            is List<*> -> payload
            is Map<*, *> -> {
                val preferredKeys = listOf("results", "memories", "items", "data")
                preferredKeys.forEach { key ->
                    val nested = payload[key]
                    if (nested is List<*>) {
                        return nested
                    }
                }
                listOf(payload)
            }
            else -> emptyList()
        }
    }

    private fun extractMemoryText(map: Map<*, *>): String {
        val direct = listOf("memory", "text", "content", "value")
            .firstNotNullOfOrNull { key ->
                when (val value = map[key]) {
                    is String -> value.trim().takeIf { it.isNotEmpty() }
                    is List<*> -> value.mapNotNull { item ->
                        (item as? Map<*, *>)?.get("text")?.toString()?.trim()?.takeIf { it.isNotEmpty() }
                    }.joinToString("\n").takeIf { it.isNotBlank() }
                    else -> null
                }
            }
        if (!direct.isNullOrBlank()) return direct
        val nested = map["memory"]
        if (nested is Map<*, *>) {
            return extractMemoryText(nested)
        }
        return ""
    }

    private fun extractCount(payload: Any?): Int {
        val map = payload as? Map<*, *> ?: return 0
        val keys = listOf("count", "deleted", "num_deleted", "history_count")
        return keys.firstNotNullOfOrNull { key ->
            when (val value = map[key]) {
                is Number -> value.toInt()
                is String -> value.toIntOrNull()
                else -> null
            }
        } ?: 0
    }

    private fun firstString(map: Map<*, *>, vararg keys: String): String {
        return keys.firstNotNullOfOrNull { key ->
            map[key]?.toString()?.trim()?.takeIf { it.isNotEmpty() }
        }.orEmpty()
    }

    private fun firstStringOrNull(map: Map<*, *>, vararg keys: String): String? {
        return keys.firstNotNullOfOrNull { key ->
            map[key]?.toString()?.trim()?.takeIf { it.isNotEmpty() }
        }
    }

    private fun firstDouble(map: Map<*, *>, vararg keys: String): Double? {
        return keys.firstNotNullOfOrNull { key ->
            when (val value = map[key]) {
                is Number -> value.toDouble()
                is String -> value.toDoubleOrNull()
                else -> null
            }
        }
    }

    private fun appendQueryParameter(
        builder: okhttp3.HttpUrl.Builder,
        key: String,
        value: Any?
    ) {
        when (value) {
            null -> Unit
            is Iterable<*> -> value.forEach { appendQueryParameter(builder, key, it) }
            is Array<*> -> value.forEach { appendQueryParameter(builder, key, it) }
            is Map<*, *> -> builder.addQueryParameter(key, gson.toJson(value))
            is List<*> -> builder.addQueryParameter(key, gson.toJson(value))
            else -> {
                val text = value.toString().trim()
                if (text.isNotEmpty()) {
                    builder.addQueryParameter(key, text)
                }
            }
        }
    }

    private fun tokenize(text: String): Set<String> {
        val lowered = text.lowercase(Locale.getDefault())
        val compact = lowered.filter { it.isLetterOrDigit() }
        val tokens = lowered
            .split(Regex("[^\\p{L}\\p{N}]+"))
            .map { it.trim() }
            .filter { it.length >= 2 }
            .toMutableSet()
        if (compact.any { it.code > 127 } && compact.length >= 2) {
            for (index in 0 until compact.length - 1) {
                tokens.add(compact.substring(index, index + 2))
            }
        }
        return tokens
    }

    private fun computeLocalScore(queryTokens: Set<String>, text: String): Double {
        if (queryTokens.isEmpty() || text.isBlank()) return 0.0
        val lower = text.lowercase(Locale.getDefault())
        val matched = queryTokens.count { token -> lower.contains(token) }
        if (matched == 0) return 0.0
        val overlap = matched.toDouble() / queryTokens.size.toDouble()
        val containBoost = min(0.5, matched * 0.08)
        return overlap + containBoost
    }
}
