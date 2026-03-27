import 'package:flutter/material.dart';
import 'package:ui/features/home/pages/chat/tool_activity_utils.dart';
import 'package:ui/features/home/pages/command_overlay/widgets/cards/terminal_output_utils.dart';
import 'package:ui/models/chat_message_model.dart';
import 'package:ui/theme/app_colors.dart';

const ValueKey<String> kChatToolActivityBarKey = ValueKey<String>(
  'chat-tool-activity-bar',
);
const ValueKey<String> kChatToolActivityPanelKey = ValueKey<String>(
  'chat-tool-activity-panel',
);
const ValueKey<String> kChatToolActivityPreviewKey = ValueKey<String>(
  'chat-tool-activity-preview',
);
const ValueKey<String> kChatToolActivityToggleKey = ValueKey<String>(
  'chat-tool-activity-toggle',
);

class ChatToolActivityStrip extends StatefulWidget {
  const ChatToolActivityStrip({super.key, required this.messages});

  final List<ChatMessageModel> messages;

  @override
  State<ChatToolActivityStrip> createState() => _ChatToolActivityStripState();
}

class _ChatToolActivityStripState extends State<ChatToolActivityStrip>
    with TickerProviderStateMixin {
  bool _expanded = false;

  @override
  Widget build(BuildContext context) {
    final cards = extractAgentToolCards(widget.messages);
    final activeCard = resolveActiveAgentToolCard(cards);
    if (activeCard == null) {
      return const SizedBox.shrink();
    }

    final transcript = buildAgentToolTranscript(cards);
    final title = resolveAgentToolTitle(activeCard);
    final statusLabel = resolveAgentToolStatusLabel(activeCard);

    return FractionallySizedBox(
      widthFactor: 0.88,
      alignment: Alignment.centerLeft,
      child: AnimatedSize(
        duration: const Duration(milliseconds: 220),
        curve: Curves.easeOutCubic,
        alignment: Alignment.bottomLeft,
        child: Column(
          mainAxisSize: MainAxisSize.min,
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            if (_expanded)
              Container(
                key: kChatToolActivityPanelKey,
                constraints: const BoxConstraints(maxHeight: 240),
                padding: const EdgeInsets.fromLTRB(10, 10, 10, 8),
                decoration: BoxDecoration(
                  color: Colors.white.withValues(alpha: 0.95),
                  borderRadius: const BorderRadius.only(
                    topLeft: Radius.circular(20),
                    topRight: Radius.circular(20),
                    bottomLeft: Radius.circular(14),
                    bottomRight: Radius.circular(14),
                  ),
                  border: Border.all(color: const Color(0xFFE0E8F7)),
                  boxShadow: [
                    BoxShadow(
                      color: const Color(0x1F23446E).withValues(alpha: 0.12),
                      blurRadius: 18,
                      offset: const Offset(0, 10),
                    ),
                  ],
                ),
                child: _ToolHistoryList(cards: cards),
              ),
            Container(
              key: kChatToolActivityBarKey,
              height: 42,
              padding: const EdgeInsets.fromLTRB(6, 4, 6, 4),
              decoration: BoxDecoration(
                gradient: const LinearGradient(
                  begin: Alignment.topLeft,
                  end: Alignment.bottomRight,
                  colors: [Color(0xFFF8FBFF), Color(0xFFEFF5FF)],
                ),
                borderRadius: BorderRadius.only(
                  topLeft: Radius.circular(_expanded ? 12 : 18),
                  topRight: const Radius.circular(18),
                  bottomLeft: const Radius.circular(10),
                  bottomRight: const Radius.circular(18),
                ),
                border: Border.all(color: const Color(0xFFD6E4FC)),
                boxShadow: [
                  BoxShadow(
                    color: const Color(0x1A1D4ED8).withValues(alpha: 0.09),
                    blurRadius: 14,
                    offset: const Offset(0, 6),
                  ),
                ],
              ),
              child: Row(
                children: [
                  _TerminalThumbnail(
                    key: kChatToolActivityPreviewKey,
                    transcript: transcript,
                    onTap: () => _openTranscriptDialog(
                      context,
                      transcript: transcript,
                      title: title,
                    ),
                  ),
                  const SizedBox(width: 10),
                  Expanded(
                    child: Column(
                      mainAxisAlignment: MainAxisAlignment.center,
                      crossAxisAlignment: CrossAxisAlignment.start,
                      children: [
                        Text(
                          title,
                          maxLines: 1,
                          overflow: TextOverflow.ellipsis,
                          style: const TextStyle(
                            color: AppColors.text,
                            fontSize: 12,
                            fontWeight: FontWeight.w600,
                            height: 1.1,
                          ),
                        ),
                        const SizedBox(height: 2),
                        Text(
                          '${cards.length} 次工具调用 · $statusLabel',
                          maxLines: 1,
                          overflow: TextOverflow.ellipsis,
                          style: const TextStyle(
                            color: AppColors.text50,
                            fontSize: 10.5,
                            fontWeight: FontWeight.w500,
                            height: 1.1,
                          ),
                        ),
                      ],
                    ),
                  ),
                  const SizedBox(width: 6),
                  Material(
                    color: Colors.transparent,
                    child: InkWell(
                      key: kChatToolActivityToggleKey,
                      onTap: () => setState(() => _expanded = !_expanded),
                      borderRadius: BorderRadius.circular(999),
                      child: Container(
                        width: 28,
                        height: 28,
                        decoration: BoxDecoration(
                          color: const Color(0xFFEDF3FF),
                          borderRadius: BorderRadius.circular(999),
                        ),
                        child: AnimatedRotation(
                          turns: _expanded ? 0.5 : 0,
                          duration: const Duration(milliseconds: 220),
                          curve: Curves.easeOutCubic,
                          child: const Icon(
                            Icons.keyboard_arrow_up_rounded,
                            size: 18,
                            color: Color(0xFF4F6485),
                          ),
                        ),
                      ),
                    ),
                  ),
                ],
              ),
            ),
          ],
        ),
      ),
    );
  }

  Future<void> _openTranscriptDialog(
    BuildContext context, {
    required String transcript,
    required String title,
  }) {
    final displayText = transcript.trim().isEmpty ? '\$ 暂无工具调用记录' : transcript;
    return showDialog<void>(
      context: context,
      useRootNavigator: false,
      builder: (dialogContext) {
        return Dialog(
          elevation: 0,
          backgroundColor: Colors.transparent,
          insetPadding: const EdgeInsets.symmetric(
            horizontal: 20,
            vertical: 30,
          ),
          child: Container(
            constraints: BoxConstraints(
              maxHeight: MediaQuery.of(dialogContext).size.height * 0.72,
            ),
            decoration: BoxDecoration(
              color: const Color(0xFF0C1220),
              borderRadius: BorderRadius.circular(24),
              border: Border.all(color: const Color(0xFF1E314F)),
              boxShadow: const [
                BoxShadow(
                  color: Color(0x6610182B),
                  blurRadius: 28,
                  offset: Offset(0, 18),
                ),
              ],
            ),
            child: Column(
              mainAxisSize: MainAxisSize.max,
              children: [
                Padding(
                  padding: const EdgeInsets.fromLTRB(18, 16, 10, 12),
                  child: Row(
                    children: [
                      Container(
                        width: 10,
                        height: 10,
                        decoration: const BoxDecoration(
                          color: Color(0xFF3FD08B),
                          shape: BoxShape.circle,
                        ),
                      ),
                      const SizedBox(width: 10),
                      Expanded(
                        child: Text(
                          title,
                          maxLines: 1,
                          overflow: TextOverflow.ellipsis,
                          style: const TextStyle(
                            color: Color(0xFFF2F7FF),
                            fontSize: 14,
                            fontWeight: FontWeight.w600,
                          ),
                        ),
                      ),
                      IconButton(
                        onPressed: () => Navigator.of(dialogContext).pop(),
                        icon: const Icon(
                          Icons.close_rounded,
                          color: Color(0xFF9FB0C8),
                        ),
                      ),
                    ],
                  ),
                ),
                Expanded(
                  child: SingleChildScrollView(
                    padding: const EdgeInsets.fromLTRB(18, 0, 18, 18),
                    child: SelectableText.rich(
                      AnsiTextSpanBuilder.build(
                        displayText,
                        const TextStyle(
                          color: Color(0xFFCBE3CF),
                          fontSize: 12,
                          fontFamily: 'monospace',
                          height: 1.45,
                        ),
                      ),
                    ),
                  ),
                ),
              ],
            ),
          ),
        );
      },
    );
  }
}

