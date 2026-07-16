package com.wordmemo.app.ui.screen.wordgraph

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.wordmemo.app.data.local.WordMemoDatabase
import com.wordmemo.app.domain.model.Word
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.*

data class GraphNode(
    val id: String,
    val label: String,
    val chinese: String = "",
    val level: Int = 0,       // 0=center, 1=L1, 2=L2
    val type: String = "",    // synonym, antonym, collocation, similar, concept
    val parentId: String? = null,
    val branchIndex: Int = 0
)

data class GraphEdge(
    val from: String,
    val to: String,
    val label: String = ""
)

data class MultiLevelGraph(
    val centerWord: String = "",
    val nodes: List<GraphNode> = emptyList(),
    val edges: List<GraphEdge> = emptyList()
)

data class WordGraphUiState(
    val centerWord: Word? = null,
    val graph: MultiLevelGraph = MultiLevelGraph(),
    val isLoading: Boolean = true,
    val error: String? = null,
    val bookmarkedLabels: Set<String> = emptySet(),
    val lastBookmarked: String? = null
)

class WordGraphViewModel(application: Application) : AndroidViewModel(application) {

    private val _uiState = MutableStateFlow(WordGraphUiState())
    val uiState: StateFlow<WordGraphUiState> = _uiState.asStateFlow()

    private var centerWordForRelations: String = ""

    fun loadWord(wordId: Long) {
        viewModelScope.launch {
            _uiState.value = WordGraphUiState(isLoading = true)

            try {
                val db = WordMemoDatabase.getInstance(getApplication())
                val entity = withContext(Dispatchers.IO) { db.wordDao().getById(wordId) }

                if (entity == null) {
                    _uiState.value = WordGraphUiState(isLoading = false, error = "单词未找到")
                    return@launch
                }

                val word = Word(
                    id = entity.id, english = entity.english,
                    chinese = entity.chinese.ifBlank { "待补充" },
                    phonetic = entity.phonetic,
                    note = entity.note, createdAt = entity.createdAt,
                    updatedAt = entity.updatedAt
                )
                centerWordForRelations = word.english.lowercase()

                val graph = withContext(Dispatchers.Default) {
                    // 1. 预定义关系（最精确）
                    val fromMap = buildGraphFromMap(word.english, word.chinese)
                    if (fromMap != null) return@withContext fromMap

                    // 2. AI API 生成（不使用随机降级）
                    try {
                        val aiGen = com.wordmemo.app.data.network.AiGraphGenerator()
                        aiGen.setCenterWord(word.english)
                        val config = aiGen.loadApiConfig(db)
                        if (config != null) {
                            val json = aiGen.generateRelations(word.english, config.first, config.second, config.third)
                            if (json != null) {
                                val result = aiGen.parseResult(json)
                                if (result != null && result.relations.size >= 5) {
                                    return@withContext buildGraphFromAi(result, word.english, word.chinese)
                                }
                            }
                        }
                    } catch (_: Exception) { }

                    // AI不可用或无结果 → 返回空图
                    MultiLevelGraph(centerWord = word.english, nodes = listOf(
                        GraphNode(id = "center", label = word.english, chinese = word.chinese, level = 0, type = "center")
                    ), edges = emptyList())
                }

                _uiState.value = WordGraphUiState(
                    centerWord = word,
                    graph = graph,
                    isLoading = false
                )

            } catch (e: Exception) {
                _uiState.value = WordGraphUiState(
                    isLoading = false,
                    error = "加载失败: ${e.message}"
                )
            }
        }
    }

    // ─── 动态图谱生成：三种策略 ───

    /** 策略1: 预定义关系 map（最精确） */
    private fun buildGraphFromMap(centerWord: String, chinese: String): MultiLevelGraph? {
        val key = centerWord.lowercase()
        val relations = relationMap[key] ?: return null
        return buildGraphFromRelations(centerWord, chinese, relations.l1)
    }

    /** 策略2: AI API 生成的关系 */
    private fun buildGraphFromAi(result: com.wordmemo.app.data.network.AiGraphGenerator.AiGraphResult, centerWord: String, chinese: String): MultiLevelGraph {
        val l1 = result.relations.map { rel ->
            val l2 = rel.children.map { child ->
                RelWord(child.word, child.chinese, child.type)
            }
            WordRel(rel.word, rel.chinese, l2)
        }
        return buildGraphFromRelations(centerWord, chinese, l1)
    }

