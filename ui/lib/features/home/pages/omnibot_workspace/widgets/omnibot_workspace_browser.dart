import 'dart:io';

import 'package:flutter/material.dart';
import 'package:flutter_svg/flutter_svg.dart';
import 'package:ui/services/omnibot_resource_service.dart';
import 'package:ui/theme/app_colors.dart';

class OmnibotWorkspaceBrowser extends StatefulWidget {
  final String workspacePath;
  final String? workspaceShellPath;

  const OmnibotWorkspaceBrowser({
    super.key,
    required this.workspacePath,
    this.workspaceShellPath,
  });

  @override
  State<OmnibotWorkspaceBrowser> createState() =>
      _OmnibotWorkspaceBrowserState();
}

class _OmnibotWorkspaceBrowserState extends State<OmnibotWorkspaceBrowser> {
  static const String _folderIconAsset =
      'assets/home/workspace_folder_icon.svg';
  static const String _folderOpenIconAsset =
      'assets/home/workspace_folder_open_icon.svg';
  static const String _audioIconAsset = 'assets/home/workspace_audio_icon.svg';
  static const String _videoIconAsset = 'assets/home/workspace_video_icon.svg';
  static const String _fileIconAsset = 'assets/home/workspace_file_icon.svg';
  static const int _maxInlineExpansionDepth = 2;
  static const int _maxExpandedItemsBeforeScroll = 8;
  static const double _itemHeight = 40;
  static const double _indentStep = 16;
  static const Set<String> _audioExtensions = <String>{
    '.mp3',
    '.m4a',
    '.wav',
    '.aac',
    '.ogg',
    '.flac',
  };
  static const Set<String> _videoExtensions = <String>{
    '.mp4',
    '.mov',
    '.m4v',
    '.avi',
    '.mkv',
    '.webm',
  };

  late final Directory _rootDirectory;
  late Directory _directory;
  List<FileSystemEntity> _entries = const [];
  final Set<String> _expandedDirectoryPaths = <String>{};
  final Map<String, List<FileSystemEntity>> _directoryChildrenCache =
      <String, List<FileSystemEntity>>{};

  @override
  void initState() {
    super.initState();
    _rootDirectory = Directory(widget.workspacePath);
    _directory = _rootDirectory;
    _refresh();
  }

  void _refresh() {
    final exists = _directory.existsSync();
    final nextEntries = exists
        ? _sortedEntriesFor(_directory)
        : const <FileSystemEntity>[];
    final nextExpandedPaths = <String>{};
    final nextChildrenCache = <String, List<FileSystemEntity>>{};

    for (final path in _expandedDirectoryPaths) {
      if (!_isDescendantOfCurrentDirectory(path)) continue;
      final dir = Directory(path);
      if (!dir.existsSync()) continue;
      nextExpandedPaths.add(path);
      nextChildrenCache[path] = _sortedEntriesFor(dir);
    }

    setState(() {
      _entries = nextEntries;
      _expandedDirectoryPaths
        ..clear()
        ..addAll(nextExpandedPaths);
      _directoryChildrenCache
        ..clear()
        ..addAll(nextChildrenCache);
    });
  }

  void _openParentDirectory() {
    if (_directory.path == _rootDirectory.path) return;
    setState(() {
      _directory = _directory.parent;
      _expandedDirectoryPaths.clear();
      _directoryChildrenCache.clear();
    });
    _refresh();
  }

  void _openDirectory(Directory directory) {
    setState(() {
      _directory = directory;
      _expandedDirectoryPaths.clear();
      _directoryChildrenCache.clear();
    });
    _refresh();
  }

  List<FileSystemEntity> _sortedEntriesFor(Directory directory) {
    return directory.listSync().toList()..sort((a, b) {
      if (a is Directory && b is! Directory) return -1;
      if (a is! Directory && b is Directory) return 1;
      return a.path.toLowerCase().compareTo(b.path.toLowerCase());
    });
  }

  bool _isDescendantOfCurrentDirectory(String path) {
    if (path == _directory.path) return true;
    return path.startsWith('${_directory.path}/');
  }

  void _collapseDirectory(String directoryPath) {
    setState(() {
      _expandedDirectoryPaths.removeWhere(
        (path) => path == directoryPath || path.startsWith('$directoryPath/'),
      );
      _directoryChildrenCache.removeWhere(
        (path, _) =>
            path == directoryPath || path.startsWith('$directoryPath/'),
      );
    });
  }

