package cn.com.omnimind.baselib.llm

import cn.com.omnimind.baselib.util.OmniLog
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.tencent.mmkv.MMKV

object SceneModelOverrideStore {
    private const val TAG = "SceneModelOverrideStore"
    private const val KEY_SCENE_OVERRIDE_MAP = "scene_model_override_map"
    private val ALLOWED_SCENES = setOf(
        "scene.dispatch.model",
        "scene.vlm.operation.primary",
        "scene.compactor.context",
        "scene.loading.sprite"
    )
    private val gson = Gson()

    fun getOverrideEntries(): List<SceneModelOverrideEntry> {
        return getOverrideMap()
            .entries
            .sortedBy { it.key }
            .map { SceneModelOverrideEntry(sceneId = it.key, model = it.value) }
    }

    fun getOverrideMap(): Map<String, String> {
        ModelProviderConfigStore.ModelProviderMigration.ensureMigrated()
        val mmkv = MMKV.defaultMMKV() ?: return emptyMap()
        return readOverrideMap(mmkv).toSortedMap()
    }

    fun getOverrideModel(sceneId: String): String? {
        return getOverrideMap()[sceneId]?.trim()?.takeIf { it.isNotEmpty() }
    }

    fun saveOverride(sceneId: String, model: String) {
        require(sceneId.trim().startsWith("scene.")) { "非法 sceneId: $sceneId" }
        require(sceneId.trim() in ALLOWED_SCENES) { "不支持的 sceneId: $sceneId" }
        require(isValidModelName(model)) { "非法模型名: $model" }
        val mmkv = MMKV.defaultMMKV() ?: return
        val current = readOverrideMap(mmkv).toMutableMap()
        current[sceneId.trim()] = model.trim()
        writeOverrideMap(mmkv, current)
    }

    fun clearOverride(sceneId: String) {
        val mmkv = MMKV.defaultMMKV() ?: return
        val current = readOverrideMap(mmkv).toMutableMap()
        current.remove(sceneId.trim())
        writeOverrideMap(mmkv, current)
    }

    fun isValidModelName(value: String): Boolean {
        val normalized = value.trim()
        return normalized.isNotEmpty() && !normalized.startsWith("scene.")
    }

    internal fun readOverrideMap(mmkv: MMKV): Map<String, String> {
        val raw = mmkv.decodeString(KEY_SCENE_OVERRIDE_MAP)
            ?.trim()
            ?.takeIf { it.isNotEmpty() }
            ?: return emptyMap()
        return parseOverrideMap(raw)
    }

    internal fun readLegacyOverrideMapForScope(mmkv: MMKV, userId: String?): Map<String, String> {
        val raw = mmkv.decodeString(ModelProviderConfigStore.scopedKey(KEY_SCENE_OVERRIDE_MAP, userId))
            ?.trim()
            ?.takeIf { it.isNotEmpty() }
            ?: return emptyMap()
        return parseOverrideMap(raw)
    }

    internal fun writeOverrideMap(mmkv: MMKV, map: Map<String, String>) {
        val normalized = map.entries
            .mapNotNull { (sceneId, model) ->
                val normalizedSceneId = sceneId.trim()
                    .takeIf { it.startsWith("scene.") && it in ALLOWED_SCENES }
                    ?: return@mapNotNull null
                val normalizedModel = model.trim().takeIf { isValidModelName(it) } ?: return@mapNotNull null
                normalizedSceneId to normalizedModel
            }
            .toMap()
        mmkv.encode(KEY_SCENE_OVERRIDE_MAP, gson.toJson(normalized))
    }

    private fun parseOverrideMap(raw: String): Map<String, String> {
        return try {
            val type = object : TypeToken<Map<String, String>>() {}.type
            val parsed: Map<String, String> = gson.fromJson(raw, type) ?: emptyMap()
            parsed.entries
                .mapNotNull { (sceneId, model) ->
                    val normalizedSceneId = sceneId.trim()
                        .takeIf { it.startsWith("scene.") && it in ALLOWED_SCENES }
                        ?: return@mapNotNull null
                    val normalizedModel = model.trim().takeIf { isValidModelName(it) } ?: return@mapNotNull null
                    normalizedSceneId to normalizedModel
                }
                .toMap()
        } catch (t: Throwable) {
            OmniLog.w(TAG, "read override map failed: ${t.message}")
            emptyMap()
        }
    }
}