    /** 策略3: 兜底 — 从预定义 word 库按首字母相近的选 */
    private fun buildGraphFallback(centerWord: String, chinese: String): MultiLevelGraph {
        val seed = centerWord.lowercase().hashCode().toLong()
        val rng = java.util.Random(seed)
        val avail = chineseMap.keys
            .filter { it != centerWord.lowercase() }
            .sortedBy { if (it.firstOrNull() == centerWord.lowercase().firstOrNull()) 0 else 1 }
            .toMutableList()

        val typeCycle = listOf("synonym", "antonym", "collocation", "similar", "concept", "synonym", "antonym")
        val l1 = (0 until 7).mapNotNull { branch ->
            if (avail.isEmpty()) return@mapNotNull null
            val idx = (rng.nextInt() and Int.MAX_VALUE) % avail.size
            val w1 = avail.removeAt(idx)
            val c1 = chineseMap[w1] ?: w1
            val l2 = (0 until 2).mapNotNull {
                if (avail.isEmpty()) return@mapNotNull null
                val ci = (rng.nextInt() and Int.MAX_VALUE) % avail.size
                val w2 = avail.removeAt(ci)
                RelWord(w2, chineseMap[w2] ?: w2, typeCycle[(branch + it + 1) % typeCycle.size])
            }
            WordRel(w1, c1, l2)
        }
        return buildGraphFromRelations(centerWord, chinese, l1)
    }

    /** 通用：从关系列表构建 MultiLevelGraph */
    private fun buildGraphFromRelations(centerWord: String, chinese: String, relations: List<WordRel>): MultiLevelGraph {
        val allNodes = mutableListOf<GraphNode>()
        val allEdges = mutableListOf<GraphEdge>()
        val typeCycle = listOf("synonym", "antonym", "collocation", "similar", "concept", "synonym", "antonym")

        allNodes.add(GraphNode(id = "center", label = centerWord, chinese = chinese, level = 0, type = "center"))

        for ((branch, rel) in relations.take(7).withIndex()) {
            val l1Id = "l1_$branch"
            val type = typeCycle[branch % typeCycle.size]
            allNodes.add(GraphNode(id = l1Id, label = rel.word, chinese = rel.chinese, level = 1, type = type, branchIndex = branch))
            allEdges.add(GraphEdge(from = "center", to = l1Id, label = type))
            for ((ci, child) in rel.l2.take(2).withIndex()) {
                val l2Id = "l2_${branch}_$ci"
                val childType = typeCycle[(branch + ci + 1) % typeCycle.size]
                allNodes.add(GraphNode(id = l2Id, label = child.word, chinese = child.chinese, level = 2, type = childType, parentId = l1Id, branchIndex = branch))
                allEdges.add(GraphEdge(from = l1Id, to = l2Id, label = childType))
            }
        }
        return MultiLevelGraph(centerWord = centerWord, nodes = allNodes, edges = allEdges)
    }

    private fun lookupChinese(word: String): String {
        val lower = word.lowercase()
        chineseMap[lower]?.let { return it }
        return word.replaceFirstChar { it.uppercase() }
    }

