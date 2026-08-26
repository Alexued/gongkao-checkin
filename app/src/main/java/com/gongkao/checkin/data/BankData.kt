package com.gongkao.checkin.data

import android.content.Context
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

object BankData {

    @Volatile
    private var cache: List<BankQuestion>? = null

    /** 首次调用时从 assets 解析并缓存（208KB，解析一次约几十毫秒）。 */
    fun list(ctx: Context): List<BankQuestion> {
        cache?.let { return it }
        return synchronized(this) {
            cache ?: load(ctx).also { cache = it }
        }
    }

    private fun load(ctx: Context): List<BankQuestion> = runCatching {
        val json = ctx.assets.open("bank.json").bufferedReader().use { it.readText() }
        val type = object : TypeToken<List<BankQuestion>>() {}.type
        Gson().fromJson<List<BankQuestion>>(json, type) ?: emptyList()
    }.getOrDefault(emptyList())

    /** 章节 chip 用，首项是「全部」。顺序按题库里出现的先后。 */
    fun chapters(ctx: Context): List<String> =
        listOf(ALL) + list(ctx).map { it.chapter }.distinct()

    fun byChapter(ctx: Context, chapter: String): List<BankQuestion> =
        if (chapter == ALL) list(ctx) else list(ctx).filter { it.chapter == chapter }

    fun byId(ctx: Context, id: String): BankQuestion? =
        list(ctx).firstOrNull { it.id == id }

    const val ALL = "全部"
}
