import 'dart:convert';

import 'package:ui/models/chat_message_model.dart';

typedef AgentUserContentBuilder = dynamic Function(ChatMessageModel message);

class AgentConversationHistoryBuilder {
  static const int _defaultMaxCount = 10;
  static const int _maxPreviewJsonChars = 3000;
  static const int _maxRawResultJsonChars = 3000;
  static const int _maxTerminalOutputChars = 2000;

  static List<Map<String, dynamic>> build(
    List<ChatMessageModel> messages, {
    int maxCount = _defaultMaxCount,
    AgentUserContentBuilder? userContentBuilder,
  }) {
    final history = <Map<String, dynamic>>[];
    final recentMessages = _recentMessagesForHistory(messages, maxCount: maxCount);

    for (final message in recentMessages) {
      if (message.user == 1) {
        final content = (userContentBuilder ?? _defaultUserContentBuilder)(message);
        if (_hasModelContent(content)) {
          history.insert(0, {'role': 'user', 'content': content});
        }
        continue;
      }

      if (message.user == 2) {
        final text = message.text?.trim() ?? '';
        if (text.isNotEmpty) {
          history.insert(0, {'role': 'assistant', 'content': text});
        }
        continue;
      }

      final toolEntries = _buildToolHistoryEntries(message);
      for (final entry in toolEntries.reversed) {
        history.insert(0, entry);
      }
    }

    return history;
  }

  static List<ChatMessageModel> _recentMessagesForHistory(
    List<ChatMessageModel> messages, {
    required int maxCount,
  }) {
    final errorMessageIds = messages
        .where((msg) => msg.isError)
        .map((msg) => msg.id.replaceAll('-ai', '-user'))
        .toSet();

    final filtered = messages.where((msg) {
      if (msg.isLoading) return false;
      if (msg.type == 1) {
        if (msg.isError) return false;
        if (errorMessageIds.contains(msg.id)) return false;
        return msg.user == 1 || msg.user == 2;
      }
      return _isPersistableToolCardMessage(msg);
    }).toList();

    return filtered.length > maxCount ? filtered.sublist(0, maxCount) : filtered;
  }

  static dynamic _defaultUserContentBuilder(ChatMessageModel message) {
    return message.text?.trim() ?? '';
  }

  static bool _hasModelContent(dynamic content) {
    if (content is String) {
      return content.trim().isNotEmpty;
    }
    if (content is List) {
      return content.isNotEmpty;
    }
    return false;
  }

  static bool _isPersistableToolCardMessage(ChatMessageModel message) {
    if (message.type != 2) return false;
    final cardData = message.cardData;
    if (cardData == null) return false;
    if ((cardData['type'] ?? '').toString() != 'agent_tool_summary') return false;

    final status = (cardData['status'] ?? '').toString().trim().toLowerCase();
    if (status == 'running') return false;

    final toolName = (cardData['toolName'] ?? '').toString().trim();
    if (toolName.isEmpty) return false;

    final hasResult = (cardData['summary'] ?? '').toString().trim().isNotEmpty ||
        (cardData['resultPreviewJson'] ?? '').toString().trim().isNotEmpty ||
        (cardData['rawResultJson'] ?? '').toString().trim().isNotEmpty;
    return hasResult;
  }

