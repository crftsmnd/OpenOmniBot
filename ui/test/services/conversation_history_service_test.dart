import 'package:flutter_test/flutter_test.dart';
import 'package:shared_preferences/shared_preferences.dart';
import 'package:ui/models/conversation_model.dart';
import 'package:ui/services/conversation_history_service.dart';

void main() {
  TestWidgetsFlutterBinding.ensureInitialized();

  setUp(() {
    SharedPreferences.setMockInitialValues({});
  });

  test('stores current conversation ids separately by mode', () async {
    await ConversationHistoryService.saveCurrentConversationId(
      101,
      mode: kConversationModeNormal,
    );
    await ConversationHistoryService.saveCurrentConversationId(
      202,
      mode: kConversationModeOpenClaw,
    );

    expect(
      await ConversationHistoryService.getCurrentConversationId(
        mode: kConversationModeNormal,
      ),
      101,
    );
    expect(
      await ConversationHistoryService.getCurrentConversationId(
        mode: kConversationModeOpenClaw,
      ),
      202,
    );
  });

  test('clearAllCurrentConversationIds clears per-mode ids and last active id',
      () async {
    await ConversationHistoryService.saveCurrentConversationId(
      101,
      mode: kConversationModeNormal,
    );
    await ConversationHistoryService.saveCurrentConversationId(
      202,
      mode: kConversationModeOpenClaw,
    );
    await ConversationHistoryService.saveLastActiveConversationId(202);

    await ConversationHistoryService.clearAllCurrentConversationIds();

    expect(
      await ConversationHistoryService.getCurrentConversationId(
        mode: kConversationModeNormal,
      ),
      isNull,
    );
    expect(
      await ConversationHistoryService.getCurrentConversationId(
        mode: kConversationModeOpenClaw,
      ),
      isNull,
    );
    expect(
      await ConversationHistoryService.getLastActiveConversationId(),
      isNull,
    );
  });
}
