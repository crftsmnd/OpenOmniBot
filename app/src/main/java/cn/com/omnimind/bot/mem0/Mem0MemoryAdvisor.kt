package cn.com.omnimind.bot.mem0

import java.util.Locale
import kotlin.math.min

object Mem0MemoryAdvisor {
    private const val UPDATE_CANDIDATE_THRESHOLD = 0.62
    private const val RELATED_CANDIDATE_THRESHOLD = 0.38

    data class CandidateMatch(
        val item: Mem0MemoryItem,
        val score: Double
    ) {
        fun toPromptMap(): Map<String, Any?> {
            return linkedMapOf(
                "id" to item.id,
                "memory" to item.text,
                "score" to score,
                "categories" to item.categories
            )
        }
    }

    data class WriteHint(
        val shouldWrite: Boolean,
        val forceWriteBeforeFinalResponse: Boolean,
        val preferredAction: String,
        val priority: String,
        val reason: String,
        val confidence: Double,
        val candidateMemoryId: String? = null,
        val candidateMemoryText: String? = null,
        val candidateScore: Double? = null,
        val relatedCandidates: List<CandidateMatch> = emptyList()
    ) {
        fun toPromptMap(): Map<String, Any?> {
            return linkedMapOf(
                "shouldWrite" to shouldWrite,
                "forceWriteBeforeFinalResponse" to forceWriteBeforeFinalResponse,
                "preferredAction" to preferredAction,
                "priority" to priority,
                "reason" to reason,
                "confidence" to confidence,
                "candidateMemoryId" to candidateMemoryId,
                "candidateMemoryText" to candidateMemoryText,
                "candidateScore" to candidateScore,
                "relatedMemories" to relatedCandidates.map(CandidateMatch::toPromptMap)
            )
        }
    }

    private data class Signature(
        val normalized: String,
        val tokens: Set<String>,
        val domains: Set<String>,
        val relationTags: Set<String>,
        val explicitRemember: Boolean,
        val noStore: Boolean,
        val firstPerson: Boolean,
        val barePreferenceStatement: Boolean,
        val questionLike: Boolean,
        val commandLike: Boolean,
        val ephemeral: Boolean,
        val stableDirective: Boolean,
        val longTermValueSignal: Boolean,
        val forceSignal: Boolean
    )

    private val domainKeywords: Map<String, List<String>> = linkedMapOf(
        "music" to listOf("歌", "歌手", "音乐", "听歌", "歌曲", "艺人", "专辑", "演唱会"),
        "food" to listOf("吃", "饭", "菜", "火锅", "面", "米饭", "甜品", "零食", "口味", "辣"),
        "drink" to listOf("喝", "咖啡", "奶茶", "果汁", "饮料", "美式", "拿铁", "可乐", "酒"),
        "movie" to listOf("电影", "电视剧", "综艺", "动漫", "动画", "演员"),
        "book" to listOf("书", "小说", "作者", "阅读", "看书"),
        "travel" to listOf("旅游", "旅行", "景点", "出行", "酒店", "航班"),
        "shopping" to listOf("购物", "买", "下单", "品牌", "尺码", "优惠券"),
        "workstyle" to listOf("回复", "总结", "简洁", "详细", "中文", "英文", "语气", "格式"),
        "health" to listOf("过敏", "忌口", "不能吃", "不能喝", "不舒服", "药"),
        "schedule" to listOf("早上", "晚上", "起床", "睡觉", "午休", "周末", "每天", "习惯"),
        "sports" to listOf("运动", "跑步", "健身", "篮球", "足球", "羽毛球")
    )

    private val relationKeywordMap: Map<String, List<String>> = linkedMapOf(
        "like" to listOf("喜欢", "最喜欢", "偏好", "爱", "常听", "常看", "常吃", "常喝", "更喜欢"),
        "dislike" to listOf("不喜欢", "讨厌", "不吃", "不喝", "别推荐", "不要推荐"),
        "default" to listOf("默认", "以后都", "一律", "优先", "通常", "习惯", "总是"),
        "identity" to listOf("我是", "我叫", "叫我", "我的名字", "来自", "职业"),
        "constraint" to listOf("过敏", "忌口", "不能吃", "不能喝", "不要")
    )

