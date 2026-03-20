import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:flutter_svg/flutter_svg.dart';
import 'package:ui/theme/app_colors.dart';
import 'package:ui/theme/app_text_styles.dart';

class TagChip extends StatelessWidget {
  final String title;
  final IconData? iconPath;
  final String? svgPath;
  final ImageProvider? appIconProvider;
  final bool selected;
  final Color? backgroundColor;
  final bool showIcon;

  const TagChip({
    Key? key, 
    required this.title, 
    this.iconPath, 
    this.svgPath,
    this.appIconProvider,
    this.selected = false,
    this.backgroundColor,
    this.showIcon = true,
  }) : super(key: key);

  @override
  Widget build(BuildContext context) {
    return Container(
      padding: const EdgeInsets.symmetric(horizontal: 10, vertical: 6),
      height: 24,
      decoration: BoxDecoration(
        color: selected ? Color(0xFFE3F1FF) : backgroundColor ?? Colors.white,
        borderRadius: BorderRadius.circular(12),
        boxShadow: [AppColors.boxShadow]
      ),
      child: Row(
        mainAxisSize: MainAxisSize.min,
        mainAxisAlignment: MainAxisAlignment.center,
        crossAxisAlignment: CrossAxisAlignment.center,
        children: [
          if (showIcon) ...[
            if (appIconProvider != null)...[
              ClipRRect(
                borderRadius: BorderRadius.circular(3),
                child: Image(
                  image: appIconProvider!,
                  width: 13,
                  height: 13,
                  errorBuilder: (context, error, stackTrace) {
                    return const Icon(Icons.apps, size: 13, color: AppColors.text);
                  },
                ),
              )
            ] else if (svgPath != null) ...[
              ClipRRect(
                borderRadius: BorderRadius.circular(3),
                child: SvgPicture.asset(
                  svgPath!,
                  width: 13,
                  height: 13,
                  color: selected ? AppColors.buttonPrimary : AppColors.text,
                  placeholderBuilder: (context) => const Icon(Icons.image, size: 13, color: AppColors.text),
                ),
              )
            ] else if (iconPath != null) ...[
              Icon(iconPath, size: 13, color: selected ? AppColors.buttonPrimary : AppColors.text),
            ] else ...[
              const Icon(Icons.label_outline, size: 13, color: AppColors.text),
            ],
            SizedBox(width: 5),
          ],
          Text(
            title,
            style: TextStyle(
              fontSize: AppTextStyles.fontSizeSmall,
              color: selected ? AppColors.buttonPrimary : AppColors.text,
              fontWeight: selected? AppTextStyles.fontWeightMedium : AppTextStyles.fontWeightRegular,
              height: 0.92,
              letterSpacing: AppTextStyles.letterSpacingWide,
            ),
          ),
        ],
      ),
    );
  }
}