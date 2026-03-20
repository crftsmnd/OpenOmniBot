package cn.com.omnimind.bot.termux

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.FileObserver
import android.util.Base64
import androidx.core.content.ContextCompat
import cn.com.omnimind.bot.agent.AgentWorkspaceManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.io.File
import java.io.RandomAccessFile
import java.nio.charset.StandardCharsets
import java.util.UUID
import java.util.concurrent.atomic.AtomicInteger
import kotlin.coroutines.resume

data class TermuxCommandSpec(
    val command: String,
    val executionMode: String = EXECUTION_MODE_TERMUX,
    val prootDistro: String? = null,
    val workingDirectory: String? = null,
    val timeoutSeconds: Int = DEFAULT_TIMEOUT_SECONDS
) {
    companion object {
        const val EXECUTION_MODE_TERMUX = "termux"
        const val EXECUTION_MODE_PROOT = "proot"
        const val DEFAULT_PROOT_DISTRO = "ubuntu"
        const val DEFAULT_TIMEOUT_SECONDS = 60
    }
}

data class TermuxInvocation(
    val commandPath: String,
    val arguments: List<String>,
    val label: String,
    val description: String
)

data class TermuxCommandResult(
    val success: Boolean,
    val timedOut: Boolean,
    val resultCode: Int?,
    val errorCode: Int?,
    val errorMessage: String?,
    val stdout: String,
    val stderr: String,
    val rawExtras: Map<String, Any?>,
    val terminalOutput: String = "",
    val liveSessionId: String? = null,
    val liveStreamState: String = LIVE_STREAM_STATE_COMPLETED,
    val liveFallbackReason: String? = null
)

internal data class ParsedTermuxResult(
    val stdout: String,
    val stderr: String,
    val resultCode: Int?,
    val errorCode: Int?,
    val errorMessage: String?,
    val success: Boolean
)

data class TermuxLiveUpdate(
    val sessionId: String,
    val summary: String,
    val outputDelta: String = "",
    val streamState: String = LIVE_STREAM_STATE_RUNNING
)

data class TermuxLiveEnvironmentResult(
    val success: Boolean,
    val wrapperReady: Boolean,
    val sharedStorageReady: Boolean,
    val message: String
)

private data class LiveExecutionPlan(
    val sessionId: String,
    val session: TermuxLiveSession? = null,
    val fallbackReason: String? = null
)

object TermuxCommandBuilder {
    internal const val TERMUX_PREFIX = "/data/data/com.termux/files/usr"
    internal const val TERMUX_BASH_PATH = "$TERMUX_PREFIX/bin/bash"
    internal const val TERMUX_PROOT_DISTRO_PATH = "$TERMUX_PREFIX/bin/proot-distro"

    fun build(spec: TermuxCommandSpec): TermuxInvocation {
        val normalizedCommand = spec.command.trim()
        require(normalizedCommand.isNotEmpty()) { "command 不能为空" }

        val executionMode = spec.executionMode.trim().lowercase()
        val wrappedCommand = buildWrappedCommand(spec)

        return when (executionMode) {
            TermuxCommandSpec.EXECUTION_MODE_TERMUX -> TermuxInvocation(
                commandPath = TERMUX_BASH_PATH,
                arguments = listOf("-lc", wrappedCommand),
                label = "Omnibot Agent",
                description = "Run non-interactive command in Termux"
            )

            TermuxCommandSpec.EXECUTION_MODE_PROOT -> {
                val distro = spec.prootDistro?.trim()
                    ?.takeIf { it.isNotEmpty() }
                    ?: TermuxCommandSpec.DEFAULT_PROOT_DISTRO
                TermuxInvocation(
                    commandPath = TERMUX_PROOT_DISTRO_PATH,
                    arguments = buildList {
                        add("login")
                        add(distro)
                        addAll(buildWorkspaceBindArguments())
                        add("--shared-tmp")
                        add("--")
                        add("bash")
                        add("-lc")
                        add(wrappedCommand)
                    },
                    label = "Omnibot Agent (proot)",
                    description = "Run non-interactive command in proot distro: $distro"
                )
            }

            else -> throw IllegalArgumentException("不支持的 executionMode: $executionMode")
        }
    }

    internal fun buildWrappedCommand(spec: TermuxCommandSpec): String {
        val normalizedCommand = spec.command.trim()
        require(normalizedCommand.isNotEmpty()) { "command 不能为空" }
        val wrapped = wrapCommand(
            command = normalizedCommand,
            workingDirectory = spec.workingDirectory?.trim().orEmpty()
        )
        return wrapped
    }

    internal fun quoteForShell(value: String): String {
        return "'" + value.replace("'", "'\"'\"'") + "'"
    }

    private fun wrapCommand(command: String, workingDirectory: String): String {
        if (workingDirectory.isBlank()) return command
        return "cd ${quoteForShell(workingDirectory)} && $command"
    }

    internal fun buildWorkspaceBindArguments(): List<String> {
        return listOf(
            "--bind",
            "${AgentWorkspaceManager.ROOT_PATH}:${AgentWorkspaceManager.SHELL_ROOT_PATH}"
        )
    }
}

