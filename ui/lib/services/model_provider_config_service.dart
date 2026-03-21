import 'dart:convert';

import 'package:flutter/services.dart';
import 'package:ui/services/assists_core_service.dart';
import 'package:ui/services/storage_service.dart';

class ModelProviderConfig {
  final String baseUrl;
  final String apiKey;
  final String source;
  final bool configured;

  const ModelProviderConfig({
    required this.baseUrl,
    required this.apiKey,
    required this.source,
    required this.configured,
  });

  factory ModelProviderConfig.empty() {
    return const ModelProviderConfig(
      baseUrl: '',
      apiKey: '',
      source: 'none',
      configured: false,
    );
  }

  factory ModelProviderConfig.fromMap(Map<dynamic, dynamic>? map) {
    if (map == null) {
      return ModelProviderConfig.empty();
    }
    return ModelProviderConfig(
      baseUrl: (map['baseUrl'] ?? '').toString(),
      apiKey: (map['apiKey'] ?? '').toString(),
      source: (map['source'] ?? 'none').toString(),
      configured: map['configured'] == true,
    );
  }
}

class ProviderModelOption {
  final String id;
  final String displayName;
  final String? ownedBy;

  const ProviderModelOption({
    required this.id,
    required this.displayName,
    this.ownedBy,
  });

  factory ProviderModelOption.fromMap(Map<dynamic, dynamic>? map) {
    return ProviderModelOption(
      id: (map?['id'] ?? '').toString(),
      displayName: (map?['displayName'] ?? map?['id'] ?? '').toString(),
      ownedBy: map?['ownedBy']?.toString(),
    );
  }
}

class ModelProviderConfigService {
  static const String _kManualModelIdsKey = 'manual_provider_model_ids_v1';
  static const String _kCachedFetchedModelsKey =
      'cached_provider_models_with_base_v1';

  static Future<ModelProviderConfig> getConfig() async {
    try {
      final result = await AssistsMessageService.assistCore
          .invokeMethod<Map<dynamic, dynamic>>('getModelProviderConfig');
      return ModelProviderConfig.fromMap(result);
    } on PlatformException {
      return ModelProviderConfig.empty();
    }
  }

  static Future<ModelProviderConfig> saveConfig({
    required String baseUrl,
    required String apiKey,
  }) async {
    final result = await AssistsMessageService.assistCore
        .invokeMethod<Map<dynamic, dynamic>>('saveModelProviderConfig', {
          'baseUrl': baseUrl,
          'apiKey': apiKey,
        });
    return ModelProviderConfig.fromMap(result);
  }

  static Future<ModelProviderConfig> clearConfig() async {
    final result = await AssistsMessageService.assistCore
        .invokeMethod<Map<dynamic, dynamic>>('clearModelProviderConfig');
    return ModelProviderConfig.fromMap(result);
  }

  static Future<List<ProviderModelOption>> fetchModels({
    String apiBase = '',
    String apiKey = '',
  }) async {
    final result = await AssistsMessageService.assistCore
        .invokeMethod<List<dynamic>>('fetchProviderModels', {
          'apiBase': apiBase,
          'apiKey': apiKey,
        });
    final models = (result ?? const [])
        .map((item) => ProviderModelOption.fromMap(item as Map?))
        .where((item) => item.id.isNotEmpty)
        .toList();

    try {
      var cacheBase = normalizeApiBase(apiBase) ?? '';
      if (cacheBase.isEmpty) {
        final config = await getConfig();
        cacheBase = config.baseUrl;
      }
      await _saveCachedFetchedModels(cacheBase, models);
    } catch (_) {
      // ignore cache write failures
    }

    return models;
  }

