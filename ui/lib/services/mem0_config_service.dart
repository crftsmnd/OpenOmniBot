import 'package:flutter/services.dart';
import 'package:ui/models/mem0_config.dart';

class Mem0ConfigService {
  static const MethodChannel _channel = MethodChannel(
    'cn.com.omnimind.bot/Mem0Config',
  );

  static Future<Mem0Config> getConfig() async {
    final result = await _channel.invokeMethod<Map<dynamic, dynamic>>(
      'getConfig',
    );
    return Mem0Config.fromMap(result ?? const {});
  }

  static Future<Mem0Config?> getResolvedConfig() async {
    final result = await _channel.invokeMethod<Map<dynamic, dynamic>>(
      'getResolvedConfig',
    );
    final config = Mem0Config.fromMap(result ?? const {});
    return config.isConfigured ? config : null;
  }

  static Future<Mem0Config> saveConfig(Mem0Config config) async {
    final result = await _channel.invokeMethod<Map<dynamic, dynamic>>(
      'saveConfig',
      config.toMap(),
    );
    return Mem0Config.fromMap(result ?? const {});
  }

  static Future<void> clearConfig() async {
    await _channel.invokeMethod('clearConfig');
  }
}