object TermuxCommandRunner {
    private const val TERMUX_PACKAGE_NAME = "com.termux"
    private const val TERMUX_RUN_COMMAND_SERVICE = "com.termux.app.RunCommandService"
    private const val TERMUX_RUN_COMMAND_ACTION = "com.termux.RUN_COMMAND"
    private const val TERMUX_RUN_COMMAND_PERMISSION = "com.termux.permission.RUN_COMMAND"
    private const val EXTRA_COMMAND_PATH = "com.termux.RUN_COMMAND_PATH"
    private const val EXTRA_ARGUMENTS = "com.termux.RUN_COMMAND_ARGUMENTS"
    private const val EXTRA_WORKDIR = "com.termux.RUN_COMMAND_WORKDIR"
    private const val EXTRA_BACKGROUND = "com.termux.RUN_COMMAND_BACKGROUND"
    private const val EXTRA_COMMAND_LABEL = "com.termux.RUN_COMMAND_LABEL"
    private const val EXTRA_COMMAND_DESCRIPTION = "com.termux.RUN_COMMAND_DESCRIPTION"
    private const val EXTRA_PENDING_INTENT = "com.termux.RUN_COMMAND_PENDING_INTENT"
    private const val RECEIVER_ACTION_PREFIX = "cn.com.omnimind.bot.termux.RESULT"
    private const val DEFAULT_WORKDIR = "/data/data/com.termux/files/home"
    private const val TERMUX_INTERNAL_OK = -1
    private const val MAX_INTERNAL_TIMEOUT_SECONDS = 1800
    private const val LIVE_EXEC_SCRIPT_VERSION = 3
    private const val LIVE_EXEC_PREFS = "termux_live_exec"
    private const val LIVE_EXEC_PREF_KEY = "live_exec_script_version"
    private const val LIVE_EXEC_SCRIPT_PATH = "$DEFAULT_WORKDIR/bin/live_exec.sh"
    private const val UBUNTU_SETUP_VERSION = 2
    private const val LIVE_LOG_DIR_NAME = "termux_logs"
    private const val LIVE_UPDATE_DELAY_MS = 120L
    private const val LIVE_LOG_RETENTION_MS = 5 * 60 * 1000L
    private const val MAX_TERMINAL_TRANSCRIPT_LINES = 600
    private const val MAX_TERMINAL_TRANSCRIPT_CHARS = 64 * 1024

    private val requestCodeGenerator = AtomicInteger(6000)
    private val cleanupScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val ubuntuSetupMutex = Mutex()
    private val ansiEscapeRegex = Regex("""\u001B(?:\[[0-?]*[ -/]*[@-~]|\([A-Za-z0-9])""")
    private val knownNoiseRegexes = listOf(
        Regex("""^Warning: CPU doesn't support 32-bit instructions, some software may not work\.$"""),
        Regex("""^proot warning: can't sanitize binding "/proc/self/fd/\d+": No such file or directory$""")
    )

    @Volatile
    private var ubuntuSetupVerifiedVersionInProcess = -1

    suspend fun execute(
        context: Context,
        spec: TermuxCommandSpec,
        onLiveUpdate: suspend (TermuxLiveUpdate) -> Unit = {}
    ): TermuxCommandResult {
        val appContext = context.applicationContext
        validatePreconditions(appContext)?.let { return it }
        val normalizedSpec = normalizeSpec(spec)

        if (shouldEnsureUbuntu(normalizedSpec)) {
            val setupError = ensureUbuntuEnvironmentReady(appContext)
            if (setupError != null) {
                return buildErrorResult(setupError)
            }
        }

        val directInvocation = try {
            TermuxCommandBuilder.build(normalizedSpec)
        } catch (error: IllegalArgumentException) {
            return buildErrorResult(error.message)
        }

        val livePlan = prepareLiveExecution(appContext, normalizedSpec, onLiveUpdate)
        val result = if (livePlan.session != null) {
            executeWithLiveSession(appContext, normalizedSpec, livePlan.session, onLiveUpdate)
        } else {
            livePlan.fallbackReason?.let { reason ->
                onLiveUpdate(
                    TermuxLiveUpdate(
                        sessionId = livePlan.sessionId,
                        summary = reason,
                        streamState = LIVE_STREAM_STATE_FALLBACK
                    )
                )
            }
            val directResult = executeInvocation(appContext, normalizedSpec, directInvocation)
            finalizeCommandResult(
                result = directResult,
                sessionId = livePlan.sessionId,
                streamState = if (livePlan.fallbackReason.isNullOrBlank()) {
                    LIVE_STREAM_STATE_COMPLETED
                } else {
                    LIVE_STREAM_STATE_FALLBACK
                },
                fallbackReason = livePlan.fallbackReason,
                terminalOutput = buildTerminalOutputFromResult(directResult, livePlan.fallbackReason)
            )
        }

        return result
    }

    suspend fun prepareLiveEnvironment(context: Context): TermuxLiveEnvironmentResult {
        val appContext = context.applicationContext
        if (!isTermuxInstalled(appContext)) {
            return TermuxLiveEnvironmentResult(
                success = false,
                wrapperReady = false,
                sharedStorageReady = false,
                message = "未检测到 Termux，请先安装 Termux。"
            )
        }
        if (!hasRunCommandPermission(appContext)) {
            return TermuxLiveEnvironmentResult(
                success = false,
                wrapperReady = false,
                sharedStorageReady = false,
                message = "当前应用未获得 com.termux.permission.RUN_COMMAND 权限。"
            )
        }

        val ubuntuSetupError = ensureUbuntuEnvironmentReady(appContext)
        if (ubuntuSetupError != null) {
            return TermuxLiveEnvironmentResult(
                success = false,
                wrapperReady = false,
                sharedStorageReady = false,
                message = ubuntuSetupError
            )
        }

        val wrapperReady = ensureLiveExecWrapperInstalled(appContext)
        if (!wrapperReady) {
            return TermuxLiveEnvironmentResult(
                success = false,
                wrapperReady = false,
                sharedStorageReady = false,
                message = "Wrapper 部署失败，请先确认 Termux 已开启 allow-external-apps=true。"
            )
        }

        val logDir = buildLiveLogFile(
            packageName = appContext.packageName,
            sessionId = "setup"
        ).parentFile
        val sharedStorageReady = logDir != null && ensureSharedLogDirectoryReady(appContext, logDir)
        if (!sharedStorageReady) {
            return TermuxLiveEnvironmentResult(
                success = false,
                wrapperReady = true,
                sharedStorageReady = false,
                message = "Wrapper 已部署，但共享存储未就绪，请在 Termux 中执行 termux-setup-storage。"
            )
        }

        return TermuxLiveEnvironmentResult(
            success = true,
            wrapperReady = true,
            sharedStorageReady = true,
            message = "Wrapper 已就绪。它不是常驻后台进程，后续每次终端调用前都会自动校验并按需使用。"
        )
    }

