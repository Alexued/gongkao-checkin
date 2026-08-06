package com.gongkao.checkin.data

/**
 * 百化分条目。[display] 是题面给出的百分数，[num]/[den] 是标准答案，
 * [alts] 是同样判对的其它常用写法（等值分数自动判对，这里只放不等值但常用的近似）。
 */
data class PercentEntry(
    val display: String,
    val num: Int,
    val den: Int,
    val alts: List<Pair<Int, Int>> = emptyList()
) {
    val exact: Double get() = num * 100.0 / den
    val fraction: String get() = "$num/$den"
    /** 12.50% 形式的精确值，用于对照 */
    val exactText: String get() = String.format("%.2f%%", exact)
    val displayValue: Double get() = display.removeSuffix("%").toDouble()
}

object PercentData {

    /** 顺序与用户给定的一致，完整背诵按此顺序。 */
    val entries: List<PercentEntry> = listOf(
        PercentEntry("50%", 1, 2),
        PercentEntry("33.3%", 1, 3),
        PercentEntry("25%", 1, 4),
        PercentEntry("20%", 1, 5),
        PercentEntry("19%", 3, 16, listOf(4 to 21)),
        PercentEntry("18%", 2, 11, listOf(9 to 50)),
        PercentEntry("17%", 1, 6, listOf(3 to 17)),
        PercentEntry("26.7%", 4, 15),
        PercentEntry("15%", 2, 13, listOf(3 to 20)),
        PercentEntry("14.3%", 1, 7),
        PercentEntry("13%", 2, 15, listOf(13 to 100)),
        PercentEntry("12.5%", 1, 8),
        PercentEntry("11.1%", 1, 9),
        PercentEntry("10.5%", 2, 19),
        PercentEntry("10%", 1, 10),
        PercentEntry("9.5%", 2, 21),
        PercentEntry("9.1%", 1, 11),
        PercentEntry("8.3%", 1, 12),
        PercentEntry("7.7%", 1, 13),
        PercentEntry("7.1%", 1, 14),
        PercentEntry("6.7%", 1, 15),
        PercentEntry("5%", 1, 20),
        PercentEntry("3.3%", 1, 30),
        PercentEntry("2.5%", 1, 40)
    )

    fun byDisplay(d: String): PercentEntry? = entries.firstOrNull { it.display == d }

    /** 判分：等值分数一律算对（3/6 == 1/2），另外接受 alts 里登记的近似写法。 */
    fun check(e: PercentEntry, num: Int, den: Int): Boolean {
        if (den == 0) return false
        if (num.toLong() * e.den == e.num.toLong() * den) return true
        return e.alts.any { (n, d) -> num.toLong() * d == n.toLong() * den }
    }

    fun reduce(num: Int, den: Int): Pair<Int, Int> {
        if (den == 0) return num to den
        val g = gcd(kotlin.math.abs(num), kotlin.math.abs(den)).coerceAtLeast(1)
        return (num / g) to (den / g)
    }

    private fun gcd(a: Int, b: Int): Int = if (b == 0) a else gcd(b, a % b)
}
