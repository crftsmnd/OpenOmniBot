import 'dart:convert';
import 'package:shared_preferences/shared_preferences.dart';
import 'package:ui/models/chat_message_model.dart';
import 'package:ui/models/conversation_model.dart';

/// 对话历史持久化服务
class ConversationHistoryService {
  static const String _legacyConversationIdKey = 'current_conversation_id';
  static const String _conversationIdKeyPrefix = 'current_conversation_id_';
  static const String _conversationMessagesKey = 'conversation_messages_';
  static const String conversationMessagesKeyPrefix = _conversationMessagesKey;

  static String _conversationIdKeyForMode(String? mode) {
    final normalizedMode = normalizeConversationMode(mode);
    return '$_conversationIdKeyPrefix$normalizedMode';
  }

  /// 保存某个模式下的当前对话ID
  static Future<void> saveCurrentConversationId(
    int? conversationId, {
    String? mode,
  }) async {
    final prefs = await SharedPreferences.getInstance();
    final key = _conversationIdKeyForMode(mode);
    if (conversationId == null) {
      await prefs.remove(key);
    } else {
      await prefs.setInt(key, conversationId);
    }
  }

  /// 获取某个模式下的当前对话ID
  static Future<int?> getCurrentConversationId({String? mode}) async {
    final prefs = await SharedPreferences.getInstance();
    final id = prefs.getInt(_conversationIdKeyForMode(mode));
    return id == 0 ? null : id;
  }

  /// 保存最后一次活跃的对话ID，用于原生任务完成后的兜底回跳
  static Future<void> saveLastActiveConversationId(int? conversationId) async {
    final prefs = await SharedPreferences.getInstance();
    if (conversationId == null) {
      await prefs.remove(_legacyConversationIdKey);
    } else {
      await prefs.setInt(_legacyConversationIdKey, conversationId);
    }
  }

  /// 获取最后一次活跃的对话ID
  static Future<int?> getLastActiveConversationId() async {
    final prefs = await SharedPreferences.getInstance();
    final id = prefs.getInt(_legacyConversationIdKey);
    return id == 0 ? null : id;
  }

  /// 清除所有模式下的当前对话ID，以及原生回跳使用的最后活跃对话ID
  static Future<void> clearAllCurrentConversationIds() async {
    final prefs = await SharedPreferences.getInstance();
    await prefs.remove(_legacyConversationIdKey);
    await prefs.remove(_conversationIdKeyForMode(kConversationModeNormal));
    await prefs.remove(_conversationIdKeyForMode(kConversationModeOpenClaw));
  }

  /// 重新加载本地存储（用于多引擎/跨隔离同步）
  static Future<void> reloadLocalCache() async {
    try {
      final prefs = await SharedPreferences.getInstance();
      await prefs.reload();
    } catch (e) {
      print('刷新本地缓存失败: $e');
    }
  }

  /// 保存对话消息列表
  static Future<void> saveConversationMessages(
    int conversationId,
    List<ChatMessageModel> messages,
  ) async {
    final prefs = await SharedPreferences.getInstance();
    final jsonList = messages.map((m) => m.toJson()).toList();
    await prefs.setString('$_conversationMessagesKey$conversationId', jsonEncode(jsonList));
  }

  /// 获取对话消息列表
  static Future<List<ChatMessageModel>> getConversationMessages(
    int conversationId,
  ) async {
    final prefs = await SharedPreferences.getInstance();
    final jsonStr = prefs.getString('$_conversationMessagesKey$conversationId');
    if (jsonStr == null) return [];

    try {
      final jsonList = jsonDecode(jsonStr) as List;
      return jsonList.map((json) => ChatMessageModel.fromJson(json)).toList();
    } catch (e) {
      print('解析对话历史失败: $e');
      return [];
    }
  }

  /// 清除对话消息
  static Future<void> clearConversationMessages(int conversationId) async {
    final prefs = await SharedPreferences.getInstance();
    await prefs.remove('$_conversationMessagesKey$conversationId');
  }
}
