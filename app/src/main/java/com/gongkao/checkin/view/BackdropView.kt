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
 * 玻璃主题铺在页面最底层的动态背景：几团光**像呼吸一样**慢慢胀缩，位置只轻微游走。
 *
 * **色都压得很淡**（团心 alpha 只有 8%~14%），底色本身就接近 `@color/bg`。
 * 卡片是不透明白的，所以这层只在卡片缝隙、页面边距、顶栏底栏那些地方透出一点色晕 ——
 * 要的是「白面板浮在一层会呼吸的淡色雾上」，不是一块花背景。改深任何一处都会立刻显脏。
 *
 * 两套主题是同一种「团」，靠**色温**、**渐变的软硬**和**呼吸的深浅**分开：
 * - [useBlurPreset]（高斯模糊）：偏冷，团巨大、渐变一路化到透明、**没有边缘**，呼吸浅而慢。
 * - [useLiquidPreset]（液态玻璃）：偏暖，团更聚拢、渐变中段是平的再快速收尾所以**隐约看得见边**，呼吸深而快。
 *
 * ## 性能（这台机器 120Hz，帧预算 8.3ms）
 * 瓶颈是**过度绘制**：几个大半径径向渐变叠起来相当于四五层全屏填充。三个措施：
 * 1. **view 只按 1/[DOWNSCALE] 尺寸布局，再用 scaleX/scaleY 放大**（见 `Themes.installBackdrop`）。
 *    配上 `LAYER_TYPE_HARDWARE`，GPU 只按小尺寸渲染成纹理、再一次性拉伸合成，填充率降到 1/9。
 *    团本来就是模糊的，降分辨率看不出来，双线性放大反而更柔和。
 * 2. **重绘必须用 [postInvalidateOnAnimation] 跟着 vsync 走，不要自己 `postInvalidateDelayed`
 *    限帧。** 试过限到 33ms，结果 120Hz 下 33 ÷ 8.33 = 3.96 不是整数，每帧落在 4~5 个 vsync
 *    之间来回跳，**呼吸这种慢动效看起来就是一顿一顿的**。省下的那点开销不值这个代价 ——
 *    实测滚动时带背景 5ms / 0.79% 掉帧，和白色主题（5ms / 0.22%）几乎一样，本来就不紧张。
 * 3. **别画到离屏 Bitmap 再贴回**。试过，反而更糟：`Canvas(bitmap)` 走**软件渲染**，
 *    径向渐变在 CPU 上比 GPU 慢得多，位图每帧被改写后还要重新上传纹理。
 *    实测掉帧 0%→99%、帧时 10→16ms。降分辨率要用上面那种 GPU 办法，不是软件位图。
 */
class BackdropView(ctx: Context) : View(ctx) {

    /** 一团光。位置/半径都用屏幕比例表示，换分辨率不用改数。 */
    private class Orb(
        val color: Int,
        /** 基准半径 ÷ 缓冲宽 */
        val radius: Float,
        val cx: Float,
        val cy: Float,
        /** 呼吸幅度：半径在 (1±amp) 之间胀缩 */
        val amp: Float,
        /** 呼吸周期，毫秒 */
        val period: Long,
        /** 位置游走幅度（很小，保持「一团在那儿」而不是到处飞） */
        val wander: Float,
        val pWander: Long,
        val phase: Float
    ) {
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        val matrix = Matrix()
        var shader: RadialGradient? = null
    }

    private val orbs = ArrayList<Orb>(5)

    /** 渐变色标。模糊是一路化开，液态中段平、末段陡，所以能看出边。 */
    private var stops = floatArrayOf(0f, 1f)
    private var edged = false

    private val basePaint = Paint()
    private var baseFrom = 0
    private var baseTo = 0

    private var running = false
    private val startAt = System.nanoTime()

    // ------------------------------------------------------------ 预设

    /**
     * 高斯模糊：冷色（蓝 / 紫），团大、无边、呼吸浅而慢。
     *
     * 团大所以中心能叠到三四层，单团 alpha 压到 8%~10%，叠完也就三成，是一层淡雾。
     */
    fun useBlurPreset() {
        baseFrom = 0xFFF1F4FC.toInt()
        baseTo = 0xFFF4F2FC.toInt()
        edged = false
        stops = floatArrayOf(0f, 1f)
        orbs.clear()
        orbs += Orb(0x187B93FF, 0.78f, 0.22f, 0.20f, 0.13f, 7200, 0.035f, 23000, 0f)
        orbs += Orb(0x16A48CFF, 0.72f, 0.80f, 0.34f, 0.11f, 8600, 0.030f, 27000, 1.1f)
        orbs += Orb(0x186FA8FF, 0.70f, 0.26f, 0.74f, 0.14f, 6400, 0.032f, 25000, 2.3f)
        orbs += Orb(0x15C0A6FF, 0.66f, 0.78f, 0.88f, 0.12f, 9400, 0.028f, 29000, 3.4f)
        orbs += Orb(0x1A8FB4FF, 0.60f, 0.50f, 0.52f, 0.15f, 5600, 0.040f, 21000, 4.6f)
        rebuild()
    }

