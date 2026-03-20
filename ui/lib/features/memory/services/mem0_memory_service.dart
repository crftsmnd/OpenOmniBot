import 'dart:async';
import 'dart:convert';

import 'package:http/http.dart' as http;
import 'package:ui/features/memory/models/mem0_memory_item.dart';
import 'package:ui/services/cache_service.dart';
import 'package:ui/services/mem0_config_service.dart';

class _Mem0RequestContext {
  final String baseUrl;
  final String apiKey;
  final String userId;
  final String agentId;

  const _Mem0RequestContext({
    required this.baseUrl,
    required this.apiKey,
    required this.userId,
    required this.agentId,
  });
}

class Mem0MemoryService {
  static const Duration _cacheTtl = Duration(minutes: 2);
  static const Duration _readTimeout = Duration(seconds: 18);
  static const Duration _mutationTimeout = Duration(seconds: 90);
  static const String _cacheKey = 'mem0_memory_center_cache_v1';
  static const int _defaultLimit = 24;
  static const String _defaultAgentId = 'omnibot-unified-agent';

  static Future<Mem0MemorySnapshot>? _inFlightRequest;
  static String? _inFlightIdentity;

  static Future<Mem0MemorySnapshot> getMemories({
    bool forceRefresh = false,
    int limit = _defaultLimit,
  }) async {
    final context = await _tryResolveContext();
    if (context == null) {
      final config = await Mem0ConfigService.getConfig();
      if (!config.isConfigured) {
        return Mem0MemorySnapshot.unconfigured();
      }
      return const Mem0MemorySnapshot(
        configured: true,
        errorMessage: '云端记忆身份初始化失败，请稍后重试',
      );
    }

    final identity = _buildIdentity(context);
    final cached = await _readCache(identity);
    if (!forceRefresh &&
        cached != null &&
        cached.fetchedAt != null &&
        DateTime.now().difference(cached.fetchedAt!) < _cacheTtl) {
      return cached.copyWith(
        configured: true,
        fromCache: true,
        infoMessage: cached.hasData ? null : '云端记忆已同步',
      );
    }

    if (!forceRefresh &&
        _inFlightRequest != null &&
        _inFlightIdentity == identity) {
      return _inFlightRequest!;
    }

    final future = _fetchRemote(
      identity: identity,
      context: context,
      limit: limit,
      cached: cached,
    );

    _inFlightRequest = future;
    _inFlightIdentity = identity;

    try {
      return await future;
    } finally {
      if (_inFlightIdentity == identity) {
        _inFlightRequest = null;
        _inFlightIdentity = null;
      }
    }
  }

  static Future<void> createMemory({
    required String memory,
    List<String> categories = const [],
    Map<String, dynamic> metadata = const {},
  }) async {
    final context = await _resolveContext();
    await _createMemoryInternal(
      context: context,
      memory: memory,
      categories: categories,
      metadata: metadata,
    );
  }

  static Future<void> updateMemory({
    required String memoryId,
    required String memory,
    List<String> categories = const [],
    Map<String, dynamic> metadata = const {},
  }) async {
    final context = await _resolveContext();
    final trimmedId = memoryId.trim();
    if (trimmedId.isEmpty) {
      throw Exception('缺少记忆 ID');
    }
    final trimmedMemory = memory.trim();
    if (trimmedMemory.isEmpty) {
      throw Exception('记忆内容不能为空');
    }

    final mergedMetadata = _mergeMetadata(
      categories: categories,
      metadata: metadata,
    );

    final uri = Uri.parse('${context.baseUrl}/memories/$trimmedId').replace(
      queryParameters: {'user_id': context.userId, 'agent_id': context.agentId},
    );

    final response = await http
        .put(
          uri,
          headers: {
            'Authorization': 'Bearer ${context.apiKey}',
            'Accept': 'application/json',
            'Content-Type': 'application/json',
          },
          body: jsonEncode({
            'text': trimmedMemory,
            if (mergedMetadata.isNotEmpty) 'metadata': mergedMetadata,
          }),
        )
        .timeout(_mutationTimeout);

    if (response.statusCode >= 200 && response.statusCode < 300) {
      return;
    }

    if (_isDictReplaceError(response)) {
      await _createMemoryInternal(
        context: context,
        memory: trimmedMemory,
        categories: categories,
        metadata: metadata,
      );
      await _deleteMemoryInternal(
        context: context,
        memoryId: trimmedId,
        swallowError: true,
      );
      return;
    }

    throw Exception(_extractErrorMessage(response));
  }

  static Future<void> deleteMemory({required String memoryId}) async {
    final context = await _resolveContext();
    await _deleteMemoryInternal(
      context: context,
      memoryId: memoryId,
      swallowError: false,
    );
  }

