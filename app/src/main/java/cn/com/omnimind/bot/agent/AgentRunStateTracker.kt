package cn.com.omnimind.bot.agent

import cn.com.omnimind.baselib.llm.ChatCompletionMessage
import cn.com.omnimind.baselib.llm.contentText
import kotlinx.serialization.json.JsonPrimitive
import java.util.Locale

internal fun compressAgentHistory(
    messages: List<ChatCompletionMessage>,
    stepSnapshot: List<AgentPlanStepSnapshot>,
    userMessage: String,
    tailCount: Int = 10,
): List<ChatCompletionMessage> {
    if (messages.size <= 12) {
        return messages
    }
    val systemMessage = messages.firstOrNull { it.role == "system" }
    val normalizedTailCount = tailCount.coerceAtLeast(1).coerceAtMost(messages.size)
    val tail = messages.takeLast(normalizedTailCount)
    val middle = messages.drop(if (systemMessage != null) 1 else 0).dropLast(normalizedTailCount)
    val summary = buildString {
        appendLine("Compressed conversation summary:")
        appendLine("Current user goal: ${userMessage.trim()}")
        if (stepSnapshot.isNotEmpty()) {
            appendLine("Plan:")
            stepSnapshot.forEach { step ->
                appendLine(
                    "- [${step.status.wireName}] ${step.title}${
                        step.summary?.takeIf { it.isNotBlank() }?.let { ": $it" } ?: ""
                    }"
                )
            }
        }
        if (middle.isNotEmpty()) {
            appendLine("Earlier context:")
            middle.forEach { message ->
                val role = message.role.lowercase(Locale.ROOT)
                val text = message.contentText().replace(Regex("\\s+"), " ").trim()
                if (text.isNotEmpty()) {
                    appendLine("- $role: ${text.take(240)}")
                }
            }
        }
    }.trim()

    val compressed = mutableListOf<ChatCompletionMessage>()
    if (systemMessage != null) {
        compressed += systemMessage
    }
    compressed += ChatCompletionMessage(
        role = "system",
        content = JsonPrimitive(summary),
    )
    tail.forEach { message ->
        if (message !== systemMessage) {
            compressed += message
        }
    }
    return compressed
}

