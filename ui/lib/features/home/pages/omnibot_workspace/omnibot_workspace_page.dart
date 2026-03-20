import 'package:flutter/material.dart';
import 'package:ui/features/home/pages/omnibot_workspace/widgets/omnibot_workspace_browser.dart';

class OmnibotWorkspacePage extends StatelessWidget {
  final String workspacePath;
  final String? workspaceId;
  final String? workspaceShellPath;

  const OmnibotWorkspacePage({
    super.key,
    required this.workspacePath,
    this.workspaceId,
    this.workspaceShellPath,
  });

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: AppBar(title: const Text('Workspace')),
      body: OmnibotWorkspaceBrowser(
        workspacePath: workspacePath,
        workspaceShellPath: workspaceShellPath,
      ),
    );
  }
}
