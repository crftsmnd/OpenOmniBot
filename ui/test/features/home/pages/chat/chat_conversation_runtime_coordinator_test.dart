import 'package:flutter/services.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:ui/features/home/pages/chat/services/chat_conversation_runtime_coordinator.dart';

void main() {
  TestWidgetsFlutterBinding.ensureInitialized();

  const channelName = 'cn.com.omnimind.bot/AssistCoreEvent';
  const codec = StandardMethodCodec();
  final coordinator = ChatConversationRuntimeCoordinator.instance;

  Future<void> emitPlatformEvent(String method, [dynamic arguments]) async {
    await TestDefaultBinaryMessengerBinding.instance.defaultBinaryMessenger
        .handlePlatformMessage(
          channelName,
          codec.encodeMethodCall(MethodCall(method, arguments)),
          (ByteData? _) {},
        );
    await Future<void>.delayed(Duration.zero);
  }

  setUp(() {
    coordinator.resetForTest();
    coordinator.ensureInitialized();
  });

  test('routes agent chat updates to the bound conversation only', () async {
    const conversationA = 1001;
    const conversationB = 1002;
    const taskId = 'agent-task-a';

    coordinator.ensureRuntime(
      conversationId: conversationA,
      mode: kChatRuntimeModeNormal,
    );
    coordinator.ensureRuntime(
      conversationId: conversationB,
      mode: kChatRuntimeModeNormal,
    );
    coordinator.registerTask(
      taskId: taskId,
      conversationId: conversationA,
      mode: kChatRuntimeModeNormal,
    );

    final runtimeA = coordinator.runtimeFor(
      conversationId: conversationA,
      mode: kChatRuntimeModeNormal,
    )!;
    runtimeA.currentDispatchTaskId = taskId;

    await emitPlatformEvent('onAgentChatMessage', <String, dynamic>{
      'taskId': taskId,
      'message': 'hello from agent',
      'isFinal': false,
    });

    final runtimeB = coordinator.runtimeFor(
      conversationId: conversationB,
      mode: kChatRuntimeModeNormal,
    )!;

    expect(runtimeA.messages, hasLength(1));
    expect(runtimeA.messages.first.id, '$taskId-text');
    expect(runtimeA.messages.first.text, 'hello from agent');
    expect(runtimeB.messages, isEmpty);
  });

  test('routes chat task chunks to the bound conversation only', () async {
    const conversationA = 2001;
    const conversationB = 2002;
    const taskId = 'chat-task-a';

    coordinator.ensureRuntime(
      conversationId: conversationA,
      mode: kChatRuntimeModeOpenClaw,
    );
    coordinator.ensureRuntime(
      conversationId: conversationB,
      mode: kChatRuntimeModeOpenClaw,
    );
    coordinator.registerTask(
      taskId: taskId,
      conversationId: conversationA,
      mode: kChatRuntimeModeOpenClaw,
    );

    await emitPlatformEvent('onChatMessage', <String, dynamic>{
      'taskID': taskId,
      'content': '{"text":"hello from openclaw"}',
      'type': null,
    });

    final runtimeA = coordinator.runtimeFor(
      conversationId: conversationA,
      mode: kChatRuntimeModeOpenClaw,
    )!;
    final runtimeB = coordinator.runtimeFor(
      conversationId: conversationB,
      mode: kChatRuntimeModeOpenClaw,
    )!;

    expect(runtimeA.messages, hasLength(1));
    expect(runtimeA.messages.first.id, taskId);
    expect(runtimeA.messages.first.text, 'hello from openclaw');
    expect(runtimeB.messages, isEmpty);
  });

  test('routes VLM request-input state to the bound conversation only', () async {
    const conversationA = 3001;
    const conversationB = 3002;
    const taskId = 'vlm-task-a';

    coordinator.ensureRuntime(
      conversationId: conversationA,
      mode: kChatRuntimeModeNormal,
    );
    coordinator.ensureRuntime(
      conversationId: conversationB,
      mode: kChatRuntimeModeNormal,
    );
    coordinator.registerTask(
      taskId: taskId,
      conversationId: conversationA,
      mode: kChatRuntimeModeNormal,
    );

    await emitPlatformEvent('onVLMRequestUserInput', <String, dynamic>{
      'taskId': taskId,
      'question': 'Need more info',
    });

    final runtimeA = coordinator.runtimeFor(
      conversationId: conversationA,
      mode: kChatRuntimeModeNormal,
    )!;
    final runtimeB = coordinator.runtimeFor(
      conversationId: conversationB,
      mode: kChatRuntimeModeNormal,
    )!;

    expect(runtimeA.vlmInfoQuestion, 'Need more info');
    expect(runtimeB.vlmInfoQuestion, isNull);
  });
}
