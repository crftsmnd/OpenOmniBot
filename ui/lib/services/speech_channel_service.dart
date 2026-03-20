import 'package:flutter/foundation.dart';
import 'package:flutter/services.dart';

const MethodChannel speechRecognition = MethodChannel('cn.com.omnimind.bot/SpeechRecognition');
const EventChannel speechRecognitionEvents = EventChannel('cn.com.omnimind.bot/SpeechRecognitionEvents');

/// ASR 语音识别服务（走你们 asr-service 转发）
class AsrSpeechRecognitionService {
  static bool _isInitialized = false;

  /// 进入语音界面时预取 asr ws token（不建 WS / 不开录音）
  static Future<bool> initialize() async {
    try {
      final result = await speechRecognition.invokeMethod('initialize');
      _isInitialized = result == true;
      return _isInitialized;
    } on PlatformException catch (e) {
      debugPrint('Failed to initialize Speech Recognition: ${e.message}');
      _isInitialized = false;
      return false;
    }
  }

  static Future<bool> ensureInitialized() async {
    if (_isInitialized) return true;
    return initialize();
  }

  /// 开始录音识别
  static Future<bool> startRecording() async {
    try {
      final result = await speechRecognition.invokeMethod('startRecording');
      return result == true;
    } on PlatformException catch (e) {
      debugPrint('Failed to start recording: ${e.message}');
      return false;
    }
  }

  /// 停止录音识别
  static Future<void> stopRecording() async {
    try {
      await speechRecognition.invokeMethod('stopRecording');
    } on PlatformException catch (e) {
      debugPrint('Failed to stop recording: ${e.message}');
    }
  }

  /// 仅停止发送音频，等待服务端主动断开
  static Future<void> stopSendingOnly() async {
    try {
      await speechRecognition.invokeMethod('stopSendingOnly');
    } on PlatformException catch (e) {
      debugPrint('Failed to stop sending audio: ${e.message}');
    }
  }

  /// 释放资源
  static Future<void> release() async {
    try {
      await speechRecognition.invokeMethod('release');
    } on PlatformException catch (e) {
      debugPrint('Failed to release: ${e.message}');
    }
  }

  /// 是否已初始化
  static bool get isInitialized => _isInitialized;

  /// 重置初始化状态（当 WS 侧报 4401/401 等鉴权错误时，下次进页面重新 initialize）
  static void resetInitState() {
    _isInitialized = false;
  }
}
