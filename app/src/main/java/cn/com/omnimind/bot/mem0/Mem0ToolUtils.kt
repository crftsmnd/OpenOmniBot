package cn.com.omnimind.bot.mem0

import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive

object Mem0ToolUtils {
    const val TOOL_CONFIGURE = "mem0_configure"
    const val TOOL_ADD = "mem0_add"
    const val TOOL_LIST = "mem0_list"
    const val TOOL_GET = "mem0_get"
    const val TOOL_UPDATE = "mem0_update"
    const val TOOL_DELETE = "mem0_delete"
    const val TOOL_HISTORY = "mem0_history"
    const val TOOL_SEARCH = "mem0_search"
    const val TOOL_DELETE_ALL = "mem0_delete_all"
    const val TOOL_RESET = "mem0_reset"

    val toolDisplayNames: Map<String, String> = linkedMapOf(
        TOOL_CONFIGURE to "配置云记忆",
        TOOL_ADD to "写入云记忆",
        TOOL_LIST to "查看云记忆",
        TOOL_GET to "读取记忆详情",
        TOOL_UPDATE to "更新记忆",
        TOOL_DELETE to "删除记忆",
        TOOL_HISTORY to "查看记忆历史",
        TOOL_SEARCH to "检索云记忆",
        TOOL_DELETE_ALL to "清空用户记忆",
        TOOL_RESET to "重置用户记忆"
    )

    data class ConfirmationValidation(
        val confirmed: Boolean,
        val clarifyQuestion: String? = null
    )

    fun isMem0Tool(toolName: String): Boolean = toolDisplayNames.containsKey(toolName)

    fun displayName(toolName: String): String = toolDisplayNames[toolName] ?: toolName

    fun requiresConfirmation(toolName: String): Boolean {
        return toolName == TOOL_DELETE_ALL || toolName == TOOL_RESET
    }

    fun validateConfirmation(toolName: String, args: JsonObject): ConfirmationValidation {
        if (!requiresConfirmation(toolName)) {
            return ConfirmationValidation(confirmed = true)
        }
        val confirmed = args["confirm"]?.jsonPrimitive?.contentOrNull
            ?.toBooleanStrictOrNull() == true
        if (confirmed) {
            return ConfirmationValidation(confirmed = true)
        }
        val question = when (toolName) {
            TOOL_DELETE_ALL -> "这是清空当前 Mem0 空间下所有记忆的危险操作。请先向用户二次确认，再在 confirm=true 时继续。"
            TOOL_RESET -> "这是重置当前 Mem0 记忆空间的危险操作。请先向用户二次确认，再在 confirm=true 时继续。"
            else -> null
        }
        return ConfirmationValidation(confirmed = false, clarifyQuestion = question)
    }
}
