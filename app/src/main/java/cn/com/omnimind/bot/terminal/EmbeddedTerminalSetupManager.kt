package cn.com.omnimind.bot.terminal

import android.content.Context
import com.ai.assistance.operit.terminal.TerminalManager
import com.ai.assistance.operit.terminal.data.PackageManagerType
import com.ai.assistance.operit.terminal.provider.type.TerminalType
import com.ai.assistance.operit.terminal.utils.SourceManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.util.UUID

class EmbeddedTerminalSetupManager(
    private val context: Context
) {
    companion object {
        private const val SETUP_SESSION_TIMEOUT_MS = 30 * 60 * 1000L
    }

    data class PackageDefinition(
        val id: String,
        val command: String,
        val categoryId: String
    )

    data class InstallResult(
        val success: Boolean,
        val message: String,
        val output: String = ""
    ) {
        fun toMap(): Map<String, Any?> = mapOf(
            "success" to success,
            "message" to message,
            "output" to output
        )
    }

    data class InstallSessionSnapshot(
        val sessionId: String? = null,
        val running: Boolean = false,
        val completed: Boolean = false,
        val success: Boolean? = null,
        val message: String = "",
        val selectedPackageIds: List<String> = emptyList()
    ) {
        fun toMap(): Map<String, Any?> = mapOf(
            "sessionId" to sessionId,
            "running" to running,
            "completed" to completed,
            "success" to success,
            "message" to message,
            "selectedPackageIds" to selectedPackageIds
        )
    }

    private val sourceManager = SourceManager(context)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val installSessionMutex = Mutex()
    @Volatile
    private var installSessionSnapshot = InstallSessionSnapshot()

    private val packageDefinitions: List<PackageDefinition> = listOf(
        PackageDefinition(
            id = "nodejs",
            command = "curl -fsSL https://deb.nodesource.com/setup_24.x | bash - && apt install -y nodejs",
            categoryId = "nodejs"
        ),
        PackageDefinition(id = "pnpm", command = "typescript", categoryId = "nodejs"),
        PackageDefinition(
            id = "python-is-python3",
            command = "python-is-python3",
            categoryId = "python"
        ),
        PackageDefinition(
            id = "python3-venv",
            command = "python3-venv",
            categoryId = "python"
        ),
        PackageDefinition(
            id = "python3-pip",
            command = "python3-pip",
            categoryId = "python"
        ),
        PackageDefinition(id = "uv", command = "pipx install uv", categoryId = "python"),
        PackageDefinition(id = "ssh", command = "ssh", categoryId = "ssh"),
        PackageDefinition(id = "sshpass", command = "sshpass", categoryId = "ssh"),
        PackageDefinition(
            id = "openssh-server",
            command = "openssh-server",
            categoryId = "ssh"
        ),
        PackageDefinition(
            id = "openjdk-17",
            command = "openjdk-17-jdk",
            categoryId = "java"
        ),
        PackageDefinition(id = "gradle", command = "gradle", categoryId = "java"),
        PackageDefinition(
            id = "rust",
            command = "RUST_INSTALL_COMMAND",
            categoryId = "rust"
        ),
        PackageDefinition(id = "go", command = "golang-go", categoryId = "go"),
        PackageDefinition(id = "git", command = "git", categoryId = "tools"),
        PackageDefinition(id = "ffmpeg", command = "ffmpeg", categoryId = "tools")
    )

    suspend fun getPackageInstallStatus(): Map<String, Boolean> = withContext(Dispatchers.IO) {
        withLocalTerminalManager { manager ->
            packageDefinitions.associate { pkg ->
                pkg.id to checkPackageInstalled(manager, pkg)
            }
        }
    }

    suspend fun installPackages(selectedPackageIds: List<String>): InstallResult = withContext(Dispatchers.IO) {
        val requestedIds = selectedPackageIds
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .distinct()
        if (requestedIds.isEmpty()) {
            return@withContext InstallResult(
                success = false,
                message = "请选择至少一个需要安装的组件。"
            )
        }

        try {
            val currentStatus = getPackageInstallStatus()
            val installIds = requestedIds.filter { currentStatus[it] != true }
            if (installIds.isEmpty()) {
                return@withContext InstallResult(
                    success = true,
                    message = "所选组件均已安装。"
                )
            }

            val commands = buildInstallCommands(installIds)
            if (commands.isEmpty()) {
                return@withContext InstallResult(
                    success = false,
                    message = "未生成任何安装命令，请检查所选组件。"
                )
            }

            val output = StringBuilder()
            val hiddenResult = withLocalTerminalManager { manager ->
                manager.executeHiddenCommand(
                    command = commands.joinToString(separator = " && "),
                    executorKey = "embedded-terminal-setup",
                    timeoutMs = 15 * 60 * 1000L,
                    onOutputChunk = { chunk ->
                        val normalized = chunk.replace("\r\n", "\n").replace('\r', '\n')
                        if (normalized.isNotBlank()) {
                            output.append(normalized)
                            if (!normalized.endsWith("\n")) {
                                output.append('\n')
                            }
                        }
                    }
                )
            }

            if (!hiddenResult.isOk || hiddenResult.exitCode != 0) {
                val details = hiddenResult.output.trim()
                    .ifBlank { hiddenResult.rawOutputPreview.trim() }
                    .ifBlank { hiddenResult.error.trim() }
                return@withContext InstallResult(
                    success = false,
                    message = if (details.isNotBlank()) {
                        "环境配置失败：$details"
                    } else {
                        "环境配置失败，请稍后重试。"
                    },
                    output = output.toString().trim()
                )
            }

            val refreshedStatus = getPackageInstallStatus()
            val remaining = installIds.filter { refreshedStatus[it] != true }
            if (remaining.isNotEmpty()) {
                val diagnostics = buildPostInstallDiagnostics(remaining)
                return@withContext InstallResult(
                    success = false,
                    message = buildInstallValidationFailureMessage(
                        remaining = remaining,
                        diagnostics = diagnostics
                    ),
                    output = output.toString().trim()
                )
            }

            InstallResult(
                success = true,
                message = "环境配置完成：${installIds.joinToString(", ")}",
                output = output.toString().trim()
            )
        } catch (error: Exception) {
            InstallResult(
                success = false,
                message = error.message ?: "环境配置失败，请稍后重试。"
            )
        }
    }

    fun getInstallSessionSnapshot(): InstallSessionSnapshot = installSessionSnapshot

    suspend fun startInstallSession(selectedPackageIds: List<String>): InstallSessionSnapshot =
        withContext(Dispatchers.IO) {
            val requestedIds = selectedPackageIds
                .map { it.trim() }
                .filter { it.isNotEmpty() }
                .distinct()
            if (requestedIds.isEmpty()) {
                val snapshot = InstallSessionSnapshot(
                    running = false,
                    completed = true,
                    success = false,
                    message = "请选择至少一个需要安装的组件。",
                    selectedPackageIds = emptyList()
                )
                updateInstallSessionSnapshot(snapshot)
                return@withContext snapshot
            }

            installSessionMutex.withLock {
                val currentSnapshot = installSessionSnapshot
                if (currentSnapshot.running && !currentSnapshot.sessionId.isNullOrBlank()) {
                    return@withLock currentSnapshot
                }

                val currentStatus = getPackageInstallStatus()
                val installIds = requestedIds.filter { currentStatus[it] != true }
                if (installIds.isEmpty()) {
                    val snapshot = InstallSessionSnapshot(
                        running = false,
                        completed = true,
                        success = true,
                        message = "所选组件均已安装。",
                        selectedPackageIds = requestedIds
                    )
                    updateInstallSessionSnapshot(snapshot)
                    return@withLock snapshot
                }

                val commands = buildInstallCommands(installIds)
                if (commands.isEmpty()) {
                    val snapshot = InstallSessionSnapshot(
                        running = false,
                        completed = true,
                        success = false,
                        message = "未生成任何安装命令，请检查所选组件。",
                        selectedPackageIds = installIds
                    )
                    updateInstallSessionSnapshot(snapshot)
                    return@withLock snapshot
                }

                withLocalTerminalManager { manager ->
                    val previousSessionId = currentSnapshot.sessionId
                    if (!currentSnapshot.running && !previousSessionId.isNullOrBlank()) {
                        runCatching { manager.closeSession(previousSessionId) }
                    }

                    val session = manager.createNewSession("环境配置", TerminalType.LOCAL)
                    manager.switchToSession(session.id)

                    val startedSnapshot = InstallSessionSnapshot(
                        sessionId = session.id,
                        running = true,
                        completed = false,
                        success = null,
                        message = "环境配置进行中，请在下方终端查看安装输出。",
                        selectedPackageIds = installIds
                    )
                    updateInstallSessionSnapshot(startedSnapshot)

                    scope.launch {
                        runInstallSession(
                            sessionId = session.id,
                            installIds = installIds,
                            command = commands.joinToString(separator = " && ")
                        )
                    }

                    startedSnapshot
                }
            }
        }

    private suspend fun <T> withLocalTerminalManager(
        block: suspend (TerminalManager) -> T
    ): T {
        val manager = TerminalManager.getInstance(context)
        val previousType = manager.getPreferredTerminalType()
        manager.setPreferredTerminalType(TerminalType.LOCAL)
        return try {
            block(manager)
        } finally {
            manager.setPreferredTerminalType(previousType)
        }
    }

    private suspend fun updateInstallSessionSnapshot(snapshot: InstallSessionSnapshot) {
        installSessionSnapshot = snapshot
    }

    private fun buildInstallCommands(selectedPackageIds: List<String>): List<String> {
        val selectedIdSet = selectedPackageIds.toSet()
        val selectedPackages = packageDefinitions.filter { selectedIdSet.contains(it.id) }
        if (selectedPackages.isEmpty()) {
            return emptyList()
        }

        val commands = mutableListOf<String>()
        commands += "dpkg --configure -a"
        commands += "apt install -f -y"
        if (selectedIdSet.contains("nodejs")) {
            // 旧初始化链路可能留下低版本或来源不一致的 Node.js，
            // 这里在重装前先清理掉未通过当前检测标准的历史安装。
            commands += buildNodejsReinstallCleanupCommand()
        }
        commands += "apt update -y"
        commands += "apt upgrade -y"
        commands += "mkdir -p ~/.config/pip"
        commands += "echo '[global]' > ~/.config/pip/pip.conf"
        commands += "echo 'index-url = https://pypi.tuna.tsinghua.edu.cn/simple' >> ~/.config/pip/pip.conf"
        commands += "mkdir -p ~/.config/uv"
        commands += "echo 'index-url = \"https://pypi.tuna.tsinghua.edu.cn/simple\"' > ~/.config/uv/uv.toml"

        val selectedAptPackages = mutableListOf<String>()
        val selectedNpmPackages = mutableListOf<String>()
        val selectedCustomCommands = mutableListOf<String>()

        selectedPackages.forEach { pkg ->
            when {
                pkg.id == "rust" -> {
                    val rustSource = sourceManager.getSelectedSource(PackageManagerType.RUST)
                    val rustEnvCommand = sourceManager.getRustSourceEnvCommand(rustSource)
                    selectedCustomCommands +=
                        "$rustEnvCommand && curl -v --proto '=https' --tlsv1.2 -sSf https://sh.rustup.rs | sh -s -- -y"
                }

                pkg.id == "uv" || pkg.id == "nodejs" -> {
                    selectedCustomCommands += pkg.command
                }

                pkg.categoryId == "nodejs" && pkg.id != "nodejs" -> {
                    selectedNpmPackages += pkg.command
                }

                else -> {
                    selectedAptPackages += pkg.command
                }
            }
        }

        if (selectedIdSet.contains("uv")) {
            selectedAptPackages += "pipx"
        }

        val allAptDependencies = linkedSetOf<String>()
        if (selectedCustomCommands.isNotEmpty()) {
            if (selectedIdSet.contains("rust")) {
                allAptDependencies += listOf("curl", "build-essential")
            }
            if (selectedIdSet.contains("nodejs")) {
                allAptDependencies += "curl"
            }
        }
        allAptDependencies += selectedAptPackages

        if (allAptDependencies.isNotEmpty()) {
            commands += "apt install -y ${allAptDependencies.joinToString(" ")}"
        }

        if (selectedCustomCommands.isNotEmpty()) {
            commands += selectedCustomCommands
            if (selectedIdSet.contains("uv")) {
                commands += "pipx ensurepath"
                commands += "source ~/.profile"
            }
        }

        if (selectedNpmPackages.isNotEmpty()) {
            commands += "npm config set registry https://registry.npmmirror.com/"
            commands += "npm cache clean --force"
            commands += "npm install -g pnpm"
            commands += "pnpm add -g ${selectedNpmPackages.joinToString(" ")}"
        }

        return commands
    }

    private fun buildNodejsReinstallCleanupCommand(): String = """
        rm -f /etc/apt/sources.list.d/nodesource.list
        rm -f /etc/apt/preferences.d/nodesource
        rm -f /usr/share/keyrings/nodesource.gpg /etc/apt/keyrings/nodesource.gpg
        for pkg in nodejs npm nodejs-doc libnode-dev; do
          if dpkg -s "${'$'}pkg" >/dev/null 2>&1; then
            apt purge -y "${'$'}pkg"
          fi
        done
        rm -f /usr/local/bin/node /usr/local/bin/npm /usr/local/bin/npx /usr/local/bin/corepack /usr/local/bin/pnpm
        rm -f "${'$'}HOME/.local/bin/node" "${'$'}HOME/.local/bin/npm" "${'$'}HOME/.local/bin/npx" "${'$'}HOME/.local/bin/corepack" "${'$'}HOME/.local/bin/pnpm"
        rm -rf /usr/local/lib/node_modules
        rm -rf "${'$'}HOME/.npm" "${'$'}HOME/.cache/node-gyp"
        apt autoremove -y || true
        hash -r || true
    """.trimIndent()

    private suspend fun buildPostInstallDiagnostics(remainingIds: List<String>): String {
        if (!remainingIds.contains("nodejs")) {
            return ""
        }
        return withLocalTerminalManager { manager ->
            val result = manager.executeHiddenCommand(
                command = """
                    echo "node_path=${'$'}(command -v node 2>/dev/null || echo missing)"
                    echo "node_realpath=${'$'}(readlink -f "${'$'}(command -v node 2>/dev/null)" 2>/dev/null || echo missing)"
                    echo "node_version=${'$'}(node -v 2>&1 || echo missing)"
                    echo "npm_path=${'$'}(command -v npm 2>/dev/null || echo missing)"
                    echo "npm_version=${'$'}(npm -v 2>&1 || echo missing)"
                """.trimIndent(),
                executorKey = "embedded-terminal-setup-nodejs-diagnostics",
                timeoutMs = 20_000L
            )
            EmbeddedTerminalRuntime.trimTerminalOutput(
                EmbeddedTerminalRuntime.sanitizeTerminalNoise(
                    result.output.ifBlank { result.rawOutputPreview }
                )
            ).trim()
        }
    }

    private fun buildInstallValidationFailureMessage(
        remaining: List<String>,
        diagnostics: String,
        tailOutput: String = ""
    ): String {
        val message = StringBuilder("以下组件安装后仍未通过校验：${remaining.joinToString(", ")}")
        if (diagnostics.isNotBlank()) {
            message.append("\n")
            message.append(diagnostics)
        }
        if (tailOutput.isNotBlank()) {
            message.append("\n")
            message.append(tailOutput)
        }
        return message.toString()
    }

    private suspend fun runInstallSession(
        sessionId: String,
        installIds: List<String>,
        command: String
    ) {
        try {
            val output = withLocalTerminalManager { manager ->
                executeCommandInSession(
                    manager = manager,
                    sessionId = sessionId,
                    command = command
                )
            } ?: throw IllegalStateException("环境配置命令执行超时，可能仍在后台继续运行。")

            val refreshedStatus = getPackageInstallStatus()
            val remaining = installIds.filter { refreshedStatus[it] != true }
            if (remaining.isNotEmpty()) {
                val details = EmbeddedTerminalRuntime.trimTerminalOutput(
                    EmbeddedTerminalRuntime.sanitizeTerminalNoise(output)
                ).takeLast(1200).trim()
                val diagnostics = buildPostInstallDiagnostics(remaining)
                updateInstallSessionSnapshot(
                    InstallSessionSnapshot(
                        sessionId = sessionId,
                        running = false,
                        completed = true,
                        success = false,
                        message = buildInstallValidationFailureMessage(
                            remaining = remaining,
                            diagnostics = diagnostics,
                            tailOutput = details
                        ),
                        selectedPackageIds = installIds
                    )
                )
                return
            }

            updateInstallSessionSnapshot(
                InstallSessionSnapshot(
                    sessionId = sessionId,
                    running = false,
                    completed = true,
                    success = true,
                    message = "环境配置完成：${installIds.joinToString(", ")}",
                    selectedPackageIds = installIds
                )
            )
        } catch (error: Exception) {
            updateInstallSessionSnapshot(
                InstallSessionSnapshot(
                    sessionId = sessionId,
                    running = false,
                    completed = true,
                    success = false,
                    message = error.message ?: "环境配置失败，请查看终端输出。",
                    selectedPackageIds = installIds
                )
            )
        }
    }

    private suspend fun executeCommandInSession(
        manager: TerminalManager,
        sessionId: String,
        command: String
    ): String? = coroutineScope {
        val commandId = UUID.randomUUID().toString()
        val awaited = async {
            withTimeoutOrNull(SETUP_SESSION_TIMEOUT_MS) {
                manager.commandExecutionEvents.first { event ->
                    event.sessionId == sessionId &&
                        event.commandId == commandId &&
                        event.isCompleted
                }
            }
        }

        manager.sendCommandToSession(
            sessionId = sessionId,
            command = command,
            commandId = commandId
        )

        awaited.await()?.outputChunk
    }

    private suspend fun checkPackageInstalled(
        manager: TerminalManager,
        pkg: PackageDefinition
    ): Boolean {
        val command = when (pkg.id) {
            "rust" -> "command -v rustc"
            "uv" -> "command -v uv"
            "nodejs" -> "node -v 2>/dev/null"
            "pnpm" -> "test -f \"\$(npm prefix -g)/bin/pnpm\" && echo FOUND_PNPM"
            "go" -> "command -v go"
            "git" -> "command -v git"
            "ffmpeg" -> "command -v ffmpeg"
            "ssh" -> "command -v ssh"
            "sshpass" -> "command -v sshpass"
            "openssh-server" -> "command -v sshd"
            "gradle" -> "command -v gradle"
            else -> "dpkg -s ${pkg.command.split(" ").first()}"
        }

        val result = manager.executeHiddenCommand(
            command = command,
            executorKey = "embedded-terminal-setup-check-${pkg.id}",
            timeoutMs = 20_000L
        )
        val output = result.output.trim()
        if (!result.isOk && output.isBlank()) {
            return false
        }

        return when (pkg.id) {
            "nodejs" -> {
                if (output.isBlank() || output.contains("not found")) {
                    false
                } else {
                    val versionMatch = Regex("""v(\d+)\..*""").find(output)
                    val majorVersion = versionMatch?.groupValues?.getOrNull(1)?.toIntOrNull() ?: 0
                    majorVersion >= 24
                }
            }

            "rust", "uv", "go", "git", "ffmpeg", "ssh", "sshpass", "openssh-server", "gradle" -> {
                output.isNotBlank() && !output.contains("not found")
            }

            "pnpm" -> output.contains("FOUND_PNPM")
            else -> output.contains("Status: install ok installed")
        }
    }
}