  static Future<Mem0MemorySnapshot> _fetchRemote({
    required String identity,
    required _Mem0RequestContext context,
    required int limit,
    required Mem0MemorySnapshot? cached,
  }) async {
    final uri = Uri.parse('${context.baseUrl}/memories').replace(
      queryParameters: {
        'user_id': context.userId,
        'agent_id': context.agentId,
        'limit': '$limit',
      },
    );

    try {
      final response = await http
          .get(
            uri,
            headers: {
              'Authorization': 'Bearer ${context.apiKey}',
              'Accept': 'application/json',
            },
          )
          .timeout(_readTimeout);

      if (response.statusCode < 200 || response.statusCode >= 300) {
        throw Exception(_extractErrorMessage(response));
      }

      final decoded = jsonDecode(response.body);
      if (decoded is! Map) {
        throw Exception('云端记忆返回格式异常');
      }

      final snapshot = _parseSnapshot(Map<String, dynamic>.from(decoded));
      final now = DateTime.now();
      final freshSnapshot = snapshot.copyWith(
        configured: true,
        fetchedAt: now,
        fromCache: false,
        isStale: false,
        infoMessage: snapshot.items.isEmpty ? '云端记忆为空' : null,
        errorMessage: null,
      );

      await _writeCache(identity, freshSnapshot);
      return freshSnapshot;
    } on TimeoutException {
      if (cached != null) {
        return cached.copyWith(
          configured: true,
          fromCache: true,
          isStale: true,
          infoMessage: '云端同步超时，先展示上次缓存',
        );
      }
      return const Mem0MemorySnapshot(
        configured: true,
        errorMessage: '云端记忆同步超时',
      );
    } catch (e) {
      if (cached != null) {
        return cached.copyWith(
          configured: true,
          fromCache: true,
          isStale: true,
          infoMessage: '云端同步失败，先展示上次缓存',
          errorMessage: null,
        );
      }
      return Mem0MemorySnapshot(
        configured: true,
        errorMessage: e.toString().replaceFirst('Exception: ', ''),
      );
    }
  }

  static Mem0MemorySnapshot _parseSnapshot(Map<String, dynamic> json) {
    final rawItems = json['results'];
    final rawRelations = json['relations'];

    final items = rawItems is List
        ? rawItems
              .whereType<Map>()
              .map(
                (item) =>
                    Mem0MemoryItem.fromJson(Map<String, dynamic>.from(item)),
              )
              .where((item) => item.memory.trim().isNotEmpty)
              .toList()
        : <Mem0MemoryItem>[];

    final relations = rawRelations is List
        ? rawRelations
              .whereType<Map>()
              .map(
                (item) => Mem0MemoryRelation.fromJson(
                  Map<String, dynamic>.from(item),
                ),
              )
              .where((item) => item.source.isNotEmpty && item.target.isNotEmpty)
              .toList()
        : <Mem0MemoryRelation>[];

    items.sort((a, b) {
      final aTime = a.displayTime?.millisecondsSinceEpoch ?? 0;
      final bTime = b.displayTime?.millisecondsSinceEpoch ?? 0;
      return bTime.compareTo(aTime);
    });

    return Mem0MemorySnapshot(
      configured: true,
      items: items,
      relations: relations,
    );
  }

  static Future<_Mem0RequestContext> _resolveContext() async {
    final resolvedContext = await _tryResolveContext();
    if (resolvedContext != null) {
      return resolvedContext;
    }

    final config = await Mem0ConfigService.getConfig();
    if (!config.isConfigured) {
      throw Exception('请先在设置中配置 Mem0');
    }
    throw Exception('云端记忆身份初始化失败，请稍后重试');
  }

  static Future<_Mem0RequestContext?> _tryResolveContext() async {
    final config = await Mem0ConfigService.getResolvedConfig();
    if (config == null || !config.isConfigured) {
      return null;
    }

    final userId = config.userId?.trim();
    if (userId == null || userId.isEmpty) {
      return null;
    }

    final agentId = config.agentId.trim().isEmpty
        ? _defaultAgentId
        : config.agentId.trim();
    return _Mem0RequestContext(
      baseUrl: config.baseUrl,
      apiKey: config.apiKey,
      userId: userId,
      agentId: agentId,
    );
  }

  static String _buildIdentity(_Mem0RequestContext context) {
    return '${context.baseUrl}|${context.agentId}|${context.userId}';
  }

