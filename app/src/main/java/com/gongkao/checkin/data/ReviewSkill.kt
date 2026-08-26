package com.gongkao.checkin.data

/**
 * 讲解风格。留这个抽象是为了以后能加「名师风格」讲解：
 * 陪陪刷那边的名师 skills 本质是给 AI 的 systemPrompt，离线没有多套讲解内容，
 * 所以目前只有 [Builtin] 一种能用，其余先注册占位、界面上灰掉。
 *
 * 真要做出多风格讲解得接 AI（要 key、要联网、按 token 计费），那是另一个决定。
 */
data class ReviewSkill(
    val id: String,
    val name: String,
    val tagline: String,
    /** false＝仅占位，界面上不可选 */
    val available: Boolean
)

object SkillRegistry {

    /** 题库自带的讲解：精选 65 题用 anim 分步，真题题库用 solution 全文。 */
    val Builtin = ReviewSkill(
        id = "builtin",
        name = "题库内置讲解",
        tagline = "精选题走分步拆解，真题走原版解析",
        available = true
    )

    private val HuaTu = ReviewSkill(
        id = "huatu",
        name = "华图风格",
        tagline = "重公式套用，先定题型再套公式",
        available = false
    )

    private val FenBi = ReviewSkill(
        id = "fenbi",
        name = "粉笔风格",
        tagline = "读题—列式—估算三段式",
        available = false
    )

    /** 顺序即界面上的展示顺序。 */
    val all = listOf(Builtin, HuaTu, FenBi)

    fun byId(id: String?): ReviewSkill = all.firstOrNull { it.id == id } ?: Builtin
}
