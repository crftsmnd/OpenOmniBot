import 'package:flutter_test/flutter_test.dart';
import 'package:ui/services/omnibot_resource_service.dart';

void main() {
  test(
    'resolveUri maps omnibot workspace uri to shell path and preview metadata',
    () {
      final metadata = OmnibotResourceService.resolveUri(
        'omnibot://workspace/demo/output.png',
      );

      expect(metadata, isNotNull);
      expect(metadata!.path, '/storage/emulated/0/workspace/demo/output.png');
      expect(metadata.shellPath, '/workspace/demo/output.png');
      expect(metadata.previewKind, 'image');
      expect(metadata.embedKind, 'image');
      expect(metadata.inlineRenderable, isTrue);
    },
  );

  test('describePath derives inline rendering hints from file extension', () {
    final audio = OmnibotResourceService.describePath(
      '/storage/emulated/0/workspace/audio/demo.mp3',
    );
    final video = OmnibotResourceService.describePath(
      '/storage/emulated/0/workspace/video/demo.mp4',
    );
    final document = OmnibotResourceService.describePath(
      '/storage/emulated/0/workspace/docs/spec.pdf',
    );

    expect(audio.shellPath, '/workspace/audio/demo.mp3');
    expect(audio.embedKind, 'audio');
    expect(audio.inlineRenderable, isTrue);

    expect(video.shellPath, '/workspace/video/demo.mp4');
    expect(video.embedKind, 'video');
    expect(video.inlineRenderable, isTrue);

    expect(document.shellPath, '/workspace/docs/spec.pdf');
    expect(document.embedKind, 'link');
    expect(document.inlineRenderable, isFalse);
  });
}