    internal fun buildLiveLogFile(packageName: String, sessionId: String): File {
        return File("/storage/emulated/0/Android/media/$packageName/$LIVE_LOG_DIR_NAME/session_$sessionId.log")
    }

    internal fun buildLiveScriptInstallCommand(scriptVersion: Int = LIVE_EXEC_SCRIPT_VERSION): String {
        val encodedScript = Base64.encodeToString(
            LIVE_EXEC_SCRIPT.trimIndent().toByteArray(StandardCharsets.UTF_8),
            Base64.NO_WRAP
        )
        val scriptPath = TermuxCommandBuilder.quoteForShell(LIVE_EXEC_SCRIPT_PATH)
        return """
            mkdir -p ${TermuxCommandBuilder.quoteForShell("$DEFAULT_WORKDIR/bin")}
            printf '%s' '$encodedScript' | base64 -d > $scriptPath
            chmod +x $scriptPath
            grep -Fqx '# omnibot-live-exec-version:$scriptVersion' $scriptPath
        """.trimIndent()
    }

    internal fun trimTerminalOutput(
        text: String,
        maxLines: Int = MAX_TERMINAL_TRANSCRIPT_LINES,
        maxChars: Int = MAX_TERMINAL_TRANSCRIPT_CHARS
    ): String {
        if (text.isEmpty()) return text

        var candidate = if (text.length > maxChars) text.takeLast(maxChars) else text
        val lines = candidate.split('\n')
        if (lines.size > maxLines) {
            candidate = lines.takeLast(maxLines).joinToString("\n")
        }

        val wasTrimmed = candidate.length < text.length || lines.size > maxLines
        if (!wasTrimmed) return candidate

        val notice = "[更早输出已省略]\n"
        val body = candidate.removePrefix(notice)
        val remaining = (maxChars - notice.length).coerceAtLeast(0)
        return notice + body.takeLast(remaining)
    }

    internal fun sanitizeTerminalNoise(text: String): String {
        if (text.isBlank()) return text
        val filtered = text.lineSequence()
            .filterNot { line -> shouldSuppressTerminalLine(line) }
            .joinToString("\n")
            .replace(Regex("\n{3,}"), "\n\n")
        return filtered.trim('\n')
    }

    private fun shouldSuppressTerminalLine(line: String): Boolean {
        val normalized = ansiEscapeRegex.replace(line, "").trim()
        if (normalized.isEmpty()) return false
        return knownNoiseRegexes.any { regex -> regex.matches(normalized) }
    }

    internal fun buildUbuntuReadyCheckCommand(): String {
        return """
            mkdir -p ${TermuxCommandBuilder.quoteForShell(AgentWorkspaceManager.ROOT_PATH)} &&
            bash -ic "u --shared-tmp -- bash -lc 'test -d ${AgentWorkspaceManager.SHELL_ROOT_PATH} && test -w ${AgentWorkspaceManager.SHELL_ROOT_PATH}'" >/dev/null 2>&1
        """.trimIndent()
    }

    private fun normalizeSpec(spec: TermuxCommandSpec): TermuxCommandSpec {
        val normalizedMode = spec.executionMode.trim().lowercase()
        if (normalizedMode != TermuxCommandSpec.EXECUTION_MODE_PROOT) {
            return spec.copy(executionMode = normalizedMode.ifEmpty { TermuxCommandSpec.EXECUTION_MODE_TERMUX })
        }
        val normalizedDistro = spec.prootDistro?.trim()
            ?.takeIf { it.isNotEmpty() }
            ?: TermuxCommandSpec.DEFAULT_PROOT_DISTRO
        return spec.copy(
            executionMode = TermuxCommandSpec.EXECUTION_MODE_PROOT,
            prootDistro = normalizedDistro
        )
    }

    private fun shouldEnsureUbuntu(spec: TermuxCommandSpec): Boolean {
        return spec.executionMode == TermuxCommandSpec.EXECUTION_MODE_PROOT &&
            spec.prootDistro?.trim()?.lowercase() == TermuxCommandSpec.DEFAULT_PROOT_DISTRO
    }

    private suspend fun ensureUbuntuEnvironmentReady(context: Context): String? {
        if (ubuntuSetupVerifiedVersionInProcess == UBUNTU_SETUP_VERSION) {
            return null
        }

        return ubuntuSetupMutex.withLock {
            if (ubuntuSetupVerifiedVersionInProcess == UBUNTU_SETUP_VERSION) {
                return@withLock null
            }

            val readyCheckResult = verifyUbuntuEnvironment(context)
            if (readyCheckResult.success) {
                ubuntuSetupVerifiedVersionInProcess = UBUNTU_SETUP_VERSION
                return@withLock null
            }

            buildUbuntuReadyCheckErrorMessage(readyCheckResult)
        }
    }

