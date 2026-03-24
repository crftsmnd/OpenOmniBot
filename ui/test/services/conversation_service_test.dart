import 'dart:convert';

import 'package:flutter_test/flutter_test.dart';
import 'package:shared_preferences/shared_preferences.dart';
import 'package:ui/models/conversation_model.dart';
import 'package:ui/services/conversation_service.dart';

void main() {
  TestWidgetsFlutterBinding.ensureInitialized();

  setUp(() {
    final conversations = <ConversationModel>[
      ConversationModel(
        id: 1,
        title: 'Normal Legacy',
        summary: null,
        mode: null,
        status: 0,
        lastMessage: 'normal legacy',
        messageCount: 1,
        createdAt: 1,
        updatedAt: 100,
      ),
      ConversationModel(
        id: 2,
        title: 'OpenClaw Latest',
        summary: null,
        mode: kConversationModeOpenClaw,
        status: 0,
        lastMessage: 'openclaw latest',
        messageCount: 1,
        createdAt: 2,
        updatedAt: 300,
      ),
      ConversationModel(
        id: 3,
        title: 'Normal Explicit',
        summary: null,
        mode: kConversationModeNormal,
        status: 0,
        lastMessage: 'normal explicit',
        messageCount: 1,
        createdAt: 3,
        updatedAt: 200,
      ),
    ];

    SharedPreferences.setMockInitialValues({
      'local_conversation_list':
          jsonEncode(conversations.map((item) => item.toJson()).toList()),
    });
  });

  test('getConversationsByMode filters using resolved conversation mode', () async {
    final normalConversations = await ConversationService.getConversationsByMode(
      kConversationModeNormal,
    );
    final openClawConversations =
        await ConversationService.getConversationsByMode(
      kConversationModeOpenClaw,
    );

    expect(normalConversations.map((item) => item.id), <int>[3, 1]);
    expect(openClawConversations.map((item) => item.id), <int>[2]);
  });

  test('getLatestConversationByMode returns latest conversation inside that mode',
      () async {
    final latestNormal = await ConversationService.getLatestConversationByMode(
      kConversationModeNormal,
    );
    final latestOpenClaw =
        await ConversationService.getLatestConversationByMode(
      kConversationModeOpenClaw,
    );

    expect(latestNormal?.id, 3);
    expect(latestOpenClaw?.id, 2);
  });
}
