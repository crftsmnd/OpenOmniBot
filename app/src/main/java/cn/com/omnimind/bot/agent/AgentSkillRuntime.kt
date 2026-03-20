package cn.com.omnimind.bot.agent

import android.content.Context
import java.io.File
import kotlin.math.min

class SkillIndexService(
    private val context: Context,
    private val workspaceManager: AgentWorkspaceManager
) {
    fun listInstalledSkills(): List<SkillIndexEntry> {
        val root = workspaceManager.skillsRoot()
        if (!root.exists()) return emptyList()
        return root.walkTopDown()
            .onEnter { directory -> directory.name != ".git" }
            .filter { file -> file.isFile && file.name == "SKILL.md" }
            .mapNotNull { skillFile -> buildIndexEntry(skillFile.parentFile ?: return@mapNotNull null) }
            .distinctBy { it.rootPath }
            .sortedBy { it.id }
            .toList()
    }

    fun findInstalledSkill(identifier: String): SkillIndexEntry? {
        val normalizedIdentifier = normalizeSkillLookup(identifier)
        if (normalizedIdentifier.isBlank()) return null
        val entries = listInstalledSkills()
        return entries.firstOrNull { normalizeSkillLookup(it.id) == normalizedIdentifier }
            ?: entries.firstOrNull { normalizeSkillLookup(it.name) == normalizedIdentifier }
            ?: entries.firstOrNull { normalizeSkillLookup(it.shellSkillFilePath) == normalizedIdentifier }
            ?: entries.firstOrNull { normalizeSkillLookup(it.skillFilePath) == normalizedIdentifier }
            ?: entries.firstOrNull { normalizeSkillLookup(it.shellRootPath) == normalizedIdentifier }
            ?: entries.firstOrNull { normalizeSkillLookup(it.rootPath) == normalizedIdentifier }
    }

    fun installSkillFromDirectory(sourcePath: String): SkillIndexEntry {
        val sourceDir = File(sourcePath).canonicalFile
        require(sourceDir.isDirectory) { "skill source 必须是目录" }
        val skillFile = File(sourceDir, "SKILL.md")
        require(skillFile.exists()) { "skill source 缺少 SKILL.md" }
        val targetDir = File(workspaceManager.skillsRoot(), sourceDir.name)
        copyRecursively(sourceDir, targetDir)
        return buildIndexEntry(targetDir)
            ?: throw IllegalStateException("安装 skill 后索引失败")
    }

    private fun buildIndexEntry(skillDir: File): SkillIndexEntry? {
        val canonicalSkillDir = skillDir.canonicalFile
        val skillFile = File(canonicalSkillDir, "SKILL.md")
        val parsed = parseSkillFile(skillFile) ?: return null
        val frontmatter = parsed.frontmatter
        val id = sanitizeSkillId(canonicalSkillDir.name, frontmatter["name"])
        val metadata = frontmatter["metadata"]
            ?.let { raw -> parseIndentedBlock(raw) }
            ?: emptyMap()
        val shellRootPath = workspaceManager.shellPathForAndroid(canonicalSkillDir)
            ?: canonicalSkillDir.absolutePath
        val shellSkillFilePath = workspaceManager.shellPathForAndroid(skillFile)
            ?: skillFile.absolutePath
        return SkillIndexEntry(
            id = id,
            name = frontmatter["name"]?.ifBlank { id } ?: id,
            description = frontmatter["description"]?.trim().orEmpty(),
            compatibility = frontmatter["compatibility"]?.trim(),
            metadata = metadata,
            rootPath = canonicalSkillDir.absolutePath,
            shellRootPath = shellRootPath,
            skillFilePath = skillFile.absolutePath,
            shellSkillFilePath = shellSkillFilePath,
            hasScripts = File(canonicalSkillDir, "scripts").isDirectory,
            hasReferences = File(canonicalSkillDir, "references").isDirectory,
            hasAssets = File(canonicalSkillDir, "assets").isDirectory,
            hasEvals = File(canonicalSkillDir, "evals").isDirectory
        )
    }

    private fun copyRecursively(source: File, target: File) {
        if (source.isDirectory) {
            if (!target.exists()) {
                target.mkdirs()
            }
            source.listFiles()?.forEach { child ->
                copyRecursively(child, File(target, child.name))
            }
            return
        }
        target.parentFile?.mkdirs()
        source.copyTo(target, overwrite = true)
    }

    private fun sanitizeSkillId(directoryName: String, frontmatterName: String?): String {
        val candidate = frontmatterName?.trim().takeUnless { it.isNullOrBlank() } ?: directoryName
        return candidate.lowercase()
            .replace(Regex("[^a-z0-9-]+"), "-")
            .trim('-')
            .ifBlank { directoryName.lowercase() }
    }

    private fun normalizeSkillLookup(value: String): String {
        return value.trim()
            .lowercase()
            .replace('\\', '/')
            .removeSuffix("/skill.md")
            .removeSuffix("/")
            .replace(Regex("\\s+"), "")
    }
}

