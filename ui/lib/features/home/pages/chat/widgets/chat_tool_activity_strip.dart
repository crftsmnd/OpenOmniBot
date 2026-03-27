import 'dart:math' as math;

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

class _ChatToolActivityStripState extends State<ChatToolActivityStrip> {
  static const double _barHeight = 40;
  static const double _barMinWidth = 236;
  static const double _barMaxWidth = 420;
  static const double _thumbnailWidth = 132;
  static const double _thumbnailHeight = 78;
  static const double _thumbnailBottom = 5;
  static const double _drawerGap = 4;
  static const double _drawerMaxHeight = 196;

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
    final rawStatus = (activeCard['status'] ?? 'running').toString();
    final statusLabel = resolveAgentToolStatusLabel(activeCard);
    final statusColor = _statusColor(rawStatus);
    final activeIndex = math.max(1, cards.indexOf(activeCard) + 1);
    final historyHeight = _expanded ? _resolveHistoryHeight(cards.length) : 0.0;
    final totalHeight =
        math.max(_thumbnailHeight + _thumbnailBottom, _barHeight) +
        historyHeight +
        (_expanded ? _drawerGap : 0);

    return AnimatedSize(
      duration: const Duration(milliseconds: 240),
      curve: Curves.easeOutCubic,
      alignment: Alignment.bottomCenter,
      child: SizedBox(
        width: double.infinity,
        height: totalHeight,
        child: LayoutBuilder(
          builder: (context, constraints) {
            final barWidth = _resolveBarWidth(constraints.maxWidth);
            final barLeft = (constraints.maxWidth - barWidth) / 2;
            final previewLeft = _resolvePreviewLeft(
              maxWidth: constraints.maxWidth,
              barLeft: barLeft,
            );

            return Stack(
              clipBehavior: Clip.none,
              children: [
                if (_expanded)
                  Positioned(
                    left: barLeft,
                    width: barWidth,
                    bottom: _barHeight - 1,
                    child: _HistoryDrawer(cards: cards, height: historyHeight),
                  ),
                Positioned(
                  left: previewLeft,
                  bottom: _thumbnailBottom,
                  child: _TerminalThumbnail(
                    key: kChatToolActivityPreviewKey,
                    transcript: transcript,
                    onTap: () => _openTranscriptDialog(
                      context,
                      transcript: transcript,
                      title: title,
                    ),
                  ),
                ),
                Positioned(
                  left: barLeft,
                  width: barWidth,
                  bottom: 0,
                  child: _ActivityBar(
                    title: title,
                    status: rawStatus,
                    statusLabel: statusLabel,
                    statusColor: statusColor,
                    activeIndex: activeIndex,
                    totalCount: cards.length,
                    expanded: _expanded,
                    onToggle: () => setState(() => _expanded = !_expanded),
                  ),
                ),
              ],
            );
          },
        ),
      ),
    );
  }

  double _resolveBarWidth(double maxWidth) {
    final cappedAvailable = math.max(0.0, maxWidth - 48);
    final desired = math.max(_barMinWidth, maxWidth * 0.62);
    return math.min(_barMaxWidth, math.min(desired, cappedAvailable));
  }

  double _resolvePreviewLeft({
    required double maxWidth,
    required double barLeft,
  }) {
    final desired = barLeft - (_thumbnailWidth * 0.74);
    final maxLeft = math.max(12.0, maxWidth - _thumbnailWidth - 12.0);
    return desired.clamp(12.0, maxLeft).toDouble();
  }

  double _resolveHistoryHeight(int count) {
    final estimated = 18 + (count * 30) + (math.max(0, count - 1) * 8) + 16;
    return math.min(_drawerMaxHeight, estimated.toDouble());
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

class _ActivityBar extends StatelessWidget {
  const _ActivityBar({
    required this.title,
    required this.status,
    required this.statusLabel,
    required this.statusColor,
    required this.activeIndex,
    required this.totalCount,
    required this.expanded,
    required this.onToggle,
  });

  final String title;
  final String status;
  final String statusLabel;
  final Color statusColor;
  final int activeIndex;
  final int totalCount;
  final bool expanded;
  final VoidCallback onToggle;

  @override
  Widget build(BuildContext context) {
    final counterLabel = totalCount > 1
        ? '$activeIndex/$totalCount'
        : statusLabel;
    return Container(
      key: kChatToolActivityBarKey,
      height: _ChatToolActivityStripState._barHeight,
      padding: const EdgeInsets.fromLTRB(16, 9, 12, 7),
      decoration: BoxDecoration(
        color: Colors.white.withValues(alpha: 0.97),
        borderRadius: const BorderRadius.only(
          topLeft: Radius.circular(20),
          topRight: Radius.circular(20),
        ),
        boxShadow: [
          BoxShadow(
            color: const Color(0x26182432).withValues(alpha: 0.12),
            blurRadius: 20,
            offset: const Offset(0, 8),
          ),
        ],
      ),
      child: Row(
        children: [
          Container(
            width: 20,
            height: 20,
            decoration: BoxDecoration(
              color: statusColor,
              shape: BoxShape.circle,
            ),
            child: Icon(_statusIcon(status), size: 13, color: Colors.white),
          ),
          const SizedBox(width: 10),
          Expanded(
            child: Text(
              title,
              maxLines: 1,
              overflow: TextOverflow.ellipsis,
              style: const TextStyle(
                color: AppColors.text,
                fontSize: 12,
                fontWeight: FontWeight.w600,
                height: 1,
              ),
            ),
          ),
          const SizedBox(width: 8),
          Text(
            counterLabel,
            maxLines: 1,
            overflow: TextOverflow.ellipsis,
            style: const TextStyle(
              color: AppColors.text50,
              fontSize: 10.5,
              fontWeight: FontWeight.w600,
              height: 1,
            ),
          ),
          const SizedBox(width: 6),
          Material(
            color: Colors.transparent,
            child: InkWell(
              key: kChatToolActivityToggleKey,
              onTap: onToggle,
              borderRadius: BorderRadius.circular(999),
              child: Padding(
                padding: const EdgeInsets.all(2),
                child: AnimatedRotation(
                  turns: expanded ? 0.5 : 0,
                  duration: const Duration(milliseconds: 220),
                  curve: Curves.easeOutCubic,
                  child: const Icon(
                    Icons.arrow_outward_rounded,
                    size: 18,
                    color: Color(0xFF4B5C77),
                  ),
                ),
              ),
            ),
          ),
        ],
      ),
    );
  }
}

class _HistoryDrawer extends StatelessWidget {
  const _HistoryDrawer({required this.cards, required this.height});

  final List<Map<String, dynamic>> cards;
  final double height;

  @override
  Widget build(BuildContext context) {
    return Container(
      key: kChatToolActivityPanelKey,
      height: height,
      padding: const EdgeInsets.fromLTRB(14, 14, 14, 12),
      decoration: BoxDecoration(
        color: Colors.white.withValues(alpha: 0.98),
        borderRadius: const BorderRadius.only(
          topLeft: Radius.circular(20),
          topRight: Radius.circular(20),
        ),
        boxShadow: [
          BoxShadow(
            color: const Color(0x22182432).withValues(alpha: 0.10),
            blurRadius: 20,
            offset: const Offset(0, 8),
          ),
        ],
      ),
      child: ListView.separated(
        reverse: true,
        padding: EdgeInsets.zero,
        shrinkWrap: true,
        itemBuilder: (context, index) {
          final card = cards[index];
          return Row(
            children: [
              Container(
                width: 6,
                height: 6,
                decoration: BoxDecoration(
                  color: _statusColor((card['status'] ?? 'running').toString()),
                  shape: BoxShape.circle,
                ),
              ),
              const SizedBox(width: 10),
              Expanded(
                child: Text(
                  resolveAgentToolTitle(card),
                  maxLines: 1,
                  overflow: TextOverflow.ellipsis,
                  style: const TextStyle(
                    color: AppColors.text70,
                    fontSize: 11.5,
                    fontWeight: FontWeight.w600,
                    height: 1.1,
                  ),
                ),
              ),
              const SizedBox(width: 10),
              Text(
                '${resolveAgentToolTypeLabel(card)} · ${resolveAgentToolStatusLabel(card)}',
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
          );
        },
        separatorBuilder: (_, __) => const SizedBox(height: 8),
        itemCount: cards.length,
      ),
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
        borderRadius: BorderRadius.circular(18),
        child: Ink(
          width: _ChatToolActivityStripState._thumbnailWidth,
          height: _ChatToolActivityStripState._thumbnailHeight,
          padding: const EdgeInsets.fromLTRB(12, 10, 10, 10),
          decoration: BoxDecoration(
            color: const Color(0xFF1C1D23),
            borderRadius: BorderRadius.circular(18),
            border: Border.all(color: const Color(0xFF2C313D)),
            boxShadow: const [
              BoxShadow(
                color: Color(0x33101521),
                blurRadius: 18,
                offset: Offset(0, 10),
              ),
            ],
          ),
          child: Align(
            alignment: Alignment.topLeft,
            child: Text(
              preview,
              maxLines: 5,
              overflow: TextOverflow.clip,
              style: const TextStyle(
                color: Color(0xFF9AF9B7),
                fontSize: 7.1,
                height: 1.08,
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
    final previewLines = lines.length > 5
        ? lines.sublist(lines.length - 5)
        : lines;
    return previewLines.join('\n');
  }
}

IconData _statusIcon(String status) {
  switch (status) {
    case 'success':
      return Icons.check_rounded;
    case 'error':
      return Icons.close_rounded;
    case 'interrupted':
      return Icons.stop_rounded;
    default:
      return Icons.more_horiz_rounded;
  }
}

Color _statusColor(String status) {
  switch (status) {
    case 'success':
      return const Color(0xFF2F8F4E);
    case 'error':
      return AppColors.alertRed;
    case 'interrupted':
      return const Color(0xFFFFAA2C);
    default:
      return const Color(0xFF2C7FEB);
  }
}
