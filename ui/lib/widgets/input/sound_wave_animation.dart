import 'dart:math' as math;
import 'package:flutter/material.dart';

/// 声波动画组件 - 模拟录音时的声波跳动效果
class SoundWaveAnimation extends StatefulWidget {
  final double width;
  final double height;
  final Gradient? gradient;

  const SoundWaveAnimation({
    super.key,
    this.width = 18,
    this.height = 18,
    this.gradient,
  });

  @override
  State<SoundWaveAnimation> createState() => _SoundWaveAnimationState();
}

class _SoundWaveAnimationState extends State<SoundWaveAnimation>
    with SingleTickerProviderStateMixin {
  late AnimationController _controller;

  @override
  void initState() {
    super.initState();
    _controller = AnimationController(
      vsync: this,
      duration: const Duration(milliseconds: 800),
    )..repeat();
  }

  @override
  void dispose() {
    _controller.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    return AnimatedBuilder(
      animation: _controller,
      builder: (context, child) {
        return CustomPaint(
          size: Size(widget.width, widget.height),
          painter: _SoundWavePainter(
            animation: _controller.value,
            gradient: widget.gradient ??
                const LinearGradient(
                  begin: Alignment.centerLeft,
                  end: Alignment.centerRight,
                  colors: [Color(0xFF2C7FEB), Color(0xFF86BAFF)],
                ),
          ),
        );
      },
    );
  }
}

class _SoundWavePainter extends CustomPainter {
  final double animation;
  final Gradient gradient;

  // 4 条竖线的相对位置（基于 SVG: 24x24）
  // 从 SVG 分析：x 位置分别是 6.5, 10.5, 14.5, 18.5
  static const List<double> _barPositions = [0.271, 0.438, 0.604, 0.771];
  // 每条线的基础高度比例（相对于总高度 24）
  // SVG高度: 10/24=0.417, 15/24=0.625, 10/24=0.417, 6/24=0.25
  static const List<double> _baseHeights = [0.417, 0.625, 0.417, 0.25];
  // 每条线的动画相位偏移
  static const List<double> _phaseOffsets = [0.0, 0.25, 0.5, 0.75];

  _SoundWavePainter({
    required this.animation,
    required this.gradient,
  });

  @override
  void paint(Canvas canvas, Size size) {
    // 竖线宽度：在 24×24 设计中约 1.5px，保证在小尺寸下清晰可见
    final barWidth = size.width * 0.0625;
    final radius = barWidth / 2;

    // 创建渐变画笔
    final paint = Paint()
      ..shader = gradient.createShader(
        Rect.fromLTWH(0, 0, size.width, size.height),
      )
      ..strokeCap = StrokeCap.round
      ..strokeWidth = barWidth
      ..style = PaintingStyle.stroke;

    for (int i = 0; i < 4; i++) {
      // 计算动画高度：基础高度 + 波动
      final phase = (animation + _phaseOffsets[i]) % 1.0;
      final wave = math.sin(phase * 2 * math.pi) * 0.15; // 波动幅度
      final heightRatio = (_baseHeights[i] + wave).clamp(0.2, 0.75);

      final barHeight = size.height * heightRatio;
      final x = size.width * _barPositions[i];
      final centerY = size.height / 2;

      // 画圆角竖线
      canvas.drawLine(
        Offset(x, centerY - barHeight / 2 + radius),
        Offset(x, centerY + barHeight / 2 - radius),
        paint,
      );
    }
  }

  @override
  bool shouldRepaint(_SoundWavePainter oldDelegate) {
    return oldDelegate.animation != animation;
  }
}

