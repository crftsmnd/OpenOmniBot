package cn.com.omnimind.bot.mem0

import cn.com.omnimind.baselib.util.OssIdentity
import com.google.gson.Gson
import com.tencent.mmkv.MMKV

object Mem0ConfigStore {
    private const val USER_KEY_PREFIX = "mem0_config_user_"
    private const val GLOBAL_KEY = "mem0_config_global"

    private val gson = Gson()
    private val mmkv by lazy { MMKV.defaultMMKV() }

    fun getConfigForUi(): Map<String, Any?> {
        val userId = currentUserId()
        val userConfig = userId?.let { readConfig(userKey(it)) }
        val globalConfig = readConfig(GLOBAL_KEY)
        val selected = userConfig ?: globalConfig ?: Mem0Config()
        val source = when {
            userConfig != null -> Mem0ConfigSource.USER.rawValue
            globalConfig != null -> Mem0ConfigSource.GLOBAL.rawValue
            else -> null
        }
        return selected.toMap(source)
    }

    fun getResolvedConfigForUi(): Map<String, Any?> {
        return getEffectiveConfig()?.toMap() ?: emptyMap()
    }

    fun getEffectiveConfig(): Mem0ResolvedConfig? {
        val userId = currentUserId() ?: return null
        val userConfig = readConfig(userKey(userId))
        val globalConfig = readConfig(GLOBAL_KEY)
        return resolveEffectiveConfig(userId, userConfig, globalConfig)
    }

    fun saveConfig(config: Mem0Config): Mem0Config {
        val normalized = config.normalized().copy(updatedAt = System.currentTimeMillis())
        val userId = currentUserId()
        val targetKey = userId?.let(::userKey) ?: GLOBAL_KEY
        mmkv.encode(targetKey, gson.toJson(normalized.toMap()))
        return normalized
    }

    fun clearConfig() {
        val userId = currentUserId()
        val userKey = userId?.let(::userKey)
        if (userKey != null && mmkv.containsKey(userKey)) {
            mmkv.removeValueForKey(userKey)
            return
        }
        mmkv.removeValueForKey(GLOBAL_KEY)
    }

    internal fun resolveEffectiveConfig(
        userId: String?,
        userConfig: Mem0Config?,
        globalConfig: Mem0Config?
    ): Mem0ResolvedConfig? {
        val stableUserId = userId?.trim()?.takeIf { it.isNotBlank() } ?: return null
        val effectiveConfig = when {
            userConfig?.isConfigured() == true -> userConfig.normalized() to Mem0ConfigSource.USER
            globalConfig?.isConfigured() == true -> globalConfig.normalized() to Mem0ConfigSource.GLOBAL
            else -> null
        } ?: return null
        return Mem0ResolvedConfig(
            baseUrl = effectiveConfig.first.baseUrl,
            apiKey = effectiveConfig.first.apiKey,
            agentId = effectiveConfig.first.agentId,
            userId = stableUserId,
            source = effectiveConfig.second
        )
    }

    private fun currentUserId(): String? {
        return OssIdentity.currentUserIdOrNull()
    }

    private fun readConfig(key: String): Mem0Config? {
        val saved = mmkv.decodeString(key)?.trim().orEmpty()
        if (saved.isBlank()) return null
        return runCatching {
            val map = gson.fromJson(saved, Map::class.java)
                ?.entries
                ?.associate { entry -> entry.key.toString() to entry.value }
                ?: emptyMap()
            Mem0Config.fromMap(map)
        }.getOrNull()
    }

    private fun userKey(userId: String): String = USER_KEY_PREFIX + userId
}
