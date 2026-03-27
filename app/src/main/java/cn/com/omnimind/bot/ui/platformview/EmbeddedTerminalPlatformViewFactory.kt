package cn.com.omnimind.bot.ui.platformview

import android.content.Context
import android.graphics.Typeface
import android.util.Log
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import android.view.View
import android.widget.FrameLayout
import android.widget.TextView
import com.ai.assistance.operit.terminal.TerminalManager
import com.rk.settings.Settings
import com.termux.terminal.TerminalEmulator
import com.termux.terminal.TerminalSession
import com.termux.view.TerminalView
import com.termux.view.TerminalViewClient
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

    override fun create(context: Context, viewId: Int, args: Any?): PlatformView {
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
    private val container = FrameLayout(hostContext)
    private val terminalView = TerminalView(hostContext, null).apply {
        setTerminalViewClient(NoOpTerminalViewClient())
        setTextSize(Settings.terminal_font_size)
        setTypeface(Typeface.MONOSPACE)
    }
    private val transcriptView = TextView(hostContext).apply {
        typeface = Typeface.MONOSPACE
        textSize = Settings.terminal_font_size.toFloat()
        setTextIsSelectable(true)
        text = transcript
    }

    init {
        container.addView(
            transcriptView,
            FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            )
        )

        scope.launch {
            terminalManager.terminalState.collectLatest { state ->
                val session = state.sessions.find { it.id == sessionId }
                val liveSession = terminalManager.getTerminalSession(sessionId)
                if (session != null && liveSession != null) {
                    attachLiveSession(liveSession)
                } else {
                    showTranscript(session?.transcript ?: transcript)
                }
            }
        }
    }

    private fun attachLiveSession(session: TerminalSession) {
        if (terminalView.parent == null) {
            container.removeAllViews()
            container.addView(
                terminalView,
                FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.MATCH_PARENT,
                    FrameLayout.LayoutParams.MATCH_PARENT
                )
            )
        }
        terminalView.attachSession(session)
        terminalView.onScreenUpdated()
    }

    private fun showTranscript(text: String) {
        transcriptView.text = text
        if (transcriptView.parent == null) {
            container.removeAllViews()
            container.addView(
                transcriptView,
                FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.MATCH_PARENT,
                    FrameLayout.LayoutParams.MATCH_PARENT
                )
            )
        }
    }

    override fun getView(): View = container

    override fun dispose() {
        scope.cancel()
    }
}

private class NoOpTerminalViewClient : TerminalViewClient {
    override fun onScale(scale: Float): Float = 1.0f

    override fun onSingleTapUp(e: MotionEvent) = Unit

    override fun shouldBackButtonBeMappedToEscape(): Boolean = false

    override fun shouldEnforceCharBasedInput(): Boolean = false

    override fun getInputMode(): Int = 0

    override fun shouldUseCtrlSpaceWorkaround(): Boolean = false

    override fun isTerminalViewSelected(): Boolean = true

    override fun copyModeChanged(copyMode: Boolean) = Unit

    override fun onKeyDown(keyCode: Int, e: KeyEvent, session: TerminalSession): Boolean = false

    override fun onKeyUp(keyCode: Int, e: KeyEvent): Boolean = false

    override fun onLongPress(event: MotionEvent): Boolean = false

    override fun readControlKey(): Boolean = false

    override fun readAltKey(): Boolean = false

    override fun readShiftKey(): Boolean = false

    override fun readFnKey(): Boolean = false

    override fun onCodePoint(codePoint: Int, ctrlDown: Boolean, session: TerminalSession): Boolean = false

    override fun onEmulatorSet() = Unit

    override fun logError(tag: String, message: String) {
        Log.e(tag, message)
    }

    override fun logWarn(tag: String, message: String) {
        Log.w(tag, message)
    }

    override fun logInfo(tag: String, message: String) {
        Log.i(tag, message)
    }

    override fun logDebug(tag: String, message: String) {
        Log.d(tag, message)
    }

    override fun logVerbose(tag: String, message: String) {
        Log.v(tag, message)
    }

    override fun logStackTraceWithMessage(tag: String, message: String, e: Exception) {
        Log.e(tag, message, e)
    }

    override fun logStackTrace(tag: String, e: Exception) {
        Log.e(tag, e.message, e)
    }
}
