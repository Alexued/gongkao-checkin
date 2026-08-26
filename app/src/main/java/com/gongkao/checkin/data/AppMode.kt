package com.gongkao.checkin.data

/**
 * 使用模式。只影响界面措辞、可见的训练记录、以及是否套用总结束日；
 * 任务、打卡、计时、训练记录一条都不动，切回来原样恢复。
 */
enum class AppMode(val id: String) {
    /** 考公模式：保留背诵训练与计划截止日 */
    EXAM("exam"),

    /** 通用模式：当习惯打卡用，隐藏背诵训练、不套用截止日 */
    GENERAL("general");

    val isGeneral: Boolean get() = this == GENERAL

    companion object {
        /** 认不出来的值一律当考公模式，保证老数据行为不变。 */
        fun of(id: String?): AppMode = entries.firstOrNull { it.id == id } ?: EXAM
    }
}
