package cn.com.omnimind.bot.mem0

object Mem0Defaults {
    const val DEFAULT_AGENT_ID = "omnibot-unified-agent"
    const val DEFAULT_SEARCH_LIMIT = 5
    const val DEFAULT_LIST_FALLBACK_LIMIT = 50
}

enum class Mem0ConfigSource(val rawValue: String) {
    USER("user"),
    GLOBAL("global")
}

data class Mem0Config(
    val baseUrl: String = "",
    val apiKey: String = "",
    val agentId: String = Mem0Defaults.DEFAULT_AGENT_ID,
    val updatedAt: Long? = null
) {
    fun normalized(): Mem0Config {
        return copy(
            baseUrl = baseUrl.trim().trimEnd('/'),
            apiKey = apiKey.trim(),
            agentId = agentId.trim().ifBlank { Mem0Defaults.DEFAULT_AGENT_ID },
            updatedAt = updatedAt ?: System.currentTimeMillis()
        )
    }

    fun isConfigured(): Boolean {
        val normalized = normalized()
        return normalized.baseUrl.isNotBlank() && normalized.apiKey.isNotBlank()
    }

    fun toMap(source: String? = null): Map<String, Any?> {
        val normalized = normalized()
        return mapOf(
            "baseUrl" to normalized.baseUrl,
            "apiKey" to normalized.apiKey,
            "agentId" to normalized.agentId,
            "updatedAt" to normalized.updatedAt,
            "configured" to normalized.isConfigured(),
            "source" to source
        )
    }

    companion object {
        fun fromMap(raw: Map<String, Any?>): Mem0Config {
            val updatedAt = when (val value = raw["updatedAt"]) {
                is Number -> value.toLong()
                is String -> value.toLongOrNull()
                else -> null
            }
            return Mem0Config(
                baseUrl = raw["baseUrl"]?.toString().orEmpty(),
                apiKey = raw["apiKey"]?.toString().orEmpty(),
                agentId = raw["agentId"]?.toString().orEmpty(),
                updatedAt = updatedAt
            ).normalized()
        }
    }
}

data class Mem0ResolvedConfig(
    val baseUrl: String,
    val apiKey: String,
    val agentId: String,
    val userId: String,
    val source: Mem0ConfigSource
) {
    fun toMap(): Map<String, Any?> {
        return mapOf(
            "baseUrl" to baseUrl,
            "apiKey" to apiKey,
            "agentId" to agentId,
            "userId" to userId,
            "source" to source.rawValue,
            "configured" to true
        )
    }
}

data class Mem0MemoryItem(
    val id: String,
    val text: String,
    val score: Double? = null,
    val metadata: Map<String, Any?> = emptyMap(),
    val categories: List<String> = emptyList(),
    val createdAt: String? = null,
    val updatedAt: String? = null
) {
    fun toPromptMap(): Map<String, Any?> {
        return linkedMapOf<String, Any?>(
            "id" to id,
            "memory" to text,
            "score" to score,
            "categories" to categories,
            "metadata" to metadata,
            "createdAt" to createdAt,
            "updatedAt" to updatedAt
        )
    }
}

data class Mem0ApiResult(
    val summaryText: String,
    val previewJson: String,
    val rawResultJson: String,
    val success: Boolean = true,
    val payload: Any? = null
)

data class Mem0InjectedContext(
    val items: List<Mem0MemoryItem>,
    val source: String,
    val usedFallback: Boolean
)