    private val explicitRememberMarkers = listOf(
        "记住", "记一下", "记着", "别忘", "以后都按这个", "以后默认", "长期记忆"
    )
    private val noStoreMarkers = listOf("别记", "不要记", "不用记", "无需记住")
    private val commandMarkers = listOf(
        "帮我", "请帮", "请你", "打开", "播放", "搜索", "查一下", "看看", "告诉我",
        "设置", "提醒", "发给", "切换", "执行", "运行", "创建", "删除", "修改", "查询"
    )
    private val questionMarkers = listOf("吗", "么", "？", "?", "为什么", "怎么", "如何", "啥")
    private val ephemeralMarkers = listOf(
        "验证码", "一次性", "临时", "这次", "本次", "今天", "现在", "刚刚", "当前",
        "马上", "待会", "订单号", "取件码", "快递单号", "会议号", "房间号", "密码", "明天"
    )
    private val barePreferencePrefixes = listOf(
        "喜欢", "最喜欢", "偏好", "习惯", "通常", "经常", "不喜欢", "讨厌", "爱吃", "爱喝", "常听", "常看"
    )
    private val stopTokens = setOf(
        "用户", "喜欢", "最喜欢", "偏好", "习惯", "默认", "以后", "一直", "总是",
        "通常", "经常", "自己", "感觉", "这个", "那个", "就是", "一个", "一些",
        "我的", "我们", "他们", "她们", "因为", "所以"
    )

    fun analyzeWriteHint(
        userMessage: String,
        candidates: List<Mem0MemoryItem>
    ): WriteHint {
        val signature = buildSignature(userMessage)
        if (!shouldWrite(signature)) {
            return WriteHint(
                shouldWrite = false,
                forceWriteBeforeFinalResponse = false,
                preferredAction = "none",
                priority = "none",
                reason = buildSkipReason(signature),
                confidence = 0.18
            )
        }

        val rankedCandidates = rankCandidates(userMessage, candidates)
        val preferredCandidate = rankedCandidates.firstOrNull { it.score >= UPDATE_CANDIDATE_THRESHOLD }
        val preferredAction = if (preferredCandidate != null) "update" else "add"
        val forceWrite = signature.forceSignal
        val priority = if (forceWrite) "high" else "medium"
        val confidence = if (preferredCandidate != null) {
            min(0.98, 0.68 + preferredCandidate.score * 0.25)
        } else if (forceWrite) {
            0.84
        } else {
            0.66
        }
        val reason = when {
            preferredCandidate != null -> "本轮用户透露了适合长期保留的信息，且与已有记忆高度同主题，应优先更新合并，避免新增近重复记忆。"
            forceWrite -> "本轮用户明确表达了稳定偏好、身份或长期约束，适合在结束前写入长期记忆。"
            else -> "本轮用户透露了较稳定的长期偏好或习惯，建议积极写入长期记忆。"
        }

        return WriteHint(
            shouldWrite = true,
            forceWriteBeforeFinalResponse = forceWrite,
            preferredAction = preferredAction,
            priority = priority,
            reason = reason,
            confidence = confidence,
            candidateMemoryId = preferredCandidate?.item?.id,
            candidateMemoryText = preferredCandidate?.item?.text,
            candidateScore = preferredCandidate?.score,
            relatedCandidates = rankedCandidates
                .filter { it.score >= RELATED_CANDIDATE_THRESHOLD }
                .take(3)
        )
    }

    fun findUpdateCandidate(
        userMessage: String,
        candidates: List<Mem0MemoryItem>
    ): CandidateMatch? {
        return rankCandidates(userMessage, candidates)
            .firstOrNull { it.score >= UPDATE_CANDIDATE_THRESHOLD }
    }

