import 'package:ui/models/agent_skill_item.dart';
import 'package:ui/services/assists_core_service.dart';

class AgentSkillStoreService {
  static Future<List<AgentSkillItem>> listSkills() async {
    final items = await AssistsMessageService.listAgentSkills();
    final skills = items.map(AgentSkillItem.fromMap).toList();
    skills.sort((a, b) {
      if (a.installed != b.installed) {
        return a.installed ? -1 : 1;
      }
      if (a.isBuiltin != b.isBuiltin) {
        return a.isBuiltin ? -1 : 1;
      }
      return a.name.toLowerCase().compareTo(b.name.toLowerCase());
    });
    return skills;
  }

  static Future<AgentSkillItem?> setEnabled({
    required String skillId,
    required bool enabled,
  }) async {
    final result = await AssistsMessageService.setAgentSkillEnabled(
      skillId: skillId,
      enabled: enabled,
    );
    if (result == null) return null;
    return AgentSkillItem.fromMap(result);
  }

  static Future<bool> deleteSkill({required String skillId}) {
    return AssistsMessageService.deleteAgentSkill(skillId: skillId);
  }

  static Future<AgentSkillItem?> installBuiltinSkill({
    required String skillId,
  }) async {
    final result = await AssistsMessageService.installBuiltinAgentSkill(
      skillId: skillId,
    );
    if (result == null) return null;
    return AgentSkillItem.fromMap(result);
  }
}