    companion object {
        // ═══════════════════════════════════════════
        //  语义关系数据
        // ═══════════════════════════════════════════
        data class RelWord(val word: String, val chinese: String, val type: String = "")
        data class WordRel(val word: String, val chinese: String, val l2: List<RelWord> = emptyList())
        data class WordRelations(val l1: List<WordRel>)

        private val relationMap: Map<String, WordRelations> = mapOf(

            "perseverance" to WordRelations(listOf(
                WordRel("persistence", "坚持；持续", listOf(
                    RelWord("tenacious", "顽强的", "similar"),
                    RelWord("relentless", "不懈的", "synonym")
                )),
                WordRel("endurance", "忍耐；耐力", listOf(
                    RelWord("stamina", "耐力；精力", "synonym"),
                    RelWord("fatigue", "疲劳；疲乏", "antonym")
                )),
                WordRel("determination", "决心；果断", listOf(
                    RelWord("resolve", "决心；解决", "synonym"),
                    RelWord("willpower", "意志力", "synonym")
                )),
                WordRel("resilience", "韧性；恢复力", listOf(
                    RelWord("adaptability", "适应性", "similar"),
                    RelWord("fragility", "脆弱；易碎", "antonym")
                )),
                WordRel("patience", "耐心；忍耐", listOf(
                    RelWord("composure", "镇静；沉着", "collocation"),
                    RelWord("impulsiveness", "冲动", "antonym")
                )),
                WordRel("diligence", "勤奋；勤勉", listOf(
                    RelWord("industrious", "勤奋的", "synonym"),
                    RelWord("lazy", "懒惰的", "antonym")
                )),
                WordRel("tenacity", "顽强；固执", listOf(
                    RelWord("grit", "勇气；毅力", "synonym"),
                    RelWord("yield", "屈服；让步", "antonym")
                ))
            )),

            "knowledge" to WordRelations(listOf(
                WordRel("learning", "学习", listOf(RelWord("study", "学习；研究", "synonym"), RelWord("education", "教育", "concept"))),
                WordRel("wisdom", "智慧", listOf(RelWord("insight", "洞察力", "similar"), RelWord("folly", "愚蠢", "antonym"))),
                WordRel("information", "信息", listOf(RelWord("data", "数据", "synonym"), RelWord("ignorance", "无知", "antonym"))),
                WordRel("skill", "技能", listOf(RelWord("expertise", "专长", "synonym"), RelWord("practice", "实践", "collocation"))),
                WordRel("science", "科学", listOf(RelWord("theory", "理论", "concept"), RelWord("experiment", "实验", "collocation"))),
                WordRel("understanding", "理解", listOf(RelWord("comprehension", "理解力", "synonym"), RelWord("confusion", "困惑", "antonym"))),
                WordRel("discovery", "发现", listOf(RelWord("innovation", "创新", "similar"), RelWord("exploration", "探索", "collocation")))
            )),

            "success" to WordRelations(listOf(
                WordRel("achievement", "成就", listOf(RelWord("accomplishment", "成就", "synonym"), RelWord("failure", "失败", "antonym"))),
                WordRel("victory", "胜利", listOf(RelWord("triumph", "凯旋", "synonym"), RelWord("defeat", "击败", "antonym"))),
                WordRel("progress", "进步", listOf(RelWord("advancement", "前进", "synonym"), RelWord("stagnation", "停滞", "antonym"))),
                WordRel("wealth", "财富", listOf(RelWord("fortune", "财富", "synonym"), RelWord("poverty", "贫困", "antonym"))),
                WordRel("recognition", "认可", listOf(RelWord("reputation", "声誉", "similar"), RelWord("fame", "名声", "synonym"))),
                WordRel("growth", "成长", listOf(RelWord("development", "发展", "synonym"), RelWord("decline", "衰退", "antonym"))),
                WordRel("happiness", "幸福", listOf(RelWord("contentment", "满足", "similar"), RelWord("sorrow", "悲伤", "antonym")))
            )),

            "failure" to WordRelations(listOf(
                WordRel("mistake", "错误", listOf(RelWord("error", "过失", "synonym"), RelWord("correction", "纠正", "antonym"))),
                WordRel("defeat", "失败", listOf(RelWord("loss", "损失", "synonym"), RelWord("victory", "胜利", "antonym"))),
                WordRel("weakness", "弱点", listOf(RelWord("fragility", "脆弱", "similar"), RelWord("strength", "力量", "antonym"))),
                WordRel("obstacle", "障碍", listOf(RelWord("barrier", "壁垒", "synonym"), RelWord("opportunity", "机会", "antonym"))),
                WordRel("regret", "遗憾", listOf(RelWord("remorse", "懊悔", "synonym"), RelWord("satisfaction", "满意", "antonym"))),
                WordRel("crisis", "危机", listOf(RelWord("emergency", "紧急", "similar"), RelWord("solution", "解决", "antonym"))),
                WordRel("decline", "衰退", listOf(RelWord("deterioration", "恶化", "synonym"), RelWord("improvement", "改善", "antonym")))
            )),

            "change" to WordRelations(listOf(
                WordRel("transformation", "转变", listOf(RelWord("metamorphosis", "蜕变", "similar"), RelWord("stagnation", "停滞", "antonym"))),
                WordRel("progress", "进步", listOf(RelWord("evolution", "进化", "similar"), RelWord("regression", "退步", "antonym"))),
                WordRel("adaptation", "适应", listOf(RelWord("adjustment", "调整", "synonym"), RelWord("rigidity", "僵化", "antonym"))),
                WordRel("innovation", "创新", listOf(RelWord("invention", "发明", "synonym"), RelWord("imitation", "模仿", "antonym"))),
                WordRel("growth", "增长", listOf(RelWord("expansion", "扩张", "synonym"), RelWord("contraction", "收缩", "antonym"))),
                WordRel("transition", "过渡", listOf(RelWord("shift", "转变", "synonym"), RelWord("continuity", "连续", "antonym"))),
                WordRel("reform", "改革", listOf(RelWord("improvement", "改善", "synonym"), RelWord("conservation", "保守", "antonym")))
            )),

            "freedom" to WordRelations(listOf(
                WordRel("liberty", "自由", listOf(RelWord("autonomy", "自治", "synonym"), RelWord("captivity", "囚禁", "antonym"))),
                WordRel("independence", "独立", listOf(RelWord("self-reliance", "自力", "similar"), RelWord("dependence", "依赖", "antonym"))),
                WordRel("rights", "权利", listOf(RelWord("entitlement", "权利", "synonym"), RelWord("oppression", "压迫", "antonym"))),
                WordRel("choice", "选择", listOf(RelWord("option", "选项", "synonym"), RelWord("compulsion", "强迫", "antonym"))),
                WordRel("democracy", "民主", listOf(RelWord("equality", "平等", "concept"), RelWord("tyranny", "暴政", "antonym"))),
                WordRel("opportunity", "机会", listOf(RelWord("possibility", "可能性", "similar"), RelWord("restriction", "限制", "antonym"))),
                WordRel("peace", "和平", listOf(RelWord("harmony", "和谐", "similar"), RelWord("conflict", "冲突", "antonym")))
            )),

            "beauty" to WordRelations(listOf(
                WordRel("elegance", "优雅", listOf(RelWord("grace", "优雅", "synonym"), RelWord("ugliness", "丑陋", "antonym"))),
                WordRel("harmony", "和谐", listOf(RelWord("balance", "平衡", "similar"), RelWord("chaos", "混乱", "antonym"))),
                WordRel("art", "艺术", listOf(RelWord("creativity", "创造力", "concept"), RelWord("craftsmanship", "工艺", "similar"))),
                WordRel("splendor", "辉煌", listOf(RelWord("magnificence", "壮丽", "synonym"), RelWord("plainness", "朴素", "antonym"))),
                WordRel("charm", "魅力", listOf(RelWord("attraction", "吸引力", "synonym"), RelWord("repulsion", "厌恶", "antonym"))),
                WordRel("perfection", "完美", listOf(RelWord("excellence", "卓越", "similar"), RelWord("imperfection", "不完美", "antonym"))),
                WordRel("aesthetics", "美学", listOf(RelWord("taste", "品味", "concept"), RelWord("style", "风格", "similar")))
            )),

            "courage" to WordRelations(listOf(
                WordRel("bravery", "勇敢", listOf(RelWord("heroism", "英雄主义", "synonym"), RelWord("cowardice", "懦弱", "antonym"))),
                WordRel("boldness", "大胆", listOf(RelWord("daring", "胆量", "synonym"), RelWord("timidity", "胆怯", "antonym"))),
                WordRel("strength", "力量", listOf(RelWord("fortitude", "坚韧", "similar"), RelWord("weakness", "软弱", "antonym"))),
                WordRel("confidence", "自信", listOf(RelWord("self-assurance", "自信", "synonym"), RelWord("doubt", "怀疑", "antonym"))),
                WordRel("honor", "荣誉", listOf(RelWord("integrity", "正直", "similar"), RelWord("disgrace", "耻辱", "antonym"))),
                WordRel("adventure", "冒险", listOf(RelWord("exploration", "探索", "similar"), RelWord("safety", "安全", "antonym"))),
                WordRel("determination", "决心", listOf(RelWord("resolve", "决心", "synonym"), RelWord("hesitation", "犹豫", "antonym")))
            )),

            "power" to WordRelations(listOf(
                WordRel("authority", "权威", listOf(RelWord("control", "控制", "synonym"), RelWord("submission", "服从", "antonym"))),
                WordRel("strength", "力量", listOf(RelWord("force", "武力", "similar"), RelWord("weakness", "软弱", "antonym"))),
                WordRel("energy", "能量", listOf(RelWord("vigor", "活力", "synonym"), RelWord("lethargy", " lethargy", "antonym"))),
                WordRel("influence", "影响力", listOf(RelWord("dominance", "主导", "similar"), RelWord("powerlessness", "无力", "antonym"))),
                WordRel("leadership", "领导力", listOf(RelWord("guidance", "指引", "synonym"), RelWord("following", "跟随", "antonym"))),
                WordRel("capability", "能力", listOf(RelWord("competence", "胜任", "synonym"), RelWord("inability", "无能", "antonym"))),
                WordRel("wealth", "财富", listOf(RelWord("fortune", "财富", "synonym"), RelWord("poverty", "贫困", "antonym")))
            )),

            "education" to WordRelations(listOf(
                WordRel("learning", "学习", listOf(RelWord("study", "研究", "synonym"), RelWord("teaching", "教学", "collocation"))),
                WordRel("knowledge", "知识", listOf(RelWord("wisdom", "智慧", "similar"), RelWord("ignorance", "无知", "antonym"))),
                WordRel("school", "学校", listOf(RelWord("academy", "学院", "synonym"), RelWord("university", "大学", "collocation"))),
                WordRel("curriculum", "课程", listOf(RelWord("syllabus", "大纲", "synonym"), RelWord("extracurricular", "课外", "antonym"))),
                WordRel("teacher", "教师", listOf(RelWord("professor", "教授", "synonym"), RelWord("student", "学生", "antonym"))),
                WordRel("training", "培训", listOf(RelWord("practice", "实践", "similar"), RelWord("theory", "理论", "antonym"))),
                WordRel("development", "发展", listOf(RelWord("growth", "成长", "synonym"), RelWord("stagnation", "停滞", "antonym")))
            )),

            "health" to WordRelations(listOf(
                WordRel("wellness", "健康", listOf(RelWord("fitness", "健壮", "synonym"), RelWord("illness", "疾病", "antonym"))),
                WordRel("nutrition", "营养", listOf(RelWord("diet", "饮食", "collocation"), RelWord("malnutrition", "营养不良", "antonym"))),
                WordRel("exercise", "锻炼", listOf(RelWord("activity", "活动", "synonym"), RelWord("sedentary", "久坐", "antonym"))),
                WordRel("medicine", "药物", listOf(RelWord("treatment", "治疗", "synonym"), RelWord("prevention", "预防", "antonym"))),
                WordRel("strength", "力量", listOf(RelWord("vitality", "活力", "synonym"), RelWord("weakness", "虚弱", "antonym"))),
                WordRel("recovery", "恢复", listOf(RelWord("healing", "康复", "synonym"), RelWord("relapse", "复发", "antonym"))),
                WordRel("balance", "平衡", listOf(RelWord("harmony", "和谐", "similar"), RelWord("imbalance", "失调", "antonym")))
            )),

            "communication" to WordRelations(listOf(
                WordRel("dialogue", "对话", listOf(RelWord("conversation", "交谈", "synonym"), RelWord("monologue", "独白", "antonym"))),
                WordRel("expression", "表达", listOf(RelWord("articulation", "清晰表达", "synonym"), RelWord("silence", "沉默", "antonym"))),
                WordRel("message", "信息", listOf(RelWord("information", "信息", "synonym"), RelWord("misunderstanding", "误解", "antonym"))),
                WordRel("connection", "连接", listOf(RelWord("bond", "纽带", "similar"), RelWord("isolation", "孤立", "antonym"))),
                WordRel("language", "语言", listOf(RelWord("speech", "言语", "similar"), RelWord("sign", "信号", "collocation"))),
                WordRel("feedback", "反馈", listOf(RelWord("response", "回应", "synonym"), RelWord("ignore", "忽视", "antonym"))),
                WordRel("sharing", "分享", listOf(RelWord("exchange", "交流", "synonym"), RelWord("withholding", "保留", "antonym")))
            )),

            "adventure" to WordRelations(listOf(
                WordRel("journey", "旅程", listOf(RelWord("expedition", "探险", "synonym"), RelWord("routine", "日常", "antonym"))),
                WordRel("exploration", "探索", listOf(RelWord("discovery", "发现", "synonym"), RelWord("familiarity", "熟悉", "antonym"))),
                WordRel("risk", "风险", listOf(RelWord("danger", "危险", "synonym"), RelWord("safety", "安全", "antonym"))),
                WordRel("thrill", "刺激", listOf(RelWord("excitement", "兴奋", "synonym"), RelWord("boredom", "无聊", "antonym"))),
                WordRel("quest", "追求", listOf(RelWord("pursuit", "追寻", "synonym"), RelWord("surrender", "放弃", "antonym"))),
                WordRel("challenge", "挑战", listOf(RelWord("struggle", "奋斗", "similar"), RelWord("comfort", "安逸", "antonym"))),
                WordRel("courage", "勇气", listOf(RelWord("bravery", "勇敢", "synonym"), RelWord("fear", "恐惧", "antonym")))
            )),

            "leadership" to WordRelations(listOf(
                WordRel("guidance", "指引", listOf(RelWord("direction", "方向", "synonym"), RelWord("misguidance", "误导", "antonym"))),
                WordRel("vision", "愿景", listOf(RelWord("insight", "洞察", "similar"), RelWord("blindness", "盲目", "antonym"))),
                WordRel("influence", "影响力", listOf(RelWord("inspiration", "激励", "similar"), RelWord("indifference", "冷漠", "antonym"))),
                WordRel("responsibility", "责任", listOf(RelWord("accountability", "问责", "synonym"), RelWord("irresponsibility", "不负责任", "antonym"))),
                WordRel("strategy", "策略", listOf(RelWord("planning", "规划", "synonym"), RelWord("improvisation", "即兴", "antonym"))),
                WordRel("teamwork", "团队合作", listOf(RelWord("collaboration", "协作", "synonym"), RelWord("conflict", "冲突", "antonym"))),
                WordRel("decision", "决策", listOf(RelWord("choice", "选择", "synonym"), RelWord("indecision", "优柔寡断", "antonym")))
            )),

            "creativity" to WordRelations(listOf(
                WordRel("imagination", "想象力", listOf(RelWord("fantasy", "幻想", "similar"), RelWord("reality", "现实", "antonym"))),
                WordRel("innovation", "创新", listOf(RelWord("invention", "发明", "synonym"), RelWord("imitation", "模仿", "antonym"))),
                WordRel("inspiration", "灵感", listOf(RelWord("motivation", "动力", "similar"), RelWord("discouragement", "气馁", "antonym"))),
                WordRel("originality", "独创性", listOf(RelWord("uniqueness", "独特", "synonym"), RelWord("conformity", "从众", "antonym"))),
                WordRel("expression", "表达", listOf(RelWord("art", "艺术", "concept"), RelWord("suppression", "压抑", "antonym"))),
                WordRel("curiosity", "好奇心", listOf(RelWord("inquiry", "探究", "synonym"), RelWord("apathy", "冷漠", "antonym"))),
                WordRel("passion", "热情", listOf(RelWord("enthusiasm", "热忱", "synonym"), RelWord("indifference", "漠不关心", "antonym")))
            )),

            "trust" to WordRelations(listOf(
                WordRel("faith", "信念", listOf(RelWord("belief", "相信", "synonym"), RelWord("doubt", "怀疑", "antonym"))),
                WordRel("confidence", "信心", listOf(RelWord("assurance", "保证", "synonym"), RelWord("insecurity", "不安", "antonym"))),
                WordRel("honesty", "诚实", listOf(RelWord("integrity", "正直", "synonym"), RelWord("deception", "欺骗", "antonym"))),
                WordRel("reliability", "可靠", listOf(RelWord("dependability", "可信赖", "synonym"), RelWord("unreliability", "不可靠", "antonym"))),
                WordRel("loyalty", "忠诚", listOf(RelWord("devotion", "奉献", "synonym"), RelWord("betrayal", "背叛", "antonym"))),
                WordRel("friendship", "友谊", listOf(RelWord("bond", "纽带", "similar"), RelWord("enmity", "敌意", "antonym"))),
                WordRel("partnership", "伙伴关系", listOf(RelWord("alliance", "联盟", "synonym"), RelWord("rivalry", "竞争", "antonym")))
            )),

            "justice" to WordRelations(listOf(
                WordRel("fairness", "公平", listOf(RelWord("equality", "平等", "synonym"), RelWord("bias", "偏见", "antonym"))),
                WordRel("law", "法律", listOf(RelWord("legislation", "立法", "synonym"), RelWord("anarchy", "无政府", "antonym"))),
                WordRel("rights", "权利", listOf(RelWord("entitlement", "权利", "synonym"), RelWord("injustice", "不公", "antonym"))),
                WordRel("truth", "真相", listOf(RelWord("honesty", "诚实", "similar"), RelWord("falsehood", "谎言", "antonym"))),
                WordRel("order", "秩序", listOf(RelWord("discipline", "纪律", "similar"), RelWord("chaos", "混乱", "antonym"))),
                WordRel("mercy", "怜悯", listOf(RelWord("compassion", "同情", "similar"), RelWord("cruelty", "残忍", "antonym"))),
                WordRel("balance", "平衡", listOf(RelWord("harmony", "和谐", "similar"), RelWord("extremism", "极端", "antonym")))
            )),

            "love" to WordRelations(listOf(
                WordRel("affection", "喜爱", listOf(RelWord("fondness", "喜爱", "synonym"), RelWord("hatred", "仇恨", "antonym"))),
                WordRel("passion", "激情", listOf(RelWord("desire", "渴望", "similar"), RelWord("apathy", "冷漠", "antonym"))),
                WordRel("compassion", "同情", listOf(RelWord("empathy", "共情", "synonym"), RelWord("cruelty", "残忍", "antonym"))),
                WordRel("devotion", "奉献", listOf(RelWord("dedication", "专注", "synonym"), RelWord("neglect", "忽视", "antonym"))),
                WordRel("romance", "浪漫", listOf(RelWord("intimacy", "亲密", "similar"), RelWord("hostility", "敌意", "antonym"))),
                WordRel("kindness", "善良", listOf(RelWord("generosity", "慷慨", "similar"), RelWord("selfishness", "自私", "antonym"))),
                WordRel("care", "关怀", listOf(RelWord("nurture", "培育", "similar"), RelWord("indifference", "冷漠", "antonym")))
            )),

            "danger" to WordRelations(listOf(
                WordRel("threat", "威胁", listOf(RelWord("menace", "恐吓", "synonym"), RelWord("safety", "安全", "antonym"))),
                WordRel("risk", "风险", listOf(RelWord("hazard", "危险", "synonym"), RelWord("security", "安全", "antonym"))),
                WordRel("crisis", "危机", listOf(RelWord("emergency", "紧急", "synonym"), RelWord("calm", "平静", "antonym"))),
                WordRel("fear", "恐惧", listOf(RelWord("terror", "恐怖", "synonym"), RelWord("courage", "勇气", "antonym"))),
                WordRel("warning", "警告", listOf(RelWord("alert", "警报", "synonym"), RelWord("ignorance", "忽视", "antonym"))),
                WordRel("harm", "伤害", listOf(RelWord("damage", "损害", "synonym"), RelWord("protection", "保护", "antonym"))),
                WordRel("disaster", "灾难", listOf(RelWord("catastrophe", "浩劫", "synonym"), RelWord("recovery", "恢复", "antonym")))
            )),

            "opportunity" to WordRelations(listOf(
                WordRel("chance", "机会", listOf(RelWord("luck", "运气", "similar"), RelWord("misfortune", "不幸", "antonym"))),
                WordRel("possibility", "可能性", listOf(RelWord("potential", "潜力", "synonym"), RelWord("impossibility", "不可能", "antonym"))),
                WordRel("advantage", "优势", listOf(RelWord("benefit", "好处", "synonym"), RelWord("disadvantage", "劣势", "antonym"))),
                WordRel("growth", "增长", listOf(RelWord("progress", "进步", "synonym"), RelWord("decline", "衰退", "antonym"))),
                WordRel("discovery", "发现", listOf(RelWord("breakthrough", "突破", "similar"), RelWord("miss", "错过", "antonym"))),
                WordRel("choice", "选择", listOf(RelWord("decision", "决定", "synonym"), RelWord("hesitation", "犹豫", "antonym"))),
                WordRel("future", "未来", listOf(RelWord("prospect", "前景", "synonym"), RelWord("past", "过去", "antonym")))
            )),

            "wisdom" to WordRelations(listOf(
                WordRel("knowledge", "知识", listOf(RelWord("learning", "学习", "synonym"), RelWord("ignorance", "无知", "antonym"))),
                WordRel("intelligence", "智力", listOf(RelWord("cleverness", "聪明", "synonym"), RelWord("foolishness", "愚蠢", "antonym"))),
                WordRel("judgment", "判断", listOf(RelWord("discernment", "辨别", "synonym"), RelWord("folly", "愚行", "antonym"))),
                WordRel("experience", "经验", listOf(RelWord("practice", "实践", "similar"), RelWord("naivety", "天真", "antonym"))),
                WordRel("understanding", "理解", listOf(RelWord("comprehension", "理解", "synonym"), RelWord("confusion", "困惑", "antonym"))),
                WordRel("insight", "洞察", listOf(RelWord("perception", "感知", "synonym"), RelWord("blindness", "盲目", "antonym"))),
                WordRel("prudence", "谨慎", listOf(RelWord("caution", "小心", "similar"), RelWord("recklessness", "鲁莽", "antonym")))
            )),

            "team" to WordRelations(listOf(
                WordRel("collaboration", "协作", listOf(RelWord("cooperation", "合作", "synonym"), RelWord("competition", "竞争", "antonym"))),
                WordRel("unity", "团结", listOf(RelWord("solidarity", "团结", "synonym"), RelWord("division", "分裂", "antonym"))),
                WordRel("trust", "信任", listOf(RelWord("confidence", "信心", "similar"), RelWord("suspicion", "怀疑", "antonym"))),
                WordRel("communication", "沟通", listOf(RelWord("dialogue", "对话", "synonym"), RelWord("misunderstanding", "误解", "antonym"))),
                WordRel("coordination", "协调", listOf(RelWord("synergy", "协同", "similar"), RelWord("disorganization", "混乱", "antonym"))),
                WordRel("support", "支持", listOf(RelWord("encouragement", "鼓励", "synonym"), RelWord("opposition", "反对", "antonym"))),
                WordRel("goal", "目标", listOf(RelWord("objective", "目标", "synonym"), RelWord("aimlessness", "无目标", "antonym")))
            )),

            "technology" to WordRelations(listOf(
                WordRel("innovation", "创新", listOf(RelWord("invention", "发明", "synonym"), RelWord("obsolescence", "过时", "antonym"))),
                WordRel("digital", "数字", listOf(RelWord("electronic", "电子", "similar"), RelWord("analog", "模拟", "antonym"))),
                WordRel("automation", "自动化", listOf(RelWord("efficiency", "效率", "collocation"), RelWord("manual", "手工", "antonym"))),
                WordRel("software", "软件", listOf(RelWord("program", "程序", "synonym"), RelWord("hardware", "硬件", "antonym"))),
                WordRel("data", "数据", listOf(RelWord("information", "信息", "synonym"), RelWord("noise", "噪声", "antonym"))),
                WordRel("network", "网络", listOf(RelWord("connection", "连接", "synonym"), RelWord("isolation", "孤立", "antonym"))),
                WordRel("progress", "进步", listOf(RelWord("advancement", "推进", "synonym"), RelWord("regression", "倒退", "antonym")))
            ))
        )

        private val chineseMap: Map<String, String> = mapOf(
            "perseverance" to "毅力；坚持不懈",
            "persistence" to "坚持；持续",
            "endurance" to "忍耐；耐力",
            "determination" to "决心；果断",
            "resilience" to "韧性；恢复力",
            "patience" to "耐心；忍耐",
            "diligence" to "勤奋；勤勉",
            "tenacity" to "顽强；固执",
            "commitment" to "承诺；投入",
            "tenacious" to "顽强的",
            "dogged" to "顽强的；固执的",
            "stubborn" to "固执的",
            "relentless" to "不懈的",
            "unwavering" to "不动摇的",
            "stamina" to "耐力；精力",
            "tolerance" to "容忍；宽容",
            "fortitude" to "坚韧；刚毅",
            "resistance" to "抵抗；阻力",
            "fatigue" to "疲劳；疲乏",
            "resolve" to "决心；解决",
            "willpower" to "意志力",
            "dedication" to "奉献；专注",
            "ambition" to "雄心；抱负",
            "hesitation" to "犹豫；踌躇",
            "adaptability" to "适应性",
            "flexibility" to "灵活性",
            "recovery" to "恢复；复原",
            "fragility" to "脆弱；易碎",
            "buoyancy" to "浮力；乐观",
            "composure" to "镇静；沉着",
            "impulsiveness" to "冲动",
            "persevere" to "坚持不懈",
            "calmness" to "平静；冷静",
            "industrious" to "勤奋的",
            "assiduous" to "刻苦的",
            "lazy" to "懒惰的",
            "conscientious" to "认真的",
            "effort" to "努力",
            "grit" to "勇气；毅力",
            "obstinate" to "倔强的",
            "yield" to "屈服；让步",
            "steely" to "钢铁般的",
            "backbone" to "骨干；骨气",
            "devotion" to "奉献；热爱",
            "loyalty" to "忠诚；忠心",
            "apathy" to "冷漠；无动于衷",
            "responsibility" to "责任",
            "engagement" to "参与；约定",
            "knowledge" to "知识；学问",
            "learning" to "学习；学问",
            "wisdom" to "智慧；才智",
            "success" to "成功；成就",
            "failure" to "失败；失灵",
            "courage" to "勇气；胆量",
            "freedom" to "自由；自主",
            "beauty" to "美丽；美感",
            "change" to "变化；改变",
            "power" to "力量；权力",
            "education" to "教育；培养",
            "health" to "健康；卫生",
            "communication" to "交流；沟通",
            "adventure" to "冒险；奇遇",
            "leadership" to "领导力",
            "creativity" to "创造力",
            "trust" to "信任；信赖",
            "justice" to "正义；公正",
            "love" to "爱；热爱",
            "danger" to "危险",
            "opportunity" to "机会；机遇",
            "team" to "团队；队伍",
            "technology" to "技术；科技"
        )

        private val phoneticMap: Map<String, String> = mapOf(
            "perseverance" to "/pɜːsəˈvɪərəns/",
            "persistence" to "/pəˈsɪstəns/",
            "endurance" to "/ɪnˈdjʊərəns/",
            "determination" to "/dɪˌtɜːmɪˈneɪʃən/",
            "resilience" to "/rɪˈzɪliəns/",
            "patience" to "/ˈpeɪʃəns/",
            "diligence" to "/ˈdɪlɪdʒəns/",
            "tenacity" to "/tɪˈnæsɪti/",
            "commitment" to "/kəˈmɪtmənt/",
            "tenacious" to "/tɪˈneɪʃəs/",
            "dogged" to "/ˈdɒɡɪd/",
            "stubborn" to "/ˈstʌbən/",
            "relentless" to "/rɪˈlentləs/",
            "unwavering" to "/ʌnˈweɪvərɪŋ/",
            "stamina" to "/ˈstæmɪnə/",
            "tolerance" to "/ˈtɒlərəns/",
            "fortitude" to "/ˈfɔːtɪtjuːd/",
            "resistance" to "/rɪˈzɪstəns/",
            "fatigue" to "/fəˈtiːɡ/",
            "resolve" to "/rɪˈzɒlv/",
            "willpower" to "/ˈwɪlˌpaʊə/",
            "dedication" to "/ˌdedɪˈkeɪʃən/",
            "ambition" to "/æmˈbɪʃən/",
            "hesitation" to "/ˌhezɪˈteɪʃən/",
            "adaptability" to "/əˌdæptəˈbɪlɪti/",
            "flexibility" to "/ˌfleksɪˈbɪlɪti/",
            "recovery" to "/rɪˈkʌvəri/",
            "fragility" to "/frəˈdʒɪlɪti/",
            "buoyancy" to "/ˈbɔɪənsi/",
            "composure" to "/kəmˈpəʊʒə/",
            "impulsiveness" to "/ɪmˈpʌlsɪvnəs/",
            "persevere" to "/ˌpɜːsɪˈvɪə/",
            "calmness" to "/ˈkɑːmnəs/",
            "industrious" to "/ɪnˈdʌstriəs/",
            "assiduous" to "/əˈsɪdjuəs/",
            "lazy" to "/ˈleɪzi/",
            "conscientious" to "/ˌkɒnʃiˈenʃəs/",
            "effort" to "/ˈefət/",
            "grit" to "/ɡrɪt/",
            "obstinate" to "/ˈɒbstɪnət/",
            "yield" to "/jiːld/",
            "steely" to "/ˈstiːli/",
            "backbone" to "/ˈbækbəʊn/",
            "devotion" to "/dɪˈvəʊʃən/",
            "loyalty" to "/ˈlɔɪəlti/",
            "apathy" to "/ˈæpəθi/",
            "responsibility" to "/rɪˌspɒnsəˈbɪlɪti/",
            "engagement" to "/ɪnˈɡeɪdʒmənt/"
        )
    }

