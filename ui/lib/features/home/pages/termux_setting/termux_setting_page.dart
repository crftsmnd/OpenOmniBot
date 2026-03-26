import 'dart:async';

import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:ui/services/special_permission.dart';
import 'package:ui/theme/app_colors.dart';
import 'package:ui/utils/ui.dart';
import 'package:ui/widgets/common_app_bar.dart';
import 'package:url_launcher/url_launcher_string.dart';

class _SetupPackageDefinition {
  const _SetupPackageDefinition({
    required this.id,
    required this.name,
    required this.description,
  });

  final String id;
  final String name;
  final String description;
}

class _SetupCategoryDefinition {
  const _SetupCategoryDefinition({
    required this.id,
    required this.name,
    required this.description,
    required this.packages,
    this.operitRequired = false,
  });

  final String id;
  final String name;
  final String description;
  final bool operitRequired;
  final List<_SetupPackageDefinition> packages;
}

const List<_SetupCategoryDefinition> _kSetupCategories =
    <_SetupCategoryDefinition>[
      _SetupCategoryDefinition(
        id: 'nodejs',
        name: 'Node.js 环境',
        description: 'Node.js 和前端开发环境',
        operitRequired: true,
        packages: <_SetupPackageDefinition>[
          _SetupPackageDefinition(
            id: 'nodejs',
            name: 'Node.js',
            description: 'JavaScript 运行时',
          ),
          _SetupPackageDefinition(
            id: 'pnpm',
            name: 'PNPM',
            description: '快速的包管理器和 TypeScript',
          ),
        ],
      ),
      _SetupCategoryDefinition(
        id: 'python',
        name: 'Python 环境',
        description: 'Python 开发环境',
        operitRequired: true,
        packages: <_SetupPackageDefinition>[
          _SetupPackageDefinition(
            id: 'python-is-python3',
            name: 'Python 链接',
            description: '将python命令链接到python3',
          ),
          _SetupPackageDefinition(
            id: 'python3-venv',
            name: '虚拟环境',
            description: 'Python 虚拟环境支持',
          ),
          _SetupPackageDefinition(
            id: 'python3-pip',
            name: 'Pip',
            description: 'Python 包管理器',
          ),
          _SetupPackageDefinition(
            id: 'uv',
            name: 'uv',
            description: '一个用 Rust 编写的极速 Python 包安装器',
          ),
        ],
      ),
      _SetupCategoryDefinition(
        id: 'ssh',
        name: 'SSH 工具',
        description: 'SSH 客户端和密码认证工具',
        packages: <_SetupPackageDefinition>[
          _SetupPackageDefinition(
            id: 'ssh',
            name: 'SSH 客户端',
            description: 'SSH 连接客户端',
          ),
          _SetupPackageDefinition(
            id: 'sshpass',
            name: 'sshpass',
            description: 'SSH 密码认证工具',
          ),
          _SetupPackageDefinition(
            id: 'openssh-server',
            name: 'OpenSSH 服务器',
            description: '用于反向隧道挂载本地文件系统',
          ),
        ],
      ),
      _SetupCategoryDefinition(
        id: 'java',
        name: 'Java 环境',
        description: 'Java 开发环境',
        packages: <_SetupPackageDefinition>[
          _SetupPackageDefinition(
            id: 'openjdk-17',
            name: 'OpenJDK 17',
            description: 'Java 17 开发环境',
          ),
          _SetupPackageDefinition(
            id: 'gradle',
            name: 'Gradle',
            description: '现代化的构建自动化工具',
          ),
        ],
      ),
      _SetupCategoryDefinition(
        id: 'rust',
        name: 'Rust (Cargo) 环境',
        description: 'Rust 开发环境和包管理器',
        packages: <_SetupPackageDefinition>[
          _SetupPackageDefinition(
            id: 'rust',
            name: 'Rust & Cargo',
            description: '通过 rustup 安装 Rust 工具链',
          ),
        ],
      ),
      _SetupCategoryDefinition(
        id: 'go',
        name: 'Go 环境',
        description: 'Go 语言开发环境',
        packages: <_SetupPackageDefinition>[
          _SetupPackageDefinition(id: 'go', name: 'Go', description: 'Go 编程语言'),
        ],
      ),
      _SetupCategoryDefinition(
        id: 'tools',
        name: '常用工具',
        description: 'Git 版本控制与 FFmpeg 音视频处理工具',
        packages: <_SetupPackageDefinition>[
          _SetupPackageDefinition(
            id: 'git',
            name: 'Git',
            description: '分布式版本控制工具',
          ),
          _SetupPackageDefinition(
            id: 'ffmpeg',
            name: 'FFmpeg',
            description: '音视频编解码与处理工具',
          ),
        ],
      ),
    ];

class TermuxSettingPage extends StatefulWidget {
  const TermuxSettingPage({super.key});

  @override
  State<TermuxSettingPage> createState() => _TermuxSettingPageState();
}

