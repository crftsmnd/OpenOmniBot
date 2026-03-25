import 'dart:io';

import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:flutter_markdown/flutter_markdown.dart';
import 'package:markdown/markdown.dart' as md;
import 'package:ui/services/omnibot_resource_service.dart';
import 'package:ui/utils/ui.dart';
import 'package:ui/widgets/omnibot_resource_widgets.dart';

class OmnibotMarkdownBody extends StatelessWidget {
  final String data;
  final TextStyle baseStyle;
  final bool selectable;
  final bool inlineResourcePlainStyle;

  const OmnibotMarkdownBody({
    super.key,
    required this.data,
    required this.baseStyle,
    this.selectable = false,
    this.inlineResourcePlainStyle = false,
  });

  @override
  Widget build(BuildContext context) {
    final codeTapHandler = OmnibotCodeTapHandler();
    return MarkdownBody(
      data: _linkifyBareOmnibotUris(data),
      selectable: selectable,
      onTapLink: (text, href, title) {
        if (href == null) return;
        OmnibotResourceService.handleLinkTap(href);
      },
      inlineSyntaxes: <md.InlineSyntax>[OmnibotInlineLinkSyntax()],
      builders: <String, MarkdownElementBuilder>{
        'code': OmnibotInlineCodeBuilder(onCopy: codeTapHandler.copy),
        'pre': OmnibotCodeBlockBuilder(onCopy: codeTapHandler.copy),
        'omnibot-link': OmnibotInlineLinkBuilder(
          inlineResourcePlainStyle: inlineResourcePlainStyle,
        ),
      },
      sizedImageBuilder: (config) {
        final uri = config.uri;
        if (uri.scheme == 'omnibot') {
          final metadata = OmnibotResourceService.resolveUri(uri.toString());
          if (metadata != null) {
            return OmnibotInlineResourceEmbed(
              metadata: metadata,
              plainStyle: inlineResourcePlainStyle,
            );
          }
        }
        if (uri.scheme == 'file') {
          return Image.file(File.fromUri(uri));
        }
        return Image.network(uri.toString());
      },
      styleSheet: buildOmnibotMarkdownStyleSheet(context, baseStyle),
    );
  }
}

MarkdownStyleSheet buildOmnibotMarkdownStyleSheet(
  BuildContext context,
  TextStyle baseStyle,
) {
  return MarkdownStyleSheet.fromTheme(Theme.of(context)).copyWith(
    p: baseStyle.copyWith(height: 1.5),
    h1: baseStyle.copyWith(fontSize: 24, fontWeight: FontWeight.bold),
    h2: baseStyle.copyWith(fontSize: 20, fontWeight: FontWeight.bold),
    code: baseStyle.copyWith(
      fontFamily: 'monospace',
      fontSize: (baseStyle.fontSize ?? 14) * 0.92,
      backgroundColor: Colors.transparent,
      color: Theme.of(context).colorScheme.onSurfaceVariant,
    ),
    codeblockDecoration: BoxDecoration(
      color: Theme.of(context).colorScheme.surfaceContainerHighest,
      borderRadius: BorderRadius.circular(14),
    ),
    blockquoteDecoration: BoxDecoration(
      color: Colors.grey.withValues(alpha: 0.1),
      border: Border(
        left: BorderSide(
          color: Theme.of(context).colorScheme.primary.withValues(alpha: 0.5),
          width: 4,
        ),
      ),
    ),
    tableColumnWidth: const IntrinsicColumnWidth(),
    tableCellsPadding: const EdgeInsets.all(6),
  );
}

class OmnibotInlineLinkSyntax extends md.InlineSyntax {
  OmnibotInlineLinkSyntax() : super(_pattern);

  static const String _pattern = r'(?<!!)\[([^\]]*?)\]\((omnibot://[^)\s]+)\)';

