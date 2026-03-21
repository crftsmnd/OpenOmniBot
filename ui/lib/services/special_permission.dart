import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:ui/utils/ui.dart';

// The channel name must match the one in MainActivity.kt
const spePermission = MethodChannel(
  'cn.com.omnimind.bot/SpecialPermissionEvent',
);

/// 检查无障碍权限，如果没有权限则弹出授权对话框
/// 返回 true 表示有权限，false 表示没有权限
Future<bool> checkAccessibilityPermission(BuildContext context) async {
  try {
    final hasPermission = await spePermission.invokeMethod(
      'isAccessibilityServiceEnabled',
    );
    if (hasPermission == true) {
      return true;
    }

    if (!context.mounted) {
      return false;
    }

    // 没有权限，弹出对话框
    final result = await AppDialog.confirm(
      context,
      title: '无障碍权限',
      content: '每次开启App需重新授权无障碍的权限，这也是为了你的安全～',
      cancelText: '取消',
      confirmText: '去授权',
    );

    if (!context.mounted) {
      return false;
    }
    if (result == true) {
      await spePermission.invokeMethod('openAccessibilitySettings');
    }

    return false;
  } catch (e) {
    debugPrint('检查无障碍权限失败: $e');
    return false;
  }
}

Future<bool> requestPermission(List<String> permissions) async {
  try {
    final hasPermission = await spePermission.invokeMethod(
      'requestPermissions',
      {'permissions': permissions},
    );
    return hasPermission == "Success";
  } catch (e) {
    debugPrint('检查无障碍权限失败: $e');
    return false;
  }
}

Future<bool> isTermuxInstalled() async {
  try {
    return await spePermission.invokeMethod<bool>('isTermuxInstalled') ?? false;
  } catch (e) {
    debugPrint('检查 Termux 安装状态失败: $e');
    return false;
  }
}

Future<bool> openTermuxApp() async {
  try {
    return await spePermission.invokeMethod<bool>('openTermuxApp') ?? false;
  } catch (e) {
    debugPrint('打开 Termux 失败: $e');
    return false;
  }
}

Future<bool> isTermuxRunCommandPermissionGranted() async {
  try {
    return await spePermission.invokeMethod<bool>(
          'isTermuxRunCommandPermissionGranted',
        ) ??
        false;
  } catch (e) {
    debugPrint('检查 Termux RUN_COMMAND 权限失败: $e');
    return false;
  }
}

Future<bool> requestTermuxRunCommandPermission() async {
  try {
    return await spePermission.invokeMethod<bool>(
          'requestTermuxRunCommandPermission',
        ) ??
        false;
  } catch (e) {
    debugPrint('请求 Termux RUN_COMMAND 权限失败: $e');
    return false;
  }
}

Future<bool> ensureInstalledAppsPermission() async {
  try {
    final hasPermission = await spePermission.invokeMethod<bool>(
          'isInstalledAppsPermissionGranted',
        ) ??
        false;
    if (hasPermission) {
      return true;
    }
    await openInstalledAppsSettings();
  } catch (e) {
    debugPrint('检查应用列表读取权限失败: $e');
  }
  return false;
}

Future<void> openInstalledAppsSettings() async {
  await spePermission.invokeMethod('openInstalledAppsSettings');
}

Future<void> openAppDetailsSettings() async {
  await spePermission.invokeMethod('openAppDetailsSettings');
}

Future<bool> isNotificationPermissionGranted() async {
  try {
    return await spePermission.invokeMethod<bool>(
          'isNotificationPermissionGranted',
        ) ??
        false;
  } catch (e) {
    debugPrint('检查通知权限失败: $e');
    return false;
  }
}

Future<bool> requestNotificationPermission() async {
  try {
    return await spePermission.invokeMethod<bool>(
          'requestNotificationPermission',
        ) ??
        false;
  } catch (e) {
    debugPrint('请求通知权限失败: $e');
    return false;
  }
}

Future<bool> ensureNotificationPermission() async {
  if (await isNotificationPermissionGranted()) {
    return true;
  }
  return requestNotificationPermission();
}

Future<bool> isWorkspaceStorageAccessGranted() async {
  try {
    return await spePermission.invokeMethod<bool>(
          'isWorkspaceStorageAccessGranted',
        ) ??
        false;
  } catch (e) {
    debugPrint('检查公共 workspace 访问权限失败: $e');
    return false;
  }
}

Future<void> openWorkspaceStorageSettings() async {
  await spePermission.invokeMethod('openWorkspaceStorageSettings');
}

Future<Map<String, dynamic>> prepareTermuxLiveWrapper() async {
  final result = await spePermission.invokeMethod<Map<dynamic, dynamic>>(
    'prepareTermuxLiveWrapper',
  );
  return Map<String, dynamic>.from(result ?? const {});
}

Future<bool> isUnknownAppInstallAllowed() async {
  try {
    return await spePermission.invokeMethod<bool>(
          'isUnknownAppInstallAllowed',
        ) ??
        false;
  } catch (e) {
    debugPrint('检查未知应用安装权限失败: $e');
    return false;
  }
}

Future<void> openUnknownAppInstallSettings() async {
  await spePermission.invokeMethod('openUnknownAppInstallSettings');
}

Future<Map<String, dynamic>> downloadAndInstallTermuxApk(
  String downloadUrl,
) async {
  final result = await spePermission.invokeMethod<Map<dynamic, dynamic>>(
    'downloadAndInstallTermuxApk',
    {'downloadUrl': downloadUrl},
  );
  return Map<String, dynamic>.from(result ?? const {});
}
