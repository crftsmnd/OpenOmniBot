import 'package:flutter/material.dart';
import 'package:ui/services/model_provider_config_service.dart';
import 'package:ui/services/scene_model_config_service.dart';
import 'package:ui/theme/app_colors.dart';
import 'package:ui/utils/ui.dart';
import 'package:ui/widgets/common_app_bar.dart';

class SceneModelSettingPage extends StatefulWidget {
  const SceneModelSettingPage({super.key});

  @override
  State<SceneModelSettingPage> createState() => _SceneModelSettingPageState();
}

class _SceneModelSettingPageState extends State<SceneModelSettingPage> {
  static const List<String> _sceneOrder = [
    'scene.dispatch.model',
    'scene.vlm.operation.primary',
    'scene.compactor.context',
    'scene.loading.sprite',
  ];

  static const Map<String, String> _sceneDisplayNameMap = {
    'scene.dispatch.model': 'Agent',
    'scene.vlm.operation.primary': 'Operation',
    'scene.compactor.context': 'Compactor',
    'scene.loading.sprite': 'Loading',
  };

  static const Map<String, String> _sceneTooltipMap = {
    'scene.dispatch.model': '负责任务理解与分流决策',
    'scene.vlm.operation.primary': '负责执行 UI 操作主链路',
    'scene.compactor.context': '负责压缩上下文并纠错',
    'scene.loading.sprite': '负责生成加载状态文案',
  };

  bool _isLoading = true;
  bool _isRefreshingModels = false;

  List<SceneCatalogItem> _catalog = const [];
  List<SceneModelOverrideEntry> _overrides = const [];
  List<ProviderModelOption> _providerModels = const [];
  Set<String> _savingSceneIds = <String>{};
  Map<String, String> _sceneSelectedModels = {};

  @override
  void initState() {
    super.initState();
    _loadData();
  }

  List<SceneCatalogItem> get _orderedCatalog {
    final map = {
      for (final item in _catalog) item.sceneId: item,
    };

    final ordered = <SceneCatalogItem>[];
    for (final sceneId in _sceneOrder) {
      final item = map.remove(sceneId);
      if (item != null) {
        ordered.add(item);
      }
    }
    ordered.addAll(map.values);
    return ordered;
  }

  String _sceneDisplayName(String sceneId) {
    return _sceneDisplayNameMap[sceneId] ?? sceneId;
  }

  String _sceneTooltip(SceneCatalogItem item) {
    final mapped = _sceneTooltipMap[item.sceneId];
    if (mapped != null) {
      return mapped;
    }
    if (item.description.trim().isNotEmpty) {
      return item.description.trim();
    }
    return item.sceneId;
  }

  Map<String, String> _buildSceneSelectedModels(
    List<SceneCatalogItem> catalog,
    List<SceneModelOverrideEntry> overrides,
  ) {
    final selectedMap = <String, String>{
      for (final item in catalog) item.sceneId: '',
    };

    for (final entry in overrides) {
      if (selectedMap.containsKey(entry.sceneId)) {
        selectedMap[entry.sceneId] = entry.model;
      }
    }
    return selectedMap;
  }

  List<ProviderModelOption> _buildModelOptions({
    required List<ProviderModelOption> remoteModels,
    required List<String> manualModelIds,
    required List<SceneModelOverrideEntry> overrides,
  }) {
    final merged = ModelProviderConfigService.mergeModelOptions(
      remoteModels: remoteModels,
      manualModelIds: manualModelIds,
    ).toList();

    final existing = merged.map((item) => item.id).toSet();
    for (final override in overrides) {
      final modelId = override.model.trim();
      if (modelId.isEmpty || !existing.add(modelId)) {
        continue;
      }
      merged.add(
        ProviderModelOption(
          id: modelId,
          displayName: modelId,
          ownedBy: 'override',
        ),
      );
    }

    merged.sort((a, b) => a.id.toLowerCase().compareTo(b.id.toLowerCase()));
    return merged;
  }

  Future<void> _loadData() async {
    setState(() => _isLoading = true);
    try {
      final baseData = await Future.wait<dynamic>([
        SceneModelConfigService.getSceneCatalog(),
        SceneModelConfigService.getSceneModelOverrides(),
        ModelProviderConfigService.getManualModelIds(),
      ]);

      if (!mounted) return;

      final catalog = baseData[0] as List<SceneCatalogItem>;
      final overrides = baseData[1] as List<SceneModelOverrideEntry>;
      final manualModelIds = baseData[2] as List<String>;

      List<ProviderModelOption> remoteModels = const [];
      try {
        remoteModels = await ModelProviderConfigService.fetchModels();
      } catch (_) {
        remoteModels = const [];
      }

      if (!mounted) return;

      setState(() {
        _catalog = catalog;
        _overrides = overrides;
        _providerModels = _buildModelOptions(
          remoteModels: remoteModels,
          manualModelIds: manualModelIds,
          overrides: overrides,
        );
        _sceneSelectedModels = _buildSceneSelectedModels(catalog, overrides);
      });
    } catch (_) {
      if (!mounted) return;
      showToast('加载场景配置失败', type: ToastType.error);
    } finally {
      if (mounted) {
        setState(() => _isLoading = false);
      }
    }
  }