  @override
  bool onMatch(md.InlineParser parser, Match match) {
    final label = match[1] ?? '';
    final href = match[2] ?? '';
    final element = md.Element.text(
      'omnibot-link',
      label.isEmpty ? href : label,
    )..attributes['href'] = href;
    parser.addNode(element);
    return true;
  }
}

typedef OmnibotCodeCopyCallback = Future<void> Function(String code);

class OmnibotCodeTapHandler {
  const OmnibotCodeTapHandler();

  Future<void> copy(String code) async {
    if (code.trim().isEmpty) return;
    try {
      await Clipboard.setData(ClipboardData(text: code));
      showToast('代码已复制', type: ToastType.success);
    } catch (_) {
      showToast('复制失败，请重试', type: ToastType.error);
    }
  }
}

class OmnibotInlineCodeBuilder extends MarkdownElementBuilder {
  OmnibotInlineCodeBuilder({required this.onCopy});

  final OmnibotCodeCopyCallback onCopy;

  @override
  Widget visitElementAfterWithContext(
    BuildContext context,
    md.Element element,
    TextStyle? preferredStyle,
    TextStyle? parentStyle,
  ) {
    final code = element.textContent;
    if (code.isEmpty) {
      return const SizedBox.shrink();
    }
    final theme = Theme.of(context);
    final borderRadius = BorderRadius.circular(8);
    final codeStyle = (preferredStyle ?? parentStyle ?? const TextStyle())
        .copyWith(
          fontFamily: 'monospace',
          backgroundColor: Colors.transparent,
          color: theme.colorScheme.onSurfaceVariant,
          fontSize:
              ((preferredStyle?.fontSize ?? parentStyle?.fontSize ?? 14) * 0.92)
                  .toDouble(),
          height: 1.2,
        );

    return Padding(
      padding: const EdgeInsets.symmetric(horizontal: 1, vertical: 1),
      child: Material(
        color: Colors.transparent,
        child: InkWell(
          borderRadius: borderRadius,
          onTap: () => onCopy(code),
          child: Ink(
            padding: const EdgeInsets.symmetric(horizontal: 7, vertical: 3),
            decoration: BoxDecoration(
              color: theme.colorScheme.surfaceContainerHighest.withValues(
                alpha: 0.72,
              ),
              borderRadius: borderRadius,
              border: Border.all(
                color: theme.colorScheme.outlineVariant.withValues(alpha: 0.45),
                width: 0.8,
              ),
            ),
            child: Text(code, style: codeStyle),
          ),
        ),
      ),
    );
  }
}

class OmnibotCodeBlockBuilder extends MarkdownElementBuilder {
  OmnibotCodeBlockBuilder({required this.onCopy});

  final OmnibotCodeCopyCallback onCopy;

