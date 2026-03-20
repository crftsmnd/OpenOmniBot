import 'package:flutter/services.dart';
import 'package:ui/services/assists_core_service.dart';
import 'package:ui/services/model_provider_config_service.dart';

class SceneCatalogItem {
  final String sceneId;
  final String description;
  final String defaultModel;
  final String effectiveModel;
  final String transport;
  final String configSource;
  final bool overrideApplied;
  final String overrideModel;
  final bool providerConfigured;

  const SceneCatalogItem({
    required this.sceneId,
    required this.description,
    required this.defaultModel,
    required this.effectiveModel,
    required this.transport,
    required this.configSource,
    required this.overrideApplied,
    required this.overrideModel,
    required this.providerConfigured,
  });

  factory SceneCatalogItem.fromMap(Map<dynamic, dynamic>? map) {
    return SceneCatalogItem(
      sceneId: (map?['sceneId'] ?? '').toString(),
      description: (map?['description'] ?? '').toString(),
      defaultModel: (map?['defaultModel'] ?? '').toString(),
      effectiveModel: (map?['effectiveModel'] ?? '').toString(),
      transport: (map?['transport'] ?? '').toString(),
      configSource: (map?['configSource'] ?? '').toString(),
      overrideApplied: map?['overrideApplied'] == true,
      overrideModel: (map?['overrideModel'] ?? '').toString(),
      providerConfigured: map?['providerConfigured'] == true,
    );
  }
}

class SceneModelOverrideEntry {
  final String sceneId;
  final String model;

  const SceneModelOverrideEntry({required this.sceneId, required this.model});

  factory SceneModelOverrideEntry.fromMap(Map<dynamic, dynamic>? map) {
    return SceneModelOverrideEntry(
      sceneId: (map?['sceneId'] ?? '').toString(),
      model: (map?['model'] ?? '').toString(),
    );
  }
}

class SceneModelConfigService {
  static Future<List<SceneCatalogItem>> getSceneCatalog() async {
    try {
      final result = await AssistsMessageService.assistCore
          .invokeMethod<List<dynamic>>('getSceneModelCatalog');
      return (result ?? const [])
          .map((item) => SceneCatalogItem.fromMap(item as Map?))
          .where((item) => item.sceneId.isNotEmpty)
          .toList();
    } on PlatformException {
      return const [];
    }
  }

  static Future<List<SceneModelOverrideEntry>> getSceneModelOverrides() async {
    try {
      final result = await AssistsMessageService.assistCore
          .invokeMethod<List<dynamic>>('getSceneModelOverrides');
      return (result ?? const [])
          .map((item) => SceneModelOverrideEntry.fromMap(item as Map?))
          .where((item) => item.sceneId.isNotEmpty && item.model.isNotEmpty)
          .toList();
    } on PlatformException {
      return const [];
    }
  }

  static Future<List<SceneModelOverrideEntry>> saveSceneModelOverride({
    required String sceneId,
    required String model,
  }) async {
    final result = await AssistsMessageService.assistCore
        .invokeMethod<List<dynamic>>('saveSceneModelOverride', {
          'sceneId': sceneId,
          'model': model,
        });
    return (result ?? const [])
        .map((item) => SceneModelOverrideEntry.fromMap(item as Map?))
        .where((item) => item.sceneId.isNotEmpty && item.model.isNotEmpty)
        .toList();
  }

  static Future<List<SceneModelOverrideEntry>> clearSceneModelOverride(
    String sceneId,
  ) async {
    final result = await AssistsMessageService.assistCore
        .invokeMethod<List<dynamic>>('clearSceneModelOverride', {
          'sceneId': sceneId,
        });
    return (result ?? const [])
        .map((item) => SceneModelOverrideEntry.fromMap(item as Map?))
        .where((item) => item.sceneId.isNotEmpty && item.model.isNotEmpty)
        .toList();
  }

  static bool isValidModelName(String value) {
    return ModelProviderConfigService.isValidModelName(value);
  }
}