  Future<void> _refreshProviderModels() async {
    if (_isRefreshingModels) return;
    setState(() => _isRefreshingModels = true);
    try {
      final results = await Future.wait<dynamic>([
        ModelProviderConfigService.fetchModels(),
        ModelProviderConfigService.getManualModelIds(),
      ]);

      if (!mounted) return;

      final remoteModels = results[0] as List<ProviderModelOption>;
      final manualModelIds = results[1] as List<String>;

      setState(() {
        _providerModels = _buildModelOptions(
          remoteModels: remoteModels,
          manualModelIds: manualModelIds,
          overrides: _overrides,
        );
      });
      showToast(
        _providerModels.isEmpty ? '当前没有可用模型' : '已更新 ${_providerModels.length} 个模型',
        type: _providerModels.isEmpty ? ToastType.warning : ToastType.success,
      );
    } catch (e) {
      if (!mounted) return;
      showToast('刷新模型列表失败：$e', type: ToastType.error);
    } finally {
      if (mounted) {
        setState(() => _isRefreshingModels = false);
      }
    }
  }

  bool _isSavingScene(String sceneId) {
    return _savingSceneIds.contains(sceneId);
  }

  Future<void> _saveSceneSelection(SceneCatalogItem scene, String modelId) async {
    final sceneId = scene.sceneId;
    final normalizedModel = modelId.trim();
    final current = _sceneSelectedModels[sceneId] ?? '';
    if (normalizedModel == current) {
      return;
    }

    if (normalizedModel.isNotEmpty &&
        !SceneModelConfigService.isValidModelName(normalizedModel)) {
      showToast('模型 ID 不能以 scene. 开头', type: ToastType.error);
      return;
    }

    setState(() {
      _savingSceneIds = {..._savingSceneIds, sceneId};
      _sceneSelectedModels = {..._sceneSelectedModels, sceneId: normalizedModel};
    });

    try {
      final overrides = normalizedModel.isEmpty
          ? await SceneModelConfigService.clearSceneModelOverride(sceneId)
          : await SceneModelConfigService.saveSceneModelOverride(
              sceneId: sceneId,
              model: normalizedModel,
            );

      if (!mounted) return;

      setState(() {
        _overrides = overrides;
        _sceneSelectedModels = _buildSceneSelectedModels(_catalog, overrides);
      });

      showToast(
        normalizedModel.isEmpty
            ? '${_sceneDisplayName(sceneId)} 已恢复默认模型'
            : '${_sceneDisplayName(sceneId)} 已绑定 $normalizedModel',
        type: ToastType.success,
      );
    } catch (_) {
      if (!mounted) return;
      setState(() {
        _sceneSelectedModels = {..._sceneSelectedModels, sceneId: current};
      });
      showToast('保存 ${_sceneDisplayName(sceneId)} 配置失败', type: ToastType.error);
    } finally {
      if (mounted) {
        setState(() {
          _savingSceneIds = {..._savingSceneIds}..remove(sceneId);
        });
      }
    }
  }

  Widget _buildCard({required Widget child}) {
    return Container(
      width: double.infinity,
      padding: const EdgeInsets.all(16),
      decoration: BoxDecoration(
        color: Colors.white,
        borderRadius: BorderRadius.circular(10),
        boxShadow: [AppColors.boxShadow],
      ),
      child: child,
    );
  }