    private suspend fun verifyUbuntuEnvironment(context: Context): TermuxCommandResult {
        return executeBootstrapCommand(
            context = context,
            command = buildUbuntuReadyCheckCommand(),
            timeoutSeconds = 60,
            label = "Omnibot Agent ubuntu verify",
            description = "Verify ubuntu runtime by alias u login"
        )
    }

    private fun buildUbuntuReadyCheckErrorMessage(result: TermuxCommandResult): String {
        val details = listOf(
            result.errorMessage?.trim(),
            result.stderr.trim().takeIf { it.isNotEmpty() },
            result.stdout.trim().takeIf { it.isNotEmpty() }
        ).firstOrNull { !it.isNullOrEmpty() }
        val prefix = "Ubuntu 终端环境未就绪，请先在 Termux 设置页执行“一键初始化指令”（会安装 Ubuntu 并配置 alias u）。"
        return if (details.isNullOrEmpty()) {
            prefix
        } else {
            "$prefix 详情：$details"
        }
    }

    internal fun parseResultMap(rawResultMap: Map<String, Any?>): ParsedTermuxResult {
        val stdout = findStringValueBySuffix(rawResultMap, "STDOUT").orEmpty()
        val stderr = findStringValueBySuffix(rawResultMap, "STDERR").orEmpty()
        val resultCode = findIntValueBySuffix(rawResultMap, "EXIT_CODE")
            ?: findIntValueBySuffix(rawResultMap, "RESULT_CODE")
        val internalErr = findIntValueBySuffix(rawResultMap, "ERR")
        val normalizedErrorCode = internalErr?.takeUnless { it == TERMUX_INTERNAL_OK }
        val normalizedErrorMessage = findStringValueBySuffix(rawResultMap, "ERRMSG")
            ?.trim()
            ?.takeIf { it.isNotEmpty() }

        val success = when {
            normalizedErrorCode != null -> false
            !normalizedErrorMessage.isNullOrBlank() -> false
            resultCode != null -> resultCode == 0
            else -> true
        }

        return ParsedTermuxResult(
            stdout = sanitizeTerminalNoise(stdout),
            stderr = sanitizeTerminalNoise(stderr),
            resultCode = resultCode,
            errorCode = normalizedErrorCode,
            errorMessage = normalizedErrorMessage,
            success = success
        )
    }

    private suspend fun prepareLiveExecution(
        context: Context,
        spec: TermuxCommandSpec,
        onLiveUpdate: suspend (TermuxLiveUpdate) -> Unit
    ): LiveExecutionPlan {
        val sessionId = UUID.randomUUID().toString()
        val logFile = buildLiveLogFile(context.packageName, sessionId)
        val logDir = logFile.parentFile
        if (logDir == null) {
            return LiveExecutionPlan(
                sessionId = sessionId,
                fallbackReason = "实时终端输出不可用，已回退为结束后展示结果。"
            )
        }
        runCatching { logDir.mkdirs() }

        val wrapperReady = ensureLiveExecWrapperInstalled(context)
        if (!wrapperReady) {
            return LiveExecutionPlan(
                sessionId = sessionId,
                fallbackReason = "实时终端输出不可用，已回退为结束后展示结果。"
            )
        }

        val sharedLogReady = ensureSharedLogDirectoryReady(context, logDir)
        if (!sharedLogReady) {
            return LiveExecutionPlan(
                sessionId = sessionId,
                fallbackReason = "共享存储未就绪，已回退为结束后展示结果。请在 Termux 中执行 termux-setup-storage。"
            )
        }

        return runCatching {
            LiveExecutionPlan(
                sessionId = sessionId,
                session = TermuxLiveSession(
                    sessionId = sessionId,
                    logFile = logFile,
                    onLiveUpdate = onLiveUpdate
                )
            )
        }.getOrElse {
            LiveExecutionPlan(
                sessionId = sessionId,
                fallbackReason = "实时终端输出初始化失败，已回退为结束后展示结果。"
            )
        }
    }

    private suspend fun executeWithLiveSession(
        context: Context,
        spec: TermuxCommandSpec,
        liveSession: TermuxLiveSession,
        onLiveUpdate: suspend (TermuxLiveUpdate) -> Unit
    ): TermuxCommandResult {
        liveSession.start()
        onLiveUpdate(
            TermuxLiveUpdate(
                sessionId = liveSession.sessionId,
                summary = "正在准备实时终端输出",
                streamState = LIVE_STREAM_STATE_STARTING
            )
        )

        val invocation = buildLiveInvocation(spec, liveSession)
        val commandResult = executeInvocation(context, spec, invocation)
        liveSession.stop()

        val sessionOutput = liveSession.snapshotOutput()
        val effectiveFallbackReason = if (sessionOutput.isBlank()) {
            "实时终端输出未写入日志，已回退为结束后展示结果。"
        } else {
            null
        }
        val effectiveOutput = if (sessionOutput.isNotBlank()) {
            sessionOutput
        } else {
            buildTerminalOutputFromResult(commandResult, effectiveFallbackReason)
        }
        scheduleLogCleanup(liveSession.logFile)

        return finalizeCommandResult(
            result = commandResult,
            sessionId = liveSession.sessionId,
            streamState = if (effectiveFallbackReason == null) {
                LIVE_STREAM_STATE_COMPLETED
            } else {
                LIVE_STREAM_STATE_FALLBACK
            },
            fallbackReason = effectiveFallbackReason,
            terminalOutput = effectiveOutput
        )
    }