class SkillLoader(
    private val workspaceManager: AgentWorkspaceManager
) {
    fun load(entry: SkillIndexEntry, triggerReason: String): ResolvedSkillContext? {
        val skillDir = File(entry.rootPath)
        val parsed = parseSkillFile(File(skillDir, "SKILL.md")) ?: return null
        val referencesDir = File(skillDir, "references")
        val loadedReferences = if (referencesDir.isDirectory) {
            referencesDir.listFiles()
                ?.filter { it.isFile }
                ?.map { file -> workspaceManager.shellPathForAndroid(file) ?: file.absolutePath }
                ?.sorted()
                ?: emptyList()
        } else {
            emptyList()
        }
        return ResolvedSkillContext(
            skillId = entry.id,
            frontmatter = parsed.frontmatter,
            metadata = entry.metadata,
            bodyMarkdown = parsed.body,
            loadedReferences = loadedReferences,
            scriptsDir = File(skillDir, "scripts")
                .takeIf { it.isDirectory }
                ?.let { workspaceManager.shellPathForAndroid(it) ?: it.absolutePath },
            assetsDir = File(skillDir, "assets")
                .takeIf { it.isDirectory }
                ?.let { workspaceManager.shellPathForAndroid(it) ?: it.absolutePath },
            triggerReason = triggerReason
        )
    }
}

object SkillCompatibilityChecker {
    fun evaluate(entry: SkillIndexEntry): SkillCompatibilityResult {
        val raw = buildString {
            append(entry.compatibility.orEmpty())
            if (entry.metadata.isNotEmpty()) {
                append(' ')
                append(entry.metadata.values.joinToString(" "))
            }
            append(' ')
            append(entry.description)
        }.lowercase()

        return when {
            raw.contains("apple-") || raw.contains("homekit") || raw.contains("healthkit") -> {
                SkillCompatibilityResult(
                    available = false,
                    reason = "当前 Omnibot 不支持 Apple 专属运行时"
                )
            }
            raw.contains("ios") && !raw.contains("android") -> {
                SkillCompatibilityResult(
                    available = false,
                    reason = "当前 Skill 标注为 iOS 专属"
                )
            }
            else -> SkillCompatibilityResult(available = true)
        }
    }
}

object SkillTriggerMatcher {
    fun resolveMatches(
        userMessage: String,
        entries: List<SkillIndexEntry>,
        maxMatches: Int = 2
    ): List<SkillMatchResult> {
        val normalizedMessage = normalize(userMessage)
        if (normalizedMessage.isBlank()) return emptyList()
        return entries.mapNotNull { entry ->
            val confidence = score(entry, normalizedMessage)
            if (confidence <= 0.0) return@mapNotNull null
            val reason = when {
                normalizedMessage.contains(normalize(entry.id)) -> "用户消息命中 skill id"
                normalizedMessage.contains(normalize(entry.name)) -> "用户消息命中 skill 名称"
                else -> "用户消息命中 skill 描述关键词"
            }
            SkillMatchResult(entry = entry, confidence = confidence, triggerReason = reason)
        }.sortedByDescending { it.confidence }
            .take(maxMatches)
    }