class _TermuxSettingPageState extends State<TermuxSettingPage>
    with WidgetsBindingObserver {
  bool _isLoadingStatus = true;
  bool _isLoadingSetupStatus = true;
  bool _isInstallingPackages = false;
  bool _showSetupTerminal = false;
  bool _isDeviceSupported = false;
  bool _isRuntimeReady = false;
  bool _isBasePackagesReady = false;
  List<String> _missingCommands = const <String>[];
  String _runtimeStatusMessage = '';
  Map<String, bool> _installedSetupPackages = const <String, bool>{};
  EmbeddedTerminalSetupSessionSnapshot _setupSessionSnapshot =
      const EmbeddedTerminalSetupSessionSnapshot(
        sessionId: null,
        running: false,
        completed: false,
        success: null,
        message: '',
        selectedPackageIds: <String>[],
      );
  final Map<String, bool> _selectedSetupPackages = <String, bool>{};

  bool _isLoadingGatewayStatus = true;
  bool _isUpdatingGatewayAutoStart = false;
  bool _isStartingGateway = false;
  bool _isStoppingGateway = false;
  bool _isPollingGatewayStatus = false;

  Timer? _gatewayStatusPoller;
  Timer? _gatewayUptimeTicker;
  Timer? _setupSessionPoller;
  OpenClawGatewayStatus? _gatewayStatus;
  DateTime? _gatewayStatusSnapshotAt;
  String? _lastCompletedSetupSessionId;

  @override
  void initState() {
    super.initState();
    WidgetsBinding.instance.addObserver(this);
    _startGatewayStatusPolling();
    _startGatewayUptimeTicker();
    _refreshStatus();
  }

  @override
  void dispose() {
    _gatewayStatusPoller?.cancel();
    _gatewayUptimeTicker?.cancel();
    _setupSessionPoller?.cancel();
    WidgetsBinding.instance.removeObserver(this);
    super.dispose();
  }

  @override
  void didChangeAppLifecycleState(AppLifecycleState state) {
    if (state == AppLifecycleState.resumed) {
      _startGatewayStatusPolling();
      _startGatewayUptimeTicker();
      _startSetupSessionPollingIfNeeded();
      _refreshStatus();
      return;
    }
    if (state == AppLifecycleState.inactive ||
        state == AppLifecycleState.paused ||
        state == AppLifecycleState.detached) {
      _gatewayStatusPoller?.cancel();
      _gatewayStatusPoller = null;
      _gatewayUptimeTicker?.cancel();
      _gatewayUptimeTicker = null;
      _setupSessionPoller?.cancel();
      _setupSessionPoller = null;
    }
  }

  void _startGatewayStatusPolling() {
    _gatewayStatusPoller?.cancel();
    _gatewayStatusPoller = Timer.periodic(const Duration(seconds: 3), (_) {
      unawaited(_refreshGatewayStatusOnly());
    });
  }

  void _startGatewayUptimeTicker() {
    _gatewayUptimeTicker?.cancel();
    _gatewayUptimeTicker = Timer.periodic(const Duration(seconds: 1), (_) {
      if (!mounted) {
        return;
      }
      final status = _gatewayStatus;
      if (status == null || !(status.running && status.healthy)) {
        return;
      }
      setState(() {});
    });
  }

  void _startSetupSessionPollingIfNeeded() {
    if (!_setupSessionSnapshot.running || _setupSessionPoller != null) {
      return;
    }
    _setupSessionPoller = Timer.periodic(const Duration(seconds: 2), (_) {
      unawaited(_refreshSetupSessionSnapshot());
    });
  }

  void _stopSetupSessionPolling() {
    _setupSessionPoller?.cancel();
    _setupSessionPoller = null;
  }

  Future<void> _refreshStatus() async {
    if (!mounted) {
      return;
    }

    setState(() {
      _isLoadingStatus = true;
      _isLoadingSetupStatus = true;
      _isLoadingGatewayStatus = true;
    });

    try {
      final initialResults = await Future.wait<dynamic>([
        getEmbeddedTerminalRuntimeStatus(),
        getEmbeddedTerminalSetupSessionSnapshot(),
        getOpenClawGatewayStatus(),
      ]);
      final status = initialResults[0] as EmbeddedTerminalRuntimeStatus;
      final setupSessionSnapshot =
          initialResults[1] as EmbeddedTerminalSetupSessionSnapshot;
      final gatewayStatus = initialResults[2] as OpenClawGatewayStatus;
      EmbeddedTerminalSetupStatus? setupStatus;
      if (!setupSessionSnapshot.running) {
        setupStatus = await getEmbeddedTerminalSetupStatus();
      }
      if (!mounted) {
        return;
      }
      setState(() {
        _isDeviceSupported = status.supported;
        _isRuntimeReady = status.runtimeReady;
        _isBasePackagesReady = status.basePackagesReady;
        _missingCommands = status.missingCommands;
        _runtimeStatusMessage = status.message;
        _setupSessionSnapshot = setupSessionSnapshot;
        if (setupStatus != null) {
          _installedSetupPackages = Map<String, bool>.from(
            setupStatus.packages,
          );
          _selectedSetupPackages.removeWhere(
            (packageId, _) => _installedSetupPackages[packageId] == true,
          );
        }
        _gatewayStatus = gatewayStatus;
        _gatewayStatusSnapshotAt = DateTime.now();
        _isLoadingStatus = false;
        _isLoadingSetupStatus = false;
        _isLoadingGatewayStatus = false;
        if (setupSessionSnapshot.running || _isInstallingPackages) {
          _showSetupTerminal = true;
        }
      });
      if (setupSessionSnapshot.running) {
        _startSetupSessionPollingIfNeeded();
      } else {
        _stopSetupSessionPolling();
      }
    } on PlatformException {
      final supported = await isTermuxInstalled();
      OpenClawGatewayStatus? gatewayStatus;
      EmbeddedTerminalSetupSessionSnapshot? setupSessionSnapshot;
      try {
        gatewayStatus = await getOpenClawGatewayStatus();
      } catch (_) {
        gatewayStatus = null;
      }
      try {
        setupSessionSnapshot = await getEmbeddedTerminalSetupSessionSnapshot();
      } catch (_) {
        setupSessionSnapshot = null;
      }
      if (!mounted) {
        return;
      }
      setState(() {
        _isDeviceSupported = supported;
        _isRuntimeReady = false;
        _isBasePackagesReady = false;
        _missingCommands = const <String>[];
        _runtimeStatusMessage = '状态探测失败，请尝试初始化。';
        _installedSetupPackages = const <String, bool>{};
        if (setupSessionSnapshot != null) {
          _setupSessionSnapshot = setupSessionSnapshot;
          if (setupSessionSnapshot.running || _isInstallingPackages) {
            _showSetupTerminal = true;
          }
        }
        _selectedSetupPackages.clear();
        _isLoadingStatus = false;
        _isLoadingSetupStatus = false;
        _gatewayStatus = gatewayStatus;
        _gatewayStatusSnapshotAt = gatewayStatus == null
            ? null
            : DateTime.now();
        _isLoadingGatewayStatus = false;
      });
      if (setupSessionSnapshot?.running == true) {
        _startSetupSessionPollingIfNeeded();
      } else {
        _stopSetupSessionPolling();
      }
    } catch (_) {
      final supported = await isTermuxInstalled();
      OpenClawGatewayStatus? gatewayStatus;
      EmbeddedTerminalSetupSessionSnapshot? setupSessionSnapshot;
      try {
        gatewayStatus = await getOpenClawGatewayStatus();
      } catch (_) {
        gatewayStatus = null;
      }
      try {
        setupSessionSnapshot = await getEmbeddedTerminalSetupSessionSnapshot();
      } catch (_) {
        setupSessionSnapshot = null;
      }
      if (!mounted) {
        return;
      }
      setState(() {
        _isDeviceSupported = supported;
        _isRuntimeReady = false;
        _isBasePackagesReady = false;
        _missingCommands = const <String>[];
        _runtimeStatusMessage = '状态探测失败，请尝试初始化。';
        _installedSetupPackages = const <String, bool>{};
        if (setupSessionSnapshot != null) {
          _setupSessionSnapshot = setupSessionSnapshot;
          if (setupSessionSnapshot.running || _isInstallingPackages) {
            _showSetupTerminal = true;
          }
        }
        _selectedSetupPackages.clear();
        _isLoadingStatus = false;
        _isLoadingSetupStatus = false;
        _gatewayStatus = gatewayStatus;
        _gatewayStatusSnapshotAt = gatewayStatus == null
            ? null
            : DateTime.now();
        _isLoadingGatewayStatus = false;
      });
      if (setupSessionSnapshot?.running == true) {
        _startSetupSessionPollingIfNeeded();
      } else {
        _stopSetupSessionPolling();
      }
    }
  }

  Future<void> _refreshSetupSessionSnapshot() async {
    if (!mounted) {
      return;
    }
    try {
      final snapshot = await getEmbeddedTerminalSetupSessionSnapshot();
      if (!mounted) {
        return;
      }
      final shouldRefreshInstalledStatus =
          _setupSessionSnapshot.running &&
          !snapshot.running &&
          snapshot.completed;
      setState(() {
        _setupSessionSnapshot = snapshot;
        _isInstallingPackages = false;
        if (snapshot.running || snapshot.hasSession) {
          _showSetupTerminal = true;
        }
      });
      if (snapshot.running) {
        _startSetupSessionPollingIfNeeded();
        return;
      }
      _stopSetupSessionPolling();
      if (snapshot.completed &&
          snapshot.hasSession &&
          snapshot.sessionId != _lastCompletedSetupSessionId) {
        _lastCompletedSetupSessionId = snapshot.sessionId;
        final completionToastMessage = _buildSetupCompletionToastMessage(
          snapshot,
        );
        if (completionToastMessage != null) {
          showToast(
            completionToastMessage,
            type: snapshot.success == true
                ? ToastType.success
                : ToastType.error,
          );
        }
      }
      if (shouldRefreshInstalledStatus) {
        await _refreshStatus();
      }
    } catch (_) {}
  }

  Future<void> _refreshGatewayStatusOnly({bool showLoading = false}) async {
    if (!mounted || _isPollingGatewayStatus) {
      return;
    }
    if (showLoading) {
      setState(() {
        _isLoadingGatewayStatus = true;
      });
    }
    _isPollingGatewayStatus = true;
    try {
      final gatewayStatus = await getOpenClawGatewayStatus();
      if (!mounted) {
        return;
      }
      setState(() {
        _gatewayStatus = gatewayStatus;
        _gatewayStatusSnapshotAt = DateTime.now();
        _isLoadingGatewayStatus = false;
      });
    } catch (_) {
      if (!mounted) {
        return;
      }
      if (showLoading) {
        setState(() {
          _isLoadingGatewayStatus = false;
        });
      }
    } finally {
      _isPollingGatewayStatus = false;
    }
  }

  Future<void> _handleGatewayAutoStartChanged(bool enabled) async {
    if (_isUpdatingGatewayAutoStart) {
      return;
    }
    setState(() {
      _isUpdatingGatewayAutoStart = true;
    });
    try {
      await setOpenClawGatewayAutoStart(enabled);
      await _refreshStatus();
    } on PlatformException catch (e) {
      showToast(e.message ?? '更新自动守护开关失败', type: ToastType.error);
    } catch (_) {
      showToast('更新自动守护开关失败', type: ToastType.error);
    } finally {
      if (mounted) {
        setState(() {
          _isUpdatingGatewayAutoStart = false;
        });
      }
    }
  }

  Future<void> _handleStartGateway({bool forceRestart = false}) async {
    if (_isStartingGateway) {
      return;
    }
    setState(() {
      _isStartingGateway = true;
    });
    try {
      await startOpenClawGateway(forceRestart: forceRestart);
      showToast(
        forceRestart ? 'Gateway 正在重启' : 'Gateway 正在启动',
        type: ToastType.success,
      );
      await _refreshStatus();
    } on PlatformException catch (e) {
      showToast(e.message ?? '启动 Gateway 失败', type: ToastType.error);
    } catch (_) {
      showToast('启动 Gateway 失败', type: ToastType.error);
    } finally {
      if (mounted) {
        setState(() {
          _isStartingGateway = false;
        });
      }
    }
  }

  Future<void> _handleStopGateway() async {
    if (_isStoppingGateway) {
      return;
    }
    setState(() {
      _isStoppingGateway = true;
    });
    try {
      await stopOpenClawGateway();
      showToast('Gateway 已停止', type: ToastType.success);
      await _refreshStatus();
    } on PlatformException catch (e) {
      showToast(e.message ?? '停止 Gateway 失败', type: ToastType.error);
    } catch (_) {
      showToast('停止 Gateway 失败', type: ToastType.error);
    } finally {
      if (mounted) {
        setState(() {
          _isStoppingGateway = false;
        });
      }
    }
  }

  List<String> get _selectedPendingPackageIds {
    return _kSetupCategories
        .expand((category) => category.packages)
        .map((pkg) => pkg.id)
        .where(
          (packageId) =>
              _selectedSetupPackages[packageId] == true &&
              _installedSetupPackages[packageId] != true,
        )
        .toList(growable: false);
  }

  Future<void> _handleInstallPackages() async {
    if (_isInstallingPackages || _isLoadingSetupStatus) {
      return;
    }
    if (!_isDeviceSupported) {
      showToast('当前设备暂不支持内嵌终端，仅支持 arm64-v8a', type: ToastType.warning);
      return;
    }

    final selectedPackageIds = _selectedPendingPackageIds;
    if (selectedPackageIds.isEmpty) {
      showToast('请先选择需要安装的环境组件', type: ToastType.warning);
      return;
    }

    final confirmed = await showDialog<bool>(
      context: context,
      builder: (dialogContext) {
        return AlertDialog(
          title: const Text('开始环境配置'),
          content: const Text(
            '即将开始环境配置，这可能需要一些时间。请尽量保持应用在前台或小窗运行以确保配置顺利进行。\n\n如果配置意外中断，不必担心。下次回到本页面再次开始配置时，会自动从上次的进度继续。',
          ),
          actions: [
            TextButton(
              onPressed: () => Navigator.of(dialogContext).pop(false),
              child: const Text('取消'),
            ),
            FilledButton(
              onPressed: () => Navigator.of(dialogContext).pop(true),
              child: const Text('开始配置'),
            ),
          ],
        );
      },
    );

    if (confirmed != true || !mounted) {
      return;
    }

    setState(() {
      _isInstallingPackages = true;
      _showSetupTerminal = true;
      _setupSessionSnapshot = const EmbeddedTerminalSetupSessionSnapshot(
        sessionId: null,
        running: true,
        completed: false,
        success: null,
        message: '正在启动环境配置终端...',
        selectedPackageIds: <String>[],
      );
    });

    try {
      final snapshot = await startEmbeddedTerminalSetupSession(
        selectedPackageIds,
      );
      if (!mounted) {
        return;
      }
      setState(() {
        _setupSessionSnapshot = snapshot;
        _isInstallingPackages = false;
        _showSetupTerminal = snapshot.running || snapshot.hasSession;
      });
      if (snapshot.running) {
        _startSetupSessionPollingIfNeeded();
        return;
      }
      _stopSetupSessionPolling();
      final completionToastMessage = _buildSetupCompletionToastMessage(
        snapshot,
      );
      if (completionToastMessage != null) {
        showToast(
          completionToastMessage,
          type: snapshot.success == true ? ToastType.success : ToastType.error,
        );
      }
      if (!snapshot.hasSession) {
        setState(() {
          _showSetupTerminal = false;
        });
      }
      await _refreshStatus();
    } on PlatformException catch (e) {
      if (!mounted) {
        return;
      }
      setState(() {
        _showSetupTerminal = false;
        _setupSessionSnapshot = const EmbeddedTerminalSetupSessionSnapshot(
          sessionId: null,
          running: false,
          completed: false,
          success: null,
          message: '',
          selectedPackageIds: <String>[],
        );
      });
      showToast(e.message ?? '环境配置失败', type: ToastType.error);
    } catch (_) {
      if (!mounted) {
        return;
      }
      setState(() {
        _showSetupTerminal = false;
        _setupSessionSnapshot = const EmbeddedTerminalSetupSessionSnapshot(
          sessionId: null,
          running: false,
          completed: false,
          success: null,
          message: '',
          selectedPackageIds: <String>[],
        );
      });
      showToast('环境配置失败', type: ToastType.error);
    } finally {
      if (mounted) {
        setState(() {
          _isInstallingPackages = false;
        });
      }
    }
  }

  Future<void> _handleReturnToSetupList() async {
    if (_setupSessionSnapshot.running || _isInstallingPackages) {
      return;
    }
    setState(() {
      _showSetupTerminal = false;
    });
    await _refreshStatus();
  }

  String? _buildSetupCompletionToastMessage(
    EmbeddedTerminalSetupSessionSnapshot snapshot,
  ) {
    if (!snapshot.completed) {
      return null;
    }
    if (snapshot.success == true) {
      final rawMessage = snapshot.message.trim();
      if (rawMessage == '所选组件均已安装。') {
        return rawMessage;
      }
      return '环境配置完成';
    }
    if (!snapshot.hasSession) {
      return '环境配置失败，请稍后重试';
    }
    return null;
  }

  String _buildSetupTerminalMessage() {
    if (_isInstallingPackages && !_setupSessionSnapshot.hasSession) {
      return '正在启动环境配置终端...';
    }
    if (_setupSessionSnapshot.running) {
      return '环境配置进行中，请在下方终端查看实时输出。';
    }
    if (_setupSessionSnapshot.completed) {
      if (_setupSessionSnapshot.success == true) {
        return '环境配置已完成，返回配置项后会自动刷新安装状态。';
      }
      return '环境配置已结束，请查看下方终端输出确认安装结果。';
    }
    return '终端会话已准备就绪。';
  }

  String _buildSetupRuntimeMessage() {
    if (_isInstallingPackages && !_setupSessionSnapshot.hasSession) {
      return '正在启动原生终端并准备环境配置会话...';
    }
    if (_setupSessionSnapshot.running) {
      return '环境配置进行中，请在下方终端查看实时输出。';
    }
    if (_isLoadingStatus) {
      return '正在检查 Ubuntu 运行时状态...';
    }
    if (!_isDeviceSupported) {
      return '当前设备 ABI 不受支持，仅支持 arm64-v8a。';
    }
    if (!_isRuntimeReady) {
      final runtimeMessage = _runtimeStatusMessage.trim();
      if (runtimeMessage.isNotEmpty) {
        return '$runtimeMessage\n首次开始配置时会自动初始化 Ubuntu 运行时。';
      }
      return 'Ubuntu 运行时尚未初始化，首次开始配置时会自动初始化。';
    }

    final totalCount = _kSetupCategories.fold<int>(
      0,
      (sum, category) => sum + category.packages.length,
    );
    final installedCount = _installedSetupPackages.values
        .where((installed) => installed)
        .length;
    if (_isLoadingSetupStatus) {
      return 'Ubuntu 运行时已就绪，正在检查环境组件安装状态...';
    }
    if (_isBasePackagesReady) {
      return 'Ubuntu 运行时与常用 CLI 已就绪，已安装 $installedCount / $totalCount 个环境组件。';
    }
    if (_missingCommands.isNotEmpty) {
      return 'Ubuntu 运行时已就绪，已安装 $installedCount / $totalCount 个环境组件。当前仍探测到缺少：${_missingCommands.join(", ")}';
    }
    if (installedCount == 0) {
      return 'Ubuntu 运行时已就绪，尚未安装任何开发环境组件。';
    }
    return 'Ubuntu 运行时已就绪，已安装 $installedCount / $totalCount 个环境组件。';
  }

  Widget _buildEnvironmentSetupCard() {
    final hasPendingSelection = _selectedPendingPackageIds.isNotEmpty;
    return _buildSectionCard(
      title: '环境配置',
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          const Text(
            '选择需要安装的开发环境和工具（支持并行安装）',
            style: TextStyle(
              color: Color(0xFF64748B),
              fontSize: 13,
              fontWeight: FontWeight.w500,
            ),
          ),
          const SizedBox(height: 12),
          _buildSetupStatusBanner(),
          const SizedBox(height: 14),
          if (_showSetupTerminal)
            _buildSetupTerminalPanel()
          else if (_isLoadingSetupStatus)
            const Padding(
              padding: EdgeInsets.symmetric(vertical: 20),
              child: Center(child: CircularProgressIndicator(strokeWidth: 2.4)),
            )
          else
            Column(
              children: [
                for (final category in _kSetupCategories) ...[
                  _buildSetupCategoryCard(category),
                  const SizedBox(height: 12),
                ],
              ],
            ),
          SizedBox(
            width: double.infinity,
            child: _showSetupTerminal
                ? OutlinedButton.icon(
                    onPressed:
                        (_setupSessionSnapshot.running || _isInstallingPackages)
                        ? null
                        : _handleReturnToSetupList,
                    icon: const Icon(Icons.view_list_rounded),
                    label: const Text('返回配置项'),
                  )
                : FilledButton(
                    onPressed:
                        (_isInstallingPackages ||
                            _isLoadingSetupStatus ||
                            _setupSessionSnapshot.running ||
                            !_isDeviceSupported ||
                            !hasPendingSelection)
                        ? null
                        : _handleInstallPackages,
                    child: _isInstallingPackages
                        ? const SizedBox(
                            width: 18,
                            height: 18,
                            child: CircularProgressIndicator(
                              strokeWidth: 2.2,
                              color: Colors.white,
                            ),
                          )
                        : const Text('开始配置'),
                  ),
          ),
        ],
      ),
    );
  }

  Widget _buildSetupTerminalPanel() {
    final message = _buildSetupTerminalMessage();
    final sessionId = _setupSessionSnapshot.sessionId?.trim();

    return Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        Container(
          width: double.infinity,
          padding: const EdgeInsets.all(12),
          decoration: BoxDecoration(
            color: const Color(0xFFF8FAFD),
            borderRadius: BorderRadius.circular(12),
            border: Border.all(color: const Color(0x14000000)),
          ),
          child: Row(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              Icon(
                _setupSessionSnapshot.running
                    ? Icons.terminal_rounded
                    : (_setupSessionSnapshot.success == true
                          ? Icons.check_circle_rounded
                          : (_setupSessionSnapshot.completed
                                ? Icons.error_outline_rounded
                                : Icons.info_outline_rounded)),
                size: 18,
                color: _setupSessionSnapshot.running
                    ? AppColors.buttonPrimary
                    : (_setupSessionSnapshot.success == true
                          ? const Color(0xFF1E9E63)
                          : (_setupSessionSnapshot.completed
                                ? const Color(0xFFB45309)
                                : const Color(0xFF64748B))),
              ),
              const SizedBox(width: 8),
              Expanded(
                child: Text(
                  message,
                  style: const TextStyle(
                    color: AppColors.text,
                    fontSize: 12,
                    fontWeight: FontWeight.w600,
                    height: 1.5,
                  ),
                ),
              ),
            ],
          ),
        ),
        const SizedBox(height: 12),
        ClipRRect(
          borderRadius: BorderRadius.circular(14),
          child: Container(
            height: 420,
            width: double.infinity,
            color: const Color(0xFF111827),
            child: (sessionId == null || sessionId.isEmpty)
                ? const Center(
                    child: Column(
                      mainAxisSize: MainAxisSize.min,
                      children: [
                        SizedBox(
                          width: 26,
                          height: 26,
                          child: CircularProgressIndicator(
                            strokeWidth: 2.4,
                            color: Colors.white,
                          ),
                        ),
                        SizedBox(height: 12),
                        Text(
                          '正在创建原生终端...',
                          style: TextStyle(
                            color: Colors.white70,
                            fontSize: 13,
                            fontWeight: FontWeight.w600,
                          ),
                        ),
                      ],
                    ),
                  )
                : AndroidView(
                    viewType: 'cn.com.omnimind.bot/embedded_terminal_view',
                    creationParams: <String, Object>{'sessionId': sessionId},
                    creationParamsCodec: const StandardMessageCodec(),
                  ),
          ),
        ),
      ],
    );
  }

  Widget _buildSetupStatusBanner() {
    final message = _buildSetupRuntimeMessage();
    final Color backgroundColor;
    final Color foregroundColor;

    if (_isLoadingStatus || _isLoadingSetupStatus) {
      backgroundColor = const Color(0x140F62FE);
      foregroundColor = const Color(0xFF0F62FE);
    } else if (!_isDeviceSupported) {
      backgroundColor = const Color(0x14EF6B5F);
      foregroundColor = const Color(0xFFB34A40);
    } else if (!_isRuntimeReady) {
      backgroundColor = const Color(0x14F59E0B);
      foregroundColor = const Color(0xFFB45309);
    } else {
      backgroundColor = const Color(0x141E9E63);
      foregroundColor = const Color(0xFF1E9E63);
    }

    return Container(
      width: double.infinity,
      padding: const EdgeInsets.all(12),
      decoration: BoxDecoration(
        color: backgroundColor,
        borderRadius: BorderRadius.circular(12),
      ),
      child: Text(
        message,
        style: TextStyle(
          color: foregroundColor,
          fontSize: 12,
          fontWeight: FontWeight.w600,
          height: 1.55,
        ),
      ),
    );
  }

  Widget _buildSetupCategoryCard(_SetupCategoryDefinition category) {
    final installedCount = category.packages
        .where((pkg) => _installedSetupPackages[pkg.id] == true)
        .length;

    return Container(
      decoration: BoxDecoration(
        color: const Color(0xFFF8FAFD),
        borderRadius: BorderRadius.circular(12),
        border: Border.all(color: const Color(0x14000000)),
      ),
      child: Padding(
        padding: const EdgeInsets.all(14),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Row(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Expanded(
                  child: Wrap(
                    spacing: 8,
                    runSpacing: 8,
                    crossAxisAlignment: WrapCrossAlignment.center,
                    children: [
                      Text(
                        category.name,
                        style: const TextStyle(
                          color: AppColors.text,
                          fontSize: 14,
                          fontWeight: FontWeight.w700,
                        ),
                      ),
                      if (category.operitRequired)
                        Container(
                          padding: const EdgeInsets.symmetric(
                            horizontal: 8,
                            vertical: 4,
                          ),
                          decoration: BoxDecoration(
                            color: const Color(0x14F59E0B),
                            borderRadius: BorderRadius.circular(999),
                          ),
                          child: const Text(
                            'Operit 必须',
                            style: TextStyle(
                              color: Color(0xFFB45309),
                              fontSize: 10,
                              fontWeight: FontWeight.w700,
                            ),
                          ),
                        ),
                    ],
                  ),
                ),
                const SizedBox(width: 12),
                Text(
                  '已安装 $installedCount / ${category.packages.length}',
                  style: const TextStyle(
                    color: AppColors.text70,
                    fontSize: 11,
                    fontWeight: FontWeight.w600,
                  ),
                ),
              ],
            ),
            const SizedBox(height: 12),
            for (var index = 0; index < category.packages.length; index++) ...[
              _buildSetupPackageTile(category.packages[index]),
              if (index != category.packages.length - 1)
                const Divider(height: 1, color: Color(0x14000000)),
            ],
          ],
        ),
      ),
    );
  }

  Widget _buildSetupPackageTile(_SetupPackageDefinition pkg) {
    final installed = _installedSetupPackages[pkg.id] == true;
    final selected = installed || _selectedSetupPackages[pkg.id] == true;
    final statusLabel = installed ? 'ready' : 'lost';
    final statusBackgroundColor = installed
        ? const Color(0x141E9E63)
        : const Color(0x140F62FE);
    final statusForegroundColor = installed
        ? const Color(0xFF1E9E63)
        : const Color(0xFF0F62FE);
    return InkWell(
      borderRadius: BorderRadius.circular(10),
      onTap: (installed || _isInstallingPackages)
          ? null
          : () {
              setState(() {
                _selectedSetupPackages[pkg.id] = !selected;
              });
            },
      child: Padding(
        padding: const EdgeInsets.symmetric(vertical: 10),
        child: Row(
          crossAxisAlignment: CrossAxisAlignment.center,
          children: [
            Theme(
              data: Theme.of(context).copyWith(
                checkboxTheme: CheckboxThemeData(
                  shape: RoundedRectangleBorder(
                    borderRadius: BorderRadius.circular(4),
                  ),
                ),
              ),
              child: Checkbox(
                value: selected,
                onChanged: (installed || _isInstallingPackages)
                    ? null
                    : (value) {
                        setState(() {
                          _selectedSetupPackages[pkg.id] = value == true;
                        });
                      },
                activeColor: installed
                    ? const Color(0xFF1E9E63)
                    : const Color(0xFF0F62FE),
                checkColor: Colors.white,
                side: BorderSide(
                  color: installed
                      ? const Color(0xFF1E9E63)
                      : const Color(0xFF0F62FE),
                  width: 1.4,
                ),
              ),
            ),
            Expanded(
              child: Row(
                children: [
                  Expanded(
                    child: Text(
                      pkg.name,
                      style: const TextStyle(
                        color: AppColors.text,
                        fontSize: 13,
                        fontWeight: FontWeight.w600,
                      ),
                    ),
                  ),
                  Container(
                    padding: const EdgeInsets.symmetric(
                      horizontal: 10,
                      vertical: 4,
                    ),
                    decoration: BoxDecoration(
                      color: statusBackgroundColor,
                      borderRadius: BorderRadius.circular(999),
                    ),
                    child: Text(
                      statusLabel,
                      style: TextStyle(
                        color: statusForegroundColor,
                        fontSize: 11,
                        fontWeight: FontWeight.w700,
                        letterSpacing: 0.2,
                      ),
                    ),
                  ),
                ],
              ),
            ),
          ],
        ),
      ),
    );
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      backgroundColor: const Color(0xFFF6F8FA),
      appBar: const CommonAppBar(title: 'Ubuntu 与 OpenClaw', primary: true),
      body: SafeArea(
        top: false,
        child: RefreshIndicator(
          onRefresh: _refreshStatus,
          child: SingleChildScrollView(
            physics: const AlwaysScrollableScrollPhysics(),
            padding: const EdgeInsets.fromLTRB(16, 12, 16, 24),
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                _buildEnvironmentSetupCard(),
                const SizedBox(height: 12),
                _buildGatewayCard(),
              ],
            ),
          ),
        ),
      ),
    );
  }

  Widget _buildGatewayCard() {
    final gatewayStatus = _gatewayStatus;
    final lastError = gatewayStatus?.lastError?.trim() ?? '';
    final statusText = _buildGatewayStatusText(gatewayStatus);
    final subtitle = _isLoadingGatewayStatus
        ? '正在检查 OpenClaw Gateway 状态'
        : statusText;
    final isRunning = gatewayStatus?.running == true;
    final canOperate =
        gatewayStatus != null &&
        !_isLoadingGatewayStatus &&
        !_isStartingGateway &&
        !_isStoppingGateway;

    return _buildSectionCard(
      title: 'OpenClaw Gateway',
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Container(
            padding: const EdgeInsets.all(14),
            decoration: BoxDecoration(
              color: const Color(0xFFF8FAFD),
              borderRadius: BorderRadius.circular(12),
              border: Border.all(color: const Color(0x14000000)),
            ),
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Row(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: [
                    Expanded(
                      child: Text(
                        subtitle,
                        style: const TextStyle(
                          color: AppColors.text,
                          fontSize: 13,
                          fontWeight: FontWeight.w600,
                          height: 1.5,
                        ),
                      ),
                    ),
                    const SizedBox(width: 8),
                    Row(
                      mainAxisSize: MainAxisSize.min,
                      children: [
                        _buildGatewayIconAction(
                          icon: Icons.refresh_rounded,
                          tooltip: '重启 Gateway',
                          onTap: canOperate
                              ? () => _handleStartGateway(forceRestart: true)
                              : null,
                        ),
                        const SizedBox(width: 6),
                        _buildGatewayIconAction(
                          icon: isRunning
                              ? Icons.stop_circle_outlined
                              : Icons.play_circle_fill_rounded,
                          tooltip: isRunning ? '停止 Gateway' : '启动 Gateway',
                          onTap: canOperate
                              ? () {
                                  if (isRunning) {
                                    _handleStopGateway();
                                  } else {
                                    _handleStartGateway(forceRestart: false);
                                  }
                                }
                              : null,
                        ),
                      ],
                    ),
                  ],
                ),
                const SizedBox(height: 8),
                _buildGatewayAddressLink(gatewayStatus),
                const SizedBox(height: 12),
                Row(
                  children: [
                    Expanded(
                      child: Column(
                        crossAxisAlignment: CrossAxisAlignment.start,
                        children: [
                          const Text(
                            '应用内 Auto-start Gateway',
                            style: TextStyle(
                              color: AppColors.text,
                              fontSize: 13,
                              fontWeight: FontWeight.w600,
                            ),
                          ),
                          const SizedBox(height: 4),
                          Text(
                            gatewayStatus?.autoStartEnabled == true
                                ? 'App 冷启动时会自动恢复 GatewayService'
                                : '默认关闭，避免未确认的后台保活',
                            style: const TextStyle(
                              color: Color(0xFF64748B),
                              fontSize: 12,
                              fontWeight: FontWeight.w500,
                            ),
                          ),
                        ],
                      ),
                    ),
                    Switch(
                      value: gatewayStatus?.autoStartEnabled == true,
                      onChanged:
                          (_isLoadingGatewayStatus ||
                              _isUpdatingGatewayAutoStart ||
                              gatewayStatus == null)
                          ? null
                          : _handleGatewayAutoStartChanged,
                    ),
                  ],
                ),
              ],
            ),
          ),
          if (lastError.isNotEmpty) ...[
            const SizedBox(height: 12),
            Container(
              width: double.infinity,
              padding: const EdgeInsets.all(12),
              decoration: BoxDecoration(
                color: const Color(0x14EF6B5F),
                borderRadius: BorderRadius.circular(12),
              ),
              child: Text(
                lastError,
                style: const TextStyle(
                  color: Color(0xFFB34A40),
                  fontSize: 12,
                  fontWeight: FontWeight.w600,
                  height: 1.5,
                ),
              ),
            ),
          ],
        ],
      ),
    );
  }

  Widget _buildGatewayIconAction({
    required IconData icon,
    required String tooltip,
    required VoidCallback? onTap,
  }) {
    final enabled = onTap != null;
    return Tooltip(
      message: tooltip,
      child: IconButton(
        onPressed: onTap,
        icon: Icon(icon),
        iconSize: 20,
        visualDensity: VisualDensity.compact,
        style: IconButton.styleFrom(
          minimumSize: const Size(34, 34),
          maximumSize: const Size(34, 34),
          shape: RoundedRectangleBorder(
            borderRadius: BorderRadius.circular(10),
          ),
          foregroundColor: enabled
              ? AppColors.buttonPrimary
              : const Color(0xFF9CA3AF),
          backgroundColor: enabled
              ? const Color(0x1A4A7CFF)
              : const Color(0x10000000),
        ),
      ),
    );
  }

  Widget _buildGatewayAddressLink(OpenClawGatewayStatus? status) {
    final url = (status?.dashboardUrl ?? '').trim();
    final normalizedUrl = url.isNotEmpty ? url : 'http://127.0.0.1:18789';
    return Row(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        const Text(
          '地址：',
          style: TextStyle(
            color: Color(0xFF64748B),
            fontSize: 12,
            fontWeight: FontWeight.w500,
            height: 1.5,
          ),
        ),
        Expanded(
          child: InkWell(
            onTap: () => _openExternalUrl(normalizedUrl),
            borderRadius: BorderRadius.circular(6),
            child: Padding(
              padding: const EdgeInsets.symmetric(vertical: 1),
              child: Text(
                normalizedUrl,
                style: const TextStyle(
                  color: Color(0xFF2563EB),
                  fontSize: 12,
                  fontWeight: FontWeight.w600,
                  height: 1.5,
                  decoration: TextDecoration.underline,
                  decorationColor: Color(0xFF2563EB),
                ),
              ),
            ),
          ),
        ),
      ],
    );
  }

  Future<void> _openExternalUrl(String url) async {
    final launched = await launchUrlString(
      url,
      mode: LaunchMode.externalApplication,
    );
    if (!launched) {
      showToast('打开 Gateway 地址失败', type: ToastType.error);
    }
  }

  String _buildGatewayStatusText(OpenClawGatewayStatus? status) {
    if (status == null) {
      return '尚未获取到 Gateway 状态';
    }
    if (!status.installed) {
      return 'OpenClaw CLI 尚未安装，请先在聊天页完成一键部署';
    }
    if (!status.configured) {
      return 'OpenClaw 配置尚未写入，请先完成部署';
    }
    if (status.legacyConfigNeedsRedeploy) {
      return '检测到旧版部署痕迹，需要重新保存或重新部署一次';
    }
    if (status.restarting) {
      return 'Gateway 正在退避重启中';
    }
    if (status.running && status.healthy) {
      final liveUptimeSeconds = _effectiveGatewayUptimeSeconds(status);
      final uptimeText = liveUptimeSeconds == null
          ? ''
          : '，已运行 ${_formatUptime(liveUptimeSeconds)}';
      return 'Gateway 正在运行且健康$uptimeText';
    }
    if (status.running) {
      return 'Gateway 已启动，正在等待健康检查';
    }
    return 'Gateway 当前未运行';
  }

  int? _effectiveGatewayUptimeSeconds(OpenClawGatewayStatus status) {
    final base = status.uptimeSeconds;
    if (base == null) {
      return null;
    }
    if (!status.running) {
      return base;
    }
    final snapshotAt = _gatewayStatusSnapshotAt;
    if (snapshotAt == null) {
      return base;
    }
    final elapsed = DateTime.now().difference(snapshotAt).inSeconds;
    if (elapsed <= 0) {
      return base;
    }
    return base + elapsed;
  }

  String _formatUptime(int totalSeconds) {
    final hours = totalSeconds ~/ 3600;
    final minutes = (totalSeconds % 3600) ~/ 60;
    final seconds = totalSeconds % 60;
    if (hours > 0) {
      return '${hours}h ${minutes}m ${seconds}s';
    }
    if (minutes > 0) {
      return '${minutes}m ${seconds}s';
    }
    return '${seconds}s';
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