    private fun buildLiveInvocation(
        spec: TermuxCommandSpec,
        liveSession: TermuxLiveSession
    ): TermuxInvocation {
        val wrappedCommand = TermuxCommandBuilder.buildWrappedCommand(spec)
        val encodedCommand = Base64.encodeToString(
            wrappedCommand.toByteArray(StandardCharsets.UTF_8),
            Base64.NO_WRAP
        )
        val executionMode = spec.executionMode.trim().lowercase()
        val prootDistro = if (executionMode == TermuxCommandSpec.EXECUTION_MODE_PROOT) {
            spec.prootDistro?.trim()
                ?.takeIf { it.isNotEmpty() }
                ?: TermuxCommandSpec.DEFAULT_PROOT_DISTRO
        } else {
            spec.prootDistro?.trim().orEmpty()
        }
        val executeWrapperCommand = buildString {
            append(TermuxCommandBuilder.quoteForShell(LIVE_EXEC_SCRIPT_PATH))
            append(' ')
            append(TermuxCommandBuilder.quoteForShell(encodedCommand))
            append(' ')
            append(TermuxCommandBuilder.quoteForShell(liveSession.logFile.absolutePath))
            append(' ')
            append(TermuxCommandBuilder.quoteForShell(executionMode))
            append(' ')
            append(TermuxCommandBuilder.quoteForShell(prootDistro))
        }
        val shellCommand = """
            if ! ( ${buildLiveExecVerifyCommand()} ) >/dev/null 2>&1; then
              ${buildLiveScriptInstallCommand()}
            fi
            $executeWrapperCommand
        """.trimIndent()

        return TermuxInvocation(
            commandPath = TermuxCommandBuilder.TERMUX_BASH_PATH,
            arguments = listOf("-lc", shellCommand),
            label = "Omnibot Agent",
            description = "Run live non-interactive command in Termux"
        )
    }

    private suspend fun ensureLiveExecWrapperInstalled(context: Context): Boolean {
        val preferences = context.getSharedPreferences(LIVE_EXEC_PREFS, Context.MODE_PRIVATE)
        val storedVersion = preferences.getInt(LIVE_EXEC_PREF_KEY, -1)
        if (storedVersion == LIVE_EXEC_SCRIPT_VERSION && verifyLiveExecWrapper(context)) {
            return true
        }

        val installResult = executeBootstrapCommand(
            context = context,
            command = buildLiveScriptInstallCommand(),
            timeoutSeconds = 30,
            label = "Omnibot Agent bootstrap",
            description = "Install Omnibot live_exec.sh"
        )
        if (!installResult.success) {
            return false
        }

        if (!verifyLiveExecWrapper(context)) {
            return false
        }

        preferences.edit().putInt(LIVE_EXEC_PREF_KEY, LIVE_EXEC_SCRIPT_VERSION).apply()
        return true
    }

    private suspend fun verifyLiveExecWrapper(context: Context): Boolean {
        val verifyResult = executeBootstrapCommand(
            context = context,
            command = buildLiveExecVerifyCommand(),
            timeoutSeconds = 15,
            label = "Omnibot Agent verify",
            description = "Verify Omnibot live_exec.sh"
        )
        return verifyResult.success
    }

    private fun buildLiveExecVerifyCommand(): String {
        return buildString {
            append("[ -x ")
            append(TermuxCommandBuilder.quoteForShell(LIVE_EXEC_SCRIPT_PATH))
            append(" ] && grep -Fqx ")
            append(TermuxCommandBuilder.quoteForShell("# omnibot-live-exec-version:$LIVE_EXEC_SCRIPT_VERSION"))
            append(' ')
            append(TermuxCommandBuilder.quoteForShell(LIVE_EXEC_SCRIPT_PATH))
        }
    }

    private suspend fun ensureSharedLogDirectoryReady(context: Context, logDir: File): Boolean {
        val logDirPath = TermuxCommandBuilder.quoteForShell(logDir.absolutePath)
        val probePath = TermuxCommandBuilder.quoteForShell(
            File(logDir, ".omni_probe_${System.currentTimeMillis()}").absolutePath
        )
        val command = """
            mkdir -p $logDirPath &&
            : > $probePath &&
            rm -f $probePath
        """.trimIndent()
        val probeResult = executeBootstrapCommand(
            context = context,
            command = command,
            timeoutSeconds = 20,
            label = "Omnibot Agent probe",
            description = "Probe shared log directory for live terminal output"
        )
        return probeResult.success
    }

    private suspend fun executeBootstrapCommand(
        context: Context,
        command: String,
        timeoutSeconds: Int,
        label: String,
        description: String
    ): TermuxCommandResult {
        val invocation = TermuxInvocation(
            commandPath = TermuxCommandBuilder.TERMUX_BASH_PATH,
            arguments = listOf("-lc", command),
            label = label,
            description = description
        )
        return executeInvocation(
            context = context,
            spec = TermuxCommandSpec(command = command, timeoutSeconds = timeoutSeconds),
            invocation = invocation
        )
    }

    private suspend fun executeInvocation(
        context: Context,
        spec: TermuxCommandSpec,
        invocation: TermuxInvocation
    ): TermuxCommandResult {
        val timeoutMs = spec.timeoutSeconds.coerceIn(5, MAX_INTERNAL_TIMEOUT_SECONDS) * 1000L
        val result = withTimeoutOrNull(timeoutMs) {
            waitForTermuxResult(context, spec, invocation)
        }

        return result ?: TermuxCommandResult(
            success = false,
            timedOut = true,
            resultCode = null,
            errorCode = null,
            errorMessage = "等待 Termux 命令结果超时，命令可能仍在后台继续运行",
            stdout = "",
            stderr = "",
            rawExtras = emptyMap()
        )
    }