    internal fun computeSimilarityScore(
        query: String,
        candidateText: String
    ): Double {
        val querySignature = buildSignature(query)
        val candidateSignature = buildSignature(candidateText)
        return computeSimilarityScore(querySignature, candidateSignature)
    }

    fun mergeMemoryText(
        existingText: String,
        incomingText: String
    ): String {
        val existing = existingText.trim()
        val incoming = incomingText.trim()
        if (existing.isBlank()) return incoming
        if (incoming.isBlank()) return existing

        val existingCompact = normalizeCompact(existing)
        val incomingCompact = normalizeCompact(incoming)
        if (existingCompact.contains(incomingCompact)) {
            return existing
        }
        if (incomingCompact.contains(existingCompact)) {
            return incoming
        }

        val sanitizedExisting = existing.trimEnd('。', '；', ';', '，', ',')
        val sanitizedIncoming = incoming.trimStart('。', '；', ';', '，', ',')
        return "$sanitizedExisting；$sanitizedIncoming"
    }

    private fun rankCandidates(
        userMessage: String,
        candidates: List<Mem0MemoryItem>
    ): List<CandidateMatch> {
        if (userMessage.isBlank() || candidates.isEmpty()) return emptyList()
        val signature = buildSignature(userMessage)
        return candidates.map { item ->
            CandidateMatch(
                item = item,
                score = computeSimilarityScore(signature, buildSignature(item.text))
            )
        }
            .filter { it.score > 0.0 }
            .sortedByDescending { it.score }
    }

    private fun shouldWrite(signature: Signature): Boolean {
        if (signature.noStore) return false
        if (signature.questionLike && !signature.explicitRemember) return false
        if (signature.ephemeral && !signature.longTermValueSignal) return false
        if (
            signature.commandLike &&
            !signature.stableDirective &&
            !signature.explicitRemember &&
            !(signature.firstPerson && signature.relationTags.isNotEmpty())
        ) {
            return false
        }
        return signature.explicitRemember ||
            signature.stableDirective ||
            (signature.longTermValueSignal && (signature.firstPerson || signature.barePreferenceStatement))
    }

    private fun buildSkipReason(signature: Signature): String {
        return when {
            signature.noStore -> "用户明确表示这轮信息无需写入长期记忆。"
            signature.ephemeral && !signature.longTermValueSignal -> "本轮信息更像一次性、时效性内容，不适合写入长期记忆。"
            signature.questionLike -> "本轮更像提问或确认，不是新的长期记忆事实。"
            signature.commandLike -> "本轮主要是执行任务指令，不是稳定的用户画像或长期偏好。"
            else -> "本轮信息不足以判断为值得长期保存的稳定记忆。"
        }
    }

