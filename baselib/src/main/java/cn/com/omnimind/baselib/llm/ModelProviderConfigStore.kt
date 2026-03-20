package cn.com.omnimind.baselib.llm

import cn.com.omnimind.baselib.util.OssIdentity
import cn.com.omnimind.baselib.util.OmniLog
import com.tencent.mmkv.MMKV

object ModelProviderConfigStore {
    private const val TAG = "ModelProviderConfigStore"
    internal const val KEY_PROVIDER_BASE_URL = "model_provider_openai_base_url"
    internal const val KEY_PROVIDER_API_KEY = "model_provider_openai_api_key"

    internal const val LEGACY_MODEL_OVERRIDE_KEY = "vlm_operation_model_override"
    internal const val LEGACY_API_BASE_OVERRIDE_KEY = "vlm_operation_api_base_override"
    internal const val LEGACY_API_KEY_OVERRIDE_KEY = "vlm_operation_api_key_override"
    internal const val MIGRATION_DONE_KEY = "model_provider_scene_config_flattened_v2"

    fun getConfig(): ModelProviderConfig {
        ModelProviderMigration.ensureMigrated()
        val mmkv = MMKV.defaultMMKV() ?: return ModelProviderConfig()
        val globalConfig = readConfig(mmkv)
        if (globalConfig.baseUrl.isNotBlank() || globalConfig.apiKey.isNotBlank()) {
            return globalConfig.copy(source = "global")
        }
        return ModelProviderConfig()
    }

    fun saveConfig(baseUrl: String, apiKey: String) {
        val normalizedBaseUrl = normalizeBaseUrl(baseUrl).orEmpty()
        val mmkv = MMKV.defaultMMKV() ?: return
        mmkv.encode(KEY_PROVIDER_BASE_URL, normalizedBaseUrl)
        mmkv.encode(KEY_PROVIDER_API_KEY, apiKey.trim())
    }

    fun clearConfig() {
        val mmkv = MMKV.defaultMMKV() ?: return
        mmkv.encode(KEY_PROVIDER_BASE_URL, "")
        mmkv.encode(KEY_PROVIDER_API_KEY, "")
    }

    fun isValidBaseUrl(value: String): Boolean = normalizeBaseUrl(value) != null

    fun normalizeBaseUrl(value: String): String? {
        val normalized = value.trim()
        if (normalized.isEmpty()) {
            return null
        }
        val uri = runCatching { java.net.URI(normalized) }.getOrNull() ?: return null
        if (uri.scheme !in setOf("http", "https") || uri.host.isNullOrBlank()) {
            return null
        }

        var result = normalized.replace(Regex("/+$"), "")
        if (result.endsWith("/v1/chat/completions", ignoreCase = true)) {
            result = result.dropLast("/v1/chat/completions".length)
        } else if (result.endsWith("/chat/completions", ignoreCase = true)) {
            result = result.dropLast("/chat/completions".length)
        } else if (result.endsWith("/v1/models", ignoreCase = true)) {
            result = result.dropLast("/v1/models".length)
        } else if (result.endsWith("/models", ignoreCase = true)) {
            result = result.dropLast("/models".length)
        }
        return result.replace(Regex("/+$"), "")
    }

    internal fun readConfig(mmkv: MMKV): ModelProviderConfig {
        val baseUrl = mmkv.decodeString(KEY_PROVIDER_BASE_URL)
            ?.trim()
            ?.let(::normalizeBaseUrl)
            .orEmpty()
        val apiKey = mmkv.decodeString(KEY_PROVIDER_API_KEY)?.trim().orEmpty()
        return ModelProviderConfig(baseUrl = baseUrl, apiKey = apiKey)
    }

    internal fun readConfigForScope(mmkv: MMKV, userId: String?): ModelProviderConfig {
        val baseUrl = readScopedString(mmkv, KEY_PROVIDER_BASE_URL, userId)
            ?.let(::normalizeBaseUrl)
            .orEmpty()
        val apiKey = readScopedString(mmkv, KEY_PROVIDER_API_KEY, userId).orEmpty()
        return ModelProviderConfig(baseUrl = baseUrl, apiKey = apiKey)
    }

