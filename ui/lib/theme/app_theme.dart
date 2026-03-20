import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'app_colors.dart';
import 'app_text_styles.dart';

/// 应用主题配置 - 基于 Figma 设计系统
class AppTheme {
  // 私有构造函数
  AppTheme._();
  
  /// 浅色主题
  static ThemeData get lightTheme {
    return ThemeData(
      useMaterial3: true,
      // brightness: Brightness.light,
      
      // 颜色方案
      colorScheme: ColorScheme.fromSeed(
        seedColor: AppColors.primaryBlue,
        brightness: Brightness.light,
        primary: AppColors.primaryBlue,
        onPrimary: AppColors.buttonText100,
        secondary: AppColors.gradientAux,
        onSecondary: AppColors.text90,
        error: AppColors.alertRed,
        onError: AppColors.buttonText100,
        surface: AppColors.white,
        onSurface: AppColors.text90,
        background: AppColors.background,
        onBackground: AppColors.text90,
      ),
      appBarTheme: const AppBarTheme(
        systemOverlayStyle: SystemUiOverlayStyle.dark,
      ),
      //
      // // 脚手架背景
      // scaffoldBackgroundColor: AppColors.background,
      //
      // // AppBar 主题
      // appBarTheme: AppBarTheme(
      //   backgroundColor: AppColors.white,
      //   foregroundColor: AppColors.text90,
      //   elevation: 0,
      //   centerTitle: true,
      //   titleTextStyle: AppTextStyles.h4,
      //   iconTheme: IconThemeData(
      //     color: AppColors.text70,
      //     size: 24,
      //   ),
      // ),
      //
      // // 文本主题
      // textTheme: TextTheme(
      //   displayLarge: AppTextStyles.h1,
      //   displayMedium: AppTextStyles.h2,
      //   displaySmall: AppTextStyles.h3,
      //   headlineLarge: AppTextStyles.h2,
      //   headlineMedium: AppTextStyles.h3,
      //   headlineSmall: AppTextStyles.h4,
      //   titleLarge: AppTextStyles.h4,
      //   titleMedium: AppTextStyles.label1,
      //   titleSmall: AppTextStyles.label2,
      //   bodyLarge: AppTextStyles.body1,
      //   bodyMedium: AppTextStyles.body2,
      //   bodySmall: AppTextStyles.body3,
      //   labelLarge: AppTextStyles.buttonLarge,
      //   labelMedium: AppTextStyles.buttonMedium,
      //   labelSmall: AppTextStyles.buttonSmall,
      // ),
      //
      // // 卡片主题
      // cardTheme: CardThemeData(
      //   color: AppColors.white,
      //   elevation: 0,
      //   shape: RoundedRectangleBorder(
      //     borderRadius: BorderRadius.circular(10),
      //   ),
      //   margin: const EdgeInsets.symmetric(horizontal: 16, vertical: 8),
      // ),
      //
      // // 输入框主题
      // inputDecorationTheme: InputDecorationTheme(
      //   filled: true,
      //   fillColor: AppColors.white,
      //   border: OutlineInputBorder(
      //     borderRadius: BorderRadius.circular(8),
      //     borderSide: BorderSide(color: AppColors.text20),
      //   ),
      //   enabledBorder: OutlineInputBorder(
      //     borderRadius: BorderRadius.circular(8),
      //     borderSide: BorderSide(color: AppColors.text20),
      //   ),
      //   focusedBorder: OutlineInputBorder(
      //     borderRadius: BorderRadius.circular(8),
      //     borderSide: BorderSide(color: AppColors.primaryBlue, width: 2),
      //   ),
      //   errorBorder: OutlineInputBorder(
      //     borderRadius: BorderRadius.circular(8),
      //     borderSide: BorderSide(color: AppColors.alertRed),
      //   ),
      //   focusedErrorBorder: OutlineInputBorder(
      //     borderRadius: BorderRadius.circular(8),
      //     borderSide: BorderSide(color: AppColors.alertRed, width: 2),
      //   ),
      //   hintStyle: AppTextStyles.body2.copyWith(color: AppColors.text50),
      //   labelStyle: AppTextStyles.label2.copyWith(color: AppColors.text70),
      //   errorStyle: AppTextStyles.error,
      // ),
      //
      // // 分隔符主题
      // dividerTheme: DividerThemeData(
      //   color: AppColors.text10,
      //   thickness: 0.5,
      //   space: 1,
      // ),
      //
      // // 图标主题
      // iconTheme: IconThemeData(
      //   color: AppColors.text70,
      //   size: 24,
      // ),
      //
      // // Chip 主题
      // chipTheme: ChipThemeData(
      //   backgroundColor: AppColors.white,
      //   selectedColor: AppColors.primaryBlue,
      //   labelStyle: AppTextStyles.label2,
      //   secondaryLabelStyle: AppTextStyles.label2.copyWith(
      //     color: AppColors.buttonText100,
      //   ),
      //   padding: const EdgeInsets.symmetric(horizontal: 10, vertical: 6),
      //   shape: RoundedRectangleBorder(
      //     borderRadius: BorderRadius.circular(12),
      //   ),
      //   side: BorderSide.none,
      // ),
      //
      // // Tab 主题
      // tabBarTheme: TabBarThemeData(
      //   labelColor: AppColors.text90,
      //   unselectedLabelColor: AppColors.text50,
      //   labelStyle: AppTextStyles.label1,
      //   unselectedLabelStyle: AppTextStyles.label1.copyWith(
      //     color: AppColors.text50,
      //   ),
      //   indicator: UnderlineTabIndicator(
      //     borderSide: BorderSide(color: AppColors.primaryBlue, width: 2),
      //   ),
      //   indicatorSize: TabBarIndicatorSize.label,
      //   dividerColor: Colors.transparent,
      // ),
      //
      // // Dialog 主题
      // dialogTheme: DialogThemeData(
      //   backgroundColor: AppColors.white,
      //   shape: RoundedRectangleBorder(
      //     borderRadius: BorderRadius.circular(16),
      //   ),
      //   titleTextStyle: AppTextStyles.h4,
      //   contentTextStyle: AppTextStyles.body2,
      // ),
      //
      // // Bottom Sheet 主题
      // bottomSheetTheme: const BottomSheetThemeData(
      //   backgroundColor: AppColors.white,
      //   shape: RoundedRectangleBorder(
      //     borderRadius: BorderRadius.vertical(top: Radius.circular(16)),
      //   ),
      // ),
    );
  }
  
  /// 暗色主题 (如果需要)
  static ThemeData get darkTheme {
    // 可以基于浅色主题调整暗色版本
    return lightTheme.copyWith(
      brightness: Brightness.dark,
      scaffoldBackgroundColor: const Color(0xFF121212),
      // 其他暗色调整...
    );
  }
}