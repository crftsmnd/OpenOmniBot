package cn.com.omnimind.bot.agent

internal data class RecoverableToolFailure(
    val toolName: String,
    val summary: String,
)

internal object AgentFailureRecovery {
    fun extractRecoverableToolFailure(
        toolName: String,
        result: ToolExecutionResult,
    ): RecoverableToolFailure? {
        val summary = when (result) {
            is ToolExecutionResult.Error -> result.message
            is ToolExecutionResult.TerminalResult -> result.summaryText.takeIf { !result.success }
            is ToolExecutionResult.ScheduleResult -> result.summaryText.takeIf { !result.success }
            is ToolExecutionResult.McpResult -> result.summaryText.takeIf { !result.success }
            is ToolExecutionResult.Mem0Result -> result.summaryText.takeIf { !result.success }
            is ToolExecutionResult.ContextResult -> result.summaryText.takeIf { !result.success }
            else -> null
        }?.trim()?.takeIf { it.isNotEmpty() }

        return summary?.let {
            RecoverableToolFailure(
                toolName = toolName,
                summary = it,
            )
        }
    }

    fun buildToolFailureRetryPrompt(
        userMessage: String,
        failure: RecoverableToolFailure,
    ): String {
        return buildString {
            appendLine("系统检测到你上一轮工具调用失败后，没有继续返回新的 tool_calls。")
            appendLine("请基于最近一次失败结果继续推进，而不是直接结束。")
            appendLine("优先选择以下其一：")
            appendLine("1. 若可修复，立刻返回新的 assistant.tool_calls 重试。")
            appendLine("2. 若缺少关键信息，直接向用户澄清。")
            appendLine("3. 若确认环境受限，请明确告诉用户具体缺什么以及下一步建议。")
            appendLine("最近一次失败工具：${failure.toolName}")
            appendLine("失败摘要：${failure.summary}")
            appendLine("用户原始请求：$userMessage")
        }.trim()
    }

    fun buildToolFailureExhaustedMessage(failure: RecoverableToolFailure): String {
        return buildString {
            append("刚才在执行 `")
            append(failure.toolName)
            append("` 时连续失败，最近一次错误是：")
            append(failure.summary)
            append("。我先停在这里，避免继续空转；你可以让我按新方案继续重试，或者我先帮你诊断环境问题。")
        }
    }
}
