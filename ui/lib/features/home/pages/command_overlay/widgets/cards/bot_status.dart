import 'package:flutter/material.dart';
import 'package:flutter_svg/flutter_svg.dart';
import 'package:ui/features/home/pages/command_overlay/widgets/thinking_animation.dart';

class BotStatus extends StatelessWidget {
  final BotStatusType status;
  final String? hintText;
  final String? costTime;

  const BotStatus({
    Key? key,
    required this.status,
    this.hintText,
    this.costTime,
  }) : super(key: key);

  @override
  Widget build(BuildContext context) {
    switch (status) {
      case BotStatusType.completed:
        return _buildStatusRow(
          context,
          customIcon: const ThinkingAnimation(isThinking: false),
          text: '思考完成',
          timeDesc: '用时',
          costTime: costTime
        );
      case BotStatusType.hint:
        return _buildStatusRow(
          context,
          customIcon: const ThinkingAnimation(isThinking: true),
          text: hintText ?? '正在思考',
          timeDesc: costTime != null ? '用时' : null,
          costTime: costTime,
        );
    }
  }

  Widget _buildStatusRow(BuildContext context, {
    IconData? icon,
    String? svgPath,
    Widget? customIcon,
    required String text,
    String? timeDesc,
    String? costTime,
    String? timeDescSuffix = ''
  }) {
    Widget iconWidget;
    if (customIcon != null) {
      iconWidget = customIcon;
    } else if (svgPath != null) {
      iconWidget = SvgPicture.asset(
        svgPath,
        width: 16,
        height: 16,
      );
    } else if (icon != null) {
      iconWidget = Icon(icon, size: 16);
    } else {
      iconWidget = const SizedBox.shrink();
    }

    return Container(
      decoration: BoxDecoration(
        borderRadius: BorderRadius.circular(8.0),
      ),
      child: Row(
        children: [
          iconWidget,
          const SizedBox(width: 4.0),
          Text(
            text,
            style: const TextStyle(
              color: Color(0x80353E53), // rgba(53,62,83,0.5)
              fontSize: 12,
              fontFamily: 'PingFang SC',
              fontWeight: FontWeight.w400,
              height: 1.50,
              letterSpacing: 0.33,
            ),
          ),
          const SizedBox(width: 4.0),
          Text(
            timeDesc != null ? '($timeDesc${costTime ?? ''})$timeDescSuffix' : '',
            style: const TextStyle(
              color: Color(0x80353E53), // rgba(53,62,83,0.5)
              fontSize: 12,
              fontFamily: 'PingFang SC',
              fontWeight: FontWeight.w400,
              height: 1.50,
              letterSpacing: 0.33,
            ),
          ),
        ],
      ),
    );
  }
}

enum BotStatusType {
  completed,
  hint,
}