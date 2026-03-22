import 'dart:async';

import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:ui/services/special_permission.dart';
import 'package:ui/theme/app_colors.dart';
import 'package:ui/utils/ui.dart';
import 'package:ui/widgets/common_app_bar.dart';

class TermuxSettingPage extends StatefulWidget {
  const TermuxSettingPage({super.key});

  @override
  State<TermuxSettingPage> createState() => _TermuxSettingPageState();
}

class _TermuxSettingPageState extends State<TermuxSettingPage>
    with WidgetsBindingObserver {
  static const int _maxPrepareLogLines = 160;

  bool _isLoadingStatus = true;
  bool _isDeviceSupported = false;
  bool _isRuntimeReady = false;
  bool _isBasePackagesReady = false;
  bool _isWorkspaceStorageGranted = false;
  List<String> _missingCommands = const <String>[];
  String _runtimeStatusMessage = '';

  bool _isPreparingWrapper = false;
  bool _isOpeningWorkspaceStorageSettings = false;

  String? _wrapperMessage;
  bool? _wrapperReady;
  String? _prepareStage;
  double _prepareProgress = 0.0;
  final List<String> _prepareLogLines = <String>[];
  late final ScrollController _prepareLogScrollController;
  Timer? _prepareSnapshotPoller;

  bool get _isFullyReady {
    return _isDeviceSupported && _isRuntimeReady && _isBasePackagesReady;
  }

  bool get _shouldShowPrepareConsole {
    return _isPreparingWrapper ||
        (_wrapperReady == false && _prepareLogLines.isNotEmpty);
  }

  @override
  void initState() {
    super.initState();
    _prepareLogScrollController = ScrollController();
    WidgetsBinding.instance.addObserver(this);
    _refreshStatus();
  }

  @override
  void dispose() {
    _prepareSnapshotPoller?.cancel();
    _prepareLogScrollController.dispose();
    WidgetsBinding.instance.removeObserver(this);
    super.dispose();
  }

  @override
  void didChangeAppLifecycleState(AppLifecycleState state) {
    if (state == AppLifecycleState.resumed) {
      _refreshStatus();
    }
  }

  Future<void> _refreshStatus() async {
    if (!mounted) {
      return;
    }

    setState(() {
      _isLoadingStatus = true;
    });

    try {
      final status = await getEmbeddedTerminalRuntimeStatus();
      if (!mounted) {
        return;
      }
      setState(() {
        _isDeviceSupported = status.supported;
        _isRuntimeReady = status.runtimeReady;
        _isBasePackagesReady = status.basePackagesReady;
        _isWorkspaceStorageGranted = status.workspaceAccessGranted;
        _missingCommands = status.missingCommands;
        _runtimeStatusMessage = status.message;
        _wrapperReady = status.allReady;
        _isLoadingStatus = false;
      });
    } on PlatformException {
      final supported = await isTermuxInstalled();
      final workspaceStorageGranted = await isWorkspaceStorageAccessGranted();
      if (!mounted) {
        return;
      }
      setState(() {
        _isDeviceSupported = supported;
        _isRuntimeReady = false;
        _isBasePackagesReady = false;
        _isWorkspaceStorageGranted = workspaceStorageGranted;
        _missingCommands = const <String>[];
        _runtimeStatusMessage = '状态探测失败，请尝试初始化。';
        _wrapperReady = false;
        _isLoadingStatus = false;
      });
    } catch (_) {
      final supported = await isTermuxInstalled();
      final workspaceStorageGranted = await isWorkspaceStorageAccessGranted();
      if (!mounted) {
        return;
      }
      setState(() {
        _isDeviceSupported = supported;
        _isRuntimeReady = false;
        _isBasePackagesReady = false;
        _isWorkspaceStorageGranted = workspaceStorageGranted;
        _missingCommands = const <String>[];
        _runtimeStatusMessage = '状态探测失败，请尝试初始化。';
        _wrapperReady = false;
        _isLoadingStatus = false;
      });
    }
  }

  Future<void> _handleOpenWorkspaceStorageSettings() async {
    if (_isOpeningWorkspaceStorageSettings) {
      return;
    }
    setState(() {
      _isOpeningWorkspaceStorageSettings = true;
    });
    try {
      await openWorkspaceStorageSettings();
    } on PlatformException catch (e) {
      showToast(e.message ?? '打开公共 workspace 权限页失败', type: ToastType.error);
    } catch (_) {
      showToast('打开公共 workspace 权限页失败', type: ToastType.error);
    } finally {
      if (mounted) {
        setState(() {
          _isOpeningWorkspaceStorageSettings = false;
        });
      }
    }
  }

  Future<void> _handlePrepareWrapper() async {
    if (_isPreparingWrapper) {
      return;
    }
    if (!_isDeviceSupported) {
      showToast('当前设备暂不支持内嵌终端，仅支持 arm64-v8a', type: ToastType.warning);
      return;
    }

    setState(() {
      _isPreparingWrapper = true;
      _wrapperMessage = null;
      _wrapperReady = null;
      _prepareStage = '准备开始';
      _prepareProgress = 0.02;
      _prepareLogLines
        ..clear()
        ..add('[系统] 正在启动内嵌 Ubuntu 环境初始化...');
    });
    _startPrepareSnapshotPolling();
    _schedulePrepareLogAutoScroll();
    try {
      final result = await prepareTermuxLiveWrapper();
      final success = result['success'] == true;
      final message = (result['message'] as String?)?.trim();
      await _pollPrepareSnapshot();
      if (!mounted) {
        return;
      }
      setState(() {
        _wrapperReady = success;
        _wrapperMessage = message;
      });
      showToast(
        message?.isNotEmpty == true ? message! : '内嵌终端环境已完成检查',
        type: success ? ToastType.success : ToastType.warning,
      );
    } on PlatformException catch (e) {
      await _pollPrepareSnapshot();
      if (mounted) {
        setState(() {
          _prepareStage = '初始化失败';
          _prepareLogLines.add('[错误] ${e.message ?? '检查内嵌终端环境失败'}');
        });
        _schedulePrepareLogAutoScroll();
      }
      showToast(e.message ?? '检查内嵌终端环境失败', type: ToastType.error);
    } catch (_) {
      await _pollPrepareSnapshot();
      if (mounted) {
        setState(() {
          _prepareStage = '初始化失败';
          _prepareLogLines.add('[错误] 检查内嵌终端环境失败');
        });
        _schedulePrepareLogAutoScroll();
      }
      showToast('检查内嵌终端环境失败', type: ToastType.error);
    } finally {
      await _pollPrepareSnapshot();
      _stopPrepareSnapshotPolling();
      if (mounted) {
        setState(() {
          _isPreparingWrapper = false;
        });
        await _refreshStatus();
      }
    }
  }

  void _startPrepareSnapshotPolling() {
    _stopPrepareSnapshotPolling();
    _pollPrepareSnapshot();
    _prepareSnapshotPoller = Timer.periodic(const Duration(milliseconds: 250), (
      _,
    ) {
      _pollPrepareSnapshot();
    });
  }

  void _stopPrepareSnapshotPolling() {
    _prepareSnapshotPoller?.cancel();
    _prepareSnapshotPoller = null;
  }

  Future<void> _pollPrepareSnapshot() async {
    try {
      final snapshot = await getEmbeddedTerminalInitSnapshot();
      if (!mounted) {
        return;
      }

      final nextLogLines = snapshot.logLines.length > _maxPrepareLogLines
          ? snapshot.logLines.sublist(
              snapshot.logLines.length - _maxPrepareLogLines,
            )
          : snapshot.logLines;
      final shouldScroll =
          nextLogLines.isNotEmpty &&
          nextLogLines.join('\n') != _prepareLogLines.join('\n');

      setState(() {
        if (snapshot.stage.isNotEmpty) {
          _prepareStage = snapshot.stage;
        }
        _prepareProgress = snapshot.progress;
        if (nextLogLines.isNotEmpty) {
          _prepareLogLines
            ..clear()
            ..addAll(nextLogLines);
        }
        if (snapshot.success != null) {
          _wrapperReady = snapshot.success;
        }
      });

      if (shouldScroll) {
        _schedulePrepareLogAutoScroll();
      }

      if (snapshot.completed) {
        _stopPrepareSnapshotPolling();
      }
    } catch (_) {
      return;
    }
  }

  void _schedulePrepareLogAutoScroll() {
    WidgetsBinding.instance.addPostFrameCallback((_) {
      if (!mounted || !_prepareLogScrollController.hasClients) {
        return;
      }
      _prepareLogScrollController.jumpTo(
        _prepareLogScrollController.position.maxScrollExtent,
      );
    });
  }

  String _buildRuntimeSubtitle() {
    if (_isLoadingStatus) {
      return '正在进行运行时功能检查';
    }
    if (!_isDeviceSupported) {
      return '当前设备 ABI 不受支持，仅支持 arm64-v8a';
    }
    if (!_isRuntimeReady) {
      return _runtimeStatusMessage.isNotEmpty
          ? _runtimeStatusMessage
          : '内嵌 Ubuntu 运行时未就绪，请先初始化。';
    }
    if (!_isBasePackagesReady) {
      if (_missingCommands.isNotEmpty) {
        return '缺失基础 CLI：${_missingCommands.join(", ")}';
      }
      return _runtimeStatusMessage.isNotEmpty
          ? _runtimeStatusMessage
          : '基础 Agent CLI 包未就绪，请执行初始化。';
    }
    return 'Ubuntu 运行时与基础 CLI 包已就绪。';
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      backgroundColor: const Color(0xFFF6F8FA),
      appBar: const CommonAppBar(title: '内嵌终端设置', primary: true),
      body: SafeArea(
        top: false,
        child: RefreshIndicator(
          onRefresh: _refreshStatus,
          child: SingleChildScrollView(
            physics: const AlwaysScrollableScrollPhysics(),
            padding: const EdgeInsets.fromLTRB(16, 12, 16, 24),
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [_buildStatusAndActionsCard()],
            ),
          ),
        ),
      ),
    );
  }

  Widget _buildStatusAndActionsCard() {
    final runtimeActionText = _isLoadingStatus
        ? '检测中'
        : (!_isDeviceSupported
              ? '不支持'
              : (_isPreparingWrapper
                    ? '初始化中...'
                    : (_isFullyReady ? '已就绪' : '初始化')));
    final runtimeActionColor = _isLoadingStatus
        ? AppColors.text50
        : (!_isDeviceSupported
              ? const Color(0xFFB34A40)
              : (_isPreparingWrapper
                    ? const Color(0xFFD08A00)
                    : (_isFullyReady
                          ? const Color(0xFF1E9E63)
                          : const Color(0xFFE58A00))));
    final runtimeActionEnabled =
        !_isLoadingStatus &&
        _isDeviceSupported &&
        !_isPreparingWrapper &&
        !_isFullyReady;

    final workspaceActionText = _isLoadingStatus
        ? '检测中'
        : (_isWorkspaceStorageGranted
              ? '已就绪'
              : (_isOpeningWorkspaceStorageSettings ? '打开中...' : '去授权'));
    final workspaceActionColor = _isLoadingStatus
        ? AppColors.text50
        : (_isWorkspaceStorageGranted
              ? const Color(0xFF1E9E63)
              : AppColors.buttonPrimary);
    final workspaceActionEnabled =
        !_isLoadingStatus &&
        !_isWorkspaceStorageGranted &&
        !_isOpeningWorkspaceStorageSettings;

    return _buildSectionCard(
      title: '当前状态与操作',
      child: Column(
        children: [
          _StatusActionRow(
            title: '内嵌 Ubuntu 运行时',
            subtitle: _buildRuntimeSubtitle(),
            actionText: runtimeActionText,
            actionColor: runtimeActionColor,
            onTap: runtimeActionEnabled ? _handlePrepareWrapper : null,
          ),
          const SizedBox(height: 12),
          _StatusActionRow(
            title: '公共 workspace 访问',
            subtitle: _isLoadingStatus
                ? '正在检测共享工作区访问能力'
                : (_isWorkspaceStorageGranted
                      ? '已允许访问 /storage/emulated/0/workspace'
                      : '未授权，文件工具和工作区预览会受限'),
            actionText: workspaceActionText,
            actionColor: workspaceActionColor,
            onTap: workspaceActionEnabled
                ? _handleOpenWorkspaceStorageSettings
                : null,
          ),
          const SizedBox(height: 14),
          _buildPrepareActionButton(),
          if (_shouldShowPrepareConsole) ...[
            const SizedBox(height: 14),
            _buildPrepareConsolePanel(),
          ],
          if (_wrapperMessage != null &&
              _wrapperMessage!.isNotEmpty &&
              !_shouldShowPrepareConsole) ...[
            const SizedBox(height: 12),
            Container(
              width: double.infinity,
              padding: const EdgeInsets.all(12),
              decoration: BoxDecoration(
                color: (_wrapperReady == true)
                    ? const Color(0x141E9E63)
                    : const Color(0x14EF6B5F),
                borderRadius: BorderRadius.circular(12),
              ),
              child: Text(
                _wrapperMessage!,
                style: TextStyle(
                  color: _wrapperReady == true
                      ? const Color(0xFF1E9E63)
                      : const Color(0xFFB34A40),
                  fontSize: 12,
                  fontWeight: FontWeight.w500,
                  height: 1.55,
                ),
              ),
            ),
          ],
        ],
      ),
    );
  }

  Widget _buildPrepareConsolePanel() {
    final stageText =
        _prepareStage ?? (_isPreparingWrapper ? '初始化进行中' : '最近一次初始化日志');
    final consoleText = _prepareLogLines.isEmpty
        ? '[系统] 初始化已启动，等待终端输出...'
        : _prepareLogLines.join('\n');

    return Container(
      width: double.infinity,
      padding: const EdgeInsets.all(14),
      decoration: BoxDecoration(
        color: const Color(0xFF0B1220),
        borderRadius: BorderRadius.circular(14),
      ),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Row(
            children: [
              Container(
                width: 10,
                height: 10,
                decoration: BoxDecoration(
                  color: _isPreparingWrapper
                      ? const Color(0xFFF6C94C)
                      : (_wrapperReady == true
                            ? const Color(0xFF1E9E63)
                            : const Color(0xFF8091A7)),
                  shape: BoxShape.circle,
                ),
              ),
              const SizedBox(width: 8),
              Expanded(
                child: Text(
                  stageText,
                  style: const TextStyle(
                    color: Colors.white,
                    fontSize: 12,
                    fontWeight: FontWeight.w600,
                  ),
                ),
              ),
            ],
          ),
          const SizedBox(height: 12),
          Container(
            width: double.infinity,
            constraints: const BoxConstraints(maxHeight: 220, minHeight: 120),
            padding: const EdgeInsets.all(12),
            decoration: BoxDecoration(
              color: const Color(0xFF111B2E),
              borderRadius: BorderRadius.circular(12),
              border: Border.all(color: const Color(0x1FFFFFFF)),
            ),
            child: SingleChildScrollView(
              controller: _prepareLogScrollController,
              child: SelectableText(
                consoleText,
                style: const TextStyle(
                  color: Color(0xFFE2E8F0),
                  fontSize: 12,
                  fontFamily: 'monospace',
                  height: 1.55,
                ),
              ),
            ),
          ),
        ],
      ),
    );
  }

  Widget _buildPrepareActionButton() {
    final progress = _isPreparingWrapper
        ? _prepareProgress.clamp(0.0, 1.0).toDouble()
        : 0.0;
    final progressPercent = (progress * 100).round();

    final bool disabled =
        _isPreparingWrapper || !_isDeviceSupported || _isFullyReady;
    final bool showInitState =
        _isDeviceSupported && !_isPreparingWrapper && !_isFullyReady;
    final buttonText = _isPreparingWrapper
        ? '初始化中... $progressPercent%'
        : (!_isDeviceSupported
              ? '当前设备不支持（仅 arm64-v8a）'
              : (_isFullyReady ? '内嵌 Ubuntu 已就绪' : '初始化内嵌 Ubuntu 环境'));
    final gradientColors = _isPreparingWrapper
        ? const [Color(0xFF1930D9), Color(0xFF2DA5F0)]
        : (!_isDeviceSupported
              ? const [Color(0xFFBFC7D5), Color(0xFFA6AFBE)]
              : (showInitState
                    ? const [Color(0xFFE58A00), Color(0xFFFFB84D)]
                    : const [Color(0xFF1E9E63), Color(0xFF45C07B)]));

    return Material(
      color: Colors.transparent,
      child: Ink(
        decoration: ShapeDecoration(
          gradient: LinearGradient(
            begin: const Alignment(0.14, -1.09),
            end: const Alignment(1.10, 1.26),
            colors: gradientColors,
          ),
          shape: RoundedRectangleBorder(
            borderRadius: BorderRadius.circular(12),
          ),
        ),
        child: InkWell(
          borderRadius: BorderRadius.circular(12),
          onTap: disabled ? null : _handlePrepareWrapper,
          child: SizedBox(
            width: double.infinity,
            height: 46,
            child: LayoutBuilder(
              builder: (context, constraints) {
                return Stack(
                  children: [
                    if (_isPreparingWrapper)
                      ClipRRect(
                        borderRadius: BorderRadius.circular(12),
                        child: Align(
                          alignment: Alignment.centerLeft,
                          child: AnimatedContainer(
                            duration: const Duration(milliseconds: 220),
                            curve: Curves.easeOutCubic,
                            width: constraints.maxWidth * progress,
                            height: double.infinity,
                            decoration: const BoxDecoration(
                              gradient: LinearGradient(
                                begin: Alignment.centerLeft,
                                end: Alignment.centerRight,
                                colors: [Color(0x4DFFFFFF), Color(0x2AFFFFFF)],
                              ),
                            ),
                          ),
                        ),
                      ),
                    Center(
                      child: Text(
                        buttonText,
                        textAlign: TextAlign.center,
                        style: const TextStyle(
                          color: Colors.white,
                          fontSize: 14,
                          fontWeight: FontWeight.w600,
                        ),
                      ),
                    ),
                  ],
                );
              },
            ),
          ),
        ),
      ),
    );
  }

  Widget _buildSectionCard({required String title, required Widget child}) {
    return Container(
      width: double.infinity,
      padding: const EdgeInsets.all(16),
      decoration: BoxDecoration(
        color: Colors.white,
        borderRadius: BorderRadius.circular(14),
        boxShadow: [AppColors.boxShadow],
      ),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Text(
            title,
            style: const TextStyle(
              color: AppColors.text,
              fontSize: 15,
              fontWeight: FontWeight.w600,
            ),
          ),
          const SizedBox(height: 14),
          child,
        ],
      ),
    );
  }
}

