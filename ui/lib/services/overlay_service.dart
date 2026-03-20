import 'package:flutter/foundation.dart';
import 'package:flutter/services.dart';

/// Overlay服务，用于与原生OverlayChannel通信
/// !!暂不使用!!
class OverlayService {
  static const MethodChannel _channel = MethodChannel(
    'cn.com.omnimind.bot/overlay',
  );

  /// 显示消息提示（在MessageView中显示）
  /// [message] 要显示的消息内容
  static Future<bool> showMessage(String message) async {
    try {
      final result = await _channel.invokeMethod('showMessage', {
        'message': message,
      });
      return result == true;
    } on PlatformException catch (e) {
      if (kDebugMode) {
        print('显示消息失败: ${e.message}');
      }
      return false;
    }
  }
}
