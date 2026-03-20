import 'package:go_router/go_router.dart';
import 'package:ui/features/welcome/pages/welcome_page/welcome_page.dart';

/// 欢迎页模块路由配置
List<GoRoute> welcomeRoutes = [
  // 欢迎页
  GoRoute(
    path: '/welcome/welcome_page',
    name: 'welcome/welcome_page',
    builder: (context, state) => const WelcomePage(),
  ),
];