    internal fun readLegacyConfigForScope(mmkv: MMKV, userId: String?): ModelProviderConfig {
        val baseUrl = readScopedString(mmkv, LEGACY_API_BASE_OVERRIDE_KEY, userId)
            ?.let(::normalizeBaseUrl)
            .orEmpty()
        val apiKey = readScopedString(mmkv, LEGACY_API_KEY_OVERRIDE_KEY, userId).orEmpty()
        return ModelProviderConfig(baseUrl = baseUrl, apiKey = apiKey)
    }

    internal fun scopedKey(key: String, userId: String?): String {
        return if (userId.isNullOrBlank()) key else "user_${userId}_$key"
    }

    internal fun readScopedString(mmkv: MMKV, key: String, userId: String?): String? {
        val value = mmkv.decodeString(scopedKey(key, userId))
            ?.trim()
            ?.takeIf { it.isNotEmpty() }
        return value
    }

    internal object ModelProviderMigration {
        private const val PRIMARY_SCENE = "scene.vlm.operation.primary"

        fun ensureMigrated() {
            val mmkv = MMKV.defaultMMKV() ?: return
            if (mmkv.decodeBool(MIGRATION_DONE_KEY, false)) {
                return
            }

            try {
                val legacyUserId = OssIdentity.currentUserIdOrNull()
                val providerConfig = resolveEffectiveLegacyConfig(mmkv, legacyUserId)
                if (providerConfig.baseUrl.isNotBlank()) {
                    mmkv.encode(KEY_PROVIDER_BASE_URL, providerConfig.baseUrl)
                }
                if (providerConfig.apiKey.isNotBlank()) {
                    mmkv.encode(KEY_PROVIDER_API_KEY, providerConfig.apiKey)
                }

                val mergedOverrides = SceneModelOverrideStore.readLegacyOverrideMapForScope(mmkv, null)
                    .toMutableMap()
                if (!legacyUserId.isNullOrBlank()) {
                    mergedOverrides.putAll(
                        SceneModelOverrideStore.readLegacyOverrideMapForScope(mmkv, legacyUserId)
                    )
                }

                val legacyModel = readScopedString(mmkv, LEGACY_MODEL_OVERRIDE_KEY, legacyUserId)
                    ?.takeIf { SceneModelOverrideStore.isValidModelName(it) }
                    ?: readScopedString(mmkv, LEGACY_MODEL_OVERRIDE_KEY, null)
                        ?.takeIf { SceneModelOverrideStore.isValidModelName(it) }
                if (legacyModel != null) {
                    mergedOverrides.putIfAbsent(PRIMARY_SCENE, legacyModel)
                } else if (
                    (providerConfig.baseUrl.isNotBlank() || providerConfig.apiKey.isNotBlank()) &&
                    !mergedOverrides.containsKey(PRIMARY_SCENE)
                ) {
                    ModelSceneRegistry.getRuntimeProfile(PRIMARY_SCENE)?.model
                        ?.takeIf { SceneModelOverrideStore.isValidModelName(it) }
                        ?.let { mergedOverrides.putIfAbsent(PRIMARY_SCENE, it) }
                }

                if (mergedOverrides.isNotEmpty()) {
                    SceneModelOverrideStore.writeOverrideMap(mmkv, mergedOverrides)
                }
            } catch (t: Throwable) {
                OmniLog.w(TAG, "migrate legacy provider config failed: ${t.message}")
            } finally {
                mmkv.encode(MIGRATION_DONE_KEY, true)
            }
        }

        private fun resolveEffectiveLegacyConfig(mmkv: MMKV, userId: String?): ModelProviderConfig {
            val candidates = buildList {
                if (!userId.isNullOrBlank()) {
                    add(readConfigForScope(mmkv, userId))
                }
                add(readConfigForScope(mmkv, null))
                if (!userId.isNullOrBlank()) {
                    add(readLegacyConfigForScope(mmkv, userId))
                }
                add(readLegacyConfigForScope(mmkv, null))
            }
            return candidates.firstOrNull { it.baseUrl.isNotBlank() || it.apiKey.isNotBlank() }
                ?: ModelProviderConfig()
        }
    }
}
