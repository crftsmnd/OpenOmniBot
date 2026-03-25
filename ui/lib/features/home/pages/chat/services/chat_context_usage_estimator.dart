import 'dart:convert';

import 'package:ui/models/chat_message_model.dart';
import 'package:ui/services/assists_core_service.dart';

const int _kAgentSystemPromptReserveTokens = 1200;
const int _kAgentRuntimeEnvelopeReserveTokens = 320;

AgentContextUsageData resolveAgentContextUsage({
  required int contextWindow,
  AgentContextUsageData? runtimeUsage,
  required List<ChatMessageModel> visibleMessages,
  String draftText = '',
}) {
  final normalizedContextWindow =
      (runtimeUsage?.contextWindow ?? contextWindow) <= 0
      ? 128000
      : (runtimeUsage?.contextWindow ?? contextWindow);
  final estimatedUsedTokens = estimateAgentContextTokens(
    visibleMessages: visibleMessages,
    draftText: draftText,
  );
  final liveUsedTokens = runtimeUsage?.usedTokens ?? 0;
  final resolvedUsedTokens = liveUsedTokens > estimatedUsedTokens
      ? liveUsedTokens
      : estimatedUsedTokens;
  final utilization = normalizedContextWindow <= 0
      ? 0.0
      : (resolvedUsedTokens / normalizedContextWindow).clamp(0.0, 1.0);

  return AgentContextUsageData(
    usedTokens: resolvedUsedTokens,
    contextWindow: normalizedContextWindow,
    utilization: utilization,
    compressionCount: runtimeUsage?.compressionCount ?? 0,
    lastCompressedAt: runtimeUsage?.lastCompressedAt,
  );
}

int estimateAgentContextTokens({
  required List<ChatMessageModel> visibleMessages,
  String draftText = '',
}) {
  final messageTokens = visibleMessages.fold<int>(
    0,
    (sum, message) => sum + _estimateValueTokens(message.content),
  );
  final draftTokens = _estimateValueTokens(draftText);
  if (messageTokens == 0 && draftTokens == 0) {
    return 0;
  }
  return _kAgentSystemPromptReserveTokens +
      _kAgentRuntimeEnvelopeReserveTokens +
      messageTokens +
      draftTokens;
}

int _estimateValueTokens(Object? value) {
  if (value == null) return 0;
  final serialized = switch (value) {
    String text => text.trim(),
    _ => jsonEncode(value),
  };
  if (serialized.isEmpty) return 0;
  final length = serialized.length;
  return ((length + 3) ~/ 4) + 12;
}