  Widget _buildSceneRow(SceneCatalogItem scene) {
    final selectedModel = _sceneSelectedModels[scene.sceneId] ?? '';
    final isSaving = _isSavingScene(scene.sceneId);

    return Container(
      padding: const EdgeInsets.symmetric(horizontal: 12, vertical: 10),
      decoration: BoxDecoration(
        color: const Color(0xFFF8FAFC),
        borderRadius: BorderRadius.circular(8),
        border: Border.all(color: const Color(0x14000000)),
      ),
      child: Row(
        children: [
          Expanded(
            flex: 4,
            child: Tooltip(
              message: _sceneTooltip(scene),
              triggerMode: TooltipTriggerMode.tap,
              showDuration: const Duration(seconds: 3),
              child: Row(
                children: [
                  Flexible(
                    child: Text(
                      _sceneDisplayName(scene.sceneId),
                      style: const TextStyle(
                        color: AppColors.text,
                        fontSize: 14,
                        fontWeight: FontWeight.w600,
                        fontFamily: 'PingFang SC',
                      ),
                      overflow: TextOverflow.ellipsis,
                    ),
                  ),
                  const SizedBox(width: 6),
                  const Icon(
                    Icons.info_outline,
                    size: 15,
                    color: AppColors.text50,
                  ),
                ],
              ),
            ),
          ),
          const SizedBox(width: 10),
          Expanded(
            flex: 6,
            child: DropdownButtonFormField<String>(
              value: selectedModel,
              isExpanded: true,
              decoration: InputDecoration(
                isDense: true,
                contentPadding: const EdgeInsets.symmetric(
                  horizontal: 10,
                  vertical: 9,
                ),
                filled: true,
                fillColor: Colors.white,
                border: OutlineInputBorder(
                  borderRadius: BorderRadius.circular(8),
                  borderSide: const BorderSide(color: Color(0x1A000000)),
                ),
                enabledBorder: OutlineInputBorder(
                  borderRadius: BorderRadius.circular(8),
                  borderSide: const BorderSide(color: Color(0x1A000000)),
                ),
                focusedBorder: OutlineInputBorder(
                  borderRadius: BorderRadius.circular(8),
                  borderSide: const BorderSide(color: Color(0xFF2C7FEB)),
                ),
              ),
              items: [
                DropdownMenuItem<String>(
                  value: '',
                  child: Text(
                    '跟随默认（${scene.defaultModel}）',
                    overflow: TextOverflow.ellipsis,
                    style: const TextStyle(
                      color: AppColors.text70,
                      fontSize: 13,
                      fontFamily: 'PingFang SC',
                    ),
                  ),
                ),
                ..._providerModels.map((item) {
                  return DropdownMenuItem<String>(
                    value: item.id,
                    child: Text(
                      item.id,
                      overflow: TextOverflow.ellipsis,
                      style: const TextStyle(
                        color: AppColors.text,
                        fontSize: 13,
                        fontFamily: 'PingFang SC',
                      ),
                    ),
                  );
                }),
              ],
              onChanged: isSaving
                  ? null
                  : (value) {
                      _saveSceneSelection(scene, value ?? '');
                    },
            ),
          ),
          if (isSaving) ...[
            const SizedBox(width: 8),
            const SizedBox(
              width: 14,
              height: 14,
              child: CircularProgressIndicator(strokeWidth: 2),
            ),
          ],
        ],
      ),
    );
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      backgroundColor: AppColors.background,
      appBar: const CommonAppBar(title: '场景模型配置', primary: true),
      body: SafeArea(
        top: false,
        child: _isLoading
            ? const Center(child: CircularProgressIndicator())
            : ListView(
                      padding: const EdgeInsets.all(16),
                      children: [
                        _buildCard(
                          child: Column(
                            crossAxisAlignment: CrossAxisAlignment.start,
                            children: [
                              Row(
                                children: [
                                  const Expanded(
                                    child: Text(
                                      '场景与模型映射',
                                      style: TextStyle(
                                        color: AppColors.text,
                                        fontSize: 15,
                                        fontWeight: FontWeight.w600,
                                        fontFamily: 'PingFang SC',
                                      ),
                                    ),
                                  ),
                                  OutlinedButton.icon(
                                    onPressed: _isRefreshingModels
                                        ? null
                                        : _refreshProviderModels,
                                    icon: _isRefreshingModels
                                        ? const SizedBox(
                                            width: 14,
                                            height: 14,
                                            child: CircularProgressIndicator(
                                              strokeWidth: 2,
                                            ),
                                          )
                                        : const Icon(Icons.refresh, size: 16),
                                    label: const Text('刷新模型列表'),
                                  ),
                                ],
                              ),
                              const SizedBox(height: 8),
                              const Text(
                                '左侧场景名称可点击查看功能说明；右侧下拉选择模型 ID，选择后立即保存。',
                                style: TextStyle(
                                  color: AppColors.text70,
                                  fontSize: 12,
                                  height: 1.5,
                                  fontFamily: 'PingFang SC',
                                ),
                              ),
                              const SizedBox(height: 12),
                              if (_orderedCatalog.isEmpty)
                                const Padding(
                                  padding: EdgeInsets.symmetric(vertical: 12),
                                  child: Text(
                                    '暂无可配置场景',
                                    style: TextStyle(
                                      color: AppColors.text70,
                                      fontSize: 12,
                                      fontFamily: 'PingFang SC',
                                    ),
                                  ),
                                )
                              else
                                ListView.separated(
                                  physics: const NeverScrollableScrollPhysics(),
                                  shrinkWrap: true,
                                  itemCount: _orderedCatalog.length,
                                  itemBuilder: (context, index) {
                                    final scene = _orderedCatalog[index];
                                    return _buildSceneRow(scene);
                                  },
                                  separatorBuilder: (_, _) =>
                                      const SizedBox(height: 8),
                                ),
                            ],
                          ),
                        ),
                      ],
                    ),
      ),
    );
  }
}
