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
  const ChatToolActivityStrip({
    super.key,
    required this.messages,
    this.anchorRect,
    this.onOccupiedHeightChanged,
  });

  final List<ChatMessageModel> messages;
  final Rect? anchorRect;
  final ValueChanged<double>? onOccupiedHeightChanged;

  @override
  State<ChatToolActivityStrip> createState() => _ChatToolActivityStripState();
}

class _ChatToolActivityStripState extends State<ChatToolActivityStrip> {
  static const double _barHeight = 46;
  static const double _barRadius = 22;
  static const double _previewWidth = 104;
  static const double _previewHeight = 62;
  static const double _previewOverlap = 8;
  static const double _drawerGap = 10;
  static const double _drawerMaxHeight = 220;

  bool _expanded = false;
  double? _lastReportedOccupiedHeight;

  @override
  Widget build(BuildContext context) {
    final cards = extractAgentToolCards(widget.messages);
    final activeCard = resolveActiveAgentToolCard(cards);
    if (activeCard == null) {
      _reportOccupiedHeight(0);
      return const SizedBox.shrink();
    }

    final activeCardId = _cardIdentity(activeCard);
    final historyCards = cards
        .where((card) => _cardIdentity(card) != activeCardId)
        .toList(growable: false);
    final canExpand = historyCards.isNotEmpty;
    final isExpanded = _expanded && canExpand;
    final transcript = buildAgentToolTranscript(cards);
    final title = resolveAgentToolTitle(activeCard);
    final historyHeight = isExpanded
        ? _resolveHistoryHeight(historyCards)
        : 0.0;
    final totalHeight =
        _barHeight +
        (isExpanded ? historyHeight + _drawerGap : 0.0) +
        (!isExpanded ? _previewHeight - _previewOverlap : 0.0);
    _reportOccupiedHeight(totalHeight);

    return AnimatedSize(
      duration: const Duration(milliseconds: 240),
      curve: Curves.easeOutCubic,
      alignment: Alignment.bottomLeft,
      child: SizedBox(
        width: widget.anchorRect?.width ?? double.infinity,
        height: totalHeight,
        child: Stack(
          clipBehavior: Clip.none,
          children: [
            if (isExpanded)
              Positioned(
                left: 0,
                right: 0,
                bottom: _barHeight + _drawerGap,
                child: _HistoryDrawer(
                  cards: historyCards,
                  height: historyHeight,
                ),
              ),
            if (!isExpanded)
              Positioned(
                left: 0,
                bottom: _barHeight - _previewOverlap,
                child: _TerminalThumbnail(
                  key: kChatToolActivityPreviewKey,
                  previewText: _buildPreviewText(activeCard),
                  transcript: transcript,
                  onTap: () => _openTranscriptDialog(
                    context,
                    transcript: transcript,
                    title: title,
                  ),
                ),
              ),
            Positioned(
              left: 0,
              right: 0,
              bottom: 0,
              child: _ActivityBar(
                card: activeCard,
                expanded: isExpanded,
                canExpand: canExpand,
                onToggle: () => setState(() => _expanded = !_expanded),
              ),
            ),
          ],
        ),
      ),
    );
  }

  @override
  void didUpdateWidget(covariant ChatToolActivityStrip oldWidget) {
    super.didUpdateWidget(oldWidget);
    if (widget.messages.isEmpty && _lastReportedOccupiedHeight != 0) {
      _reportOccupiedHeight(0);
    }
  }

  String _cardIdentity(Map<String, dynamic> cardData) {
    final explicit = (cardData['cardId'] ?? '').toString().trim();
    if (explicit.isNotEmpty) {
      return explicit;
    }
    return [
      (cardData['taskId'] ?? '').toString(),
      (cardData['toolName'] ?? '').toString(),
      (cardData['toolTitle'] ?? '').toString(),
      (cardData['status'] ?? '').toString(),
    ].join('|');
  }

  String _buildPreviewText(Map<String, dynamic> activeCard) {
    final toolType = (activeCard['toolType'] ?? '').toString();
    if (toolType == 'terminal') {
      final output = resolveAgentToolTerminalOutput(activeCard).trim();
      if (output.isNotEmpty) {
        final lines = output
            .split('\n')
            .map((line) => line.trimRight())
            .where((line) => line.isNotEmpty)
            .toList(growable: false);
        if (lines.isNotEmpty) {
          final previewLines = lines.length > 4
              ? lines.sublist(lines.length - 4)
              : lines;
          return previewLines.join('\n');
        }
      }
    }

    final title = resolveAgentToolTitle(activeCard);
    final preview = resolveAgentToolPreview(activeCard);
    final meta = resolveAgentToolTypeLabel(activeCard);
    return [
      '\$ $title',
      '> $meta · $preview',
    ].where((line) => line.trim().isNotEmpty).join('\n');
  }

  double _resolveHistoryHeight(List<Map<String, dynamic>> cards) {
    final visibleCount = cards.length.clamp(1, 5);
    final estimated =
        18 + (visibleCount * 40) + (math.max(0, visibleCount - 1) * 2) + 14;
    return math.min(_drawerMaxHeight, estimated.toDouble());
  }

