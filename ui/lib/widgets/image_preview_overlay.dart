import 'dart:io';
import 'dart:typed_data';

import 'package:flutter/material.dart';

/// Represents the source of an image to preview.
sealed class ImagePreviewSource {}

class FileImageSource extends ImagePreviewSource {
  final String path;
  FileImageSource(this.path);
}

class NetworkImageSource extends ImagePreviewSource {
  final String url;
  NetworkImageSource(this.url);
}

class MemoryImageSource extends ImagePreviewSource {
  final Uint8List bytes;
  MemoryImageSource(this.bytes);
}

/// Lightweight full-screen image preview overlay with pinch-to-zoom and swipe.
class ImagePreviewOverlay {
  ImagePreviewOverlay._();

  /// Show preview for a single image.
  static Future<void> show(
    BuildContext context, {
    required ImagePreviewSource source,
  }) {
    return showAll(context, sources: [source], initialIndex: 0);
  }

  /// Show preview for multiple images with swipe navigation.
  static Future<void> showAll(
    BuildContext context, {
    required List<ImagePreviewSource> sources,
    int initialIndex = 0,
  }) {
    assert(sources.isNotEmpty);
    return showGeneralDialog(
      context: context,
      barrierDismissible: false,
      barrierLabel: 'Close image preview',
      barrierColor: Colors.black87,
      transitionDuration: const Duration(milliseconds: 200),
      pageBuilder: (_, __, ___) => _ImagePreviewDialog(
        sources: sources,
        initialIndex: initialIndex,
      ),
      transitionBuilder: (_, animation, __, child) {
        return FadeTransition(opacity: animation, child: child);
      },
    );
  }
}

class _ImagePreviewDialog extends StatefulWidget {
  final List<ImagePreviewSource> sources;
  final int initialIndex;

  const _ImagePreviewDialog({
    required this.sources,
    required this.initialIndex,
  });

  @override
  State<_ImagePreviewDialog> createState() => _ImagePreviewDialogState();
}

class _ImagePreviewDialogState extends State<_ImagePreviewDialog> {
  late final PageController _pageController;
  late int _currentIndex;
  bool _isZoomed = false;

  bool get _hasMultipleImages => widget.sources.length > 1;

  @override
  void initState() {
    super.initState();
    _currentIndex = widget.initialIndex;
    _pageController = PageController(initialPage: widget.initialIndex);
  }

  @override
  void dispose() {
    _pageController.dispose();
    super.dispose();
  }

  void _dismiss() => Navigator.of(context).pop();

  @override
  Widget build(BuildContext context) {
    final topPadding = MediaQuery.of(context).padding.top;
    final bottomPadding = MediaQuery.of(context).padding.bottom;

    return Scaffold(
      backgroundColor: Colors.transparent,
      body: Stack(
        children: [
          // Image page view (swipeable)
          PageView.builder(
            controller: _pageController,
            itemCount: widget.sources.length,
            physics: _isZoomed
                ? const NeverScrollableScrollPhysics()
                : const BouncingScrollPhysics(),
            onPageChanged: (index) => setState(() => _currentIndex = index),
            itemBuilder: (context, index) {
              return _InteractiveImagePage(
                source: widget.sources[index],
                onTap: _dismiss,
                onScaleChanged: (zoomed) {
                  if (_isZoomed != zoomed) setState(() => _isZoomed = zoomed);
                },
              );
            },
          ),

          // Close button
          Positioned(
            top: topPadding + 8,
            right: 12,
            child: _buildCloseButton(),
          ),

          // Page indicator
          if (_hasMultipleImages)
            Positioned(
              bottom: bottomPadding + 20,
              left: 0,
              right: 0,
              child: _buildPageIndicator(),
            ),
        ],
      ),
    );
  }

  Widget _buildCloseButton() {
    return GestureDetector(
      onTap: _dismiss,
      child: Container(
        width: 36,
        height: 36,
        decoration: const BoxDecoration(
          color: Colors.black45,
          shape: BoxShape.circle,
        ),
        child: const Icon(
          Icons.close_rounded,
          color: Colors.white,
          size: 20,
        ),
      ),
    );
  }

  Widget _buildPageIndicator() {
    return Row(
      mainAxisAlignment: MainAxisAlignment.center,
      children: List.generate(widget.sources.length, (index) {
        final isActive = index == _currentIndex;
        return AnimatedContainer(
          duration: const Duration(milliseconds: 200),
          margin: const EdgeInsets.symmetric(horizontal: 3),
          width: isActive ? 18 : 6,
          height: 6,
          decoration: BoxDecoration(
            borderRadius: BorderRadius.circular(3),
            color: isActive ? Colors.white : Colors.white38,
          ),
        );
      }),
    );
  }
}

/// A single interactive image page with zoom and tap-to-dismiss.
class _InteractiveImagePage extends StatefulWidget {
  final ImagePreviewSource source;
  final VoidCallback onTap;
  final ValueChanged<bool> onScaleChanged;

  const _InteractiveImagePage({
    required this.source,
    required this.onTap,
    required this.onScaleChanged,
  });

  @override
  State<_InteractiveImagePage> createState() => _InteractiveImagePageState();
}

class _InteractiveImagePageState extends State<_InteractiveImagePage> {
  final TransformationController _transformController =
      TransformationController();

  @override
  void dispose() {
    _transformController.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    return GestureDetector(
      onTap: widget.onTap,
      // Double-tap to toggle zoom
      onDoubleTapDown: (details) => _handleDoubleTap(details),
      child: InteractiveViewer(
        transformationController: _transformController,
        minScale: 1.0,
        maxScale: 5.0,
        onInteractionEnd: (_) {
          final scale = _transformController.value.getMaxScaleOnAxis();
          widget.onScaleChanged(scale > 1.05);
        },
        child: Center(child: _buildImage(widget.source)),
      ),
    );
  }

  void _handleDoubleTap(TapDownDetails details) {
    final currentScale = _transformController.value.getMaxScaleOnAxis();
    if (currentScale > 1.05) {
      // Reset to original
      _transformController.value = Matrix4.identity();
      widget.onScaleChanged(false);
    } else {
      // Zoom to 2.5x at tap position
      final position = details.localPosition;
      const targetScale = 2.5;
      final zoomed = Matrix4.identity()
        ..translate(
          -position.dx * (targetScale - 1),
          -position.dy * (targetScale - 1),
        )
        ..scale(targetScale);
      _transformController.value = zoomed;
      widget.onScaleChanged(true);
    }
  }

  static Widget _buildImage(ImagePreviewSource source) {
    return switch (source) {
      FileImageSource(path: final p) => Image.file(
          File(p),
          fit: BoxFit.contain,
          errorBuilder: (_, __, ___) => _buildError(),
        ),
      NetworkImageSource(url: final u) => Image.network(
          u,
          fit: BoxFit.contain,
          errorBuilder: (_, __, ___) => _buildError(),
        ),
      MemoryImageSource(bytes: final b) => Image.memory(
          b,
          fit: BoxFit.contain,
          errorBuilder: (_, __, ___) => _buildError(),
        ),
    };
  }

  static Widget _buildError() {
    return const Column(
      mainAxisSize: MainAxisSize.min,
      children: [
        Icon(Icons.broken_image_outlined, size: 48, color: Colors.white54),
        SizedBox(height: 8),
        Text(
          '无法加载图片',
          style: TextStyle(color: Colors.white54, fontSize: 14),
        ),
      ],
    );
  }
}