  static Future<Mem0MemoryItem?> _createMemoryInternal({
    required _Mem0RequestContext context,
    required String memory,
    required List<String> categories,
    required Map<String, dynamic> metadata,
  }) async {
    final trimmedMemory = memory.trim();
    if (trimmedMemory.isEmpty) {
      throw Exception('记忆内容不能为空');
    }
    final mergedMetadata = _mergeMetadata(
      categories: categories,
      metadata: metadata,
    );
    final uri = Uri.parse('${context.baseUrl}/memories');
    final response = await http
        .post(
          uri,
          headers: {
            'Authorization': 'Bearer ${context.apiKey}',
            'Accept': 'application/json',
            'Content-Type': 'application/json',
          },
          body: jsonEncode({
            'messages': [
              {'role': 'user', 'content': trimmedMemory},
            ],
            'user_id': context.userId,
            'agent_id': context.agentId,
            if (mergedMetadata.isNotEmpty) 'metadata': mergedMetadata,
          }),
        )
        .timeout(_mutationTimeout);
    if (response.statusCode < 200 || response.statusCode >= 300) {
      throw Exception(_extractErrorMessage(response));
    }
    return _extractFirstMemoryItem(response.body);
  }

  static Future<void> _deleteMemoryInternal({
    required _Mem0RequestContext context,
    required String memoryId,
    required bool swallowError,
  }) async {
    final trimmedId = memoryId.trim();
    if (trimmedId.isEmpty) {
      throw Exception('缺少记忆 ID');
    }
    final uri = Uri.parse('${context.baseUrl}/memories/$trimmedId').replace(
      queryParameters: {'user_id': context.userId, 'agent_id': context.agentId},
    );
    final response = await http
        .delete(
          uri,
          headers: {
            'Authorization': 'Bearer ${context.apiKey}',
            'Accept': 'application/json',
          },
        )
        .timeout(_mutationTimeout);
    if (response.statusCode >= 200 && response.statusCode < 300) {
      return;
    }
    if (swallowError) {
      return;
    }
    throw Exception(_extractErrorMessage(response));
  }

  static Mem0MemoryItem? _extractFirstMemoryItem(String body) {
    final decoded = _safeDecodeBody(body);
    final first = _extractFirstMapCandidate(decoded);
    if (first == null) {
      return null;
    }
    return Mem0MemoryItem.fromJson(first);
  }

  static Map<String, dynamic>? _extractFirstMapCandidate(dynamic payload) {
    if (payload is Map<String, dynamic>) {
      final candidateKeys = ['results', 'memories', 'items', 'data'];
      for (final key in candidateKeys) {
        final nested = payload[key];
        if (nested is List) {
          for (final item in nested) {
            if (item is Map) {
              return Map<String, dynamic>.from(item);
            }
          }
        }
      }
      if (payload.containsKey('id') || payload.containsKey('memory')) {
        return payload;
      }
      return null;
    }
    if (payload is List) {
      for (final item in payload) {
        if (item is Map) {
          return Map<String, dynamic>.from(item);
        }
      }
    }
    return null;
  }

  static dynamic _safeDecodeBody(String body) {
    try {
      return jsonDecode(body);
    } catch (_) {
      return null;
    }
  }

  static bool _isDictReplaceError(http.Response response) {
    final merged = '${response.body} ${response.reasonPhrase ?? ''}'
        .toLowerCase();
    return merged.contains('dict') && merged.contains('replace');
  }

  static Map<String, dynamic> _mergeMetadata({
    required List<String> categories,
    required Map<String, dynamic> metadata,
  }) {
    final merged = <String, dynamic>{};
    metadata.forEach((key, value) {
      if (value != null) {
        merged[key] = value;
      }
    });
    final cleanedCategories = categories
        .map((item) => item.trim())
        .where((item) => item.isNotEmpty)
        .toSet()
        .toList();
    if (cleanedCategories.isNotEmpty) {
      merged['categories'] = cleanedCategories;
    }
    return merged;
  }

  static Future<Mem0MemorySnapshot?> _readCache(String identity) async {
    final cached = await CacheService.getJson<Map<String, dynamic>>(_cacheKey);
    if (cached == null) {
      return null;
    }

    if ((cached['identity'] ?? '').toString() != identity) {
      return null;
    }

    final payload = cached['payload'];
    if (payload is! Map) {
      return null;
    }

    return Mem0MemorySnapshot.fromCacheJson(Map<String, dynamic>.from(payload));
  }

  static Future<void> _writeCache(
    String identity,
    Mem0MemorySnapshot snapshot,
  ) async {
    await CacheService.setJson(_cacheKey, {
      'identity': identity,
      'payload': snapshot.toCacheJson(),
    });
  }

  static String _extractErrorMessage(http.Response response) {
    try {
      final decoded = jsonDecode(response.body);
      if (decoded is Map && decoded['detail'] != null) {
        return decoded['detail'].toString();
      }
    } catch (_) {}
    return '请求失败（${response.statusCode}）';
  }
}
