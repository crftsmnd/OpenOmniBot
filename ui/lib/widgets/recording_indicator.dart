// lib/widgets/recording_indicator.dart

import 'package:flutter/material.dart';
import 'package:loading_animation_widget/loading_animation_widget.dart';
import 'package:ui/theme/app_colors.dart';

class RecordingIndicator extends StatelessWidget {
  const RecordingIndicator({super.key});

  @override
  Widget build(BuildContext context) {
    return Row(
      mainAxisSize: MainAxisSize.min,
      children: [
        LoadingAnimationWidget.progressiveDots(
          color: AppColors.primaryBlue,
          size: 40,
        ),
      ],
    );
  }
}
