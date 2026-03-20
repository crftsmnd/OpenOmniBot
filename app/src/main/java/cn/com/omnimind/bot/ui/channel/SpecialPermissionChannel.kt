package cn.com.omnimind.bot.ui.channel

import cn.com.omnimind.bot.manager.SpecialPermissionManager
import android.annotation.SuppressLint
import android.content.Context
import io.flutter.embedding.engine.FlutterEngine
import io.flutter.plugin.common.MethodChannel

class SpecialPermissionChannel {
    @SuppressLint("StaticFieldLeak")
    var specialPermissionManager: SpecialPermissionManager? = null
    private  val TAG = "[PlatformChannel]"
    private  val CHANNEL = "cn.com.omnimind.bot/SpecialPermissionEvent"
    private var methodChannel: MethodChannel? = null

    fun onCreate(context: Context) {
        specialPermissionManager = SpecialPermissionManager(context)
    }

    fun setChannel(flutterEngine: FlutterEngine) {

        methodChannel= MethodChannel(flutterEngine.dartExecutor.binaryMessenger, CHANNEL)
        methodChannel?.setMethodCallHandler { call, result ->
                when (call.method) {
                    "isAccessibilityServiceEnabled" -> specialPermissionManager!!.isAccessibilityServiceEnabled(
                        result
                    )

                    "openAccessibilitySettings" -> specialPermissionManager!!.openAccessibilitySettings(
                        result
                    )

                    "isIgnoringBatteryOptimizations" -> specialPermissionManager!!.isIgnoringBatteryOptimizations(
                        result
                    )

                    "openBatteryOptimizationSettings" -> specialPermissionManager!!.openBatteryOptimizationSettings(
                        result
                    )
                    "isOverlayPermission" -> specialPermissionManager!!.isOverlayPermission(
                        result
                    )

                    "openOverlaySettings" -> specialPermissionManager!!.openOverlaySettings(
                        result
                    )

                    "isInstalledAppsPermissionGranted" -> specialPermissionManager!!.isInstalledAppsPermissionGranted(
                        result
                    )

                    "openInstalledAppsSettings" -> specialPermissionManager!!.openInstalledAppsSettings(
                        result
                    )
                    "openAutoStartSettings" -> specialPermissionManager!!.openAutoStartSettings(
                        result
                    )
                    "isTermuxInstalled" -> specialPermissionManager!!.isTermuxInstalled(
                        result
                    )
                    "isTermuxRunCommandPermissionGranted" -> specialPermissionManager!!
                        .isTermuxRunCommandPermissionGranted(result)
                    "requestTermuxRunCommandPermission" -> specialPermissionManager!!
                        .requestTermuxRunCommandPermission(result)
                    "openTermuxApp" -> specialPermissionManager!!.openTermuxApp(
                        result
                    )
                    "openAppDetailsSettings" -> specialPermissionManager!!
                        .openAppDetailsSettings(result)
                    "isWorkspaceStorageAccessGranted" -> specialPermissionManager!!
                        .isWorkspaceStorageAccessGranted(result)
                    "openWorkspaceStorageSettings" -> specialPermissionManager!!
                        .openWorkspaceStorageSettings(result)
                    "prepareTermuxLiveWrapper" -> specialPermissionManager!!
                        .prepareTermuxLiveWrapper(result)
                    "isUnknownAppInstallAllowed" -> specialPermissionManager!!
                        .isUnknownAppInstallAllowed(result)
                    "openUnknownAppInstallSettings" -> specialPermissionManager!!
                        .openUnknownAppInstallSettings(result)
                    "downloadAndInstallTermuxApk" -> specialPermissionManager!!
                        .downloadAndInstallTermuxApk(call, result)
                    "requestPermissions"-> specialPermissionManager!!.requestPermissions(
                        call, result
                    )

                    else -> {
                        result.notImplemented()
                    }
                }
            }
    }

    fun clear() {
        methodChannel?.setMethodCallHandler(null)
        methodChannel = null
    }
}
