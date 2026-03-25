import 'package:flutter_test/flutter_test.dart';
import 'package:ui/features/home/pages/chat/services/chat_context_usage_estimator.dart';
import 'package:ui/models/chat_message_model.dart';
import 'package:ui/services/assists_core_service.dart';

void main() {
  test('returns zero when there is no visible context', () {
    final usage = resolveAgentContextUsage(
      contextWindow: 128000,
      visibleMessages: const <ChatMessageModel>[],
    );

    expect(usage.usedTokens, 0);
    expect(usage.utilization, 0);
  });

  test('falls back to local estimation when runtime usage is missing', () {
    final usage = resolveAgentContextUsage(
      contextWindow: 128000,
      visibleMessages: <ChatMessageModel>[
        ChatMessageModel.userMessage('帮我整理一下这个仓库的 agent 结构'),
      ],
      draftText: '顺便检查一下上下文压缩逻辑',
    );

    expect(usage.usedTokens, greaterThan(0));
    expect(usage.utilization, greaterThan(0));
  });

  test('keeps larger runtime usage and preserves compression metadata', () {
    final usage = resolveAgentContextUsage(
      contextWindow: 128000,
      runtimeUsage: const AgentContextUsageData(
        usedTokens: 4096,
        contextWindow: 200000,
        utilization: 0.02,
        compressionCount: 2,
        lastCompressedAt: 123456,
      ),
      visibleMessages: <ChatMessageModel>[
        ChatMessageModel.userMessage('hello'),
      ],
    );

    expect(usage.usedTokens, 4096);
    expect(usage.contextWindow, 200000);
    expect(usage.compressionCount, 2);
    expect(usage.lastCompressedAt, 123456);
  });
}
