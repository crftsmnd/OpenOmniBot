package cn.com.omnimind.bot.openclaw

import android.content.Context
import cn.com.omnimind.baselib.util.OmniLog
import cn.com.omnimind.bot.termux.TermuxCommandResult
import cn.com.omnimind.bot.termux.TermuxCommandRunner
import cn.com.omnimind.bot.termux.TermuxCommandSpec
import cn.com.omnimind.bot.terminal.EmbeddedTerminalRuntime
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.security.SecureRandom

class OpenClawDeployManager(
    private val context: Context
) {
    data class DeployRequest(
        val providerBaseUrl: String,
        val providerApiKey: String,
        val modelId: String,
        val configJson: String
    )

    data class DeployStartResult(
        val accepted: Boolean,
        val alreadyRunning: Boolean,
        val message: String
    ) {
        fun toMap(): Map<String, Any?> = mapOf(
            "accepted" to accepted,
            "alreadyRunning" to alreadyRunning,
            "message" to message
        )
    }

    data class DeploySnapshot(
        val running: Boolean = false,
        val completed: Boolean = false,
        val success: Boolean? = null,
        val progress: Double = 0.0,
        val stage: String = "",
        val logLines: List<String> = emptyList(),
        val gatewayBaseUrl: String? = null,
        val gatewayToken: String? = null,
        val errorMessage: String? = null
    ) {
        fun toMap(): Map<String, Any?> = mapOf(
            "running" to running,
            "completed" to completed,
            "success" to success,
            "progress" to progress,
            "stage" to stage,
            "logLines" to logLines,
            "gatewayBaseUrl" to gatewayBaseUrl,
            "gatewayToken" to gatewayToken,
            "errorMessage" to errorMessage
        )
    }

    private data class DeployState(
        val running: Boolean = false,
        val completed: Boolean = false,
        val success: Boolean? = null,
        val progress: Double = 0.0,
        val stage: String = "",
        val logLines: List<String> = emptyList(),
        val gatewayBaseUrl: String? = null,
        val gatewayToken: String? = null,
        val errorMessage: String? = null
    )

    private companion object {
        private const val TAG = "OpenClawDeployManager"
        private const val LOOPBACK_BASE_URL = "http://127.0.0.1:18789"
        private const val MAX_LOG_LINES = 160
        private const val DEFAULT_COMMAND_TIMEOUT_SECONDS = 15 * 60
        private const val HEALTH_TIMEOUT_SECONDS = 75
        private const val NODE_BYPASS_PATH = "/root/.openclaw/bionic-bypass.js"
        private const val OPENCLAW_CONFIG_PATH = "/root/.openclaw/openclaw.json"
        private const val OPENCLAW_WORKSPACE_PATH = "/root/.openclaw/workspace"
        private const val OPENCLAW_LOG_PATH = "/root/openclaw.log"
        private const val PROVIDER_API_KEY_ENV = "OMNIBOT_OPENCLAW_PROVIDER_API_KEY"
        private const val GATEWAY_TOKEN_ENV = "OPENCLAW_GATEWAY_TOKEN"
        private const val GATEWAY_TOKEN_ENV_REF = "${'$'}{OPENCLAW_GATEWAY_TOKEN}"

        private val secureRandom = SecureRandom()

        private val bypassScript = """
            const os = require('os');
            const originalNetworkInterfaces = os.networkInterfaces;

            os.networkInterfaces = function() {
              try {
                const interfaces = originalNetworkInterfaces.call(os);
                if (interfaces && Object.keys(interfaces).length > 0) {
                  return interfaces;
                }
              } catch (e) {}

              return {
                lo: [{
                  address: '127.0.0.1',
                  netmask: '255.0.0.0',
                  family: 'IPv4',
                  mac: '00:00:00:00:00:00',
                  internal: true,
                  cidr: '127.0.0.1/8'
                }]
              };
            };
        """.trimIndent()
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val lock = Any()
    private var state = DeployState()

    fun getSnapshot(): DeploySnapshot {
        val current = synchronized(lock) { state }
        return DeploySnapshot(
            running = current.running,
            completed = current.completed,
            success = current.success,
            progress = current.progress,
            stage = current.stage,
            logLines = current.logLines,
            gatewayBaseUrl = current.gatewayBaseUrl,
            gatewayToken = current.gatewayToken,
            errorMessage = current.errorMessage
        )
    }

    fun startDeploy(request: DeployRequest): DeployStartResult {
        val baseUrl = request.providerBaseUrl.trim()
        val apiKey = request.providerApiKey.trim()
        val modelId = request.modelId.trim()
        val configJson = request.configJson.trim()
        require(baseUrl.isNotEmpty()) { "providerBaseUrl 不能为空" }
        require(apiKey.isNotEmpty()) { "providerApiKey 不能为空" }
        require(modelId.isNotEmpty()) { "modelId 不能为空" }
        require(configJson.isNotEmpty()) { "configJson 不能为空" }
        validateGatewayConfig(configJson)

        synchronized(lock) {
            if (state.running) {
                return DeployStartResult(
                    accepted = false,
                    alreadyRunning = true,
                    message = "OpenClaw 正在部署中，请稍候。"
                )
            }
            state = DeployState(
                running = true,
                completed = false,
                success = null,
                progress = 0.02,
                stage = "准备开始",
                logLines = listOf("[系统] 正在准备 OpenClaw 一键部署..."),
                gatewayBaseUrl = null,
                gatewayToken = null,
                errorMessage = null
            )
        }

        scope.launch {
            runDeploy(
                request = DeployRequest(
                    providerBaseUrl = baseUrl,
                    providerApiKey = apiKey,
                    modelId = modelId,
                    configJson = configJson
                )
            )
        }
        return DeployStartResult(
            accepted = true,
            alreadyRunning = false,
            message = "OpenClaw 部署已开始。"
        )
    }

    private suspend fun runDeploy(request: DeployRequest) {
        try {
            val readiness = EmbeddedTerminalRuntime.inspectRuntimeReadiness(context)
            if (!readiness.supported || !readiness.runtimeReady || !readiness.basePackagesReady) {
                fail(
                    "内嵌 Ubuntu 尚未完成初始化，请先前往设置页完成初始化后再试。",
                    stage = "运行时未就绪",
                    progress = 0.05
                )
                return
            }

            val gatewayToken = generateGatewayToken()
            val commandContext = buildCommandContext(request, gatewayToken)

            updateState(
                progress = 0.08,
                stage = "检查 Node.js",
                appendLines = listOf(
                    "[系统] 将使用模型 ${request.modelId}",
                    "[系统] Provider: ${request.providerBaseUrl}"
                )
            )
            executeStep(
                stage = "检查 Node.js",
                progress = 0.16,
                command = commandContext.nodeSetupCommand,
                timeoutSeconds = DEFAULT_COMMAND_TIMEOUT_SECONDS
            )

            executeStep(
                stage = "安装 OpenClaw CLI",
                progress = 0.32,
                command = commandContext.installOpenClawCommand,
                timeoutSeconds = DEFAULT_COMMAND_TIMEOUT_SECONDS
            )

            executeStep(
                stage = "写入 Android 兼容补丁",
                progress = 0.48,
                command = commandContext.configureBypassCommand,
                timeoutSeconds = 120
            )

            executeStep(
                stage = "写入 OpenClaw 配置",
                progress = 0.66,
                command = commandContext.writeConfigCommand,
                timeoutSeconds = 120
            )

            executeStep(
                stage = "校验 OpenClaw 配置",
                progress = 0.74,
                command = commandContext.validateConfigCommand,
                timeoutSeconds = DEFAULT_COMMAND_TIMEOUT_SECONDS
            )

            executeStep(
                stage = "启动 Gateway",
                progress = 0.82,
                command = commandContext.launchGatewayCommand,
                timeoutSeconds = 120
            )

            executeStep(
                stage = "验证本机 Gateway",
                progress = 0.92,
                command = commandContext.healthCheckCommand,
                timeoutSeconds = HEALTH_TIMEOUT_SECONDS
            )

            synchronized(lock) {
                state = state.copy(
                    running = false,
                    completed = true,
                    success = true,
                    progress = 1.0,
                    stage = "部署完成",
                    logLines = appendLinesLocked(
                        state.logLines,
                        listOf("[成功] OpenClaw 已部署并通过本机健康检查。")
                    ),
                    gatewayBaseUrl = LOOPBACK_BASE_URL,
                    gatewayToken = gatewayToken,
                    errorMessage = null
                )
            }
        } catch (e: Exception) {
            OmniLog.e(TAG, "openclaw deploy failed", e)
            fail(
                message = e.message ?: "OpenClaw 部署失败",
                stage = "部署失败",
                progress = synchronized(lock) { state.progress.coerceAtLeast(0.1) }
            )
        }
    }

    private suspend fun executeStep(
        stage: String,
        progress: Double,
        command: String,
        timeoutSeconds: Int
    ) {
        updateState(
            progress = progress,
            stage = stage,
            appendLines = listOf("[阶段] $stage")
        )
        val result = executeCommand(command, timeoutSeconds)
        if (!result.success) {
            val output = (result.stderr.ifBlank { result.stdout.ifBlank { result.errorMessage.orEmpty() } }).trim()
            val errorText = if (output.isNotBlank()) output else "执行失败，exit=${result.resultCode ?: result.errorCode ?: -1}"
            throw IllegalStateException(errorText)
        }
        updateState(appendLines = listOf("[完成] $stage"))
    }

    private suspend fun executeCommand(
        command: String,
        timeoutSeconds: Int
    ): TermuxCommandResult {
        return TermuxCommandRunner.execute(
            context = context,
            spec = TermuxCommandSpec(
                command = command,
                executionMode = TermuxCommandSpec.EXECUTION_MODE_PROOT,
                prootDistro = TermuxCommandSpec.DEFAULT_PROOT_DISTRO,
                workingDirectory = "/root",
                timeoutSeconds = timeoutSeconds
            ),
            onLiveUpdate = { update ->
                if (update.outputDelta.isNotBlank()) {
                    appendLogChunk(update.outputDelta)
                }
            }
        )
    }

    private fun updateState(
        progress: Double? = null,
        stage: String? = null,
        appendLines: List<String> = emptyList()
    ) {
        synchronized(lock) {
            val nextLogs = if (appendLines.isEmpty()) {
                state.logLines
            } else {
                appendLinesLocked(state.logLines, appendLines)
            }
            state = state.copy(
                progress = progress ?: state.progress,
                stage = stage ?: state.stage,
                logLines = nextLogs
            )
        }
    }

    private fun appendLogChunk(chunk: String) {
        val lines = chunk
            .replace("\r\n", "\n")
            .replace('\r', '\n')
            .lineSequence()
            .map { it.trimEnd() }
            .filter { it.isNotBlank() }
            .toList()
        if (lines.isEmpty()) {
            return
        }
        synchronized(lock) {
            state = state.copy(
                logLines = appendLinesLocked(state.logLines, lines)
            )
        }
    }

    private suspend fun fail(
        message: String,
        stage: String,
        progress: Double
    ) {
        withContext(Dispatchers.IO) {
            synchronized(lock) {
                state = state.copy(
                    running = false,
                    completed = true,
                    success = false,
                    progress = progress,
                    stage = stage,
                    logLines = appendLinesLocked(
                        state.logLines,
                        listOf("[错误] $message")
                    ),
                    gatewayBaseUrl = null,
                    gatewayToken = null,
                    errorMessage = message
                )
            }
        }
    }

    private fun buildCommandContext(
        request: DeployRequest,
        gatewayToken: String
    ): CommandContext {
        val quotedApiKey = quoteShell(request.providerApiKey)
        val quotedGatewayToken = quoteShell(gatewayToken)
        val quotedBypassPath = quoteShell(NODE_BYPASS_PATH)
        val quotedConfigPath = quoteShell(OPENCLAW_CONFIG_PATH)
        val quotedWorkspacePath = quoteShell(OPENCLAW_WORKSPACE_PATH)
        val configJson = request.configJson.trim()

        val nodeSetupCommand = """
            set -euo pipefail
            export DEBIAN_FRONTEND=noninteractive
            if ! command -v pkill >/dev/null 2>&1 || ! command -v fuser >/dev/null 2>&1; then
              apt-get update
              apt-get install -y procps psmisc
            fi
            NODE_MAJOR=0
            if command -v node >/dev/null 2>&1; then
              NODE_MAJOR=$(node -p "parseInt(process.versions.node.split('.')[0], 10)" 2>/dev/null || echo 0)
            fi
            if [ "${'$'}NODE_MAJOR" -ge 22 ]; then
              echo "Node.js already ready: $(node -v)"
              exit 0
            fi
            apt-get update
            apt-get install -y ca-certificates curl gnupg procps psmisc
            curl -fsSL https://deb.nodesource.com/setup_22.x | bash -
            apt-get install -y nodejs
            echo "Node.js installed: $(node -v)"
            echo "npm installed: $(npm -v)"
        """.trimIndent()

        val installOpenClawCommand = """
            set -euo pipefail
            if command -v openclaw >/dev/null 2>&1; then
              echo "openclaw already installed: $(command -v openclaw)"
              openclaw --version || true
              exit 0
            fi
            npm install -g openclaw
            echo "openclaw installed: $(command -v openclaw)"
            openclaw --version || true
        """.trimIndent()

        val configureBypassCommand = listOf(
            "set -euo pipefail",
            "mkdir -p /root/.openclaw",
            "cat > $quotedBypassPath <<'EOF'",
            bypassScript,
            "EOF",
            "touch /root/.bashrc",
            "if ! grep -Fqx 'export NODE_OPTIONS=\"--require /root/.openclaw/bionic-bypass.js\"' /root/.bashrc; then",
            "  printf '\\n%s\\n' 'export NODE_OPTIONS=\"--require /root/.openclaw/bionic-bypass.js\"' >> /root/.bashrc",
            "fi",
            "echo \"bionic bypass configured\""
        ).joinToString("\n")

        val writeConfigCommand = listOf(
            "set -euo pipefail",
            "mkdir -p /root/.openclaw",
            "mkdir -p $quotedWorkspacePath",
            "cat > $quotedConfigPath <<'EOF'",
            configJson,
            "EOF",
            "echo \"openclaw config written: $OPENCLAW_CONFIG_PATH\""
        ).joinToString("\n")

        val validateConfigCommand = """
            set -euo pipefail
            export NODE_OPTIONS="--require /root/.openclaw/bionic-bypass.js"
            export $PROVIDER_API_KEY_ENV=$quotedApiKey
            export $GATEWAY_TOKEN_ENV=$quotedGatewayToken
            openclaw config validate
            echo "openclaw config validated"
        """.trimIndent()

        val launchGatewayCommand = """
            set -euo pipefail
            if command -v pkill >/dev/null 2>&1; then
              pkill -f "openclaw gateway" || true
            else
              EXISTING_PIDS=$(ps -eo pid,args | awk '/[o]penclaw gateway/ {print $1}')
              if [ -n "${'$'}EXISTING_PIDS" ]; then
                echo "${'$'}EXISTING_PIDS" | xargs kill || true
              fi
            fi
            if command -v fuser >/dev/null 2>&1; then
              fuser -k 18789/tcp >/dev/null 2>&1 || true
            fi
            rm -f $OPENCLAW_LOG_PATH
            export NODE_OPTIONS="--require /root/.openclaw/bionic-bypass.js"
            export $PROVIDER_API_KEY_ENV=$quotedApiKey
            export $GATEWAY_TOKEN_ENV=$quotedGatewayToken
            nohup openclaw gateway run --port 18789 > $OPENCLAW_LOG_PATH 2>&1 &
            GATEWAY_PID=${'$'}!
            sleep 2
            if ! kill -0 "${'$'}GATEWAY_PID" 2>/dev/null; then
              echo "gateway exited early"
              tail -n 60 $OPENCLAW_LOG_PATH || true
              exit 1
            fi
            echo "gateway started in background: ${'$'}GATEWAY_PID"
        """.trimIndent()

        val healthCheckCommand = listOf(
            "set -euo pipefail",
            "if python3 - <<'PY'",
            "import socket",
            "import sys",
            "import time",
            "",
            "deadline = time.time() + 60",
            "attempt = 0",
            "last_error = None",
            "while time.time() < deadline:",
            "    attempt += 1",
            "    sock = socket.socket(socket.AF_INET, socket.SOCK_STREAM)",
            "    sock.settimeout(2.0)",
            "    try:",
            "        sock.connect(('127.0.0.1', 18789))",
            "        print(f'gateway tcp probe ok on attempt {attempt}')",
            "        sys.exit(0)",
            "    except Exception as exc:",
            "        last_error = exc",
            "        print(f'gateway tcp probe retry {attempt}: {exc}')",
            "        time.sleep(3)",
            "    finally:",
            "        try:",
            "            sock.close()",
            "        except Exception:",
            "            pass",
            "",
            "print(f'gateway tcp probe failed: {last_error}', file=sys.stderr)",
            "sys.exit(1)",
            "PY",
            "then",
            "  echo \"gateway tcp probe completed\"",
            "else",
            "  status=${'$'}?",
            "  echo \"Gateway tcp probe failed\"",
            "  tail -n 40 $OPENCLAW_LOG_PATH || true",
            "  exit \"${'$'}status\"",
            "fi"
        ).joinToString("\n")

        return CommandContext(
            nodeSetupCommand = nodeSetupCommand,
            installOpenClawCommand = installOpenClawCommand,
            configureBypassCommand = configureBypassCommand,
            writeConfigCommand = writeConfigCommand,
            validateConfigCommand = validateConfigCommand,
            launchGatewayCommand = launchGatewayCommand,
            healthCheckCommand = healthCheckCommand
        )
    }

    private fun appendLinesLocked(
        current: List<String>,
        incoming: List<String>
    ): List<String> {
        if (incoming.isEmpty()) {
            return current
        }
        val merged = buildList {
            addAll(current)
            incoming.forEach { line ->
                val trimmed = line.trimEnd()
                if (trimmed.isNotBlank()) {
                    add(trimmed)
                }
            }
        }
        return if (merged.size > MAX_LOG_LINES) {
            merged.takeLast(MAX_LOG_LINES)
        } else {
            merged
        }
    }

    private fun quoteShell(value: String): String {
        return "'" + value.replace("'", "'\"'\"'") + "'"
    }

    private fun validateGatewayConfig(configJson: String) {
        val root = try {
            JSONObject(configJson)
        } catch (e: Exception) {
            throw IllegalArgumentException("configJson 不是合法 JSON")
        }
        val gateway = root.optJSONObject("gateway")
            ?: throw IllegalArgumentException("configJson 缺少 gateway 配置")
        val auth = gateway.optJSONObject("auth")
            ?: throw IllegalArgumentException("configJson 缺少 gateway.auth 配置")
        val authMode = auth.optString("mode").trim()
        require(authMode == "token") { "gateway.auth.mode 必须保持为 token" }
        val token = auth.optString("token").trim()
        require(token == GATEWAY_TOKEN_ENV_REF) {
            "gateway.auth.token 必须保持为 $GATEWAY_TOKEN_ENV_REF"
        }
    }

    private fun generateGatewayToken(): String {
        val bytes = ByteArray(24)
        secureRandom.nextBytes(bytes)
        return buildString(bytes.size * 2) {
            bytes.forEach { append("%02x".format(it)) }
        }
    }

    private data class CommandContext(
        val nodeSetupCommand: String,
        val installOpenClawCommand: String,
        val configureBypassCommand: String,
        val writeConfigCommand: String,
        val validateConfigCommand: String,
        val launchGatewayCommand: String,
        val healthCheckCommand: String
    )
}