  @override
  Widget visitElementAfterWithContext(
    BuildContext context,
    md.Element element,
    TextStyle? preferredStyle,
    TextStyle? parentStyle,
  ) {
    final code = _normalizedCodeText(element.textContent);
    final canCopy = code.trim().isNotEmpty;
    final theme = Theme.of(context);
    final borderRadius = BorderRadius.circular(14);
    final codeStyle = (preferredStyle ?? parentStyle ?? const TextStyle())
        .copyWith(
          fontFamily: 'monospace',
          backgroundColor: Colors.transparent,
          color: theme.colorScheme.onSurfaceVariant,
          fontSize:
              ((preferredStyle?.fontSize ?? parentStyle?.fontSize ?? 14) * 0.92)
                  .toDouble(),
          height: 1.45,
        );

    return Padding(
      padding: const EdgeInsets.symmetric(vertical: 4),
      child: Material(
        color: Colors.transparent,
        child: InkWell(
          borderRadius: borderRadius,
          onTap: canCopy ? () => onCopy(code) : null,
          child: Ink(
            decoration: BoxDecoration(
              gradient: LinearGradient(
                begin: Alignment.topLeft,
                end: Alignment.bottomRight,
                colors: [
                  theme.colorScheme.surfaceContainerHighest.withValues(
                    alpha: 0.96,
                  ),
                  theme.colorScheme.surfaceContainerHigh.withValues(
                    alpha: 0.90,
                  ),
                ],
              ),
              borderRadius: borderRadius,
              border: Border.all(
                color: theme.colorScheme.outlineVariant.withValues(alpha: 0.42),
                width: 0.9,
              ),
            ),
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Padding(
                  padding: const EdgeInsets.fromLTRB(12, 10, 12, 0),
                  child: Row(
                    children: [
                      Icon(
                        Icons.content_copy_rounded,
                        size: 14,
                        color: theme.colorScheme.primary.withValues(
                          alpha: canCopy ? 0.9 : 0.35,
                        ),
                      ),
                      const SizedBox(width: 6),
                      Text(
                        canCopy ? '点击复制代码' : '代码块',
                        style: TextStyle(
                          fontSize: 11,
                          color: theme.colorScheme.onSurfaceVariant.withValues(
                            alpha: 0.74,
                          ),
                          fontWeight: FontWeight.w500,
                        ),
                      ),
                    ],
                  ),
                ),
                SingleChildScrollView(
                  scrollDirection: Axis.horizontal,
                  padding: const EdgeInsets.fromLTRB(12, 8, 12, 12),
                  child: Text(code, style: codeStyle, softWrap: false),
                ),
              ],
            ),
          ),
        ),
      ),
    );
  }

  String _normalizedCodeText(String value) {
    if (value.endsWith('\n') && value.length > 1) {
      return value.substring(0, value.length - 1);
    }
    return value;
  }
}

class OmnibotInlineLinkBuilder extends MarkdownElementBuilder {
  OmnibotInlineLinkBuilder({this.inlineResourcePlainStyle = false});

  final bool inlineResourcePlainStyle;

  @override
  Widget visitElementAfterWithContext(
    BuildContext context,
    md.Element element,
    TextStyle? preferredStyle,
    TextStyle? parentStyle,
  ) {
    final href = element.attributes['href'];
    final metadata = href == null
        ? null
        : OmnibotResourceService.resolveUri(href);
    if (metadata == null) {
      return Text.rich(
        WidgetSpan(
          alignment: PlaceholderAlignment.middle,
          child: InkWell(
            onTap: href == null
                ? null
                : () => OmnibotResourceService.handleLinkTap(href),
            child: Padding(
              padding: const EdgeInsets.symmetric(vertical: 2),
              child: Text(
                element.textContent,
                style: preferredStyle?.copyWith(
                  color: Theme.of(context).colorScheme.primary,
                ),
              ),
            ),
          ),
        ),
      );
    }
    return Text.rich(
      WidgetSpan(
        alignment: PlaceholderAlignment.middle,
        child: Padding(
          padding: const EdgeInsets.symmetric(vertical: 4),
          child: OmnibotInlineResourceEmbed(
            metadata: metadata,
            plainStyle: inlineResourcePlainStyle,
          ),
        ),
      ),
    );
  }
}

String _linkifyBareOmnibotUris(String input) {
  final buffer = StringBuffer();
  final lines = input.split('\n');
  for (var i = 0; i < lines.length; i++) {
    final line = lines[i];
    final trimmed = line.trim();
    if (trimmed.startsWith('omnibot://') &&
        !trimmed.contains(' ') &&
        !trimmed.contains('[') &&
        !trimmed.contains(']')) {
      final parsed = Uri.tryParse(trimmed);
      final label = parsed?.pathSegments.isNotEmpty == true
          ? parsed!.pathSegments.last
          : '资源';
      buffer.write('[$label]($trimmed)');
    } else {
      buffer.write(line);
    }
    if (i != lines.length - 1) {
      buffer.write('\n');
    }
  }
  return buffer.toString();
}
