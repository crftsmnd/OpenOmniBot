import 'package:flutter/material.dart';
import 'artifact_card.dart';
import 'agent_tool_summary_card.dart';
import 'deep_thinking_card.dart';
import 'executable_task_card.dart';
import 'permission_button_card.dart';
import 'permission_section_card.dart';
import 'stage_hint_card.dart';
import 'openclaw_attachment_card.dart';

/// 任务执行前的回调类型
typedef OnBeforeTaskExecute = Future<void> Function();
typedef OnRequestAuthorize = void Function(List<String> requiredPermissionIds);

/// 卡片组件工厂
///
/// 根据卡片类型返回对应的Widget
/// 支持扩展新的卡片类型
class CardWidgetFactory {
  static Widget createCard(
    Map<String, dynamic> cardData, {
    OnBeforeTaskExecute? onBeforeTaskExecute,
    OnRequestAuthorize? onRequestAuthorize,
    void Function(String taskId)? onCancelTask,
    bool enableThinkingCollapse = false,
    ScrollController? parentScrollController,
  }) {
    final type = cardData['type'] as String? ?? 'unknown';

    switch (type) {
      case 'executable_task':
        return ExecutableTaskCard(
          cardData: cardData,
          onBeforeTaskExecute: onBeforeTaskExecute,
        );
      case 'permission_button':
        return PermissionButtonCard(cardData: cardData);
      case 'deep_thinking':
        final isLoading = cardData['isLoading'] as bool? ?? true;
        final thinkingText = cardData['thinkingContent'] as String? ?? '';
        final stage = cardData['stage'] as int? ?? 1;
        final taskID = cardData['taskID'] as String?;
        final startTime = cardData['startTime'] as int?;
        final endTime = cardData['endTime'] as int?;
        final isExecutable = cardData['isExecutable'] as bool? ?? false;
        final isCollapsible =
            cardData['isCollapsible'] as bool? ?? enableThinkingCollapse;
        // 使用 taskID 作为 key，确保同一个 task 使用同一个 Widget 实例
        final key = taskID != null ? ValueKey('deep_thinking_$taskID') : null;
        return DeepThinkingCard(
          key: key,
          isLoading: isLoading,
          thinkingText: thinkingText,
          stage: stage,
          startTime: startTime,
          endTime: endTime,
          taskId: taskID,
          onCancelTask: onCancelTask,
          isExecutable: isExecutable,
          isCollapsible: isCollapsible,
          parentScrollController: parentScrollController,
        );
      case 'stage_hint':
        final hint = cardData['hint'] as String? ?? '';
        final startTime = cardData['startTime'] as int?;
        return StageHintCard(
          hint: hint,
          startTime: startTime != null
              ? DateTime.fromMillisecondsSinceEpoch(startTime)
              : null,
        );
      case 'openclaw_attachment':
        final attachment =
            cardData['attachment'] as Map<String, dynamic>? ?? {};
        return OpenClawAttachmentCard(attachment: attachment);
      case 'permission_section':
        return PermissionSectionCard(
          cardData: cardData,
          onRequestAuthorize: onRequestAuthorize,
        );
      case 'agent_tool_summary':
        return AgentToolSummaryCard(
          cardData: cardData,
          parentScrollController: parentScrollController,
        );
      case 'artifact_card':
        final artifact = cardData['artifact'] as Map<String, dynamic>? ?? {};
        return ArtifactCard(artifact: artifact);
      default:
        return _UnknownCard(type: type);
    }
  }
}

/// 未知类型卡片
///
/// 当卡片类型不被识别时显示的默认组件
class _UnknownCard extends StatelessWidget {
  final String type;

  const _UnknownCard({required this.type});

  @override
  Widget build(BuildContext context) {
    return Container(
      padding: const EdgeInsets.all(24),
      decoration: BoxDecoration(
        // color: Colors.grey[100],
        borderRadius: BorderRadius.circular(12),
        border: Border.all(color: Colors.grey[300]!),
      ),
      child: Column(
        children: [
          Icon(Icons.help_outline, size: 40, color: Colors.grey[400]),
          const SizedBox(height: 8),
          Text('未知卡片类型：$type', style: TextStyle(color: Colors.grey[600])),
        ],
      ),
    );
  }
}
