package com.gongkao.checkin.view

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.RadialGradient
import android.graphics.Shader
import android.view.View
import kotlin.math.sin

/**
 * 会缓慢流动的彩色光团背景，玻璃主题（高斯模糊 / 液态玻璃）铺在页面最底层。
 *
 * 每团光是一个「中心色 → 透明」的径向渐变。位置用**两组不同周期的正弦叠加**算出来，
 * 周期比取在无理数附近，所以合成轨迹很长时间不重复，看着是混沌漂移而不是来回摆。
 *
 * 渐变 shader 只在尺寸变化时建一次，每帧只改 local matrix 平移 —— 每帧新建 shader
 * 会持续分配内存、拖出掉帧。
 */
class BackdropView(ctx: Context) : View(ctx) {

    /** 一团光。位置和漂移幅度都用屏幕比例表示，换分辨率不用改数。 */
    private class Blob(
        val color: Int,
        /** 半径 ÷ 屏宽 */
        val radius: Float,
        val cx: Float,
        val cy: Float,
        /** 漂移幅度（占屏宽/屏高的比例） */
        val ax: Float,
        val ay: Float,
        /** 主/次周期，毫秒。两者不成整数比才不显出循环。 */
        val p1: Long,
        val p2: Long,
        val phase: Float
    ) {
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        val matrix = Matrix()
        var shader: RadialGradient? = null
    }

    private val blobs = ArrayList<Blob>(5)
    private var basePaint = Paint()
    private var baseFrom = 0
    private var baseTo = 0
    private var running = false
    private val startAt = System.nanoTime()

    /**
     * 高斯模糊：**只用冷色**（蓝 / 紫 / 青），光团巨大且互相重叠，铺满整屏后
     * 只剩缓慢流动的色雾，看不出单个团的边界。
     *
     * 三条经验都是返工换来的：
     * 1. **半径要大到互相重叠**（0.8 以上）。半径小的话只有某个角落有颜色、
     *    其余大片是平的底色，等于没效果。
     * 2. **中心 alpha 要给满**。卡片是半透明白的，压在浅底上几乎等于纯白，
     *    底色不够浓根本透不上来。
     * 3. **两套主题的色板必须不相交**。第一版两边都用同一组浅蓝紫 + 近白底色，
     *    差 11 个色阶，肉眼完全分不出，被当成「偷懒没做」。
     */
    fun useBlurPreset() {
        baseFrom = 0xFFE4E9F8.toInt()
        baseTo = 0xFFEAE6FA.toInt()
        blobs.clear()
        blobs += Blob(0xFF7B93FF.toInt(), 0.95f, 0.20f, 0.18f, 0.26f, 0.18f, 15000, 9700, 0f)
        blobs += Blob(0xFFA48CFF.toInt(), 0.88f, 0.84f, 0.32f, 0.24f, 0.20f, 18500, 12300, 1.1f)
        blobs += Blob(0xFF6FA8FF.toInt(), 0.84f, 0.24f, 0.76f, 0.25f, 0.16f, 16800, 11100, 2.3f)
        blobs += Blob(0xFFC0A6FF.toInt(), 0.80f, 0.78f, 0.90f, 0.22f, 0.19f, 21000, 13900, 3.4f)
        blobs += Blob(0xFF8FB4FF.toInt(), 0.72f, 0.50f, 0.50f, 0.30f, 0.24f, 24000, 15700, 4.6f)
        rebuildShaders()
    }