class _TerminalThumbnail extends StatelessWidget {
  const _TerminalThumbnail({
    super.key,
    required this.transcript,
    required this.onTap,
  });

  final String transcript;
  final VoidCallback onTap;

  @override
  Widget build(BuildContext context) {
    final preview = _thumbnailText(transcript);
    return Material(
      color: Colors.transparent,
      child: InkWell(
        onTap: onTap,
        borderRadius: BorderRadius.circular(12),
        child: Ink(
          width: 54,
          height: 30,
          padding: const EdgeInsets.symmetric(horizontal: 6, vertical: 4),
          decoration: BoxDecoration(
            color: const Color(0xFF0D1524),
            borderRadius: BorderRadius.circular(12),
            border: Border.all(color: const Color(0xFF213451)),
          ),
          child: Align(
            alignment: Alignment.topLeft,
            child: Text(
              preview,
              maxLines: 3,
              overflow: TextOverflow.clip,
              style: const TextStyle(
                color: Color(0xFF9FFFC5),
                fontSize: 5.8,
                height: 1.05,
                fontFamily: 'monospace',
              ),
            ),
          ),
        ),
      ),
    );
  }

  String _thumbnailText(String value) {
    final text = value.trim();
    if (text.isEmpty) {
      return '\$ idle';
    }
    final lines = text
        .split('\n')
        .map((line) => line.trimRight())
        .where((line) => line.isNotEmpty)
        .toList(growable: false);
    if (lines.isEmpty) {
      return '\$ idle';
    }
    final previewLines = lines.length > 3
        ? lines.sublist(lines.length - 3)
        : lines;
    return previewLines.join('\n');
  }
}