    fun navigateToWord(label: String) {
        viewModelScope.launch {
            try {
                val db = WordMemoDatabase.getInstance(getApplication())
                val entity = withContext(Dispatchers.IO) { db.wordDao().search("%$label%").firstOrNull() }
                if (entity != null) loadWord(entity.id)
            } catch (_: Exception) { }
        }
    }

    fun addWordToBook(label: String, chinese: String = "") {
        viewModelScope.launch {
            if (label in _uiState.value.bookmarkedLabels) return@launch
            try {
                val phonetic = lookupPhonetic(label)
                val db = WordMemoDatabase.getInstance(getApplication())
                val existing = withContext(Dispatchers.IO) { db.wordDao().search("%$label%").firstOrNull() }
                if (existing != null) {
                    _uiState.value = _uiState.value.copy(bookmarkedLabels = _uiState.value.bookmarkedLabels + label, lastBookmarked = label)
                    return@launch
                }
                val now = System.currentTimeMillis()
                val wid = withContext(Dispatchers.IO) { db.wordDao().insert(com.wordmemo.app.data.local.entity.WordEntity(english = label, chinese = chinese, phonetic = phonetic, note = "来自关系图谱", createdAt = now, updatedAt = now)) }
                withContext(Dispatchers.IO) { com.wordmemo.app.data.local.entity.FlashcardEntity(wordId = wid, state = "NEW", due = now).let { db.flashcardDao().insert(it) } }
                _uiState.value = _uiState.value.copy(bookmarkedLabels = _uiState.value.bookmarkedLabels + label, lastBookmarked = label)
            } catch (_: Exception) { }
        }
    }

    private fun lookupPhonetic(word: String): String {
        val lower = word.lowercase()
        return phoneticMap[lower] ?: ""
    }
}