  static List<Map<String, dynamic>> _buildToolHistoryEntries(
    ChatMessageModel message,
  ) {
    if (!_isPersistableToolCardMessage(message)) {
      return const [];
    }

    final cardData = Map<String, dynamic>.from(message.cardData ?? const {});
    final toolName = (cardData['toolName'] ?? '').toString().trim();
    if (toolName.isEmpty) return const [];

    final toolCallId = _resolveToolCallId(message, cardData);
    final argsJson = _normalizeArgumentsJson(
      (cardData['argsJson'] ?? cardData['args'] ?? '').toString(),
    );

    final assistantMessage = <String, dynamic>{
      'role': 'assistant',
      'content': '',
      'tool_calls': [
        {
          'id': toolCallId,
          'type': 'function',
          'function': {
            'name': toolName,
            'arguments': argsJson,
          },
        },
      ],
    };

    final toolPayload = <String, dynamic>{
      'toolName': toolName,
      'toolType': (cardData['toolType'] ?? 'builtin').toString(),
      'success': cardData['success'] != false,
      'summary': (cardData['summary'] ?? '').toString(),
      'status': (cardData['status'] ?? '').toString(),
    };

    final serverName = (cardData['serverName'] ?? '').toString().trim();
    if (serverName.isNotEmpty) {
      toolPayload['serverName'] = serverName;
    }

    final resultPreviewJson =
        _truncate((cardData['resultPreviewJson'] ?? '').toString(), _maxPreviewJsonChars);
    if (resultPreviewJson.isNotEmpty) {
      toolPayload['previewJson'] = resultPreviewJson;
    }

    final rawResultJson =
        _truncate((cardData['rawResultJson'] ?? '').toString(), _maxRawResultJsonChars);
    if (rawResultJson.isNotEmpty && rawResultJson != resultPreviewJson) {
      toolPayload['rawResultJson'] = rawResultJson;
    }

    final terminalOutput =
        _truncate((cardData['terminalOutput'] ?? '').toString(), _maxTerminalOutputChars);
    if (terminalOutput.isNotEmpty) {
      toolPayload['terminalOutput'] = terminalOutput;
    }

    final terminalSessionId = (cardData['terminalSessionId'] ?? '').toString().trim();
    if (terminalSessionId.isNotEmpty) {
      toolPayload['terminalSessionId'] = terminalSessionId;
    }

    final terminalStreamState =
        (cardData['terminalStreamState'] ?? '').toString().trim();
    if (terminalStreamState.isNotEmpty) {
      toolPayload['terminalStreamState'] = terminalStreamState;
    }

    final workspaceId = (cardData['workspaceId'] ?? '').toString().trim();
    if (workspaceId.isNotEmpty) {
      toolPayload['workspaceId'] = workspaceId;
    }

    final artifacts = ((cardData['artifacts'] as List?) ?? const [])
        .whereType<Map>()
        .map((item) => item.map((k, v) => MapEntry(k.toString(), v)))
        .toList();
    if (artifacts.isNotEmpty) {
      toolPayload['artifacts'] = artifacts;
    }

    final actions = ((cardData['actions'] as List?) ?? const [])
        .whereType<Map>()
        .map((item) => item.map((k, v) => MapEntry(k.toString(), v)))
        .toList();
    if (actions.isNotEmpty) {
      toolPayload['actions'] = actions;
    }

    return <Map<String, dynamic>>[
      assistantMessage,
      <String, dynamic>{
        'role': 'tool',
        'tool_call_id': toolCallId,
        'content': jsonEncode(toolPayload),
      },
    ];
  }

  static String _resolveToolCallId(
    ChatMessageModel message,
    Map<String, dynamic> cardData,
  ) {
    final explicit = (cardData['toolCallId'] ?? '').toString().trim();
    if (explicit.isNotEmpty) return explicit;
    return 'history_tool_${message.id}';
  }

  static String _normalizeArgumentsJson(String raw) {
    final trimmed = raw.trim();
    if (trimmed.isEmpty) return '{}';
    try {
      final decoded = jsonDecode(trimmed);
      if (decoded is Map<String, dynamic>) {
        return trimmed;
      }
      if (decoded is Map) {
        return jsonEncode(decoded);
      }
    } catch (_) {
      // Ignore invalid JSON and fall back to an empty object.
    }
    return '{}';
  }

  static String _truncate(String raw, int maxChars) {
    if (raw.length <= maxChars) return raw;
    return '${raw.substring(0, maxChars)}\n...(truncated)';
  }
}