class _ToolHistoryList extends StatelessWidget {
  const _ToolHistoryList({required this.cards});

  final List<Map<String, dynamic>> cards;

  @override
  Widget build(BuildContext context) {
    return ListView.separated(
      reverse: true,
      shrinkWrap: true,
      padding: EdgeInsets.zero,
      itemCount: cards.length,
      separatorBuilder: (_, __) => const SizedBox(height: 8),
      itemBuilder: (context, index) {
        final card = cards[index];
        final status = (card['status'] ?? 'running').toString();
        final statusColor = switch (status) {
          'success' => const Color(0xFF2F8F4E),
          'error' => AppColors.alertRed,
          'interrupted' => const Color(0xFFFFAA2C),
          _ => const Color(0xFF2C7FEB),
        };

        return Container(
          padding: const EdgeInsets.fromLTRB(12, 10, 12, 10),
          decoration: BoxDecoration(
            color: statusColor.withValues(alpha: 0.07),
            borderRadius: BorderRadius.circular(16),
            border: Border.all(color: statusColor.withValues(alpha: 0.18)),
          ),
          child: Row(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              Container(
                width: 8,
                height: 8,
                margin: const EdgeInsets.only(top: 5),
                decoration: BoxDecoration(
                  color: statusColor,
                  shape: BoxShape.circle,
                ),
              ),
              const SizedBox(width: 10),
              Expanded(
                child: Column(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: [
                    Text(
                      resolveAgentToolTitle(card),
                      maxLines: 1,
                      overflow: TextOverflow.ellipsis,
                      style: const TextStyle(
                        color: AppColors.text,
                        fontSize: 12,
                        fontWeight: FontWeight.w600,
                        height: 1.2,
                      ),
                    ),
                    const SizedBox(height: 4),
                    Text(
                      resolveAgentToolPreview(card),
                      maxLines: 2,
                      overflow: TextOverflow.ellipsis,
                      style: const TextStyle(
                        color: AppColors.text50,
                        fontSize: 11,
                        height: 1.3,
                      ),
                    ),
                  ],
                ),
              ),
              const SizedBox(width: 10),
              Container(
                padding: const EdgeInsets.symmetric(horizontal: 8, vertical: 4),
                decoration: BoxDecoration(
                  color: Colors.white.withValues(alpha: 0.7),
                  borderRadius: BorderRadius.circular(999),
                ),
                child: Text(
                  resolveAgentToolTypeLabel(card),
                  style: const TextStyle(
                    color: AppColors.text70,
                    fontSize: 10,
                    fontWeight: FontWeight.w600,
                    height: 1,
                  ),
                ),
              ),
            ],
          ),
        );
      },
    );
  }
}
