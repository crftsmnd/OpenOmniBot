package cn.com.omnimind.bot.openclaw

import android.content.Context
import com.ai.assistance.operit.terminal.TerminalManager
import com.ai.assistance.operit.terminal.provider.type.HiddenExecResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class OpenClawInstallCommandRunner(
    private val context: Context
) {
    data class Result(
        val success: Boolean,
        val timedOut: Boolean,
        val exitCode: Int?,
        val output: String,
        val errorMessage: String?
    )

    companion object {
        private const val EXECUTOR_KEY = "openclaw-install"
    }

    suspend fun execute(
        command: String,
        timeoutSeconds: Int,
        onOutputChunk: (String) -> Unit = {}
    ): Result = withContext(Dispatchers.IO) {
        OpenClawRuntimeSupport.ensureRuntimeFiles(context)

        val hiddenResult = runCatching {
            TerminalManager.getInstance(context).executeHiddenCommand(
                command = command,
                executorKey = EXECUTOR_KEY,
                timeoutMs = timeoutSeconds.toLong() * 1000L,
                onOutputChunk = { chunk ->
                    val normalized = chunk.replace("\r\n", "\n").replace('\r', '\n')
                    if (normalized.isNotBlank()) {
                        runCatching { onOutputChunk(normalized) }
                    }
                }
            )
        }.getOrElse { error ->
            return@withContext Result(
                success = false,
                timedOut = false,
                exitCode = null,
                output = "",
                errorMessage = error.message ?: "Failed to execute install command"
            )
        }

        val output = hiddenResult.output.trim()
        when (hiddenResult.state) {
            HiddenExecResult.State.OK -> {
                return@withContext Result(
                    success = hiddenResult.exitCode == 0,
                    timedOut = false,
                    exitCode = hiddenResult.exitCode,
                    output = output,
                    errorMessage = if (hiddenResult.exitCode == 0) {
                        null
                    } else {
                        hiddenResult.error.ifBlank {
                            "Command failed with exit code ${hiddenResult.exitCode}"
                        }
                    }
                )
            }

            HiddenExecResult.State.TIMEOUT -> {
                return@withContext Result(
                    success = false,
                    timedOut = true,
                    exitCode = null,
                    output = output,
                    errorMessage = hiddenResult.error.ifBlank {
                        "Command timed out after ${timeoutSeconds}s"
                    }
                )
            }

            else -> {
                val fallbackError = buildString {
                    append("Command execution failed")
                    if (hiddenResult.state != HiddenExecResult.State.EXECUTION_ERROR) {
                        append(" [${hiddenResult.state.name}]")
                    }
                    val detail = hiddenResult.error.trim().ifBlank {
                        hiddenResult.rawOutputPreview.trim()
                    }
                    if (detail.isNotEmpty()) {
                        append(": ")
                        append(detail)
                    }
                }
                return@withContext Result(
                    success = false,
                    timedOut = false,
                    exitCode = if (hiddenResult.exitCode >= 0) hiddenResult.exitCode else null,
                    output = output,
                    errorMessage = fallbackError
                )
            }
        }
    }
}
