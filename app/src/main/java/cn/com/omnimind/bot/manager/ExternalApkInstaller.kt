package cn.com.omnimind.bot.manager

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.content.FileProvider
import cn.com.omnimind.baselib.http.OkHttpManager
import cn.com.omnimind.baselib.util.OmniLog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

data class ExternalApkInstallResult(
    val success: Boolean,
    val status: String,
    val message: String,
    val filePath: String? = null
)

object ExternalApkInstaller {
    private const val TAG = "ExternalApkInstaller"
    private const val FILE_PROVIDER_AUTHORITY = "cn.com.omnimind.bot.fileprovider"
    private const val DOWNLOAD_DIR_NAME = "external_apk"

    const val STATUS_INSTALLER_LAUNCHED = "installer_launched"
    const val STATUS_INSTALL_PERMISSION_REQUIRED = "install_permission_required"
    const val STATUS_DOWNLOAD_FAILED = "download_failed"
    const val STATUS_INSTALL_FAILED = "install_failed"

    fun canInstallPackages(context: Context): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            context.packageManager.canRequestPackageInstalls()
        } else {
            true
        }
    }

    fun openInstallPermissionSettings(context: Context) {
        val intent = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Intent(
                Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                Uri.parse("package:${context.packageName}")
            )
        } else {
            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                data = Uri.parse("package:${context.packageName}")
            }
        }.apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
        context.startActivity(intent)
    }

    suspend fun downloadAndInstall(
        context: Context,
        downloadUrl: String,
        apkFileName: String,
        displayName: String
    ): ExternalApkInstallResult {
        val appContext = context.applicationContext
        if (!canInstallPackages(appContext)) {
            withContext(Dispatchers.Main) {
                openInstallPermissionSettings(context)
            }
            return ExternalApkInstallResult(
                success = false,
                status = STATUS_INSTALL_PERMISSION_REQUIRED,
                message = "请先允许本应用安装未知应用，然后再次点击安装 $displayName。"
            )
        }

        val apkFile = downloadApk(appContext, downloadUrl, apkFileName)
            ?: return ExternalApkInstallResult(
                success = false,
                status = STATUS_DOWNLOAD_FAILED,
                message = "$displayName 安装包下载失败，请稍后重试。"
            )

        val launched = withContext(Dispatchers.Main) {
            installApk(context, apkFile)
        }
        return if (launched) {
            ExternalApkInstallResult(
                success = true,
                status = STATUS_INSTALLER_LAUNCHED,
                message = "$displayName 安装包已下载完成，已打开系统安装界面。",
                filePath = apkFile.absolutePath
            )
        } else {
            ExternalApkInstallResult(
                success = false,
                status = STATUS_INSTALL_FAILED,
                message = "$displayName 安装包已下载完成，但无法打开系统安装界面。",
                filePath = apkFile.absolutePath
            )
        }
    }

    private suspend fun downloadApk(
        context: Context,
        downloadUrl: String,
        apkFileName: String
    ): File? = withContext(Dispatchers.IO) {
        val downloadDir = File(context.filesDir, DOWNLOAD_DIR_NAME)
        if (!downloadDir.exists() && !downloadDir.mkdirs()) {
            OmniLog.e(TAG, "Failed to create external apk directory: ${downloadDir.absolutePath}")
            return@withContext null
        }

        val apkFile = File(downloadDir, apkFileName)
        val tempFile = File(downloadDir, "$apkFileName.download")

        runCatching {
            downloadDir.listFiles()?.forEach { file ->
                if (file != apkFile && file != tempFile) {
                    file.delete()
                }
            }
        }

        return@withContext try {
            val request = OkHttpManager.newBuilder()
                .url(downloadUrl)
                .get()
                .build()
            OkHttpManager.enqueue(request).use { response ->
                if (!response.isSuccessful) {
                    OmniLog.e(TAG, "Download apk failed with code: ${response.code}")
                    return@use null
                }

                val body = response.body ?: return@use null
                tempFile.outputStream().use { output ->
                    body.byteStream().use { input ->
                        val buffer = ByteArray(8 * 1024)
                        while (true) {
                            val read = input.read(buffer)
                            if (read == -1) break
                            output.write(buffer, 0, read)
                        }
                        output.flush()
                    }
                }

                if (apkFile.exists()) {
                    apkFile.delete()
                }
                if (!tempFile.renameTo(apkFile)) {
                    FileOutputStream(apkFile).use { output ->
                        tempFile.inputStream().use { input ->
                            input.copyTo(output)
                        }
                    }
                    tempFile.delete()
                }
                apkFile
            }
        } catch (e: Exception) {
            OmniLog.e(TAG, "Download external apk failed", e)
            null
        } finally {
            if (tempFile.exists() && tempFile.length() == 0L) {
                tempFile.delete()
            }
        }
    }

    private fun installApk(context: Context, apkFile: File): Boolean {
        return try {
            if (!apkFile.exists()) {
                OmniLog.e(TAG, "APK file does not exist: ${apkFile.absolutePath}")
                false
            } else {
                val uri = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                    FileProvider.getUriForFile(context, FILE_PROVIDER_AUTHORITY, apkFile)
                } else {
                    Uri.fromFile(apkFile)
                }
                val intent = Intent(Intent.ACTION_VIEW).apply {
                    setDataAndType(uri, "application/vnd.android.package-archive")
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
                context.startActivity(intent)
                true
            }
        } catch (e: Exception) {
            OmniLog.e(TAG, "Launch package installer failed", e)
            false
        }
    }
}
