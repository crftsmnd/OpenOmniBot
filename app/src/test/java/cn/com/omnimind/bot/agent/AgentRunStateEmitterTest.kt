package cn.com.omnimind.bot.agent

import cn.com.omnimind.baselib.llm.ChatCompletionMessage
import cn.com.omnimind.baselib.llm.contentText
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AgentRunStateEmitterTest {
    @Test
    fun replanKeepsStableStepIdsAndCompletionState() = runBlocking {
        val callback = RecordingAgentCallback()
        val emitter = AgentRunStateEmitter(
            callback = callback,
            runId = "run-1",
            contextWindow = 100,
        )
        val initialSteps = listOf(
            AgentPlanStepSnapshot(
                id = "step-1-understand",
                title = "理解目标",
                status = AgentPlanStepStatus.PENDING,
                order = 0,
            ),
            AgentPlanStepSnapshot(
                id = "step-2-execute",
                title = "执行工具",
                status = AgentPlanStepStatus.PENDING,
                order = 1,
            ),
        )

        emitter.replacePlan(initialSteps)
        emitter.markNextStepRunning()
        emitter.completeCurrentStep("done")
        emitter.replan(
            listOf(
                AgentPlanStepSnapshot(
                    id = "new-1",
                    title = "理解目标",
                    status = AgentPlanStepStatus.PENDING,
                    order = 0,
                ),
                AgentPlanStepSnapshot(
                    id = "new-2",
                    title = "执行工具",
                    status = AgentPlanStepStatus.PENDING,
                    order = 1,
                ),
                AgentPlanStepSnapshot(
                    id = "new-3",
                    title = "整理结果",
                    status = AgentPlanStepStatus.PENDING,
                    order = 2,
                ),
            ),
        )

        val latest = callback.lastSnapshot()
        assertEquals("step-1-understand", latest.steps[0].id)
        assertEquals(AgentPlanStepStatus.COMPLETED, latest.steps[0].status)
        assertEquals("step-2-execute", latest.steps[1].id)
        assertEquals(AgentPlanStepStatus.PENDING, latest.steps[1].status)
        assertEquals("new-3", latest.steps[2].id)
    }

    @Test
    fun compressionThresholdsFollowConfiguredContextWindow() = runBlocking {
        val callback = RecordingAgentCallback()
        val emitter = AgentRunStateEmitter(
            callback = callback,
            runId = "run-2",
            contextWindow = 100,
        )

        emitter.updateContextUsage(79)
        assertFalse(emitter.shouldCompress())

        emitter.updateContextUsage(92)
        assertTrue(emitter.shouldCompress())
        assertFalse(emitter.isHardLimited())

        emitter.markCompressed(97)
        assertTrue(emitter.isHardLimited())

        val latest = callback.lastSnapshot()
        assertEquals(97, latest.contextUsage.usedTokens)
        assertEquals(1, latest.contextUsage.compressionCount)
        assertNotNull(latest.contextUsage.lastCompressedAt)
    }

    @Test
    fun compressHistoryPreservesSystemPromptPlanAndRecentMessages() {
        val messages = buildList {
            add(
                ChatCompletionMessage(
                    role = "system",
                    content = JsonPrimitive("system safety"),
                ),
            )
            repeat(6) { index ->
                add(
                    ChatCompletionMessage(
                        role = if (index % 2 == 0) "user" else "assistant",
                        content = JsonPrimitive("middle-message-$index"),
                    ),
                )
            }
            repeat(10) { index ->
                add(
                    ChatCompletionMessage(
                        role = if (index % 2 == 0) "user" else "assistant",
                        content = JsonPrimitive("tail-message-$index"),
                    ),
                )
            }
        }

        val compressed = compressAgentHistory(
            messages = messages,
            stepSnapshot = listOf(
                AgentPlanStepSnapshot(
                    id = "step-1",
                    title = "执行工具",
                    status = AgentPlanStepStatus.RUNNING,
                    order = 0,
                    summary = "正在执行",
                ),
            ),
            userMessage = "请继续完成任务",
        )

        assertEquals("system", compressed.first().role)
        assertEquals("system safety", compressed.first().contentText())
        assertEquals("system", compressed[1].role)
        assertTrue(compressed[1].contentText().contains("Current user goal: 请继续完成任务"))
        assertTrue(compressed[1].contentText().contains("[running] 执行工具: 正在执行"))
        assertTrue(compressed.any { it.contentText().contains("tail-message-9") })
        assertTrue(compressed.none { it.contentText() == "middle-message-0" })
    }

    @Test
    fun runStatePayloadKeepsRunIdSeparateFromOuterTaskId() {
        val payload = AgentRunStateSnapshot(
            runId = "run-123",
            phase = AgentRunPhase.EXECUTING,
        ).toPayload()

        assertEquals("run-123", payload["runId"])
        assertFalse(payload.containsKey("taskId"))
    }
}

private class RecordingAgentCallback : AgentCallback {
    private val snapshots = mutableListOf<AgentRunStateSnapshot>()

    fun lastSnapshot(): AgentRunStateSnapshot = snapshots.last()

    override suspend fun onThinkingStart() = Unit

    override suspend fun onThinkingUpdate(thinking: String) = Unit

    override suspend fun onToolCallStart(toolName: String, arguments: JsonObject) = Unit

    override suspend fun onToolCallProgress(
        toolName: String,
        progress: String,
        extras: Map<String, Any?>,
    ) = Unit

    override suspend fun onToolCallComplete(toolName: String, result: ToolExecutionResult) = Unit

    override suspend fun onRunState(snapshot: AgentRunStateSnapshot) {
        snapshots += snapshot
    }

    override suspend fun onChatMessage(message: String) = Unit

    override suspend fun onClarifyRequired(question: String, missingFields: List<String>?) = Unit

    override suspend fun onComplete(result: AgentResult) = Unit

    override suspend fun onError(error: String) = Unit

    override suspend fun onPermissionRequired(missing: List<String>) = Unit
}
