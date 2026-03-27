package cn.com.omnimind.bot.activity

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.lifecycle.lifecycleScope
import cn.com.omnimind.bot.terminal.EmbeddedTerminalRuntime
import com.rk.terminal.ui.activities.terminal.MainActivity
import kotlinx.coroutines.launch

class TerminalActivity : ComponentActivity() {
    companion object {
        const val EXTRA_OPEN_SETUP = "open_setup"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        lifecycleScope.launch {
            runCatching {
                EmbeddedTerminalRuntime.warmup(this@TerminalActivity)
            }
            startActivity(
                Intent(this@TerminalActivity, MainActivity::class.java).apply {
                    addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP)
                    putExtra(EXTRA_OPEN_SETUP, intent?.getBooleanExtra(EXTRA_OPEN_SETUP, false) == true)
                }
            )
            finish()
        }
    }
}
