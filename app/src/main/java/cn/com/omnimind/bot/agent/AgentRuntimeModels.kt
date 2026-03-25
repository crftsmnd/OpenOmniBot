package cn.com.omnimind.bot.agent

import java.io.File

data class AgentWorkspaceDescriptor(
    val id: String,
    val rootPath: String,
    val androidRootPath: String,
    val uriRoot: String,
    val currentCwd: String,
    val androidCurrentCwd: String,
    val shellRootPath: String,
    val retentionPolicy: String
)

enum class AgentRunPhase(val wireName: String) {
    PLANNING("planning"),
    EXECUTING("executing"),
    REPLANNING("replanning"),
    COMPRESSING("compressing"),
    FINALIZING("finalizing"),
    COMPLETED("completed"),
    FAILED("failed")
}

enum class AgentPlanStepStatus(val wireName: String) {
    PENDING("pending"),
    RUNNING("running"),
    COMPLETED("completed"),
    FAILED("failed"),
    SKIPPED("skipped")
}

enum class AgentWorkflowNodeStatus(val wireName: String) {
    PENDING("pending"),
    ACTIVE("active"),
    COMPLETED("completed"),
    FAILED("failed")
}

data class AgentPlanStepSnapshot(
    val id: String,
    val title: String,
    val status: AgentPlanStepStatus,
    val order: Int,
    val summary: String? = null
) {
    fun toPayload(): Map<String, Any?> = linkedMapOf(
        "id" to id,
        "title" to title,
        "status" to status.wireName,
        "order" to order,
        "summary" to summary
    )
}

data class AgentWorkflowNodeSnapshot(
    val id: String,
    val title: String,
    val status: AgentWorkflowNodeStatus,
    val order: Int
) {
    fun toPayload(): Map<String, Any?> = linkedMapOf(
        "id" to id,
        "title" to title,
        "status" to status.wireName,
        "order" to order
    )
}

data class AgentWorkflowEdgeSnapshot(
    val from: String,
    val to: String
) {
    fun toPayload(): Map<String, Any?> = linkedMapOf(
        "from" to from,
        "to" to to
    )
}

data class AgentWorkflowSnapshot(
    val nodes: List<AgentWorkflowNodeSnapshot>,
    val edges: List<AgentWorkflowEdgeSnapshot>,
    val activeNodeId: String? = null
) {
    fun toPayload(): Map<String, Any?> = linkedMapOf(
        "nodes" to nodes.map { it.toPayload() },
        "edges" to edges.map { it.toPayload() },
        "activeNodeId" to activeNodeId
    )
}

data class AgentContextUsageSnapshot(
    val usedTokens: Int,
    val contextWindow: Int,
    val utilization: Double,
    val compressionCount: Int,
    val lastCompressedAt: Long? = null
) {
    fun toPayload(): Map<String, Any?> = linkedMapOf(
        "usedTokens" to usedTokens,
        "contextWindow" to contextWindow,
        "utilization" to utilization,
        "compressionCount" to compressionCount,
        "lastCompressedAt" to lastCompressedAt
    )
}

data class AgentRunStateSnapshot(
    val runId: String,
    val phase: AgentRunPhase,
    val currentStepId: String? = null,
    val steps: List<AgentPlanStepSnapshot> = emptyList(),
    val workflow: AgentWorkflowSnapshot = AgentWorkflowSnapshot(
        nodes = emptyList(),
        edges = emptyList(),
        activeNodeId = null
    ),
    val contextUsage: AgentContextUsageSnapshot = AgentContextUsageSnapshot(
        usedTokens = 0,
        contextWindow = 128000,
        utilization = 0.0,
        compressionCount = 0,
        lastCompressedAt = null
    )
) {
    fun toPayload(): Map<String, Any?> = linkedMapOf(
        "runId" to runId,
        "phase" to phase.wireName,
        "currentStepId" to currentStepId,
        "steps" to steps.map { it.toPayload() },
        "workflow" to workflow.toPayload(),
        "contextUsage" to contextUsage.toPayload()
    )
}

data class AgentModelOverride(
    val providerProfileId: String,
    val providerProfileName: String? = null,
    val modelId: String,
    val apiBase: String,
    val apiKey: String,
    val contextWindow: Int? = null
)