    private fun buildSignature(text: String): Signature {
        val raw = text.trim()
        val normalized = raw.lowercase(Locale.getDefault())
        val explicitRemember = explicitRememberMarkers.any { normalized.contains(it) }
        val noStore = noStoreMarkers.any { normalized.contains(it) }
        val firstPerson = listOf("我", "我的", "叫我", "我是").any { normalized.contains(it) }
        val barePreferenceStatement = barePreferencePrefixes.any { normalized.startsWith(it) }
        val questionLike = questionMarkers.any { normalized.contains(it) }
        val ephemeral = ephemeralMarkers.any { normalized.contains(it) }
        val domains = domainKeywords
            .filterValues { keywords -> keywords.any { normalized.contains(it) } }
            .keys
            .toSet()
        val relationTags = relationKeywordMap
            .filterValues { keywords -> keywords.any { normalized.contains(it) } }
            .keys
            .toMutableSet()
        if (barePreferenceStatement) {
            relationTags.add("like")
        }
        val commandLike = commandMarkers.any { normalized.contains(it) } &&
            !explicitRemember &&
            !barePreferenceStatement
        val stableDirective = relationTags.any { it == "default" || it == "constraint" || it == "identity" } ||
            normalized.contains("以后默认") ||
            normalized.contains("以后都") ||
            normalized.contains("默认用") ||
            normalized.contains("统一用") ||
            normalized.contains("记住")
        val longTermValueSignal = relationTags.isNotEmpty() ||
            barePreferenceStatement ||
            normalized.contains("我的偏好") ||
            normalized.contains("以后") ||
            normalized.contains("默认") ||
            normalized.contains("习惯") ||
            normalized.contains("经常")
        val forceSignal = explicitRemember ||
            stableDirective ||
            normalized.contains("默认") ||
            normalized.contains("以后都") ||
            normalized.contains("我的偏好") ||
            normalized.contains("我是") ||
            normalized.contains("我叫") ||
            normalized.contains("叫我") ||
            (firstPerson && longTermValueSignal && !ephemeral && !commandLike)
        return Signature(
            normalized = normalized,
            tokens = tokenize(normalized),
            domains = domains,
            relationTags = relationTags,
            explicitRemember = explicitRemember,
            noStore = noStore,
            firstPerson = firstPerson,
            barePreferenceStatement = barePreferenceStatement,
            questionLike = questionLike,
            commandLike = commandLike,
            ephemeral = ephemeral,
            stableDirective = stableDirective,
            longTermValueSignal = longTermValueSignal,
            forceSignal = forceSignal
        )
    }

    private fun computeSimilarityScore(
        querySignature: Signature,
        candidateSignature: Signature
    ): Double {
        if (querySignature.normalized.isBlank() || candidateSignature.normalized.isBlank()) {
            return 0.0
        }

        var score = 0.0
        val sharedDomains = querySignature.domains.intersect(candidateSignature.domains)
        if (sharedDomains.isNotEmpty()) {
            score += 0.52 + min(0.16, (sharedDomains.size - 1).coerceAtLeast(0) * 0.08)
        }

        val sharedRelations = querySignature.relationTags.intersect(candidateSignature.relationTags)
        if (sharedRelations.isNotEmpty()) {
            score += 0.22
        }

        val tokenOverlap = jaccardSimilarity(querySignature.tokens, candidateSignature.tokens)
        score += min(0.26, tokenOverlap * 0.4)

        val queryCompact = normalizeCompact(querySignature.normalized)
        val candidateCompact = normalizeCompact(candidateSignature.normalized)
        if (queryCompact.isNotBlank() && candidateCompact.isNotBlank()) {
            if (candidateCompact.contains(queryCompact) || queryCompact.contains(candidateCompact)) {
                score += 0.18
            }
        }

        if (sharedDomains.isEmpty() && tokenOverlap < 0.12 && sharedRelations.isEmpty()) {
            return 0.0
        }
        return score.coerceIn(0.0, 1.0)
    }

    private fun jaccardSimilarity(left: Set<String>, right: Set<String>): Double {
        if (left.isEmpty() || right.isEmpty()) return 0.0
        val intersection = left.intersect(right).size.toDouble()
        val union = left.union(right).size.toDouble()
        if (union <= 0.0) return 0.0
        return intersection / union
    }

    private fun tokenize(text: String): Set<String> {
        val compact = normalizeCompact(text)
        val tokens = text
            .split(Regex("[^\\p{L}\\p{N}]+"))
            .map { it.trim() }
            .filter { it.length >= 2 && it !in stopTokens }
            .toMutableSet()

        if (compact.any { it.code > 127 } && compact.length >= 2) {
            for (index in 0 until compact.length - 1) {
                val biGram = compact.substring(index, index + 2)
                if (biGram !in stopTokens) {
                    tokens.add(biGram)
                }
            }
        }
        return tokens
    }

    private fun normalizeCompact(text: String): String {
        return text.lowercase(Locale.getDefault())
            .filter { it.isLetterOrDigit() }
    }
}
