package com.gongkao.checkin.view

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RadialGradient
import android.graphics.Shader
import android.view.View
import kotlin.math.sin

/**
 * 玻璃主题铺在页面最底层的动态背景。**两套主题用的是两种不同画法**，不是换个配色：
 *
 * - [useBlurPreset]（高斯模糊）：巨大的径向渐变光团互相重叠，**没有边界**，
 *   看到的是一片缓慢流动的色雾 —— 这才像光透过磨砂玻璃。
 * - [useLiquidPreset]（液态玻璃）：横向的**液带**，上下边缘各按不同的正弦波起伏
 *   （所以带子的厚度一直在变），填虹彩渐变，上缘描一道白色镜面高光 ——
 *   有边界、有高光、有厚薄，才像流动的玻璃/液体。
 *
 * 之前两套都用光团、只改半径和颜色，用户一眼就看出「是同一个东西」。
 *
 * 动画都靠 [postInvalidateOnAnimation] 自驱，窗口不可见时停掉。
 */
class BackdropView(ctx: Context) : View(ctx) {

    private enum class Mode { BLOBS, RIBBONS }

    private var mode = Mode.BLOBS
    private var basePaint = Paint()
    private var baseFrom = 0
    private var baseTo = 0
    private var running = false
    private val startAt = System.nanoTime()

    // ------------------------------------------------------------ 高斯模糊：光团

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

    // ------------------------------------------------------------ 液态玻璃：液带

    /**
     * 一条液带。上下缘用**两条独立的正弦波**，所以厚度沿 x 变、也随时间变；
     * 整条带子还会慢慢上下漂。
     */
    private class Ribbon(
        /** 带子中心线的位置 ÷ 屏高 */
        val cy: Float,
        /** 半厚 ÷ 屏高 */
        val half: Float,
        /** 上/下缘的起伏幅度 ÷ 屏高 */
        val ampTop: Float,
        val ampBot: Float,
        /** 上/下缘的空间波数（一屏宽里几个波） */
        val waveTop: Float,
        val waveBot: Float,
        /** 上/下缘的时间周期，毫秒 */
        val pTop: Long,
        val pBot: Long,
        /** 整条带子上下漂的幅度与周期 */
        val drift: Float,
        val pDrift: Long,
        val phase: Float,
        /** 虹彩渐变的三个色标 */
        val c0: Int, val c1: Int, val c2: Int
    ) {
        val fill = Paint(Paint.ANTI_ALIAS_FLAG)
        val path = Path()
        val edge = Path()
    }

    private val ribbons = ArrayList<Ribbon>(5)

