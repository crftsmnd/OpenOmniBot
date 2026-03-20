import 'package:flutter/services.dart';
import 'package:ui/services/assists_core_service.dart';

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
    return (result ?? const [])
        .map((item) => ProviderModelOption.fromMap(item as Map?))
        .where((item) => item.id.isNotEmpty)
        .toList();
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
