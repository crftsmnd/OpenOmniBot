package cn.com.omnimind.bot.activity

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.lifecycle.lifecycleScope
import com.ai.assistance.operit.terminal.TerminalManager
import com.ai.assistance.operit.terminal.setup.EnvironmentSetupLogic
import cn.com.omnimind.bot.terminal.EmbeddedTerminalRuntime
import com.rk.libcommons.TerminalCommand
import com.rk.libcommons.pendingCommand
import com.rk.terminal.ui.activities.terminal.MainActivity
import com.rk.terminal.ui.screens.settings.WorkingMode
import kotlinx.coroutines.launch
import java.io.File

class TerminalActivity : ComponentActivity() {
    companion object {
        const val EXTRA_OPEN_SETUP = "open_setup"
        const val EXTRA_SETUP_PACKAGE_IDS = "setup_package_ids"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        pendingCommand = null
        lifecycleScope.launch {
            runCatching {
                EmbeddedTerminalRuntime.warmup(this@TerminalActivity)
                TerminalManager.getInstance(this@TerminalActivity).initializeEnvironment()
            }
            configurePendingSetupSession()
            startActivity(
                Intent(this@TerminalActivity, MainActivity::class.java).apply {
                    addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP)
                    putExtra(EXTRA_OPEN_SETUP, intent?.getBooleanExtra(EXTRA_OPEN_SETUP, false) == true)
                }
            )
            finish()
        }
    }

    private fun configurePendingSetupSession() {
        val openSetup = intent?.getBooleanExtra(EXTRA_OPEN_SETUP, false) == true
        if (!openSetup) {
            return
        }
        val selectedPackageIds = intent
            ?.getStringArrayListExtra(EXTRA_SETUP_PACKAGE_IDS)
            .orEmpty()
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .distinct()
        if (selectedPackageIds.isEmpty()) {
            return
        }

        val commands = EnvironmentSetupLogic.buildInstallCommands(
            selectedPackageIds = selectedPackageIds,
            repositorySetupCommand = ""
        )
        if (commands.isEmpty()) {
            return
        }

        val initHostPath = File(filesDir.parentFile, "local/bin/init-host").absolutePath
        val installScript = buildString {
            appendLine("""printf '\033[34;1m[*]\033[0m 开始配置 Alpine 开发环境\n'""")
            appendLine(commands.joinToString(separator = " && "))
            appendLine("status=\$?")
            appendLine("""if [ "${'$'}status" -eq 0 ]; then""")
            appendLine("""  printf '\033[32;1m[+]\033[0m 选中的环境已准备完成\n'""")
            appendLine("else")
            appendLine(
                """  printf '\033[31;1m[!]\033[0m 环境配置失败，退出码: %s\n' "${'$'}status" """,
            )
            appendLine("fi")
            appendLine("echo")
            appendLine("exec /bin/ash -l")
        }.trim()

        pendingCommand = TerminalCommand(
            shell = initHostPath,
            args = arrayOf("/bin/sh", "-lc", installScript),
            id = "setup-${System.currentTimeMillis()}",
            workingMode = WorkingMode.ALPINE,
            terminatePreviousSession = false,
            workingDir = "/"
        )
    }
}
