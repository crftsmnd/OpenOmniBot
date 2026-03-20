package cn.com.omnimind.bot.manager

import io.flutter.plugin.common.EventChannel
import io.flutter.plugin.common.MethodChannel

/**
 * 语音识别统一接口，用于在不同供应商 SDK 之间切换。
 *
 * Flutter 侧通过 MethodChannel 控制（initialize/start/stop/release），通过 EventChannel 接收文本流。
 */
interface SpeechRecognitionManager {
    /** 当前实现是否可用（例如：所需配置是否齐全） */
    val isAvailable: Boolean

    fun setEventSink(sink: EventChannel.EventSink?)

    /**
     * 进入语音界面时的预初始化：
     * - 预取 asr ws token（只用于 WS 握手）
     * - 不建立 WS，不启动录音
     */
    fun initialize(result: MethodChannel.Result)

    fun start(result: MethodChannel.Result): Boolean

    fun stop(result: MethodChannel.Result)

    /**
     * 仅停止发送音频，保持 WS 连接，等待服务端主动断开。
     */
    fun stopSendingOnly(result: MethodChannel.Result)

    fun release()
}
