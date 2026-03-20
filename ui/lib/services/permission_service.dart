import 'package:flutter/material.dart';
import 'package:ui/features/home/pages/authorize/widgets/permission_section.dart';
import 'package:ui/services/device_service.dart';
import 'package:ui/services/permission_registry.dart';
import 'package:ui/services/special_permission.dart';

/// 权限管理服务
/// 提供统一的权限检查、状态管理和转换方法
class PermissionService {
  PermissionService._();

  /// 根据品牌加载权限规格列表
  static List<PermissionSpec> loadSpecs({required String brand}) {
    return PermissionRegistry.getPermissions(brand: brand);
  }

  /// 将权限规格转换为 PermissionData（供 UI 组件使用）
  ///
  /// [specs] 权限规格列表
  /// [context] BuildContext，用于显示自定义授权弹窗
  static List<PermissionData> specsToPermissionData(
    List<PermissionSpec> specs, {
    required BuildContext context,
  }) {
    return specs.map((spec) {
      return PermissionData(
        id: spec.id,
        iconPath: spec.iconPath,
        iconWidth: spec.iconWidth,
        iconHeight: spec.iconHeight,
        name: spec.name,
        description: spec.description,
        onAuthorize: () async {
          // 优先使用 spec 中定义的自定义授权方法
          if (spec.customAuthMethod != null) {
            await spec.customAuthMethod!(context);
          } else {
            // 使用默认的原生方法
            await spePermission.invokeMethod(spec.openMethod);
          }
        },
        checkAuthorization: () async {
          // 优先使用 spec 中定义的自定义检查方法
          if (spec.customCheckMethod != null) {
            return await spec.customCheckMethod!();
          }
          
          // 使用默认的原生方法检查
          try {
            if (spec.checkMethod.isEmpty) {
              return false;
            }
            final result = await spePermission.invokeMethod(spec.checkMethod);
            return result ?? false;
          } catch (e) {
            print('检查权限 ${spec.id} 失败: $e');
            return false;
          }
        },
        iconInfo: spec.infoLabel,
        iconClick: spec.onInfoClick,
      );
    }).toList();
  }

  /// 检查指定权限列表中缺失的权限
  ///
  /// [specs] 权限规格列表
  /// 返回未授权的权限规格列表
  static Future<List<PermissionSpec>> getMissing(
    List<PermissionSpec> specs,
  ) async {
    final missing = <PermissionSpec>[];

    for (final spec in specs) {
      try {
        // 优先使用自定义检查方法
        if (spec.customCheckMethod != null) {
          final granted = await spec.customCheckMethod!();
          print('权限 ${spec.id} 授权状态(自定义检查): $granted');
          if (!granted) {
            missing.add(spec);
          }
          continue;
        }
        
        final granted = await spePermission.invokeMethod(spec.checkMethod);
        print('权限 ${spec.id} 授权状态: $granted');
        if (granted != true) {
          missing.add(spec);
        }
      } catch (e) {
        print('检查权限 ${spec.id} 失败: $e');
        missing.add(spec);
      }
    }

    return missing;
  }

  /// 检查是否所有权限都已授权
  static Future<bool> allGranted(List<PermissionSpec> specs) async {
    final missing = await getMissing(specs);
    return missing.isEmpty;
  }

  /// 检查指定的权限规格列表
  ///
  /// 为每个 PermissionData 检查授权状态并更新其 notifier
  /// [permissions] 需要检查的权限数据列表
  static Future<void> checkPermissions(List<PermissionData> permissions) async {
    for (var item in permissions) {
      bool granted = await item.checkAuthorization();
      item.notifier.value = granted;
    }
  }

  /// 检查所有权限是否已授权
  ///
  /// [permissions] 权限数据列表
  /// 返回是否全部授权
  static bool checkAllAuthorized(List<PermissionData> permissions) {
    return permissions.every((item) => item.notifier.value);
  }

  static bool checkAuthorizedByIds(
    List<PermissionData> permissions,
    Set<String> requiredIds,
  ) {
    if (requiredIds.isEmpty) {
      return checkAllAuthorized(permissions);
    }

    final matchedPermissions = permissions
        .where((item) => requiredIds.contains(item.id))
        .toList(growable: false);
    final matchedIds = matchedPermissions
        .map((item) => item.id)
        .toSet();

    if (!requiredIds.every(matchedIds.contains)) {
      return false;
    }

    return matchedPermissions.every((item) => item.notifier.value);
  }

  /// 检查指定层级的缺失权限
  ///
  /// [brand] 设备品牌
  /// [level] 权限层级
  /// 返回该层级下未授权的权限规格列表
  static Future<List<PermissionSpec>> getMissingByLevel({
    required String brand,
    required PermissionLevel level,
  }) async {
    final specs = PermissionRegistry.getPermissionsByLevel(
      brand: brand,
      level: level,
    );
    return getMissing(specs);
  }

  /// 检查指定层级权限是否全部授权
  ///
  /// [brand] 设备品牌
  /// [level] 权限层级
  /// 返回是否全部授权
  static Future<bool> allGrantedByLevel({
    required String brand,
    required PermissionLevel level,
  }) async {
    final missing = await getMissingByLevel(brand: brand, level: level);
    return missing.isEmpty;
  }
}

/// 快速检查缺失权限数量的辅助函数
///
/// [brand] 设备品牌，如 'huawei', 'xiaomi', 'oppo', 'vivo' 等
/// 如果 brand 为 null 或空字符串，则自动检测设备品牌
Future<int> checkMissingPermissions(String? brand) async {
  if (brand == null || brand.isEmpty) {
      final deviceInfo = await DeviceService.getDeviceInfo();
      brand = (deviceInfo?['brand'] as String?)?.toLowerCase() ?? 'other';
  }
  final specs = PermissionService.loadSpecs(brand: brand);
  final missing = await PermissionService.getMissing(specs);
  return missing.length;
}
