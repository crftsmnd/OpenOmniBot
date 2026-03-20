import 'dart:convert';
import 'package:shared_preferences/shared_preferences.dart';
import 'package:ui/models/chat_message_model.dart';

/// 对话历史持久化服务
class ConversationHistoryService {
  static const String _conversationIdKey = 'current_conversation_id';
  static const String _conversationMessagesKey = 'conversation_messages_';
  static const String conversationMessagesKeyPrefix = _conversationMessagesKey;

  /// 保存当前对话ID
  static Future<void> saveCurrentConversationId(int? conversationId) async {
    final prefs = await SharedPreferences.getInstance();
    if (conversationId == null) {
      await prefs.remove(_conversationIdKey);
    } else {
      await prefs.setInt(_conversationIdKey, conversationId);
    }
  }

  /// 获取当前对话ID
  static Future<int?> getCurrentConversationId() async {
    final prefs = await SharedPreferences.getInstance();
    final id = prefs.getInt(_conversationIdKey);
    return id == 0 ? null : id;
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
