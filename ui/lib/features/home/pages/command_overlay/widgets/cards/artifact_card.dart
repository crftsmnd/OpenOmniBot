import 'package:flutter/material.dart';
import 'package:ui/services/omnibot_resource_service.dart';

class ArtifactCard extends StatelessWidget {
  final Map<String, dynamic> artifact;

  const ArtifactCard({super.key, required this.artifact});

  @override
  Widget build(BuildContext context) {
    final title = (artifact['title'] ?? artifact['fileName'] ?? 'artifact')
        .toString();
    final mimeType = (artifact['mimeType'] ?? '').toString();
    final size = artifact['size']?.toString() ?? '';
    final shellPath = artifact['workspacePath']?.toString() ?? '';
    final path = artifact['androidPath']?.toString() ?? '';
    final uri = artifact['uri']?.toString();

    return Container(
      padding: const EdgeInsets.all(12),
      decoration: BoxDecoration(
        borderRadius: BorderRadius.circular(12),
        border: Border.all(color: const Color(0xFFE0E0E0)),
        color: const Color(0xFFF8F9FB),
      ),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Text(
            title,
            style: const TextStyle(fontSize: 14, fontWeight: FontWeight.w600),
          ),
          const SizedBox(height: 4),
          Text(
            [
              mimeType,
              if (size.isNotEmpty) '$size bytes',
            ].where((item) => item.isNotEmpty).join(' · '),
            style: const TextStyle(fontSize: 12, color: Color(0xFF667085)),
          ),
          if (shellPath.isNotEmpty) ...[
            const SizedBox(height: 4),
            Text(
              shellPath,
              style: const TextStyle(fontSize: 11, color: Color(0xFF98A2B3)),
            ),
          ],
          const SizedBox(height: 8),
          Wrap(
            spacing: 8,
            children: [
              TextButton(
                onPressed: () {
                  if (path.isNotEmpty) {
                    OmnibotResourceService.openFilePath(
                      path,
                      uri: uri,
                      title: title,
                      previewKind: artifact['previewKind']?.toString(),
                      mimeType: mimeType,
                      shellPath: shellPath,
                    );
                  } else if (uri != null) {
                    OmnibotResourceService.openUri(uri);
                  }
                },
                child: const Text('预览'),
              ),
              if (path.isNotEmpty)
                TextButton(
                  onPressed: () {
                    OmnibotResourceService.saveToLocal(
                      sourcePath: path,
                      fileName:
                          (artifact['fileName'] ??
                                  artifact['title'] ??
                                  'artifact')
                              .toString(),
                      mimeType: mimeType,
                    );
                  },
                  child: const Text('保存'),
                ),
            ],
          ),
        ],
      ),
    );
  }
}
