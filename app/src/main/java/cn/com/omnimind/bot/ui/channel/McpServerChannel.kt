package cn.com.omnimind.bot.ui.channel

import android.content.Context
import cn.com.omnimind.baselib.util.OmniLog
import cn.com.omnimind.bot.mcp.McpServerManager
import io.flutter.embedding.engine.FlutterEngine
import io.flutter.plugin.common.MethodChannel

class McpServerChannel {
    private val EVENT_CHANNEL = "cn.com.omnimind.bot/McpServer"
    private var channel: MethodChannel? = null
    private var appContext: Context? = null

    fun onCreate(context: Context) {
        appContext = context.applicationContext
    }

    fun setChannel(flutterEngine: FlutterEngine) {
        channel = MethodChannel(flutterEngine.dartExecutor.binaryMessenger, EVENT_CHANNEL)
        channel?.setMethodCallHandler { call, result ->
            val context = appContext
            if (context == null) {
                result.error("MCP_INIT_ERROR", "Context not initialized", null)
                return@setMethodCallHandler
            }
            try {
                when (call.method) {
                    "state" -> {
                        result.success(McpServerManager.currentState().toMap())
                    }
                    "setEnabled" -> {
                        val enable = call.argument<Boolean>("enable") ?: false
                        val port = call.argument<Int>("port")
                        val state = McpServerManager.setEnabled(context, enable, port)
                        result.success(state.toMap())
                    }
                    "refreshToken" -> {
                        val state = McpServerManager.refreshToken(context)
                        result.success(state.toMap())
                    }
                    else -> result.notImplemented()
                }
            } catch (e: Exception) {
                OmniLog.e("[McpServerChannel]", "channel error: ${e.message}")
                result.error("MCP_ERROR", e.message, null)
            }
        }
    }

    fun clear() {
        channel?.setMethodCallHandler(null)
        channel = null
    }
}