internal class AgentRunStateEmitter(
    private val callback: AgentCallback,
    private val runId: String,
    private val contextWindow: Int,
) {
    companion object {
        const val NODE_PREPARE_INPUT = "prepareInput"
        const val NODE_PLAN_TASK = "planTask"
        const val NODE_EXECUTE_STEP = "executeStep"
        const val NODE_EVALUATE_PROGRESS = "evaluateProgress"
        const val NODE_COMPRESS_HISTORY = "compressHistory"
        const val NODE_FINALIZE_ANSWER = "finalizeAnswer"
    }

    private val workflowNodes = linkedMapOf(
        NODE_PREPARE_INPUT to AgentWorkflowNodeSnapshot(
            id = NODE_PREPARE_INPUT,
            title = "Prepare Input",
            status = AgentWorkflowNodeStatus.PENDING,
            order = 0,
        ),
        NODE_PLAN_TASK to AgentWorkflowNodeSnapshot(
            id = NODE_PLAN_TASK,
            title = "Plan Task",
            status = AgentWorkflowNodeStatus.PENDING,
            order = 1,
        ),
        NODE_EXECUTE_STEP to AgentWorkflowNodeSnapshot(
            id = NODE_EXECUTE_STEP,
            title = "Execute Step",
            status = AgentWorkflowNodeStatus.PENDING,
            order = 2,
        ),
        NODE_EVALUATE_PROGRESS to AgentWorkflowNodeSnapshot(
            id = NODE_EVALUATE_PROGRESS,
            title = "Evaluate Progress",
            status = AgentWorkflowNodeStatus.PENDING,
            order = 3,
        ),
        NODE_COMPRESS_HISTORY to AgentWorkflowNodeSnapshot(
            id = NODE_COMPRESS_HISTORY,
            title = "Compress History",
            status = AgentWorkflowNodeStatus.PENDING,
            order = 4,
        ),
        NODE_FINALIZE_ANSWER to AgentWorkflowNodeSnapshot(
            id = NODE_FINALIZE_ANSWER,
            title = "Finalize Answer",
            status = AgentWorkflowNodeStatus.PENDING,
            order = 5,
        ),
    )
    private val workflowEdges = listOf(
        AgentWorkflowEdgeSnapshot(from = NODE_PREPARE_INPUT, to = NODE_PLAN_TASK),
        AgentWorkflowEdgeSnapshot(from = NODE_PLAN_TASK, to = NODE_EXECUTE_STEP),
        AgentWorkflowEdgeSnapshot(from = NODE_EXECUTE_STEP, to = NODE_EVALUATE_PROGRESS),
        AgentWorkflowEdgeSnapshot(from = NODE_EVALUATE_PROGRESS, to = NODE_PLAN_TASK),
        AgentWorkflowEdgeSnapshot(from = NODE_EVALUATE_PROGRESS, to = NODE_COMPRESS_HISTORY),
        AgentWorkflowEdgeSnapshot(from = NODE_COMPRESS_HISTORY, to = NODE_EXECUTE_STEP),
        AgentWorkflowEdgeSnapshot(from = NODE_EVALUATE_PROGRESS, to = NODE_FINALIZE_ANSWER),
    )
    private val steps = mutableListOf<AgentPlanStepSnapshot>()
    private var phase: AgentRunPhase = AgentRunPhase.PLANNING
    private var currentStepId: String? = null
    private var activeNodeId: String? = null
    private var contextUsage = AgentContextUsageSnapshot(
        usedTokens = 0,
        contextWindow = contextWindow,
        utilization = 0.0,
        compressionCount = 0,
        lastCompressedAt = null,
    )

    suspend fun replacePlan(nextSteps: List<AgentPlanStepSnapshot>) {
        steps.clear()
        steps.addAll(nextSteps)
        currentStepId = steps.firstOrNull()?.id
        emit()
    }

    suspend fun replan(nextSteps: List<AgentPlanStepSnapshot>) {
        val existingByTitle = steps.associateBy { it.title.trim().lowercase(Locale.ROOT) }
        val merged = nextSteps.mapIndexed { index, step ->
            val existing = existingByTitle[step.title.trim().lowercase(Locale.ROOT)]
            val status = when {
                existing == null -> AgentPlanStepStatus.PENDING
                existing.status == AgentPlanStepStatus.COMPLETED -> AgentPlanStepStatus.COMPLETED
                existing.status == AgentPlanStepStatus.RUNNING -> AgentPlanStepStatus.RUNNING
                else -> AgentPlanStepStatus.PENDING
            }
            step.copy(
                id = existing?.id ?: step.id,
                status = status,
                order = index,
            )
        }
        replacePlan(merged)
    }

    suspend fun activateWorkflowNode(nodeId: String, phase: AgentRunPhase) {
        this.phase = phase
        activeNodeId = nodeId
        workflowNodes.keys.toList().forEach { id ->
            val node = workflowNodes[id] ?: return@forEach
            workflowNodes[id] = when {
                id == nodeId -> node.copy(status = AgentWorkflowNodeStatus.ACTIVE)
                node.status == AgentWorkflowNodeStatus.ACTIVE -> node.copy(status = AgentWorkflowNodeStatus.COMPLETED)
                else -> node
            }
        }
        emit()
    }

    suspend fun markNextStepRunning(summaryHint: String? = null) {
        val runningIndex = steps.indexOfFirst { it.status == AgentPlanStepStatus.RUNNING }
        val targetIndex = if (runningIndex >= 0) {
            runningIndex
        } else {
            steps.indexOfFirst { it.status == AgentPlanStepStatus.PENDING }
        }
        if (targetIndex < 0) {
            emit()
            return
        }
        val step = steps[targetIndex]
        steps[targetIndex] = step.copy(
            status = AgentPlanStepStatus.RUNNING,
            summary = summaryHint ?: step.summary,
        )
        currentStepId = steps[targetIndex].id
        emit()
    }

    suspend fun completeCurrentStep(summary: String? = null) {
        val index = steps.indexOfFirst { it.id == currentStepId || it.status == AgentPlanStepStatus.RUNNING }
        if (index >= 0) {
            val step = steps[index]
            steps[index] = step.copy(
                status = AgentPlanStepStatus.COMPLETED,
                summary = summary?.takeIf { it.isNotBlank() } ?: step.summary,
            )
            currentStepId = steps.firstOrNull { it.status == AgentPlanStepStatus.PENDING }?.id
        }
        emit()
    }

    suspend fun failCurrentStep(summary: String? = null) {
        phase = AgentRunPhase.FAILED
        val index = steps.indexOfFirst { it.id == currentStepId || it.status == AgentPlanStepStatus.RUNNING }
        if (index >= 0) {
            val step = steps[index]
            steps[index] = step.copy(
                status = AgentPlanStepStatus.FAILED,
                summary = summary?.takeIf { it.isNotBlank() } ?: step.summary,
            )
            currentStepId = steps[index].id
        }
        workflowNodes.keys.toList().forEach { id ->
            val node = workflowNodes[id] ?: return@forEach
            workflowNodes[id] = if (node.id == activeNodeId) {
                node.copy(status = AgentWorkflowNodeStatus.FAILED)
            } else {
                node
            }
        }
        emit()
    }

    suspend fun finishSuccessfully() {
        phase = AgentRunPhase.COMPLETED
        activeNodeId = NODE_FINALIZE_ANSWER
        workflowNodes.keys.toList().forEach { id ->
            val node = workflowNodes[id] ?: return@forEach
            workflowNodes[id] = if (
                id == NODE_FINALIZE_ANSWER ||
                node.status == AgentWorkflowNodeStatus.ACTIVE
            ) {
                node.copy(status = AgentWorkflowNodeStatus.COMPLETED)
            } else {
                node
            }
        }
        steps.indices.forEach { index ->
            val step = steps[index]
            steps[index] = when (step.status) {
                AgentPlanStepStatus.PENDING,
                AgentPlanStepStatus.RUNNING -> step.copy(status = AgentPlanStepStatus.COMPLETED)
                else -> step
            }
        }
        currentStepId = null
        emit()
    }

    suspend fun updateContextUsage(usedTokens: Int) {
        val normalizedUsedTokens = usedTokens.coerceAtLeast(0)
        contextUsage = contextUsage.copy(
            usedTokens = normalizedUsedTokens,
            utilization = normalizedUsedTokens.toDouble() / contextWindow.coerceAtLeast(1),
        )
        emit()
    }

    suspend fun markCompressed(usedTokens: Int) {
        val now = System.currentTimeMillis()
        contextUsage = contextUsage.copy(
            usedTokens = usedTokens.coerceAtLeast(0),
            utilization = usedTokens.toDouble() / contextWindow.coerceAtLeast(1),
            compressionCount = contextUsage.compressionCount + 1,
            lastCompressedAt = now,
        )
        emit()
    }

    fun shouldCompress(): Boolean = contextUsage.utilization >= 0.92

    fun isHardLimited(): Boolean = contextUsage.utilization >= 0.97

    fun stepsSnapshot(): List<AgentPlanStepSnapshot> = steps.toList()

    private suspend fun emit() {
        callback.onRunState(
            AgentRunStateSnapshot(
                runId = runId,
                phase = phase,
                currentStepId = currentStepId,
                steps = steps.toList(),
                workflow = AgentWorkflowSnapshot(
                    nodes = workflowNodes.values.toList(),
                    edges = workflowEdges,
                    activeNodeId = activeNodeId,
                ),
                contextUsage = contextUsage,
            ),
        )
    }
}
