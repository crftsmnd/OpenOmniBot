package cn.com.omnimind.bot.manager

import io.flutter.plugin.common.EventChannel
import io.flutter.plugin.common.MethodChannel

/**
 * OSS 占位实现：保留 ASR 接口，但不执行任何云端会话逻辑。
 */
class AsrServiceIatSpeechRecognitionManager(
    private val context: android.content.Context,
) : SpeechRecognitionManager {

    companion object {
        private const val NOT_AVAILABLE_CODE = "ASR_NOT_AVAILABLE"
        private const val NOT_AVAILABLE_MESSAGE =
            "ASR is not available in the OSS build yet."
    }

    private var eventSink: EventChannel.EventSink? = null

    override val isAvailable: Boolean
        get() = false

    override fun setEventSink(sink: EventChannel.EventSink?) {
        eventSink = sink
    }

    override fun initialize(result: MethodChannel.Result) {
        result.error(NOT_AVAILABLE_CODE, NOT_AVAILABLE_MESSAGE, "UNSUPPORTED_IN_OSS")
    }

    override fun start(result: MethodChannel.Result): Boolean {
        result.error(NOT_AVAILABLE_CODE, NOT_AVAILABLE_MESSAGE, "UNSUPPORTED_IN_OSS")
        return false
    }

    override fun stop(result: MethodChannel.Result) {
        result.success(null)
    }

    override fun stopSendingOnly(result: MethodChannel.Result) {
        result.success(null)
    }

    override fun release() {
        eventSink = null
    }
}
