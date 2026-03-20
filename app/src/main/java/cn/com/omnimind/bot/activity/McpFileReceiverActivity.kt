package cn.com.omnimind.bot.activity

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.lifecycle.lifecycleScope
import cn.com.omnimind.baselib.util.OmniLog
import cn.com.omnimind.bot.mcp.McpFileInbox
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class McpFileReceiverActivity : ComponentActivity() {
    companion object {
        private const val TAG = "McpFileReceiver"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        handleIntent(intent)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIntent(intent)
    }

    private fun handleIntent(intent: Intent?) {
        if (intent == null) {
            finish()
            return
        }

        val uris = extractUris(intent)
        if (uris.isEmpty()) {
            OmniLog.w(TAG, "No uri found in intent: ${intent.action}")
            Toast.makeText(this, "No file found", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        val mimeTypeHint = intent.type

        lifecycleScope.launch(Dispatchers.IO) {
            val records = uris.mapNotNull { uri ->
                McpFileInbox.storeFromUri(this@McpFileReceiverActivity, uri, mimeTypeHint)
            }
            if (records.isNotEmpty()) {
                val fileNames = records.map { it.fileName }.distinct()

                // Build enhanced priority event message
                val message = if (fileNames.size == 1) {
                    """
                    |═══════════════════════════════════════
                    |✅ 文件接收成功
                    |═══════════════════════════════════════
                    |文件: ${fileNames.first()}
                    |状态: 已在小万中完全接收
                    |提示: 任务目标已达成，可以使用 COMPLETE 动作结束
                    |═══════════════════════════════════════
                    """.trimMargin()
                } else {
                    """
                    |═══════════════════════════════════════
                    |✅ 批量文件接收成功
                    |═══════════════════════════════════════
                    |数量: ${fileNames.size}个文件
                    |文件: ${fileNames.joinToString("、")}
                    |状态: 已在小万中完全接收
                    |提示: 任务目标已达成，可以使用 COMPLETE 动作结束
                    |═══════════════════════════════════════
                    """.trimMargin()
                }

                // Inject as priority event instead of regular memory
                cn.com.omnimind.bot.util.AssistsUtil.Core.appendVlmPriorityEvent(
                    memory = message,
                    eventType = "file_received",
                    suggestCompletion = true  // Guide VLM to complete task
                )
            }
            withContext(Dispatchers.Main) {
                if (records.isNotEmpty()) {
                    val message = if (records.size == 1) {
                        "File received"
                    } else {
                        "${records.size} files received"
                    }
                    Toast.makeText(this@McpFileReceiverActivity, message, Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(this@McpFileReceiverActivity, "Failed to receive file", Toast.LENGTH_SHORT).show()
                }
                finish()
            }
        }
    }

    private fun extractUris(intent: Intent): List<Uri> {
        return when (intent.action) {
            Intent.ACTION_SEND -> extractSendUri(intent)?.let { listOf(it) } ?: extractClipUris(intent)
            Intent.ACTION_SEND_MULTIPLE -> extractSendUris(intent).ifEmpty { extractClipUris(intent) }
            Intent.ACTION_VIEW -> intent.data?.let { listOf(it) } ?: emptyList()
            else -> emptyList()
        }
    }

    private fun extractSendUri(intent: Intent): Uri? {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            intent.getParcelableExtra(Intent.EXTRA_STREAM, Uri::class.java)
        } else {
            @Suppress("DEPRECATION")
            intent.getParcelableExtra(Intent.EXTRA_STREAM)
        }
    }

    private fun extractSendUris(intent: Intent): List<Uri> {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            intent.getParcelableArrayListExtra(Intent.EXTRA_STREAM, Uri::class.java) ?: emptyList()
        } else {
            @Suppress("DEPRECATION")
            intent.getParcelableArrayListExtra(Intent.EXTRA_STREAM) ?: emptyList()
        }
    }

    private fun extractClipUris(intent: Intent): List<Uri> {
        val clipData = intent.clipData ?: return emptyList()
        val uris = ArrayList<Uri>(clipData.itemCount)
        for (index in 0 until clipData.itemCount) {
            clipData.getItemAt(index)?.uri?.let { uris.add(it) }
        }
        return uris
    }
}