    /** 上缘镜面高光。所有带子共用一支笔。 */
    private val specular = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
    }

    /** 曲线采样段数。1080px 宽下每段约 27px，足够顺滑，也不至于每帧画太多线段。 */
    private val steps = 40

    // ------------------------------------------------------------ 预设

    /**
     * 高斯模糊：只用冷色（蓝 / 紫），光团巨大且互相重叠，铺满整屏后只剩流动的色雾。
     *
     * 半径必须大到互相重叠（0.7 以上），中心 alpha 要给满 —— 卡片是半透明白的，
     * 底色不够浓根本透不上来。
     */
    fun useBlurPreset() {
        mode = Mode.BLOBS
        baseFrom = 0xFFE4E9F8.toInt()
        baseTo = 0xFFEAE6FA.toInt()
        blobs.clear()
        blobs += Blob(0xFF7B93FF.toInt(), 0.95f, 0.20f, 0.18f, 0.26f, 0.18f, 15000, 9700, 0f)
        blobs += Blob(0xFFA48CFF.toInt(), 0.88f, 0.84f, 0.32f, 0.24f, 0.20f, 18500, 12300, 1.1f)
        blobs += Blob(0xFF6FA8FF.toInt(), 0.84f, 0.24f, 0.76f, 0.25f, 0.16f, 16800, 11100, 2.3f)
        blobs += Blob(0xFFC0A6FF.toInt(), 0.80f, 0.78f, 0.90f, 0.22f, 0.19f, 21000, 13900, 3.4f)
        blobs += Blob(0xFF8FB4FF.toInt(), 0.72f, 0.50f, 0.50f, 0.30f, 0.24f, 24000, 15700, 4.6f)
        ribbons.clear()
        rebuild()
    }

    /**
     * 液态玻璃：五条虹彩液带横贯屏幕，边缘起伏、厚度变化、上缘一道白高光。
     * 带子之间半透明叠加，交叠处颜色会混。
     */
    fun useLiquidPreset() {
        mode = Mode.RIBBONS
        baseFrom = 0xFFF4F8FF.toInt()
        baseTo = 0xFFFFF2F7.toInt()
        ribbons.clear()
        ribbons += Ribbon(
            cy = 0.10f, half = 0.085f, ampTop = 0.030f, ampBot = 0.042f,
            waveTop = 1.6f, waveBot = 2.3f, pTop = 9000, pBot = 12500,
            drift = 0.030f, pDrift = 17000, phase = 0.0f,
            c0 = 0xFF7FE9E0.toInt(), c1 = 0xFF86C8FF.toInt(), c2 = 0xFFB49CFF.toInt()
        )
        ribbons += Ribbon(
            cy = 0.30f, half = 0.075f, ampTop = 0.038f, ampBot = 0.028f,
            waveTop = 2.1f, waveBot = 1.4f, pTop = 11000, pBot = 8200,
            drift = 0.034f, pDrift = 21000, phase = 1.2f,
            c0 = 0xFFB49CFF.toInt(), c1 = 0xFFFF9ECF.toInt(), c2 = 0xFFFFAE8A.toInt()
        )
        ribbons += Ribbon(
            cy = 0.50f, half = 0.090f, ampTop = 0.032f, ampBot = 0.046f,
            waveTop = 1.3f, waveBot = 2.7f, pTop = 13500, pBot = 10200,
            drift = 0.028f, pDrift = 18500, phase = 2.5f,
            c0 = 0xFFFFD98A.toInt(), c1 = 0xFFFFA98A.toInt(), c2 = 0xFFFF93C4.toInt()
        )
        ribbons += Ribbon(
            cy = 0.71f, half = 0.078f, ampTop = 0.044f, ampBot = 0.030f,
            waveTop = 2.5f, waveBot = 1.7f, pTop = 10500, pBot = 14200,
            drift = 0.032f, pDrift = 23000, phase = 3.8f,
            c0 = 0xFF8FEFC8.toInt(), c1 = 0xFF79E4E4.toInt(), c2 = 0xFF93CBFF.toInt()
        )
        ribbons += Ribbon(
            cy = 0.91f, half = 0.088f, ampTop = 0.036f, ampBot = 0.040f,
            waveTop = 1.9f, waveBot = 2.9f, pTop = 12000, pBot = 9400,
            drift = 0.026f, pDrift = 19500, phase = 5.1f,
            c0 = 0xFFFFA0D0.toInt(), c1 = 0xFFC3A3FF.toInt(), c2 = 0xFF8ADFE8.toInt()
        )
        blobs.clear()
        rebuild()
    }

    // ------------------------------------------------------------ shader 只建一次

    override fun onSizeChanged(w: Int, h: Int, ow: Int, oh: Int) {
        super.onSizeChanged(w, h, ow, oh)
        rebuild()
    }

    private fun rebuild() {
        val w = width
        val h = height
        if (w <= 0 || h <= 0) return
        basePaint.shader = LinearGradient(
            0f, 0f, w.toFloat(), h.toFloat(), baseFrom, baseTo, Shader.TileMode.CLAMP
        )
        for (b in blobs) {
            val r = (b.radius * w).coerceAtLeast(1f)
            // shader 建在原点，每帧靠 matrix 平移到当前位置；每帧新建 shader 会持续分配内存
            b.shader = RadialGradient(
                0f, 0f, r,
                intArrayOf(b.color, b.color and 0x00FFFFFF),
                floatArrayOf(0f, 1f),
                Shader.TileMode.CLAMP
            )
            b.paint.shader = b.shader
        }
        for (r in ribbons) {
            // 横向渐变：带子只上下动，横向 shader 一直有效，不用每帧重建
            r.fill.shader = LinearGradient(
                0f, 0f, w.toFloat(), 0f,
                intArrayOf(r.c0, r.c1, r.c2),
                floatArrayOf(0f, 0.5f, 1f),
                Shader.TileMode.CLAMP
            )
            r.fill.alpha = 0xA8
        }
        specular.strokeWidth = (h * 0.0022f).coerceAtLeast(2f)
    }

    // ------------------------------------------------------------ 绘制

    override fun onDraw(canvas: Canvas) {
        val w = width.toFloat()
        val h = height.toFloat()
        if (w <= 0f || h <= 0f) return
        canvas.drawRect(0f, 0f, w, h, basePaint)

        val t = (System.nanoTime() - startAt) / 1_000_000L
        when (mode) {
            Mode.BLOBS -> drawBlobs(canvas, w, h, t)
            Mode.RIBBONS -> drawRibbons(canvas, w, h, t)
        }
        if (running) postInvalidateOnAnimation()
    }

    private fun drawBlobs(canvas: Canvas, w: Float, h: Float, t: Long) {
        for (b in blobs) {
            val a1 = 2.0 * Math.PI * t / b.p1 + b.phase
            val a2 = 2.0 * Math.PI * t / b.p2 + b.phase * 1.7
            // 两个正弦叠加：主项给大范围漂移，次项让轨迹不闭合
            val x = w * (b.cx + b.ax * (0.68f * sin(a1) + 0.32f * sin(a2 * 1.31)).toFloat())
            val y = h * (b.cy + b.ay * (0.68f * sin(a2) + 0.32f * sin(a1 * 1.27)).toFloat())
            b.matrix.setTranslate(x, y)
            b.shader?.setLocalMatrix(b.matrix)
            canvas.drawCircle(x, y, b.radius * w, b.paint)
        }
    }

    private fun drawRibbons(canvas: Canvas, w: Float, h: Float, t: Long) {
        for (r in ribbons) {
            val drift = r.drift * sin(2.0 * Math.PI * t / r.pDrift + r.phase).toFloat()
            val mid = h * (r.cy + drift)
            val halfPx = h * r.half
            val ampT = h * r.ampTop
            val ampB = h * r.ampBot
            val phT = 2.0 * Math.PI * t / r.pTop + r.phase
            val phB = 2.0 * Math.PI * t / r.pBot + r.phase * 1.6

            r.path.reset()
            r.edge.reset()
            // 上缘：左 → 右，同时记进 edge 供描高光
            for (i in 0..steps) {
                val fx = i.toFloat() / steps
                val x = fx * w
                val y = mid - halfPx + ampT * sin(r.waveTop * 2.0 * Math.PI * fx + phT).toFloat()
                if (i == 0) {
                    r.path.moveTo(x, y); r.edge.moveTo(x, y)
                } else {
                    r.path.lineTo(x, y); r.edge.lineTo(x, y)
                }
            }
            // 下缘：右 → 左，闭合成带状
            for (i in steps downTo 0) {
                val fx = i.toFloat() / steps
                val x = fx * w
                val y = mid + halfPx + ampB * sin(r.waveBot * 2.0 * Math.PI * fx + phB).toFloat()
                r.path.lineTo(x, y)
            }
            r.path.close()
            canvas.drawPath(r.path, r.fill)

            // 上缘高光：这道白线是「玻璃感」的关键，光团那套完全没有
            specular.color = Color.WHITE
            specular.alpha = 0xC0
            canvas.drawPath(r.edge, specular)
        }
    }

    // ------------------------------------------------------------ 生命周期

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