    private fun score(entry: SkillIndexEntry, normalizedMessage: String): Double {
        var score = 0.0
        val normalizedId = normalize(entry.id)
        val normalizedName = normalize(entry.name)
        if (normalizedId.isNotBlank() && normalizedMessage.contains(normalizedId)) {
            score += 1.0
        }
        if (normalizedName.isNotBlank() && normalizedMessage.contains(normalizedName)) {
            score += 0.9
        }
        extractCandidatePhrases(entry.description).forEach { phrase ->
            if (phrase.isNotBlank() && normalizedMessage.contains(normalize(phrase))) {
                score += 0.35
            }
        }
        return min(score, 1.5)
    }

    private fun extractCandidatePhrases(description: String): List<String> {
        val quoted = Regex("[\"“”'‘’]([^\"“”'‘’]{2,40})[\"“”'‘’]")
            .findAll(description)
            .map { it.groupValues[1] }
            .toList()
        val fallback = description.split(Regex("[,，。;；、\\n]"))
            .map { it.trim() }
            .filter { it.length in 2..24 }
        return (quoted + fallback).distinct().take(20)
    }

    private fun normalize(value: String): String {
        return value.lowercase()
            .replace(Regex("\\s+"), "")
            .replace("“", "")
            .replace("”", "")
            .replace("\"", "")
            .replace("'", "")
            .replace("。", "")
            .replace("，", "")
            .replace(",", "")
            .replace("！", "")
            .replace("!", "")
            .replace("？", "")
            .replace("?", "")
    }
}

internal data class ParsedSkillFile(
    val frontmatter: Map<String, String>,
    val body: String
)

internal fun parseSkillFile(skillFile: File): ParsedSkillFile? {
    if (!skillFile.exists() || !skillFile.isFile) return null
    val raw = skillFile.readText()
    if (!raw.startsWith("---")) {
        return ParsedSkillFile(frontmatter = emptyMap(), body = raw.trim())
    }
    val markerIndex = raw.indexOf("\n---", startIndex = 3)
    if (markerIndex <= 0) {
        return ParsedSkillFile(frontmatter = emptyMap(), body = raw.trim())
    }
    val frontmatterText = raw.substring(3, markerIndex).trim('\n', '\r')
    val body = raw.substring(markerIndex + 4).trim()
    return ParsedSkillFile(
        frontmatter = parseSimpleFrontmatter(frontmatterText),
        body = body
    )
}

internal fun parseSimpleFrontmatter(frontmatter: String): Map<String, String> {
    if (frontmatter.isBlank()) return emptyMap()
    val lines = frontmatter.lines()
    val result = linkedMapOf<String, String>()
    var index = 0
    while (index < lines.size) {
        val rawLine = lines[index]
        if (rawLine.isBlank()) {
            index += 1
            continue
        }
        val keyMatch = Regex("^([A-Za-z0-9_-]+):\\s*(.*)$").find(rawLine)
        if (keyMatch == null) {
            index += 1
            continue
        }
        val key = keyMatch.groupValues[1]
        val value = keyMatch.groupValues[2]
        if (value == ">" || value == "|") {
            val builder = StringBuilder()
            index += 1
            while (index < lines.size && (lines[index].startsWith("  ") || lines[index].isBlank())) {
                val next = lines[index]
                if (next.isNotBlank()) {
                    if (builder.isNotEmpty()) builder.append('\n')
                    builder.append(next.trim())
                }
                index += 1
            }
            result[key] = builder.toString().trim()
            continue
        }
        if (value.isBlank()) {
            val builder = StringBuilder()
            index += 1
            while (index < lines.size && (lines[index].startsWith("  ") || lines[index].startsWith("\t"))) {
                if (builder.isNotEmpty()) builder.append('\n')
                builder.append(lines[index].trimEnd())
                index += 1
            }
            result[key] = builder.toString().trim()
            continue
        }
        result[key] = value.trim().trim('"')
        index += 1
    }
    return result
}

internal fun parseIndentedBlock(raw: String): Map<String, String> {
    if (raw.isBlank()) return emptyMap()
    return raw.lines().mapNotNull { line ->
        val match = Regex("^\\s*([A-Za-z0-9_.-]+):\\s*(.*)$").find(line) ?: return@mapNotNull null
        match.groupValues[1] to match.groupValues[2].trim().trim('"')
    }.toMap()
}
