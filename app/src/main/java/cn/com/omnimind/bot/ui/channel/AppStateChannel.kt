package cn.com.omnimind.bot.ui.channel

import android.content.Context
import android.os.Handler
import android.os.Looper
import cn.com.omnimind.baselib.util.OmniLog
import cn.com.omnimind.bot.activity.MainActivity
import io.flutter.embedding.engine.FlutterEngine
import io.flutter.plugin.common.MethodCall
import io.flutter.plugin.common.MethodChannel

/**
 * 应用状态通道 - 处理Flutter与Android应用级状态之间的通信
 */
class AppStateChannel {

    private val TAG = "AppStateChannel"
    private val CHANNEL = "cn.com.omnimind.bot/app_state"

    private var context: Context? = null
    private var methodChannel: MethodChannel? = null


    fun onCreate(context: Context) {
        this.context = context
    }

    fun setChannel(flutterEngine: FlutterEngine) {
        methodChannel = MethodChannel(flutterEngine.dartExecutor.binaryMessenger, CHANNEL)
        methodChannel?.setMethodCallHandler { call, result ->
            handleMethodCall(call, result)
        }
    }

    private fun handleMethodCall(call: MethodCall, result: MethodChannel.Result) {
        when (call.method) {
            "initHalfScreenEngine" -> {
                // Flutter主页面加载完成，通知原生初始化半屏引擎
                OmniLog.d(TAG, "Received initHalfScreenEngine call from Flutter")
                val context = this.context
                if (context is MainActivity) {
                    context.initializeHalfScreenEngine()
                    result.success(true)
                } else {
                    OmniLog.e(TAG, "Context is not MainActivity, cannot initialize half screen engine")
                    result.error("INVALID_CONTEXT", "Context is not MainActivity", null)
                }
            }
            "exitApp" -> {
                OmniLog.d(TAG, "Received exitApp call from Flutter")
                val context = this.context
                if (context is MainActivity) {
                    // 延迟3秒后彻底杀死APP
                    Handler(Looper.getMainLooper()).postDelayed({
                        context.finish()
                        val pid = android.os.Process.myPid()
                        android.os.Process.killProcess(pid)
                    }, 3000)
                    result.success(true)
                } else {
                    OmniLog.e(TAG, "Context is not MainActivity, cannot exit app")
                    result.error("INVALID_CONTEXT", "Context is not MainActivity", null)
                }
            }
            else -> {
                result.notImplemented()
            }
        }
    }

    fun clear() {
        methodChannel?.setMethodCallHandler(null)
        methodChannel = null
    }
}