    private suspend fun waitForTermuxResult(
        context: Context,
        spec: TermuxCommandSpec,
        invocation: TermuxInvocation
    ): TermuxCommandResult = withContext(Dispatchers.Main.immediate) {
        suspendCancellableCoroutine { continuation ->
            val requestCode = requestCodeGenerator.incrementAndGet()
            val action = "$RECEIVER_ACTION_PREFIX.$requestCode.${System.currentTimeMillis()}"
            val receiver = object : BroadcastReceiver() {
                override fun onReceive(receiverContext: Context?, intent: Intent?) {
                    runCatching { context.unregisterReceiver(this) }
                    if (!continuation.isCompleted) {
                        continuation.resume(parseTermuxResult(intent))
                    }
                }
            }

            ContextCompat.registerReceiver(
                context,
                receiver,
                IntentFilter(action),
                ContextCompat.RECEIVER_NOT_EXPORTED
            )

            continuation.invokeOnCancellation {
                runCatching { context.unregisterReceiver(receiver) }
            }

            try {
                val resultIntent = Intent(action).setPackage(context.packageName)
                val pendingIntentFlags = PendingIntent.FLAG_ONE_SHOT or
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                        PendingIntent.FLAG_MUTABLE
                    } else {
                        0
                    }
                val pendingIntent = PendingIntent.getBroadcast(
                    context,
                    requestCode,
                    resultIntent,
                    pendingIntentFlags
                )

                val serviceIntent = Intent(TERMUX_RUN_COMMAND_ACTION).apply {
                    setClassName(TERMUX_PACKAGE_NAME, TERMUX_RUN_COMMAND_SERVICE)
                    putExtra(EXTRA_COMMAND_PATH, invocation.commandPath)
                    putExtra(EXTRA_ARGUMENTS, invocation.arguments.toTypedArray())
                    putExtra(EXTRA_WORKDIR, DEFAULT_WORKDIR)
                    putExtra(EXTRA_BACKGROUND, true)
                    putExtra(EXTRA_COMMAND_LABEL, invocation.label)
                    putExtra(EXTRA_COMMAND_DESCRIPTION, invocation.description)
                    putExtra(EXTRA_PENDING_INTENT, pendingIntent)
                }

                val componentName = context.startService(serviceIntent)
                if (componentName == null && !continuation.isCompleted) {
                    runCatching { context.unregisterReceiver(receiver) }
                    continuation.resume(
                        TermuxCommandResult(
                            success = false,
                            timedOut = false,
                            resultCode = null,
                            errorCode = null,
                            errorMessage = "Termux RunCommandService 启动失败",
                            stdout = "",
                            stderr = "",
                            rawExtras = mapOf(
                                "executionMode" to spec.executionMode,
                                "workingDirectory" to (spec.workingDirectory ?: "")
                            )
                        )
                    )
                }
            } catch (error: Exception) {
                runCatching { context.unregisterReceiver(receiver) }
                if (!continuation.isCompleted) {
                    continuation.resume(
                        TermuxCommandResult(
                            success = false,
                            timedOut = false,
                            resultCode = null,
                            errorCode = null,
                            errorMessage = error.message ?: "调用 Termux 失败",
                            stdout = "",
                            stderr = "",
                            rawExtras = emptyMap()
                        )
                    )
                }
            }
        }
    }

    private fun parseTermuxResult(intent: Intent?): TermuxCommandResult {
        if (intent == null) {
            return buildErrorResult("Termux 返回了空结果")
        }

        val rawResultMap = buildResultMap(intent)
        val parsed = parseResultMap(rawResultMap)

        return TermuxCommandResult(
            success = parsed.success,
            timedOut = false,
            resultCode = parsed.resultCode,
            errorCode = parsed.errorCode,
            errorMessage = parsed.errorMessage,
            stdout = parsed.stdout,
            stderr = parsed.stderr,
            rawExtras = rawResultMap
        )
    }

    @Suppress("DEPRECATION")
    private fun buildResultMap(intent: Intent): Map<String, Any?> {
        val extras = intent.extras ?: return emptyMap()
        val firstNestedBundle = extras.keySet()
            .mapNotNull { key -> extras.get(key) as? Bundle }
            .firstOrNull()
        val container = firstNestedBundle ?: extras
        return flattenBundle(container)
    }

    @Suppress("DEPRECATION")
    private fun flattenBundle(
        bundle: Bundle,
        prefix: String = ""
    ): Map<String, Any?> {
        val result = linkedMapOf<String, Any?>()
        val keys = bundle.keySet().sorted()
        keys.forEach { key ->
            val value = bundle.get(key)
            val fullKey = if (prefix.isEmpty()) key else "$prefix.$key"
            when (value) {
                is Bundle -> result.putAll(flattenBundle(value, fullKey))
                is Array<*> -> result[fullKey] = value.map { it?.toString() }
                is IntArray -> result[fullKey] = value.toList()
                is LongArray -> result[fullKey] = value.toList()
                is BooleanArray -> result[fullKey] = value.toList()
                is FloatArray -> result[fullKey] = value.toList()
                is DoubleArray -> result[fullKey] = value.toList()
                else -> result[fullKey] = value
            }
        }
        return result
    }

    private fun finalizeCommandResult(
        result: TermuxCommandResult,
        sessionId: String?,
        streamState: String,
        fallbackReason: String?,
        terminalOutput: String
    ): TermuxCommandResult {
        return result.copy(
            terminalOutput = trimTerminalOutput(terminalOutput),
            liveSessionId = sessionId,
            liveStreamState = streamState,
            liveFallbackReason = fallbackReason
        )
    }

    private fun buildTerminalOutputFromResult(
        result: TermuxCommandResult,
        fallbackReason: String? = null
    ): String {
        val segments = mutableListOf<String>()
        fallbackReason?.takeIf { it.isNotBlank() }?.let { segments += "[实时输出已回退]\n$it" }
        result.stdout.trimEnd().takeIf { it.isNotEmpty() }?.let { segments += it }
        result.stderr.trimEnd().takeIf { it.isNotEmpty() }?.let { stderr ->
            if (segments.isEmpty()) {
                segments += stderr
            } else {
                segments += "[stderr]\n$stderr"
            }
        }
        result.errorMessage?.trim()?.takeIf { it.isNotEmpty() }?.let { message ->
            if (segments.none { it.contains(message) }) {
                segments += message
            }
        }
        return trimTerminalOutput(sanitizeTerminalNoise(segments.joinToString("\n\n")))
    }

    private fun validatePreconditions(context: Context): TermuxCommandResult? {
        if (!isTermuxInstalled(context)) {
            return buildErrorResult("未检测到 Termux（包名 com.termux）")
        }
        if (!hasRunCommandPermission(context)) {
            return buildErrorResult("当前应用未获得 com.termux.permission.RUN_COMMAND 权限")
        }
        return null
    }

    private fun buildErrorResult(message: String?): TermuxCommandResult {
        return TermuxCommandResult(
            success = false,
            timedOut = false,
            resultCode = null,
            errorCode = null,
            errorMessage = message,
            stdout = "",
            stderr = "",
            rawExtras = emptyMap()
        )
    }

    private fun scheduleLogCleanup(logFile: File) {
        cleanupScope.launch {
            delay(LIVE_LOG_RETENTION_MS)
            runCatching {
                if (logFile.exists()) {
                    logFile.delete()
                }
            }
        }
    }

    private fun findStringValueBySuffix(
        values: Map<String, Any?>,
        suffix: String
    ): String? {
        return values.entries
            .firstOrNull { (key, value) ->
                value is String && matchesSuffix(key, suffix)
            }
            ?.value as? String
    }

    private fun findIntValueBySuffix(
        values: Map<String, Any?>,
        suffix: String
    ): Int? {
        val entry = values.entries.firstOrNull { (key, value) ->
            value is Number && matchesSuffix(key, suffix)
        } ?: return null
        return (entry.value as Number).toInt()
    }

    private fun matchesSuffix(rawKey: String, suffix: String): Boolean {
        val normalizedKey = rawKey.substringAfterLast('.').uppercase()
        return normalizedKey == suffix
    }

    fun isTermuxInstalled(context: Context): Boolean {
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                context.packageManager.getPackageInfo(
                    TERMUX_PACKAGE_NAME,
                    PackageManager.PackageInfoFlags.of(0)
                )
            } else {
                @Suppress("DEPRECATION")
                context.packageManager.getPackageInfo(TERMUX_PACKAGE_NAME, 0)
            }
            true
        } catch (_: PackageManager.NameNotFoundException) {
            false
        }
    }

    fun hasRunCommandPermission(context: Context): Boolean {
        return context.packageManager.checkPermission(
            TERMUX_RUN_COMMAND_PERMISSION,
            context.packageName
        ) == PackageManager.PERMISSION_GRANTED
    }
}

