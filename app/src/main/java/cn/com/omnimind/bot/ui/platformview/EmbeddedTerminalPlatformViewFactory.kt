package cn.com.omnimind.bot.ui.platformview

import android.content.Context
import android.view.MotionEvent
import android.view.View
import com.ai.assistance.operit.terminal.TerminalManager
import com.ai.assistance.operit.terminal.view.canvas.CanvasTerminalView
import com.ai.assistance.operit.terminal.view.domain.ansi.AnsiTerminalEmulator
import io.flutter.embedding.engine.FlutterEngine
import io.flutter.plugin.common.StandardMessageCodec
import io.flutter.plugin.platform.PlatformView
import io.flutter.plugin.platform.PlatformViewFactory
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class EmbeddedTerminalPlatformViewFactory : PlatformViewFactory(StandardMessageCodec.INSTANCE) {
    companion object {
        const val VIEW_TYPE = "cn.com.omnimind.bot/embedded_terminal_view"

        private val registeredEngineKeys = mutableSetOf<Int>()

        @Synchronized
        fun registerWith(flutterEngine: FlutterEngine) {
            val engineKey = System.identityHashCode(flutterEngine)
            if (!registeredEngineKeys.add(engineKey)) {
                return
            }
            flutterEngine
                .platformViewsController
                .registry
                .registerViewFactory(VIEW_TYPE, EmbeddedTerminalPlatformViewFactory())
        }
    }

    override fun create(
        context: Context,
        viewId: Int,
        args: Any?
    ): PlatformView {
        val params = args as? Map<*, *>
        val sessionId = params?.get("sessionId")?.toString()?.trim().orEmpty()
        val transcript = params?.get("transcript")?.toString().orEmpty()
        return EmbeddedTerminalPlatformView(
            hostContext = context,
            sessionId = sessionId,
            transcript = transcript
        )
    }
}

private class EmbeddedTerminalPlatformView(
    hostContext: Context,
    private val sessionId: String,
    private val transcript: String
) : PlatformView {
    private val terminalManager = TerminalManager.getInstance(hostContext.applicationContext)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val terminalView = CanvasTerminalView(hostContext).apply {
        setFullscreenMode(false)
        setSessionScrollCallbacks(
            sessionId = sessionId,
            onScrollOffsetChanged = { id, offset ->
                terminalManager.saveScrollOffset(id, offset)
            },
            getScrollOffset = { id ->
                terminalManager.getScrollOffset(id)
            }
        )
        setOnTouchListener { v, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> v.parent?.requestDisallowInterceptTouchEvent(true)
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    v.parent?.requestDisallowInterceptTouchEvent(false)
                }
            }
            false
        }
    }

    private fun renderTranscriptFallback() {
        val emulator = AnsiTerminalEmulator()
        if (transcript.isNotBlank()) {
            emulator.parse(transcript)
        }
        terminalView.setEmulator(emulator)
        terminalView.setPty(null)
    }

    init {
        scope.launch {
            terminalManager.terminalState.collectLatest { state ->
                val session = state.sessions.find { it.id == sessionId }
                if (session != null) {
                    terminalView.setEmulator(session.ansiParser)
                    terminalView.setPty(session.pty)
                } else {
                    renderTranscriptFallback()
                }
            }
        }
    }

    override fun getView(): View = terminalView

    override fun dispose() {
        scope.cancel()
        terminalView.release()
    }
}