data class ArtifactAction(
    val type: String,
    val label: String,
    val target: String? = null,
    val payload: Map<String, Any?> = emptyMap()
) {
    fun toPayload(): Map<String, Any?> = mapOf(
        "type" to type,
        "label" to label,
        "target" to target,
        "payload" to payload
    )
}

data class ArtifactRef(
    val id: String,
    val uri: String,
    val title: String,
    val mimeType: String,
    val size: Long,
    val sourceTool: String,
    val workspacePath: String,
    val androidPath: String,
    val previewKind: String,
    val actions: List<ArtifactAction> = emptyList()
) {
    fun fileName(): String = File(androidPath).name

    val embedKind: String
        get() = when (previewKind) {
            "image" -> "image"
            "audio" -> "audio"
            "video" -> "video"
            else -> "link"
        }

    val inlineRenderable: Boolean
        get() = embedKind != "link"

    val renderMarkdown: String
        get() {
            val safeTitle = title
                .replace("\\", "\\\\")
                .replace("[", "\\[")
                .replace("]", "\\]")
            return if (embedKind == "image") {
                "![${safeTitle}]($uri)"
            } else {
                "[${safeTitle}]($uri)"
            }
        }

    fun toPayload(): Map<String, Any?> = mapOf(
        "id" to id,
        "uri" to uri,
        "title" to title,
        "fileName" to fileName(),
        "mimeType" to mimeType,
        "size" to size,
        "sourceTool" to sourceTool,
        "workspacePath" to workspacePath,
        "androidPath" to androidPath,
        "previewKind" to previewKind,
        "embedKind" to embedKind,
        "inlineRenderable" to inlineRenderable,
        "renderMarkdown" to renderMarkdown,
        "actions" to actions.map { it.toPayload() }
    )
}

data class SkillIndexEntry(
    val id: String,
    val name: String,
    val description: String,
    val compatibility: String? = null,
    val metadata: Map<String, String> = emptyMap(),
    val rootPath: String,
    val shellRootPath: String,
    val skillFilePath: String,
    val shellSkillFilePath: String,
    val hasScripts: Boolean,
    val hasReferences: Boolean,
    val hasAssets: Boolean,
    val hasEvals: Boolean
)

data class ResolvedSkillContext(
    val skillId: String,
    val frontmatter: Map<String, String>,
    val metadata: Map<String, String> = emptyMap(),
    val bodyMarkdown: String,
    val loadedReferences: List<String> = emptyList(),
    val scriptsDir: String? = null,
    val assetsDir: String? = null,
    val triggerReason: String
) {
    fun promptSummary(maxChars: Int = 1800): String {
        val skillName = frontmatter["name"]?.ifBlank { skillId } ?: skillId
        val lines = bodyMarkdown.lines()
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .dropWhile { it.startsWith("---") }
            .dropWhile { it.startsWith("#") }
            .take(16)
        val base = buildString {
            appendLine("Skill: $skillName")
            appendLine("Trigger: $triggerReason")
            scriptsDir?.takeIf { it.isNotBlank() }?.let { appendLine("Scripts: $it") }
            assetsDir?.takeIf { it.isNotBlank() }?.let { appendLine("Assets: $it") }
            if (loadedReferences.isNotEmpty()) {
                appendLine("References: ${loadedReferences.joinToString(", ")}")
            }
            appendLine(lines.joinToString("\n"))
        }.trim()
        return if (base.length <= maxChars) base else base.take(maxChars) + "\n..."
    }

    fun stepGuidance(maxChars: Int = 900): String {
        val lines = bodyMarkdown.lines()
            .map { it.trim() }
            .filter { line ->
                line.isNotEmpty() &&
                    !line.startsWith("---") &&
                    !line.startsWith("#") &&
                    !line.startsWith("```")
            }
            .take(10)
        val base = lines.joinToString("\n")
        return if (base.length <= maxChars) base else base.take(maxChars) + "\n..."
    }
}

data class SkillCompatibilityResult(
    val available: Boolean,
    val reason: String? = null
)

data class SkillMatchResult(
    val entry: SkillIndexEntry,
    val confidence: Double,
    val triggerReason: String
)
