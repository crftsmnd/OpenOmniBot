package cn.com.omnimind.bot.workspace

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.Settings
import androidx.core.content.ContextCompat

object WorkspaceStorageAccess {
    const val REQUIRED_PERMISSION_NAME = "公共 workspace 访问权限"

    fun isGranted(context: Context): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            Environment.isExternalStorageManager()
        } else {
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.READ_EXTERNAL_STORAGE
            ) == PackageManager.PERMISSION_GRANTED &&
                ContextCompat.checkSelfPermission(
                    context,
                    Manifest.permission.WRITE_EXTERNAL_STORAGE
                ) == PackageManager.PERMISSION_GRANTED
        }
    }

    fun requiredPermissionNames(): List<String> = listOf(REQUIRED_PERMISSION_NAME)

    fun buildSettingsIntent(context: Context): Intent {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION).apply {
                data = Uri.parse("package:${context.packageName}")
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
        } else {
            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                data = Uri.parse("package:${context.packageName}")
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
        }
    }

    fun buildFallbackSettingsIntent(): Intent {
        return Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
    }

    fun looksLikePermissionError(throwable: Throwable): Boolean {
        if (throwable is SecurityException) {
            return true
        }
        val message = throwable.message.orEmpty()
        return message.contains("EPERM", ignoreCase = true) ||
            message.contains("Operation not permitted", ignoreCase = true) ||
            message.contains("Permission denied", ignoreCase = true)
    }
}