    /**
     * 液态玻璃：暖色为主 + 一团青，团聚拢、**隐约有边**，呼吸深而快。
     *
     * 团比模糊那套小，叠得少，所以单团 alpha 可以略高（11%~13%）才看得见。
     */
    fun useLiquidPreset() {
        baseFrom = 0xFFF6F8FD.toInt()
        baseTo = 0xFFFDF5F7.toInt()
        edged = true
        // 中段几乎不衰减，0.82 之后才快速收到透明 —— 这是「看得见边」的关键
        stops = floatArrayOf(0f, 0.62f, 0.82f, 1f)
        orbs.clear()
        orbs += Orb(0x22FF7FB0, 0.40f, 0.24f, 0.20f, 0.22f, 4200, 0.045f, 15000, 0.4f)
        orbs += Orb(0x20FFA96B, 0.36f, 0.79f, 0.33f, 0.20f, 5000, 0.040f, 17500, 1.7f)
        orbs += Orb(0x1EE07BFF, 0.34f, 0.22f, 0.72f, 0.24f, 3800, 0.048f, 13500, 2.9f)
        orbs += Orb(0x22FFD05B, 0.31f, 0.78f, 0.86f, 0.21f, 4600, 0.042f, 16500, 4.1f)
        orbs += Orb(0x1C4FD8C4, 0.28f, 0.52f, 0.50f, 0.25f, 3400, 0.052f, 12000, 5.2f)
        rebuild()
    }

    // ------------------------------------------------------------ 缓冲与 shader

    override fun onSizeChanged(w: Int, h: Int, ow: Int, oh: Int) {
        super.onSizeChanged(w, h, ow, oh)
        rebuild()
    }

    private fun rebuild() {
        if (width <= 0 || height <= 0) return
        basePaint.shader = LinearGradient(
            0f, 0f, width.toFloat(), height.toFloat(), baseFrom, baseTo, Shader.TileMode.CLAMP
        )
        for (o in orbs) {
            // shader 建成「圆心在原点、半径 1」，每帧靠 matrix 缩放 + 平移。
            // 半径每帧都在呼吸，如果按实际半径新建 shader 就会每帧分配内存。
            val cols = if (edged) {
                intArrayOf(o.color, o.color, o.color and 0x00FFFFFF, o.color and 0x00FFFFFF)
            } else {
                intArrayOf(o.color, o.color and 0x00FFFFFF)
            }
            o.shader = RadialGradient(0f, 0f, 1f, cols, stops, Shader.TileMode.CLAMP)
            o.paint.shader = o.shader
        }
    }

    // ------------------------------------------------------------ 绘制

    override fun onDraw(canvas: Canvas) {
        val w = width.toFloat()
        val h = height.toFloat()
        if (w <= 0f || h <= 0f) return
        val t = (System.nanoTime() - startAt) / 1_000_000L

        canvas.drawRect(0f, 0f, w, h, basePaint)
        for (o in orbs) {
            val breath = sin(2.0 * Math.PI * t / o.period + o.phase).toFloat()
            val r = o.radius * w * (1f + o.amp * breath)
            // 游走用另一个周期，跟呼吸错开，看着才不像单纯的缩放
            val wa = 2.0 * Math.PI * t / o.pWander + o.phase
            val x = w * (o.cx + o.wander * sin(wa).toFloat())
            val y = h * (o.cy + o.wander * sin(wa * 1.31 + 1.7).toFloat())

            o.matrix.setScale(r, r)
            o.matrix.postTranslate(x, y)
            o.shader?.setLocalMatrix(o.matrix)
            canvas.drawCircle(x, y, r, o.paint)
        }

        // 跟着 vsync 走，动起来才是匀的
        if (running) postInvalidateOnAnimation()
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
        setBackgroundColor(Color.TRANSPARENT)
        // 配合 1/DOWNSCALE 布局尺寸 + scaleX/Y 放大：硬件层让 GPU 只渲染小尺寸纹理。
        // 注意这跟「全尺寸 view 加硬件层」不是一回事，后者只是白交一次合成。
        setLayerType(LAYER_TYPE_HARDWARE, null)
    }

    companion object {
        /** 实际绘制尺寸是屏幕的 1/3，靠 scaleX/Y 放大回去，填充率降到 1/9。 */
        const val DOWNSCALE = 3
    }
}