private class TermuxLiveSession(
    val sessionId: String,
    val logFile: File,
    private val onLiveUpdate: suspend (TermuxLiveUpdate) -> Unit
) {
    private val eventScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val accumulator = TerminalTranscriptAccumulator()
    private val observer = LiveLogObserver(
        targetFile = logFile,
        scope = eventScope
    ) { delta ->
        if (delta.isEmpty()) return@LiveLogObserver
        accumulator.append(delta)
        eventScope.launch {
            onLiveUpdate(
                TermuxLiveUpdate(
                    sessionId = sessionId,
                    summary = "正在实时接收终端输出",
                    outputDelta = delta,
                    streamState = LIVE_STREAM_STATE_RUNNING
                )
            )
        }
    }

    fun start() {
        observer.start()
    }

    fun stop() {
        observer.stop()
    }

    fun snapshotOutput(): String {
        return accumulator.snapshot()
    }
}

private class LiveLogObserver(
    private val targetFile: File,
    private val scope: CoroutineScope,
    private val onDelta: (String) -> Unit
) {
    private val lock = Any()
    private val pendingText = StringBuilder()
    private var lastOffset = 0L
    private var flushJob: Job? = null
    private val observer = object : FileObserver(
        targetFile.parentFile?.absolutePath.orEmpty(),
        CREATE or MODIFY or CLOSE_WRITE or MOVED_TO
    ) {
        override fun onEvent(event: Int, path: String?) {
            if (path == null || path != targetFile.name) return
            readNewContent(forceFlush = (event and CLOSE_WRITE) != 0)
        }
    }

    fun start() {
        observer.startWatching()
        readNewContent(forceFlush = true)
    }

    fun stop() {
        observer.stopWatching()
        readNewContent(forceFlush = true)
        flushNow()
    }

    private fun readNewContent(forceFlush: Boolean) {
        val delta = synchronized(lock) {
            if (!targetFile.exists()) return@synchronized ""
            RandomAccessFile(targetFile, "r").use { raf ->
                if (lastOffset > raf.length()) {
                    lastOffset = 0L
                }
                raf.seek(lastOffset)
                if (raf.filePointer == raf.length()) {
                    return@synchronized ""
                }
                val builder = StringBuilder()
                val buffer = ByteArray(8 * 1024)
                while (true) {
                    val read = raf.read(buffer)
                    if (read <= 0) {
                        break
                    }
                    builder.append(String(buffer, 0, read, StandardCharsets.UTF_8))
                }
                lastOffset = raf.filePointer
                builder.toString()
            }
        }
        if (delta.isEmpty()) {
            if (forceFlush) flushNow()
            return
        }

        synchronized(lock) {
            pendingText.append(delta)
        }
        if (forceFlush) {
            flushNow()
            return
        }

        if (flushJob?.isActive == true) {
            return
        }
        flushJob = scope.launch {
            delay(120)
            flushNow()
        }
    }

    private fun flushNow() {
        val text = synchronized(lock) {
            if (pendingText.isEmpty()) {
                flushJob = null
                return@synchronized ""
            }
            val content = pendingText.toString()
            pendingText.clear()
            flushJob = null
            content
        }
        if (text.isEmpty()) return
        val sanitized = TermuxCommandRunner.sanitizeTerminalNoise(text)
        if (sanitized.isEmpty()) return
        onDelta(sanitized)
    }
}

