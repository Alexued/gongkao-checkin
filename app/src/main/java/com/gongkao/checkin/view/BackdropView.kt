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
     * 高斯模糊：**大而糊**的光团，半径大、重叠多、动得慢，像光透过磨砂玻璃散开。
     *
     * alpha 给得比直觉高：卡片是半透明白的，压在浅底上几乎等于纯白，
     * 底色不够浓的话透不上来，两个玻璃主题看着会一模一样（第一版就是这样）。
     */
    fun useBlurPreset() {
        baseFrom = 0xFFEDF1FF.toInt()
        baseTo = 0xFFE9F4F7.toInt()
        blobs.clear()
        blobs += Blob(0xB36C8CFF.toInt(), 0.66f, 0.18f, 0.16f, 0.16f, 0.10f, 21000, 13400, 0f)
        blobs += Blob(0xA6B37BFF.toInt(), 0.62f, 0.82f, 0.34f, 0.13f, 0.12f, 26000, 17300, 1.1f)
        blobs += Blob(0x9E35D0BA.toInt(), 0.58f, 0.26f, 0.78f, 0.15f, 0.09f, 23500, 15100, 2.3f)
        blobs += Blob(0x8CFF8FA8.toInt(), 0.54f, 0.72f, 0.92f, 0.12f, 0.11f, 29000, 19700, 3.4f)
        blobs += Blob(0x80FFC46B.toInt(), 0.46f, 0.50f, 0.52f, 0.18f, 0.14f, 33000, 21300, 4.6f)
        rebuildShaders()
    }

    /** 液态玻璃：**小而艳**的光团，半径小、色更浓、动得快，边界看得出来，偏「湿」。 */
    fun useLiquidPreset() {
        baseFrom = 0xFFE2EAFF.toInt()
        baseTo = 0xFFDFF5F0.toInt()
        blobs.clear()
        blobs += Blob(0xE05B7CFF.toInt(), 0.44f, 0.16f, 0.14f, 0.19f, 0.13f, 15000, 9800, 0.4f)
        blobs += Blob(0xD9C46BFF.toInt(), 0.40f, 0.86f, 0.30f, 0.16f, 0.15f, 18500, 12100, 1.7f)
        blobs += Blob(0xD916CFB4.toInt(), 0.38f, 0.24f, 0.80f, 0.18f, 0.12f, 16800, 11200, 2.9f)
        blobs += Blob(0xCCFF6B8B.toInt(), 0.34f, 0.78f, 0.94f, 0.15f, 0.14f, 21500, 14300, 4.1f)
        blobs += Blob(0xB3FFB24D.toInt(), 0.30f, 0.52f, 0.50f, 0.21f, 0.17f, 24500, 16100, 5.2f)
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