class _StatusActionRow extends StatelessWidget {
  static const double _subtitleSlotHeight = 36;
  static const double _actionButtonMinWidth = 72;

  const _StatusActionRow({
    required this.title,
    required this.subtitle,
    required this.actionText,
    required this.actionColor,
    this.onTap,
  });

  final String title;
  final String subtitle;
  final String actionText;
  final Color actionColor;
  final VoidCallback? onTap;

  @override
  Widget build(BuildContext context) {
    final enabled = onTap != null;
    return Container(
      padding: const EdgeInsets.all(14),
      decoration: BoxDecoration(
        color: const Color(0xFFF8FAFD),
        borderRadius: BorderRadius.circular(12),
        border: Border.all(color: const Color(0x14000000)),
      ),
      child: Row(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Expanded(
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Text(
                  title,
                  style: const TextStyle(
                    color: AppColors.text,
                    fontSize: 14,
                    fontWeight: FontWeight.w600,
                  ),
                ),
                const SizedBox(height: 4),
                SizedBox(
                  height: _subtitleSlotHeight,
                  child: Align(
                    alignment: Alignment.topLeft,
                    child: Text(
                      subtitle,
                      maxLines: 2,
                      overflow: TextOverflow.ellipsis,
                      style: TextStyle(
                        color: AppColors.text.withValues(alpha: 0.66),
                        fontSize: 12,
                        height: 1.5,
                      ),
                    ),
                  ),
                ),
              ],
            ),
          ),
          const SizedBox(width: 12),
          GestureDetector(
            onTap: onTap,
            child: Container(
              height: 36,
              constraints: const BoxConstraints(minWidth: _actionButtonMinWidth),
              padding: const EdgeInsets.symmetric(horizontal: 14),
              decoration: BoxDecoration(
                color: actionColor.withValues(alpha: enabled ? 0.14 : 0.10),
                borderRadius: BorderRadius.circular(10),
              ),
              alignment: Alignment.center,
              child: Text(
                actionText,
                style: TextStyle(
                  color: enabled
                      ? actionColor
                      : actionColor.withValues(alpha: 0.88),
                  fontSize: 12,
                  fontWeight: FontWeight.w700,
                ),
              ),
            ),
          ),
        ],
      ),
    );
  }
}