    /**
     * 液态玻璃：**暖色主导**（粉 / 珊瑚 / 琥珀 / 品红），只留一团青做冷暖对比。
     * 光团更小更艳、动得更快，能看出一团团的边界在互相挤压，像水彩在流。
     *
     * 跟高斯模糊**色系完全错开**是刻意的：两边都用「蓝紫在上、青绿在下」的时候，
     * 就算数值上有差异，一眼看过去还是同一张图，用户直接说「一模一样」。
     */
    fun useLiquidPreset() {
        baseFrom = 0xFFFFE9F0.toInt()
        baseTo = 0xFFFFF0E2.toInt()
        blobs.clear()
        blobs += Blob(0xFFFF6FA8.toInt(), 0.58f, 0.18f, 0.16f, 0.30f, 0.20f, 11000, 7300, 0.4f)
        blobs += Blob(0xFFFF9A5B.toInt(), 0.54f, 0.84f, 0.30f, 0.27f, 0.22f, 13500, 8900, 1.7f)
        blobs += Blob(0xFFE86BFF.toInt(), 0.52f, 0.22f, 0.78f, 0.28f, 0.18f, 12200, 8100, 2.9f)
        blobs += Blob(0xFFFFC93C.toInt(), 0.48f, 0.80f, 0.92f, 0.26f, 0.21f, 15500, 10300, 4.1f)
        blobs += Blob(0xFF35D0BA.toInt(), 0.44f, 0.50f, 0.50f, 0.32f, 0.26f, 17500, 11700, 5.2f)
        rebuildShaders()
    }

    override fun onSizeChanged(w: Int, h: Int, ow: Int, oh: Int) {
        super.onSizeChanged(w, h, ow, oh)
        rebuildShaders()
    }

    private fun rebuildShaders() {
        val w = width
        val h = height
        if (w <= 0 || h <= 0) return
        basePaint.shader = LinearGradient(
            0f, 0f, w.toFloat(), h.toFloat(), baseFrom, baseTo, Shader.TileMode.CLAMP
        )
        for (b in blobs) {
            val r = (b.radius * w).coerceAtLeast(1f)
            // shader 建在原点，每帧靠 matrix 平移到当前位置
            b.shader = RadialGradient(
                0f, 0f, r,
                intArrayOf(b.color, b.color and 0x00FFFFFF),
                floatArrayOf(0f, 1f),
                Shader.TileMode.CLAMP
            )
            b.paint.shader = b.shader
        }
    }

    override fun onDraw(canvas: Canvas) {
        val w = width.toFloat()
        val h = height.toFloat()
        if (w <= 0f || h <= 0f) return
        canvas.drawRect(0f, 0f, w, h, basePaint)

        val t = (System.nanoTime() - startAt) / 1_000_000L
        for (b in blobs) {
            val a1 = 2.0 * Math.PI * t / b.p1 + b.phase
            val a2 = 2.0 * Math.PI * t / b.p2 + b.phase * 1.7
            // 两个正弦叠加：主项给大范围漂移，次项让轨迹不闭合
            val x = w * (b.cx + b.ax * (0.68f * sin(a1) + 0.32f * sin(a2 * 1.31)).toFloat())
            val y = h * (b.cy + b.ay * (0.68f * sin(a2) + 0.32f * sin(a1 * 1.27)).toFloat())
            val r = b.radius * w
            b.matrix.setTranslate(x, y)
            b.shader?.setLocalMatrix(b.matrix)
            canvas.drawCircle(x, y, r, b.paint)
        }
        if (running) postInvalidateOnAnimation()
    }

    // 不可见时停掉重绘循环，否则切到后台还在按帧画
    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        setRunning(windowVisibility == VISIBLE)
    }

    override fun onDetachedFromWindow() {
        running = false
        super.onDetachedFromWindow()
    }

    override fun onWindowVisibilityChanged(visibility: Int) {
        super.onWindowVisibilityChanged(visibility)
        setRunning(visibility == VISIBLE)
    }

    private fun setRunning(on: Boolean) {
        if (running == on) return
        running = on
        if (on) postInvalidateOnAnimation()
    }

    init {
        isClickable = false
        isFocusable = false
        setLayerType(LAYER_TYPE_HARDWARE, null)
        setBackgroundColor(Color.TRANSPARENT)
    }
}