  static Future<List<ProviderModelOption>> getCachedFetchedModels({
    String apiBase = '',
  }) async {
    final raw = StorageService.getString(
      _kCachedFetchedModelsKey,
      defaultValue: '',
    );
    if (raw == null || raw.trim().isEmpty) {
      return const [];
    }

    final requestedBase = normalizeApiBase(apiBase) ?? '';
    try {
      final decoded = jsonDecode(raw);

      if (decoded is Map<String, dynamic>) {
        final cacheBase = (decoded['apiBase'] ?? '').toString();
        if (requestedBase.isNotEmpty && cacheBase != requestedBase) {
          return const [];
        }
        final modelsRaw = decoded['models'];
        if (modelsRaw is! List) {
          return const [];
        }
        return modelsRaw
            .map((item) => ProviderModelOption.fromMap(item as Map?))
            .where((item) => item.id.isNotEmpty)
            .toList();
      }

      if (decoded is List) {
        // Backward-compatible fallback if older cache shape exists.
        return decoded
            .map((item) => ProviderModelOption.fromMap(item as Map?))
            .where((item) => item.id.isNotEmpty)
            .toList();
      }
      return const [];
    } catch (_) {
      return const [];
    }
  }

  static Future<void> saveCachedFetchedModels({
    required String apiBase,
    required List<ProviderModelOption> models,
  }) async {
    await _saveCachedFetchedModels(apiBase, models);
  }

  static Future<void> _saveCachedFetchedModels(
    String apiBase,
    List<ProviderModelOption> models,
  ) async {
    final normalizedBase = normalizeApiBase(apiBase) ?? '';
    final payload = {
      'apiBase': normalizedBase,
      'models': models
          .map(
            (item) => {
              'id': item.id,
              'displayName': item.displayName,
              'ownedBy': item.ownedBy,
            },
          )
          .toList(),
    };
    await StorageService.setString(_kCachedFetchedModelsKey, jsonEncode(payload));
  }

  static Future<List<String>> getManualModelIds() async {
    final ids =
        StorageService.getStringList(_kManualModelIdsKey, defaultValue: []) ??
        [];
    return _normalizeModelIds(ids);
  }

  static Future<void> saveManualModelIds(List<String> ids) async {
    final normalized = _normalizeModelIds(ids);
    await StorageService.setStringList(_kManualModelIdsKey, normalized);
  }

  static List<ProviderModelOption> mergeModelOptions({
    required List<ProviderModelOption> remoteModels,
    required List<String> manualModelIds,
  }) {
    final merged = <ProviderModelOption>[];
    final seen = <String>{};

    for (final modelId in _normalizeModelIds(manualModelIds)) {
      if (seen.add(modelId)) {
        merged.add(
          ProviderModelOption(
            id: modelId,
            displayName: modelId,
            ownedBy: 'manual',
          ),
        );
      }
    }

    for (final item in remoteModels) {
      if (seen.add(item.id)) {
        merged.add(item);
      }
    }
    return merged;
  }

  static List<String> _normalizeModelIds(List<String> ids) {
    final result = <String>[];
    final seen = <String>{};
    for (final raw in ids) {
      final normalized = raw.trim();
      if (!isValidModelName(normalized)) {
        continue;
      }
      if (seen.add(normalized)) {
        result.add(normalized);
      }
    }
    return result;
  }

  static bool isValidApiBase(String value) {
    return normalizeApiBase(value) != null;
  }

  static String? normalizeApiBase(String value) {
    final normalized = value.trim();
    if (normalized.isEmpty) {
      return null;
    }

    final uri = Uri.tryParse(normalized);
    if (uri == null || !uri.hasScheme || !uri.hasAuthority) {
      return null;
    }
    if (uri.scheme != 'http' && uri.scheme != 'https') {
      return null;
    }

    var result = normalized.replaceAll(RegExp(r'/+$'), '');
    const suffixes = [
      '/v1/chat/completions',
      '/chat/completions',
      '/v1/models',
      '/models',
    ];
    for (final suffix in suffixes) {
      if (result.toLowerCase().endsWith(suffix)) {
        result = result.substring(0, result.length - suffix.length);
        break;
      }
    }
    return result.replaceAll(RegExp(r'/+$'), '');
  }

  static bool isValidModelName(String value) {
    final normalized = value.trim();
    return normalized.isNotEmpty && !normalized.startsWith('scene.');
  }
}