  void _toggleDirectoryExpansion(Directory directory, {required int depth}) {
    if (depth >= _maxInlineExpansionDepth) {
      _openDirectory(directory);
      return;
    }
    final path = directory.path;
    if (_expandedDirectoryPaths.contains(path)) {
      _collapseDirectory(path);
      return;
    }
    final children = _sortedEntriesFor(directory);
    setState(() {
      _expandedDirectoryPaths.add(path);
      _directoryChildrenCache[path] = children;
    });
  }

  @override
  Widget build(BuildContext context) {
    final exists = _directory.existsSync();
    final currentShellPath = _currentShellPath();
    final canGoUp = _directory.path != _rootDirectory.path;
    final itemCount = _entries.length + (canGoUp ? 1 : 0);

    return Column(
      children: [
        Expanded(
          child: RefreshIndicator(
            onRefresh: () async => _refresh(),
            child: !exists
                ? _buildStatusList(message: '工作区不存在')
                : itemCount == 0
                ? _buildStatusList(message: '当前目录为空')
                : ListView.separated(
                    physics: const AlwaysScrollableScrollPhysics(),
                    padding: const EdgeInsets.fromLTRB(16, 12, 16, 16),
                    itemCount: itemCount,
                    separatorBuilder: (_, __) => const Divider(
                      height: 1,
                      thickness: 1,
                      indent: 12,
                      endIndent: 12,
                    ),
                    itemBuilder: (context, index) {
                      final isFirst = index == 0;
                      final isLast = index == itemCount - 1;
                      final borderRadius = BorderRadius.vertical(
                        top: isFirst ? const Radius.circular(4) : Radius.zero,
                        bottom: isLast ? const Radius.circular(4) : Radius.zero,
                      );

                      if (canGoUp && index == 0) {
                        return _buildWorkspaceItem(
                          title: '..',
                          leading: Icon(
                            Icons.arrow_upward_rounded,
                            size: 20,
                            color: AppColors.text.withValues(alpha: 0.8),
                          ),
                          borderRadius: borderRadius,
                          onTap: _openParentDirectory,
                        );
                      }

                      final entry = _entries[index - (canGoUp ? 1 : 0)];
                      return _buildEntryNode(
                        entry: entry,
                        depth: 0,
                        currentShellPath: currentShellPath,
                        borderRadius: borderRadius,
                      );
                    },
                  ),
          ),
        ),
      ],
    );
  }

  Widget _buildEntryNode({
    required FileSystemEntity entry,
    required int depth,
    required String? currentShellPath,
    BorderRadius borderRadius = const BorderRadius.all(Radius.circular(4)),
  }) {
    final name = entry.path.split('/').last;
    final isDirectory = entry is Directory;
    final canExpandInline = isDirectory && depth < _maxInlineExpansionDepth;
    final isExpanded =
        isDirectory &&
        canExpandInline &&
        _expandedDirectoryPaths.contains(entry.path);

    final trailing = isDirectory
        ? Icon(
            canExpandInline
                ? (isExpanded
                      ? Icons.expand_less_rounded
                      : Icons.expand_more_rounded)
                : Icons.chevron_right_rounded,
            color: AppColors.text.withValues(alpha: 0.5),
            size: 18,
          )
        : null;

    final row = _buildWorkspaceItem(
      title: name,
      leading: SvgPicture.asset(
        _iconAssetForEntry(entry, isExpanded: isExpanded),
        width: 20,
        height: 20,
        colorFilter: const ColorFilter.mode(AppColors.text, BlendMode.srcIn),
      ),
      borderRadius: borderRadius,
      trailing: trailing,
      onTap: () {
        if (entry is Directory) {
          final directory = entry;
          if (canExpandInline) {
            _toggleDirectoryExpansion(directory, depth: depth);
          } else {
            _openDirectory(directory);
          }
          return;
        }
        final shellPath =
            OmnibotResourceService.shellPathForAndroidPath(entry.path) ??
            (currentShellPath == null ? null : '$currentShellPath/$name');
        OmnibotResourceService.openFilePath(
          entry.path,
          title: name,
          shellPath: shellPath,
        );
      },
    );

    if (!isDirectory || !isExpanded) {
      return row;
    }

    final children = _directoryChildrenCache[entry.path] ?? const [];
    return Column(
      crossAxisAlignment: CrossAxisAlignment.stretch,
      children: [
        row,
        _buildExpandedChildren(
          entries: children,
          depth: depth + 1,
          currentShellPath: currentShellPath,
        ),
      ],
    );
  }

