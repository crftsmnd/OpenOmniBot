package cn.com.omnimind.bot.ui.channel

import cn.com.omnimind.baselib.util.OmniLog
import cn.com.omnimind.bot.mem0.Mem0Config
import cn.com.omnimind.bot.mem0.Mem0ConfigStore
import io.flutter.embedding.engine.FlutterEngine
import io.flutter.plugin.common.MethodChannel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class Mem0ConfigChannel {
    private val channelName = "cn.com.omnimind.bot/Mem0Config"
    private val scope = CoroutineScope(Dispatchers.IO)
    private var channel: MethodChannel? = null

    fun onCreate() = Unit

    fun setChannel(flutterEngine: FlutterEngine) {
        channel = MethodChannel(flutterEngine.dartExecutor.binaryMessenger, channelName)
        channel?.setMethodCallHandler { call, result ->
            scope.launch {
                try {
                    when (call.method) {
                        "getConfig" -> respondSuccess(result, Mem0ConfigStore.getConfigForUi())
                        "getResolvedConfig" -> respondSuccess(result, Mem0ConfigStore.getResolvedConfigForUi())
                        "saveConfig" -> {
                            val raw = call.arguments<Map<String, Any?>>() ?: emptyMap()
                            val saved = Mem0ConfigStore.saveConfig(Mem0Config.fromMap(raw))
                            respondSuccess(result, saved.toMap(Mem0ConfigStore.getConfigForUi()["source"]?.toString()))
                        }
                        "clearConfig" -> {
                            Mem0ConfigStore.clearConfig()
                            respondSuccess(result, true)
                        }
                        else -> withContext(Dispatchers.Main) { result.notImplemented() }
                    }
                } catch (t: Throwable) {
                    OmniLog.e("[Mem0ConfigChannel]", "channel error: ${t.message}")
                    withContext(Dispatchers.Main) {
                        result.error("MEM0_CONFIG_ERROR", t.message, null)
                    }
                }
            }
        }
    }

    fun clear() {
        channel?.setMethodCallHandler(null)
        channel = null
    }

    private suspend fun respondSuccess(result: MethodChannel.Result, value: Any?) {
        withContext(Dispatchers.Main) {
            result.success(value)
        }
    }
}
