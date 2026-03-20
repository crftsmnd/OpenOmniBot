package cn.com.omnimind.baselib.llm

data class ModelProviderConfig(
    val baseUrl: String = "",
    val apiKey: String = "",
    val source: String = "none"
) {
    fun isConfigured(): Boolean = baseUrl.isNotBlank()
}

data class ProviderModelOption(
    val id: String,
    val displayName: String = id,
    val ownedBy: String? = null
)

data class SceneCatalogItem(
    val sceneId: String,
    val description: String? = null,
    val defaultModel: String,
    val effectiveModel: String,
    val transport: String,
    val configSource: String,
    val overrideApplied: Boolean,
    val overrideModel: String? = null,
    val providerConfigured: Boolean = false
)

data class SceneModelOverrideEntry(
    val sceneId: String,
    val model: String
)
