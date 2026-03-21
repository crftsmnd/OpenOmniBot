package cn.com.omnimind.bot.manager

import android.Manifest
import android.content.Intent
import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.content.ContextCompat
import cn.com.omnimind.baselib.permission.PermissionRequest
import cn.com.omnimind.baselib.util.OmniLog
import cn.com.omnimind.bot.termux.TermuxCommandRunner
import cn.com.omnimind.bot.util.AssistsUtil
import cn.com.omnimind.bot.workspace.WorkspaceStorageAccess
import io.flutter.plugin.common.MethodCall
import io.flutter.plugin.common.MethodChannel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class SpecialPermissionManager(private val context: Context) {

    companion object {
        private const val TAG = "[PlatformManager]"
        private const val TERMUX_PACKAGE_NAME = "com.termux"
        private const val TERMUX_RUN_COMMAND_PERMISSION = "com.termux.permission.RUN_COMMAND"
    }

    fun isAccessibilityServiceEnabled(result: MethodChannel.Result) {
        try {
            val isEnabled = AssistsUtil.Core.isAccessibilityServiceEnabled()
            result.success(isEnabled)
        } catch (e: Exception) {
            OmniLog.e(TAG, "Error checking accessibility service", e)
            result.error("CHECK_FAILED", "Failed to check accessibility service.", e.message)
        }
    }

    fun isIgnoringBatteryOptimizations(result: MethodChannel.Result) {
        try {
            val value = AssistsUtil.Setting.isIgnoringBatteryOptimizations(context);
            result.success(value)
        } catch (e: Exception) {
            OmniLog.e(TAG, "Error checking battery optimization", e)
            result.error("CHECK_FAILED", "Failed to check battery optimization.", e.message)
        }
    }

    fun openBatteryOptimizationSettings(result: MethodChannel.Result) {
        try {
            AssistsUtil.Setting.openBatteryOptimizationSettings(context)
            OmniLog.v(TAG, "Requesting to ignore battery optimizations.")
            result.success(null)

        } catch (e: Exception) {
            OmniLog.e(TAG, "请求忽略电池优化时发生异常，可能没有 Activity 能处理此 Intent。", e)
            result.error(
                "INTENT_FAILED",
                "无法打开电池优化设置页面，可能没有 Activity 能处理此 Intent。",
                e.message
            )
        }

    }

    fun openAccessibilitySettings(result: MethodChannel.Result) {
        try {
            AssistsUtil.Setting.openAccessibilitySettings(context);
            OmniLog.v(TAG, "Opening accessibility settings.")
            result.success(null)

        } catch (e: Exception) {
            OmniLog.e(TAG, "请求打开辅助功能设置时发生异常，可能没有 Activity 能处理此 Intent。", e)
            result.error(
                "INTENT_FAILED",
                "无法打开辅助功能设置页面，可能没有 Activity 能处理此 Intent。",
                e.message
            )
        }
    }

    fun isOverlayPermission(result: MethodChannel.Result) {
        try {
            val value = AssistsUtil.Setting.isOverlayPermission(context);
            result.success(value)
        } catch (e: Exception) {
            OmniLog.e(TAG, "Error checking battery optimization", e)
            result.error("CHECK_FAILED", "Failed to overlay permission.", e.message)
        }
    }

    fun openOverlaySettings(result: MethodChannel.Result) {
        try {
            AssistsUtil.Setting.openOverlaySettings(context);
            result.success(null)
            OmniLog.v(TAG, "Opening overlay settings.")
        } catch (e: Exception) {
            OmniLog.e(TAG, "请求打开悬浮窗设置时发生异常，可能没有 Activity 能处理此 Intent。", e)
            result.error(
                "INTENT_FAILED",
                "无法打开打开悬浮窗设置页面，可能没有 Activity 能处理此 Intent。",
                e.message
            )
        }

    }

    fun isInstalledAppsPermissionGranted(result: MethodChannel.Result) {
        try {
            val value = AssistsUtil.Setting.isInstalledAppsPermissionGranted(context)
            result.success(value)
        } catch (e: Exception) {
            OmniLog.e(TAG, "Error checking installed apps permission", e)
            result.error("CHECK_FAILED", "Failed to check installed apps permission.", e.message)
        }
    }

    fun openInstalledAppsSettings(result: MethodChannel.Result) {
        try {
            AssistsUtil.Setting.openInstalledAppsSettings(context)
            result.success(null)
            OmniLog.v(TAG, "Opening installed apps settings.")
        } catch (e: Exception) {
            OmniLog.e(
                TAG,
                "请求打开已安装应用列表权限设置时发生异常，可能没有 Activity 能处理此 Intent。",
                e
            )
            result.error(
                "INTENT_FAILED",
                "无法打开已安装应用列表权限设置页面，可能没有 Activity 能处理此 Intent。",
                e.message
            )
        }
    }

    fun openAutoStartSettings(result: MethodChannel.Result) {
        try {
            AssistsUtil.Setting.openAutoStartSettings(context)
            result.success(null)
            OmniLog.v(TAG, "Opening auto start settings.")
        } catch (e: Exception) {
            OmniLog.e(
                TAG,
                "请求打开应用启动管理设置时发生异常，可能没有 Activity 能处理此 Intent。",
                e
            )
            result.error(
                "INTENT_FAILED",
                "无法打开应用启动管理设置页面，可能没有 Activity 能处理此 Intent。",
                e.message
            )
        }
    }

    fun isTermuxInstalled(result: MethodChannel.Result) {
        try {
            val installed = TermuxCommandRunner.isTermuxInstalled(context)
            result.success(installed)
        } catch (e: Exception) {
            OmniLog.e(TAG, "Error checking termux installation", e)
            result.error("CHECK_FAILED", "Failed to check Termux installation.", e.message)
        }
    }

    fun openTermuxApp(result: MethodChannel.Result) {
        try {
            val launchIntent = context.packageManager.getLaunchIntentForPackage(TERMUX_PACKAGE_NAME)
            if (launchIntent == null) {
                OmniLog.w(TAG, "Termux is not installed or has no launch intent.")
                result.success(false)
                return
            }

            launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(launchIntent)
            OmniLog.v(TAG, "Opening Termux app.")
            result.success(true)
        } catch (e: Exception) {
            OmniLog.e(TAG, "请求打开 Termux 时发生异常。", e)
            result.error(
                "INTENT_FAILED",
                "无法打开 Termux，可能没有 Activity 能处理此 Intent。",
                e.message
            )
        }
    }

    fun isTermuxRunCommandPermissionGranted(result: MethodChannel.Result) {
        try {
            val granted = TermuxCommandRunner.hasRunCommandPermission(context)
            result.success(granted)
        } catch (e: Exception) {
            OmniLog.e(TAG, "Error checking Termux run command permission", e)
            result.error(
                "CHECK_FAILED",
                "Failed to check Termux RUN_COMMAND permission.",
                e.message
            )
        }
    }

    fun requestTermuxRunCommandPermission(result: MethodChannel.Result) {
        try {
            CoroutineScope(Dispatchers.Default).launch {
                AssistsUtil.UI.closeChatBotDialog()
            }
            PermissionRequest.requestPermissions(context, arrayOf(TERMUX_RUN_COMMAND_PERMISSION)) {
                val granted = it[TERMUX_RUN_COMMAND_PERMISSION] == true
                result.success(granted)
            }
        } catch (e: Exception) {
            OmniLog.e(TAG, "Error requesting Termux RUN_COMMAND permission", e)
            result.error(
                "REQUEST_FAILED",
                "Failed to request Termux RUN_COMMAND permission.",
                e.message
            )
        }
    }

    fun openAppDetailsSettings(result: MethodChannel.Result) {
        try {
            val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                data = Uri.parse("package:${context.packageName}")
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            context.startActivity(intent)
            result.success(true)
        } catch (e: Exception) {
            OmniLog.e(TAG, "请求打开应用详情页时发生异常。", e)
            result.error(
                "INTENT_FAILED",
                "无法打开应用详情页，可能没有 Activity 能处理此 Intent。",
                e.message
            )
        }
    }

    fun isNotificationPermissionGranted(result: MethodChannel.Result) {
        try {
            val granted = if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
                true
            } else {
                ContextCompat.checkSelfPermission(
                    context,
                    Manifest.permission.POST_NOTIFICATIONS
                ) == PackageManager.PERMISSION_GRANTED
            }
            result.success(granted)
        } catch (e: Exception) {
            OmniLog.e(TAG, "Error checking notification permission", e)
            result.error(
                "CHECK_FAILED",
                "Failed to check notification permission.",
                e.message
            )
        }
    }

    fun requestNotificationPermission(result: MethodChannel.Result) {
        try {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
                result.success(true)
                return
            }
            CoroutineScope(Dispatchers.Default).launch {
                AssistsUtil.UI.closeChatBotDialog()
            }
            PermissionRequest.requestPermissions(
                context,
                arrayOf(Manifest.permission.POST_NOTIFICATIONS)
            ) {
                result.success(it[Manifest.permission.POST_NOTIFICATIONS] == true)
            }
        } catch (e: Exception) {
            OmniLog.e(TAG, "Error requesting notification permission", e)
            result.error(
                "REQUEST_FAILED",
                "Failed to request notification permission.",
                e.message
            )
        }
    }

    fun isWorkspaceStorageAccessGranted(result: MethodChannel.Result) {
        try {
            result.success(WorkspaceStorageAccess.isGranted(context))
        } catch (e: Exception) {
            OmniLog.e(TAG, "Error checking workspace storage access", e)
            result.error(
                "CHECK_FAILED",
                "Failed to check workspace storage access.",
                e.message
            )
        }
    }

    fun openWorkspaceStorageSettings(result: MethodChannel.Result) {
        try {
            val primaryIntent = WorkspaceStorageAccess.buildSettingsIntent(context)
            runCatching {
                context.startActivity(primaryIntent)
            }.recoverCatching {
                context.startActivity(WorkspaceStorageAccess.buildFallbackSettingsIntent())
            }.getOrThrow()
            result.success(true)
        } catch (e: Exception) {
            OmniLog.e(TAG, "请求打开公共 workspace 存储设置页时发生异常。", e)
            result.error(
                "INTENT_FAILED",
                "无法打开公共 workspace 存储设置页，可能没有 Activity 能处理此 Intent。",
                e.message
            )
        }
    }

    fun prepareTermuxLiveWrapper(result: MethodChannel.Result) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val status = TermuxCommandRunner.prepareLiveEnvironment(context)
                withContext(Dispatchers.Main) {
                    result.success(
                        mapOf(
                            "success" to status.success,
                            "wrapperReady" to status.wrapperReady,
                            "sharedStorageReady" to status.sharedStorageReady,
                            "message" to status.message
                        )
                    )
                }
            } catch (e: Exception) {
                OmniLog.e(TAG, "Error preparing Termux live wrapper", e)
                withContext(Dispatchers.Main) {
                    result.error(
                        "PREPARE_FAILED",
                        "Failed to prepare Termux live wrapper.",
                        e.message
                    )
                }
            }
        }
    }

    fun isUnknownAppInstallAllowed(result: MethodChannel.Result) {
        try {
            result.success(ExternalApkInstaller.canInstallPackages(context))
        } catch (e: Exception) {
            OmniLog.e(TAG, "Error checking unknown app install permission", e)
            result.error(
                "CHECK_FAILED",
                "Failed to check unknown app install permission.",
                e.message
            )
        }
    }

    fun openUnknownAppInstallSettings(result: MethodChannel.Result) {
        try {
            ExternalApkInstaller.openInstallPermissionSettings(context)
            result.success(true)
        } catch (e: Exception) {
            OmniLog.e(TAG, "请求打开未知应用安装设置页时发生异常。", e)
            result.error(
                "INTENT_FAILED",
                "无法打开未知应用安装设置页，可能没有 Activity 能处理此 Intent。",
                e.message
            )
        }
    }

    fun downloadAndInstallTermuxApk(call: MethodCall, result: MethodChannel.Result) {
        val downloadUrl = call.argument<String>("downloadUrl")?.trim()
        if (downloadUrl.isNullOrEmpty()) {
            result.error("INVALID_ARGUMENT", "downloadUrl is required.", null)
            return
        }

        CoroutineScope(Dispatchers.IO).launch {
            try {
                val installResult = ExternalApkInstaller.downloadAndInstall(
                    context = context,
                    downloadUrl = downloadUrl,
                    apkFileName = "termux_latest.apk",
                    displayName = "Termux"
                )
                withContext(Dispatchers.Main) {
                    result.success(
                        mapOf(
                            "success" to installResult.success,
                            "status" to installResult.status,
                            "message" to installResult.message,
                            "filePath" to installResult.filePath
                        )
                    )
                }
            } catch (e: Exception) {
                OmniLog.e(TAG, "Error downloading and installing Termux apk", e)
                withContext(Dispatchers.Main) {
                    result.error(
                        "INSTALL_FAILED",
                        "Failed to download and install Termux apk.",
                        e.message
                    )
                }
            }
        }
    }

    fun requestPermissions(call: MethodCall, result: MethodChannel.Result) {
        try {
            val permissions = call.argument<List<String>>("permissions")
            if (permissions == null||permissions.isEmpty()) {
                result.error("INVALID_ARGUMENT", "Invalid argument: permissions is null.", null)
                return
            }
            val mPermissions = ArrayList<String>();
            mPermissions.addAll(permissions)
            //要权限优先关闭 关闭聊天对话框
            CoroutineScope(Dispatchers.Default).launch {
                AssistsUtil.UI.closeChatBotDialog()
            }
            PermissionRequest.requestPermissions(context, mPermissions.toTypedArray()) {
                val isGranted = it.all { it.value }
                result.success(if (isGranted) "Success" else "Failed")
            }
        } catch (e: Exception) {
            OmniLog.e(TAG, "Error requesting permissions", e)
            result.error("request_Permissions_FAILED", "Failed to request permissions.", e.message)
        }
    }
}