  void _reportOccupiedHeight(double height) {
    if (widget.onOccupiedHeightChanged == null) {
      return;
    }
    final normalized = height.isFinite ? height : 0.0;
    if (_lastReportedOccupiedHeight != null &&
        (_lastReportedOccupiedHeight! - normalized).abs() < 0.5) {
      return;
    }
    _lastReportedOccupiedHeight = normalized;
    WidgetsBinding.instance.addPostFrameCallback((_) {
      if (!mounted) {
        return;
      }
      widget.onOccupiedHeightChanged?.call(normalized);
    });
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
    required this.card,
    required this.expanded,
    required this.canExpand,
    required this.onToggle,
  });

  final Map<String, dynamic> card;
  final bool expanded;
  final bool canExpand;
  final VoidCallback onToggle;

  @override
  Widget build(BuildContext context) {
    return Material(
      color: Colors.transparent,
      child: Ink(
        key: kChatToolActivityBarKey,
        decoration: _surfaceDecoration(
          borderRadius: BorderRadius.circular(
            _ChatToolActivityStripState._barRadius,
          ),
        ),
        child: InkWell(
          onTap: canExpand ? onToggle : null,
          borderRadius: BorderRadius.circular(
            _ChatToolActivityStripState._barRadius,
          ),
          child: ToolActivityRow(
            card: card,
            trailing: canExpand
                ? Material(
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
                            Icons.keyboard_arrow_up_rounded,
                            size: 18,
                            color: Color(0xFF5B6E8A),
                          ),
                        ),
                      ),
                    ),
                  )
                : null,
          ),
        ),
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
      padding: const EdgeInsets.fromLTRB(10, 10, 10, 8),
      decoration: _surfaceDecoration(
        borderRadius: BorderRadius.circular(
          _ChatToolActivityStripState._barRadius,
        ),
      ),
      child: ListView.separated(
        padding: EdgeInsets.zero,
        shrinkWrap: true,
        itemBuilder: (context, index) {
          return ToolActivityRow(card: cards[index]);
        },
        separatorBuilder: (_, __) => Divider(
          height: 2,
          thickness: 1,
          color: const Color(0xFF1A273B).withValues(alpha: 0.05),
        ),
        itemCount: cards.length,
      ),
    );
  }
}

class ToolActivityRow extends StatelessWidget {
  const ToolActivityRow({super.key, required this.card, this.trailing});

  final Map<String, dynamic> card;
  final Widget? trailing;

  @override
  Widget build(BuildContext context) {
    final status = (card['status'] ?? 'running').toString();
    final meta =
        '${resolveAgentToolTypeLabel(card)} · ${resolveAgentToolStatusLabel(card)}';

    return SizedBox(
      height: 40,
      child: Row(
        children: [
          Container(
            width: 8,
            height: 8,
            decoration: BoxDecoration(
              color: _statusColor(status),
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
                color: AppColors.text,
                fontSize: 12,
                fontWeight: FontWeight.w600,
                height: 1.1,
              ),
            ),
          ),
          const SizedBox(width: 10),
          Flexible(
            child: Text(
              meta,
              maxLines: 1,
              overflow: TextOverflow.ellipsis,
              textAlign: TextAlign.right,
              style: const TextStyle(
                color: AppColors.text50,
                fontSize: 10.5,
                fontWeight: FontWeight.w600,
                height: 1.1,
              ),
            ),
          ),
          if (trailing != null) ...[const SizedBox(width: 6), trailing!],
        ],
      ),
    );
  }
}

class _TerminalThumbnail extends StatelessWidget {
  const _TerminalThumbnail({
    super.key,
    required this.previewText,
    required this.transcript,
    required this.onTap,
  });

  final String previewText;
  final String transcript;
  final VoidCallback onTap;

  @override
  Widget build(BuildContext context) {
    return Material(
      color: Colors.transparent,
      child: InkWell(
        onTap: onTap,
        borderRadius: BorderRadius.circular(16),
        child: Ink(
          width: _ChatToolActivityStripState._previewWidth,
          height: _ChatToolActivityStripState._previewHeight,
          padding: const EdgeInsets.fromLTRB(10, 9, 10, 9),
          decoration: BoxDecoration(
            color: const Color(0xFF191D27),
            borderRadius: BorderRadius.circular(16),
            border: Border.all(color: const Color(0xFF2B313F)),
            boxShadow: const [
              BoxShadow(
                color: Color(0x2E0D1627),
                blurRadius: 18,
                offset: Offset(0, 12),
              ),
            ],
          ),
          child: Align(
            alignment: Alignment.topLeft,
            child: Text(
              _thumbnailText(previewText, transcript),
              maxLines: 4,
              overflow: TextOverflow.clip,
              style: const TextStyle(
                color: Color(0xFF9AF9B7),
                fontSize: 7.4,
                height: 1.1,
                fontFamily: 'monospace',
              ),
            ),
          ),
        ),
      ),
    );
  }

  String _thumbnailText(String previewText, String transcript) {
    final preferred = previewText.trim();
    if (preferred.isNotEmpty) {
      return preferred;
    }
    final fallback = transcript.trim();
    if (fallback.isEmpty) {
      return '\$ idle';
    }
    final lines = fallback
        .split('\n')
        .map((line) => line.trimRight())
        .where((line) => line.isNotEmpty)
        .toList(growable: false);
    if (lines.isEmpty) {
      return '\$ idle';
    }
    final previewLines = lines.length > 4
        ? lines.sublist(lines.length - 4)
        : lines;
    return previewLines.join('\n');
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

BoxDecoration _surfaceDecoration({required BorderRadius borderRadius}) {
  return BoxDecoration(
    color: const Color(0xFFFDFEFF),
    borderRadius: borderRadius,
    border: Border.all(color: const Color(0xFF102039).withValues(alpha: 0.06)),
    boxShadow: const [
      BoxShadow(
        color: Color(0x14111B2D),
        blurRadius: 20,
        offset: Offset(0, 10),
      ),
    ],
  );
}
