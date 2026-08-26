package com.gongkao.checkin.data

import android.content.Context
import android.os.Handler
import android.os.Looper
import com.google.gson.Gson
import com.google.gson.JsonElement
import com.google.gson.reflect.TypeToken

/**
 * 资料分析技巧复盘题库（65 题，随 assets/bank.json 打包）。
 * 每题给材料或表格 + 题干 + ABCD 选项，答完后逐步放出 [BankQuestion.anim] 里的讲解步骤。
 */
data class BankQuestion(
    var id: String = "",
    /** deck1..deck6，与 [chapter] 一对一 */
    var deck: String = "",
    var chapter: String = "",
    /** 真题出处，如「2023广东」，可能为空 */
    var source: String = "",
    /** 表格题才有，与 [material] 互斥（65 题里 4 题有表格） */
    var table: BankTable? = null,
    /** 文字材料，与 [table] 互斥（61 题有） */
    var material: String = "",
    var stem: String = "",
    /** 固定 A/B/C/D 四个键 */
    var options: LinkedHashMap<String, String> = LinkedHashMap(),
    var answer: String = "",
    /** 本题考的技巧名，如「增长量＝现期−基期 + 尾数法」 */
    var skill: String = "",
    /** 完整解析全文，题库浏览页直接展示 */
    var solution: String = "",
    /** 分步讲解，逐步放出 */
    var anim: MutableList<AnimStep> = mutableListOf()
) {
    fun optionList(): List<Pair<String, String>> = OPTION_KEYS.mapNotNull { k ->
        options[k]?.let { k to it }
    }

    companion object {
        val OPTION_KEYS = listOf("A", "B", "C", "D")
    }
}

/** 讲解的一步。*/
data class AnimStep(
    /** 小标题，如「别套公式」「尾数法更快」「坑」 */
    var t: String = "",
    /** 正文 */
    var b: String = "",
    /** 这一步排除掉的选项字母，界面上划掉它们 */
    var kill: MutableList<String> = mutableListOf(),
    /** 这一步是否点出正确答案（每题恰好有一步为 true） */
    var pick: Boolean = false,
    /** 正文里要高亮的关键数字 */
    var facts: MutableList<String> = mutableListOf()
)

/**
 * 表格材料。[head] 的单元格在 JSON 里是两种形态：纯字符串，或 `{"t":"年份","rs":2}` 这样带跨行/跨列的对象，
 * 所以先按 [JsonElement] 收下再统一成 [HeadCell]。
 */
class BankTable {
    var title: String = ""
    var head: MutableList<MutableList<JsonElement>> = mutableListOf()
    var rows: MutableList<MutableList<String>> = mutableListOf()

    /** 表头按行归一化成带跨度的单元格。 */
    fun headCells(): List<List<HeadCell>> = head.map { row ->
        row.map { cell ->
            if (cell.isJsonObject) {
                val o = cell.asJsonObject
                HeadCell(
                    text = o.get("t")?.asString ?: "",
                    rowSpan = o.get("rs")?.asInt ?: 1,
                    colSpan = o.get("cs")?.asInt ?: 1
                )
            } else {
                HeadCell(text = cell.asString)
            }
        }
    }

    /** 总列数：以数据行为准，表头缺列时用表头跨度兜底。 */
    fun columnCount(): Int {
        val byRows = rows.maxOfOrNull { it.size } ?: 0
        val byHead = headCells().firstOrNull()?.sumOf { it.colSpan } ?: 0
        return maxOf(byRows, byHead)
    }
}

data class HeadCell(val text: String, val rowSpan: Int = 1, val colSpan: Int = 1)

/**
 * 一个题库来源。[hasSteps] 决定练习页怎么讲解：
 * 精选那 65 题带 `anim` 分步数据，陪陪刷那批只有一段 `solution` 纯文本。
 */
data class BankSource(
    val id: String,
    val name: String,
    val asset: String,
    val hasSteps: Boolean
)

object BankSources {
    val CURATED = BankSource("curated", "精选 65 题", "bank.json", hasSteps = true)
    val PEIPEI = BankSource("peipei", "真题题库", "bank_peipei.json", hasSteps = false)

    val all = listOf(CURATED, PEIPEI)

    fun byId(id: String?): BankSource = all.firstOrNull { it.id == id } ?: CURATED
}

object BankData {

    private val cache = mutableMapOf<String, List<BankQuestion>>()

    /** 已经缓存好了就直接给，否则返回 null——调用方该走 [loadAsync]。 */
    fun cached(source: BankSource): List<BankQuestion>? = synchronized(cache) { cache[source.id] }

    /**
     * 取题库。真题题库有 1175 题、2.3MB，解析要几百毫秒，不能在主线程做，
     * 所以统一走后台线程 + 主线程回调；已缓存时同步回调，不闪一下加载态。
     */
    fun loadAsync(ctx: Context, source: BankSource, onReady: (List<BankQuestion>) -> Unit) {
        cached(source)?.let {
            onReady(it)
            return
        }
        val app = ctx.applicationContext
        val main = Handler(Looper.getMainLooper())
        Thread {
            val list = load(app, source)
            synchronized(cache) { cache[source.id] = list }
            main.post { onReady(list) }
        }.start()
    }

    private fun load(ctx: Context, source: BankSource): List<BankQuestion> = runCatching {
        val json = ctx.assets.open(source.asset).bufferedReader().use { it.readText() }
        val type = object : TypeToken<List<BankQuestion>>() {}.type
        Gson().fromJson<List<BankQuestion>>(json, type) ?: emptyList()
    }.getOrDefault(emptyList())

    /** 章节 chip 用，首项是「全部」。顺序按题库里出现的先后。 */
    fun chapters(list: List<BankQuestion>): List<String> =
        listOf(ALL) + list.map { it.chapter }.distinct()

    fun byChapter(list: List<BankQuestion>, chapter: String): List<BankQuestion> =
        if (chapter == ALL) list else list.filter { it.chapter == chapter }

    /**
     * 关键词搜索：空格分词，每个词都要命中（题干/考点/章节/出处/材料/选项任一）。
     * 全是中文所以直接 contains，不做分词。
     */
    fun search(list: List<BankQuestion>, query: String, limit: Int = 200): List<BankQuestion> {
        val terms = query.trim().split(Regex("\\s+")).filter { it.isNotBlank() }
        if (terms.isEmpty()) return emptyList()
        return list.asSequence()
            .filter { q ->
                val hay = buildString {
                    append(q.stem).append('\n').append(q.skill).append('\n')
                    append(q.chapter).append('\n').append(q.source).append('\n')
                    append(q.material).append('\n')
                    q.options.values.forEach { append(it).append('\n') }
                }
                terms.all { hay.contains(it, ignoreCase = true) }
            }
            .take(limit)
            .toList()
    }

    const val ALL = "全部"
}
