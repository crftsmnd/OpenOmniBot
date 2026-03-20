package cn.com.omnimind.bot.mcp

/**
 * MCP 服务器状态
 */
data class McpServerState(
    val enabled: Boolean,
    val running: Boolean,
    val host: String?,
    val port: Int,
    val token: String,
) {
    fun toMap(): Map<String, Any?> = mapOf(
        "enabled" to enabled,
        "running" to running,
        "host" to host,
        "port" to port,
        "token" to token,
    )
}

/**
 * VLM 任务请求参数
 */
data class VlmTaskRequest(
    val goal: String = "",
    val model: String? = null,
    val maxSteps: Int? = null,
    val packageName: String? = null,
    val needSummary: Boolean? = null,
)

/**
 * 任务状态枚举
 */
enum class TaskStatus {
    RUNNING,           // 正在执行
    WAITING_INPUT,     // 等待用户输入（INFO动作触发）
    USER_PAUSED,       // 用户主动暂停
    SCREEN_LOCKED,     // 屏幕锁定/息屏，等待解锁
    FINISHED,          // 任务完成
    ERROR,             // 任务出错
    CANCELLED          // 任务取消
}

/**
 * 任务状态数据
 */
data class TaskState(
    val taskId: String,
    val goal: String,
    var status: TaskStatus,
    val needSummary: Boolean = false,
    var message: String = "",
    var waitingQuestion: String? = null,
    var chatMessages: MutableList<String> = mutableListOf(),
    @Volatile var summaryText: String? = null,
    val startTime: Long = System.currentTimeMillis(),
    @Volatile var stateChanged: Boolean = false
) {
    fun markStateChanged() {
        stateChanged = true
    }
    
    fun resetStateChanged() {
        stateChanged = false
    }
    
    fun toResponseMap(): Map<String, Any?> = mapOf(
        "taskId" to taskId,
        "goal" to goal,
        "status" to status.name,
        "needSummary" to needSummary,
        "message" to message,
        "waitingQuestion" to waitingQuestion,
        "recentMessages" to chatMessages.takeLast(10),
        "summary" to summaryText,
        "elapsedMs" to (System.currentTimeMillis() - startTime)
    )
    
    fun addChatMessage(content: String) {
        synchronized(chatMessages) {
            chatMessages.add(content)
            if (chatMessages.size > 20) {
                chatMessages.removeAt(0)
            }
        }
    }

    fun updateSummary(summary: String) {
        summaryText = summary
    }
}