private class TerminalTranscriptAccumulator {
    private var value: String = ""

    fun append(delta: String) {
        if (delta.isEmpty()) return
        value = TermuxCommandRunner.trimTerminalOutput(value + delta)
    }

    fun snapshot(): String = value
}

const val LIVE_STREAM_STATE_STARTING = "starting"
const val LIVE_STREAM_STATE_RUNNING = "running"
const val LIVE_STREAM_STATE_COMPLETED = "completed"
const val LIVE_STREAM_STATE_FALLBACK = "fallback"
const val LIVE_STREAM_STATE_ERROR = "error"

private val LIVE_EXEC_SCRIPT = """
#!/data/data/com.termux/files/usr/bin/bash
# omnibot-live-exec-version:3
set -u

COMMAND_B64="__DOLLAR__{1:-}"
LOGFILE="__DOLLAR__{2:-}"
EXECUTION_MODE="__DOLLAR__{3:-termux}"
PROOT_DISTRO="__DOLLAR__{4:-ubuntu}"

TERMUX_PREFIX="/data/data/com.termux/files/usr"
TERMUX_BASH_PATH="__DOLLAR__TERMUX_PREFIX/bin/bash"
TERMUX_PROOT_DISTRO_PATH="__DOLLAR__TERMUX_PREFIX/bin/proot-distro"
WORKSPACE_BIND_SOURCE="${AgentWorkspaceManager.ROOT_PATH}"
WORKSPACE_BIND_TARGET="${AgentWorkspaceManager.SHELL_ROOT_PATH}"
SESSION_ID="__DOLLAR__(basename "__DOLLAR__LOGFILE" .log)"

decode_b64() {
    printf '%s' "__DOLLAR__1" | base64 -d 2>/dev/null
}

COMMAND="__DOLLAR__(decode_b64 "__DOLLAR__COMMAND_B64")"
if [ -z "__DOLLAR__COMMAND" ] || [ -z "__DOLLAR__LOGFILE" ]; then
    echo "live_exec 参数错误" >&2
    exit 1
fi

run_actual_command() {
    if command -v stdbuf >/dev/null 2>&1; then
        if [ "__DOLLAR__EXECUTION_MODE" = "proot" ]; then
            stdbuf -oL -eL "__DOLLAR__TERMUX_PROOT_DISTRO_PATH" login "__DOLLAR__PROOT_DISTRO" --bind "__DOLLAR__WORKSPACE_BIND_SOURCE:__DOLLAR__WORKSPACE_BIND_TARGET" --shared-tmp -- bash -lc "__DOLLAR__COMMAND"
        else
            stdbuf -oL -eL "__DOLLAR__TERMUX_BASH_PATH" -lc "__DOLLAR__COMMAND"
        fi
    else
        if [ "__DOLLAR__EXECUTION_MODE" = "proot" ]; then
            "__DOLLAR__TERMUX_PROOT_DISTRO_PATH" login "__DOLLAR__PROOT_DISTRO" --bind "__DOLLAR__WORKSPACE_BIND_SOURCE:__DOLLAR__WORKSPACE_BIND_TARGET" --shared-tmp -- bash -lc "__DOLLAR__COMMAND"
        else
            "__DOLLAR__TERMUX_BASH_PATH" -lc "__DOLLAR__COMMAND"
        fi
    fi
}

filter_known_noise() {
    while IFS= read -r line || [ -n "__DOLLAR__line" ]; do
        case "__DOLLAR__line" in
            *"Warning: CPU doesn't support 32-bit instructions, some software may not work."*) continue ;;
            *'proot warning: can'"'"'t sanitize binding "/proc/self/fd/'*': No such file or directory'*) continue ;;
        esac
        printf '%s\n' "__DOLLAR__line"
    done
}

mkdir -p "__DOLLAR__(dirname "__DOLLAR__LOGFILE")" 2>/dev/null
if ! touch "__DOLLAR__LOGFILE" 2>/dev/null; then
    echo "Omnibot live output unavailable: log path not writable. Please run termux-setup-storage." >&2
    run_actual_command
    exit __DOLLAR__?
fi

printf '\n=== [%s] 开始执行 ===\n命令: %s\n时间: %s\n\n' \
    "__DOLLAR__SESSION_ID" "__DOLLAR__COMMAND" "__DOLLAR__(date '+%Y-%m-%d %H:%M:%S')" >> "__DOLLAR__LOGFILE"

run_actual_command 2>&1 | filter_known_noise | tee -a "__DOLLAR__LOGFILE"
EXIT_CODE=__DOLLAR__{PIPESTATUS[0]}

printf '\n=== [%s] 执行结束 ===\n退出码: %s\n时间: %s\n' \
    "__DOLLAR__SESSION_ID" "__DOLLAR__EXIT_CODE" "__DOLLAR__(date '+%Y-%m-%d %H:%M:%S')" >> "__DOLLAR__LOGFILE"

exit "__DOLLAR__EXIT_CODE"
""".trimIndent().replace("__DOLLAR__", "$")
