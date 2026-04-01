package cn.com.omnimind.bot.agent

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SkillRuntimeBehaviorTest {
    private fun entry(
        id: String,
        description: String,
        enabled: Boolean = true,
        installed: Boolean = true
    ): SkillIndexEntry {
        return SkillIndexEntry(
            id = id,
            name = id,
            description = description,
            rootPath = "/tmp/$id",
            shellRootPath = "/workspace/.omnibot/skills/$id",
            skillFilePath = "/tmp/$id/SKILL.md",
            shellSkillFilePath = "/workspace/.omnibot/skills/$id/SKILL.md",
            hasScripts = false,
            hasReferences = false,
            hasAssets = false,
            hasEvals = false,
            enabled = enabled,
            installed = installed
        )
    }

    @Test
    fun resolveMatchesSkipsDisabledAndUninstalledSkills() {
        val matches = SkillTriggerMatcher.resolveMatches(
            userMessage = "请用 skill-creator 帮我创建一个新的技能",
            entries = listOf(
                entry(
                    id = "skill-creator",
                    description = "用于创建和更新技能",
                    enabled = false
                ),
                entry(
                    id = "skill-creator",
                    description = "用于创建和更新技能",
                    installed = false
                )
            )
        )

        assertTrue(matches.isEmpty())
    }

    @Test
    fun buildOmitsDisabledAndUninstalledSkillsFromPromptIndex() {
        val prompt = AgentSystemPrompt.build(
            workspace = AgentWorkspaceDescriptor(
                id = "conversation-1",
                rootPath = "/workspace",
                androidRootPath = "/data/user/0/cn.com.omnimind.bot/workspace",
                uriRoot = "omnibot://workspace",
                currentCwd = "/workspace",
                androidCurrentCwd = "/data/user/0/cn.com.omnimind.bot/workspace",
                shellRootPath = "/workspace",
                retentionPolicy = "shared_root"
            ),
            installedSkills = listOf(
                entry(
                    id = "active-skill",
                    description = "可正常使用的技能"
                ),
                entry(
                    id = "disabled-skill",
                    description = "已禁用的技能",
                    enabled = false
                ),
                entry(
                    id = "removed-skill",
                    description = "已删除但可恢复的技能",
                    installed = false
                )
            ),
            skillsRootShellPath = "/workspace/.omnibot/skills",
            skillsRootAndroidPath = "/data/user/0/cn.com.omnimind.bot/workspace/.omnibot/skills",
            resolvedSkills = emptyList(),
            memoryContext = null
        )

        assertTrue(prompt.contains("id=active-skill"))
        assertFalse(prompt.contains("id=disabled-skill"))
        assertFalse(prompt.contains("id=removed-skill"))
    }
}
