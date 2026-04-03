part of 'chat_input_area.dart';

mixin _ChatInputAreaRecordingMixin on _ChatInputAreaStateBase {
  // ==================== 录音相关方法 ====================

  /// 切换录音状态（开始/停止）
  Future<void> toggleRecording() async {
    final shouldStop = _recordingState != RecordingState.idle;

    // Flutter 侧互斥：同一时刻只允许一个 start/stop 逻辑在跑
    if (_toggleInProgress) {
      if (!shouldStop) {
        _showFastTapToast();
      }
      return;
    }

    if (!shouldStop) {
      // Flutter 侧时间窗口限流：频繁点击不下发到原生
      final now = DateTime.now().millisecondsSinceEpoch;
      if (now - _lastToggleAcceptedAtMs < _toggleMinIntervalMs) {
        _showFastTapToast();
        return;
      }
      _lastToggleAcceptedAtMs = now;
    }

    _toggleInProgress = true;
    try {
      widget.focusNode.unfocus();

      if (shouldStop) {
        await _stopRecording();
        return;
      }

      await _startRecording();
    } finally {
      _toggleInProgress = false;
    }
  }

  void _showFastTapToast() {
    final now = DateTime.now().millisecondsSinceEpoch;
    if (now - _lastFastTapToastAtMs < _fastTapToastMinIntervalMs) return;
    _lastFastTapToastAtMs = now;

    if (!mounted) return;
    final messenger = ScaffoldMessenger.maybeOf(context);
    if (messenger == null) return;
    messenger.hideCurrentSnackBar();
    messenger.showSnackBar(
      const SnackBar(
        content: Text('请不要点击太快'),
        duration: Duration(milliseconds: 800),
        behavior: SnackBarBehavior.floating,
      ),
    );
  }

  /// 开始录音
  Future<void> _startRecording() async {
    _setRecordingState(RecordingState.starting);

    try {
      var status = await Permission.microphone.status;

      if (!status.isGranted) {
        await requestPermission(['android.permission.RECORD_AUDIO']);
        _setRecordingState(RecordingState.idle);
        return;
      }

      // 开始录音
      final bool started = await AsrSpeechRecognitionService.startRecording()
          .timeout(_startTimeout, onTimeout: () => false);
      if (!started) {
        if (mounted) {
          ScaffoldMessenger.of(
            context,
          ).showSnackBar(const SnackBar(content: Text('暂未实现，请耐心等待')));
        }
        _setRecordingState(RecordingState.idle);
        return;
      }

      await _transcriptionSubscription?.cancel();
      _streamDoneCompleter = Completer<void>();

      // 保存录音开始前的文本，用于追加模式
      _textBeforeRecording = widget.controller.text;
      _currentTranscript = '';

      _transcriptionSubscription = speechRecognitionEvents
          .receiveBroadcastStream()
          .listen(
            (transcript) {
              debugPrint(
                "[SpeechRecognition] Received transcript: $transcript",
              );
              if (mounted &&
                  (_recordingState == RecordingState.recording ||
                      _recordingState == RecordingState.stopping ||
                      _recordingState == RecordingState.waitingServerStop)) {
                // 直接更新 controller，实时显示识别结果
                _currentTranscript = transcript;
                if (_textBeforeRecording.isNotEmpty) {
                  widget.controller.text =
                      '$_textBeforeRecording $_currentTranscript';
                } else {
                  widget.controller.text = _currentTranscript;
                }
                // 移动光标到末尾
                widget.controller.selection = TextSelection.fromPosition(
                  TextPosition(offset: widget.controller.text.length),
                );
                // 滚动到末尾
                WidgetsBinding.instance.addPostFrameCallback((_) {
                  if (_textFieldScrollController.hasClients) {
                    _textFieldScrollController.jumpTo(
                      _textFieldScrollController.position.maxScrollExtent,
                    );
                  }
                });
              }
            },
            onError: (error) {
              debugPrint(
                "[SpeechRecognition] Transcription stream error: $error",
              );
              final isServerInitiatedStop =
                  error is PlatformException && error.code == 'ASR_FINAL';
              _completeStreamDone();
              _handleTranscriptionEnded(
                error: error,
                isServerInitiatedStop: isServerInitiatedStop,
              );
            },
            onDone: () {
              debugPrint("[SpeechRecognition] Transcription stream done");
              _completeStreamDone();
              _handleTranscriptionEnded();
            },
          );

      if (_recordingState != RecordingState.starting) {
        await AsrSpeechRecognitionService.stopSendingOnly().timeout(
          _stopTimeout,
          onTimeout: () => null,
        );
        return;
      }

      _setRecordingState(RecordingState.recording);
    } on PlatformException catch (e) {
      debugPrint("Failed to start recording: '${e.message}'.");
      _setRecordingState(RecordingState.idle);
    } on TimeoutException {
      if (mounted) {
        ScaffoldMessenger.of(
          context,
        ).showSnackBar(const SnackBar(content: Text('启动录音超时')));
      }
      _setRecordingState(RecordingState.idle);
    }
  }

  /// 停止录音
  Future<void> _stopRecording() async {
    if (_recordingState != RecordingState.recording &&
        _recordingState != RecordingState.starting &&
        _recordingState != RecordingState.waitingServerStop) {
      return;
    }

    if (_recordingState != RecordingState.waitingServerStop) {
      // 先渲染“停止中”UI，再执行 stop 逻辑，避免体感卡顿
      _setRecordingState(RecordingState.stopping);
      await Future<void>.delayed(Duration.zero);

      try {
        await AsrSpeechRecognitionService.stopSendingOnly().timeout(
          _stopTimeout,
          onTimeout: () => null,
        );
        if (_recordingState != RecordingState.idle) {
          _setRecordingState(RecordingState.waitingServerStop);
        }
      } catch (e) {
        debugPrint("Failed to stop recording: $e");
      }
    }

    if (_recordingState == RecordingState.idle) {
      return;
    }
    await _waitForServerStop();
    await _handleTranscriptionEnded();
  }

  Future<void> _waitForServerStop() async {
    final streamDoneCompleter = _streamDoneCompleter;
    if (streamDoneCompleter == null) return;

    final streamDone = streamDoneCompleter.future;
    final timeoutCompleter = Completer<void>();
    var timedOut = false;
    _waitingServerStopTimer?.cancel();
    _waitingServerStopTimer = Timer(_waitingServerStopTimeout, () {
      if (!timeoutCompleter.isCompleted) {
        timedOut = true;
        timeoutCompleter.complete();
      }
    });
    await Future.any<void>([streamDone, timeoutCompleter.future]);
    _waitingServerStopTimer?.cancel();
    _waitingServerStopTimer = null;

    if (timedOut &&
        !streamDoneCompleter.isCompleted &&
        _recordingState == RecordingState.waitingServerStop) {
      debugPrint(
        '[SpeechRecognition] waitingServerStop timed out, forcing stop',
      );
      await AsrSpeechRecognitionService.stopRecording().timeout(
        _stopTimeout,
        onTimeout: () => null,
      );
      _completeStreamDone();
    }
  }

  void _completeStreamDone() {
    if (!(_streamDoneCompleter?.isCompleted ?? true)) {
      _streamDoneCompleter?.complete();
    }
  }

  Future<void> _handleTranscriptionEnded({
    Object? error,
    bool isServerInitiatedStop = false,
  }) async {
    if (_isFinalizingTranscription) return;
    _isFinalizingTranscription = true;

    _completeStreamDone();
    _waitingServerStopTimer?.cancel();
    _waitingServerStopTimer = null;

    try {
      // 检查是否是 token 过期错误，如果是则重新初始化
      if (error != null && error.toString().contains('TOKEN_EXPIRED')) {
        debugPrint(
          "[SpeechRecognition] Token expired, will reinitialize on next recording",
        );
        AsrSpeechRecognitionService.resetInitState();
      }
      if (isServerInitiatedStop) {
        debugPrint('[SpeechRecognition] ASR_FINAL received, stopping session');
      }

      await _transcriptionSubscription?.cancel();
      _transcriptionSubscription = null;
      _streamDoneCompleter = null;

      if (!mounted) {
        _recordingState = RecordingState.idle;
        _currentTranscript = '';
        return;
      }
      setState(() {
        _recordingState = RecordingState.idle;
        _currentTranscript = '';
      });
      widget.onRecordingStateChanged?.call(RecordingState.idle);
    } finally {
      _isFinalizingTranscription = false;
    }
  }

  /// 点击语音识别文字时，切换到输入模式
  void _onTranscriptTap() {
    if (isRecording) {
      // 停止录音，不再自动聚焦输入框，避免自动弹出软键盘
      _stopRecording();
    }
  }
}
