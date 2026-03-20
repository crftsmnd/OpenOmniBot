import 'package:flutter/material.dart';
import 'package:flutter_svg/flutter_svg.dart';
import 'package:ui/core/router/go_router_manager.dart';
import 'package:ui/theme/app_colors.dart';
import 'package:ui/widgets/ai_generated_badge.dart';

/// 通用 AppBar 组件
/// 
/// 统一应用内页面的顶部导航栏样式，支持多种展示模式：
/// - 简单返回模式：仅显示返回按钮
/// - 标题模式：显示居中标题 + 返回按钮
/// - 带 AI 标签模式：标题 + AI 标签 + 返回按钮
/// - 完整模式：标题 + AI 标签 + 返回按钮 + 右侧操作
class CommonAppBar extends StatelessWidget {
  /// 标题文字（可选，不传则不显示标题区域）
  final String? title;

  /// 是否显示 AI 生成标签（仅在有标题时生效）
  final bool showAiBadge;

  /// 返回按钮点击回调（默认执行 GoRouterManager.pop()）
  final VoidCallback? onBackPressed;

  /// 右侧操作区域（可选）
  final Widget? trailing;

  /// 返回图标颜色
  final Color backIconColor;

  /// 标题文字样式（可选，有默认值）
  final TextStyle? titleStyle;

  /// AppBar 高度
  final double height;

  const CommonAppBar({
    super.key,
    this.title,
    this.showAiBadge = false,
    this.onBackPressed,
    this.trailing,
    this.backIconColor = AppColors.text,
    this.titleStyle,
    this.height = 44,
  });

  @override
  Widget build(BuildContext context) {
    return Container(
      height: height,
      child: Stack(
        alignment: Alignment.center,
        children: [
          // 标题居中（如果有标题）
          if (title != null)
            Center(
              child: Column(
                mainAxisSize: MainAxisSize.min,
                children: [
                  Text(
                    title!,
                    style: titleStyle ?? const TextStyle(
                      color: AppColors.text,
                      fontSize: 16,
                      fontFamily: 'PingFang SC',
                      fontWeight: FontWeight.w500,
                      height: 1.50,
                    ),
                  ),
                  if (showAiBadge) const AiGeneratedBadge(),
                ],
              ),
            ),
          
          // 返回按钮（左侧）
          Align(
            alignment: Alignment.centerLeft,
            child: GestureDetector(
              onTap: onBackPressed ?? () => GoRouterManager.pop(),
              child: Container(
                width: 44,
                height: 44,
                alignment: Alignment.center,
                child: SvgPicture.asset(
                  'assets/common/chevron_left.svg',
                  width: 20,//24??
                  height: 20,
                  colorFilter: ColorFilter.mode(
                    backIconColor,
                    BlendMode.srcIn,
                  ),
                ),
              ),
            ),
          ),

          // 右侧操作区域（如果有）
          if (trailing != null)
            Align(
              alignment: Alignment.centerRight,
              child: trailing!,
            ),
        ],
      ),
    );
  }
}