  Widget _buildExpandedChildren({
    required List<FileSystemEntity> entries,
    required int depth,
    required String? currentShellPath,
  }) {
    final indent = _indentStep * depth;

    if (entries.isEmpty) {
      return Padding(
        padding: EdgeInsets.only(left: indent + 12, top: 6, bottom: 6),
        child: Text(
          '空文件夹',
          style: TextStyle(
            fontSize: 12,
            color: AppColors.text.withValues(alpha: 0.45),
          ),
        ),
      );
    }

    Widget buildItem(BuildContext context, int index) {
      return _buildEntryNode(
        entry: entries[index],
        depth: depth,
        currentShellPath: currentShellPath,
      );
    }

    final listContent = entries.length > _maxExpandedItemsBeforeScroll
        ? SizedBox(
            height: (_itemHeight + 1) * _maxExpandedItemsBeforeScroll - 1,
            child: ListView.separated(
              primary: false,
              physics: const ClampingScrollPhysics(),
              itemCount: entries.length,
              separatorBuilder: (_, __) => const Divider(
                height: 1,
                thickness: 1,
                indent: 12,
                endIndent: 12,
              ),
              itemBuilder: buildItem,
            ),
          )
        : Column(
            mainAxisSize: MainAxisSize.min,
            children: [
              for (var index = 0; index < entries.length; index++) ...[
                if (index > 0)
                  const Divider(
                    height: 1,
                    thickness: 1,
                    indent: 12,
                    endIndent: 12,
                  ),
                buildItem(context, index),
              ],
            ],
          );

    return Padding(
      padding: EdgeInsets.only(left: indent, top: 2, bottom: 2),
      child: DecoratedBox(
        decoration: BoxDecoration(
          color: Colors.white,
          borderRadius: const BorderRadius.all(Radius.circular(4)),
          boxShadow: [AppColors.boxShadow],
        ),
        child: listContent,
      ),
    );
  }

  Widget _buildStatusList({required String message}) {
    return ListView(
      physics: const AlwaysScrollableScrollPhysics(),
      children: [
        SizedBox(
          height: 280,
          child: Center(
            child: Text(
              message,
              style: TextStyle(
                color: AppColors.text.withValues(alpha: 0.45),
                fontSize: 14,
              ),
            ),
          ),
        ),
      ],
    );
  }

  Widget _buildWorkspaceItem({
    required String title,
    required Widget leading,
    required BorderRadius borderRadius,
    required VoidCallback onTap,
    Widget? trailing,
  }) {
    return DecoratedBox(
      decoration: BoxDecoration(
        color: Colors.white,
        borderRadius: borderRadius,
        boxShadow: [AppColors.boxShadow],
      ),
      child: Material(
        color: Colors.transparent,
        borderRadius: borderRadius,
        child: InkWell(
          onTap: onTap,
          borderRadius: borderRadius,
          child: SizedBox(
            height: _itemHeight,
            child: Padding(
              padding: const EdgeInsets.symmetric(horizontal: 12),
              child: Row(
                children: [
                  leading,
                  const SizedBox(width: 8),
                  Expanded(
                    child: Text(
                      title,
                      maxLines: 1,
                      overflow: TextOverflow.ellipsis,
                      style: const TextStyle(
                        fontSize: 14,
                        fontWeight: FontWeight.w500,
                        color: AppColors.text,
                        fontFamily: 'PingFang SC',
                      ),
                    ),
                  ),
                  if (trailing != null) ...[const SizedBox(width: 8), trailing],
                ],
              ),
            ),
          ),
        ),
      ),
    );
  }

  String _iconAssetForEntry(FileSystemEntity entry, {bool isExpanded = false}) {
    if (entry is Directory) {
      return isExpanded ? _folderOpenIconAsset : _folderIconAsset;
    }
    final fileName = entry.path.split('/').last.toLowerCase();
    final dotIndex = fileName.lastIndexOf('.');
    final extension = dotIndex >= 0 ? fileName.substring(dotIndex) : '';
    if (_audioExtensions.contains(extension)) {
      return _audioIconAsset;
    }
    if (_videoExtensions.contains(extension)) {
      return _videoIconAsset;
    }
    return _fileIconAsset;
  }

  String? _currentShellPath() {
    final baseAndroid = widget.workspacePath;
    final baseShell = widget.workspaceShellPath;
    if (baseShell == null || baseShell.isEmpty) return null;
    if (_directory.path == baseAndroid) return baseShell;
    if (_directory.path.startsWith('$baseAndroid/')) {
      final suffix = _directory.path.substring(baseAndroid.length + 1);
      return '$baseShell/$suffix';
    }
    return OmnibotResourceService.shellPathForAndroidPath(_directory.path) ??
        baseShell;
  }
}
