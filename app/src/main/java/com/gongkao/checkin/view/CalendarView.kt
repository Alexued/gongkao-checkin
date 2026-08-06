package com.gongkao.checkin.view

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import androidx.core.graphics.ColorUtils
import com.gongkao.checkin.anim.Motion
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth

/**
 * 月历。既是打卡热力图（格子按完成率着色 + 显示日期数字），也是日期选择器。
 *
 * 相比旧的 [HeatmapView]（14 周方格墙、不显示日期），这里能看清具体哪一天，
 * 代价是一屏只有一个月，所以支持左右滑动换月。
 *
 * 行数固定按 6 行排版：真实月份占 5 或 6 行，固定成 6 行才不会换月时高度跳变。
 */
class CalendarView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null
) : View(context, attrs) {

    enum class Mode {
        /** 统计页：按完成率着色，未来日期不可点。 */
        HEATMAP,

        /** 选日期：不着色，未来日期可点。 */
        PICKER
    }

    // ---------------------------------------------------------------- 画笔

    private val cellPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
    }
    private val dayPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
    }
    private val headPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
    }
    private val titlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        typeface = android.graphics.Typeface.create("sans-serif-medium", android.graphics.Typeface.NORMAL)
    }
    private val rect = RectF()

    // ---------------------------------------------------------------- 配色

    var emptyColor = Color.parseColor("#EDF0F7")
    var accent = Color.parseColor("#6C8CFF")
    var inkColor = Color.parseColor("#0E1526")
    var dimColor = Color.parseColor("#8C97AF")

    // ---------------------------------------------------------------- 状态

    var mode = Mode.HEATMAP
    var onPick: ((String) -> Unit)? = null

    /** 换月时回调，宿主可以据此更新自己的标题。 */
    var onMonthChange: ((YearMonth) -> Unit)? = null

    private var month: YearMonth = YearMonth.now()
    private var ratios: Map<String, Float> = emptyMap()
    private var picked: String? = null

    /** 入场进度 0→1，驱动逐格放大。 */
    private var reveal = 1f

    /** 换月滑动进度：绝对值 0→1，符号表示方向。 */
    private var slide = 0f

    private val ROWS = 6
    private val COLS = 7

    private val weekLabels by lazy {
        arrayOf("一", "二", "三", "四", "五", "六", "日")
    }

    // ---------------------------------------------------------------- 尺寸

    private fun dp(v: Float) = v * resources.displayMetrics.density

    private val titleH get() = dp(38f)
    private val weekH get() = dp(26f)
    private val gap get() = dp(5f)

    private fun cellSize(): Float {
        val w = (width - paddingLeft - paddingRight).toFloat()
        return ((w - gap * (COLS - 1)) / COLS).coerceAtLeast(dp(20f))
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val w = MeasureSpec.getSize(widthMeasureSpec)
        val avail = (w - paddingLeft - paddingRight).toFloat()
        val size = ((avail - gap * (COLS - 1)) / COLS).coerceAtLeast(dp(20f))
        val h = titleH + weekH + size * ROWS + gap * (ROWS - 1) + paddingTop + paddingBottom
        setMeasuredDimension(w, h.toInt())
    }

    // ---------------------------------------------------------------- 数据

    /**
     * @param ratios 日期(yyyy-MM-dd) → 完成率 0..1
     * @param animated 是否播放逐格入场
     */
    fun setData(ratios: Map<String, Float>, animated: Boolean = true) {
        this.ratios = ratios
        if (animated) {
            reveal = 0f
            Motion.animate(720L, Motion.EMPHASIZED) { reveal = it; invalidate() }
        } else {
            reveal = 1f
        }
        invalidate()
    }

    fun select(date: String?) {
        picked = date
        // 选中的日期不在当月时跟着跳过去
        date?.let {
            runCatching { LocalDate.parse(it) }.getOrNull()?.let { d ->
                val ym = YearMonth.from(d)
                if (ym != month) {
                    month = ym
                    onMonthChange?.invoke(month)
                }
            }
        }
        invalidate()
    }

    fun showMonth(ym: YearMonth, animatedFrom: Int = 0) {
        month = ym
        onMonthChange?.invoke(month)
        if (animatedFrom != 0) {
            slide = animatedFrom.toFloat()
            Motion.animate(340L, Motion.EMPHASIZED) { f ->
                slide = animatedFrom * (1f - f)
                invalidate()
            }
        }
        invalidate()
    }

    fun currentMonth(): YearMonth = month

    // ---------------------------------------------------------------- 绘制

    /** 网格左上角对应的那个周一（可能落在上个月）。 */
    private fun gridStart(): LocalDate =
        month.atDay(1).with(DayOfWeek.MONDAY).let {
            // atDay(1).with(MONDAY) 可能落到当月之后，退一周
            if (it > month.atDay(1)) it.minusWeeks(1) else it
        }

    override fun onDraw(canvas: Canvas) {
        val size = cellSize()
        val left = paddingLeft.toFloat()
        val top = paddingTop.toFloat()

        // 月份标题
        titlePaint.color = inkColor
        titlePaint.textSize = dp(16f)
        canvas.drawText(
            context.getString(
                com.gongkao.checkin.R.string.calendar_month,
                month.year, month.monthValue
            ),
            left + dp(2f), top + dp(24f), titlePaint
        )

        // 星期表头
        headPaint.color = dimColor
        headPaint.textSize = dp(12f)
        for (c in 0 until COLS) {
            val cx = left + c * (size + gap) + size / 2f
            canvas.drawText(weekLabels[c], cx, top + titleH + dp(16f), headPaint)
        }

        // 整体随换月横向平移 + 淡出，做出翻月的方向感
        val shift = slide * width * 0.22f
        val bodyAlpha = 1f - kotlin.math.abs(slide) * 0.65f
        canvas.save()
        canvas.translate(shift, 0f)

        val today = LocalDate.now()
        val start = gridStart()
        val gridTop = top + titleH + weekH

        dayPaint.textSize = dp(13f)

        for (r in 0 until ROWS) {
            for (c in 0 until COLS) {
                val date = start.plusWeeks(r.toLong()).plusDays(c.toLong())
                // 只画当月，前后补白留空（比画灰字更干净，也避免误点）
                if (YearMonth.from(date) != month) continue

                val key = date.toString()
                // 只有热力图才把未来调暗（那时确实没数据）；
                // 选日期时未来是主要目标，调暗反而挡路
                val future = date.isAfter(today) && mode == Mode.HEATMAP
                val ratio = ratios[key] ?: 0f

                val x = left + c * (size + gap)
                val y = gridTop + r * (size + gap)

                // 逐格错峰：沿左上到右下的对角线推进
                val delay = (r + c) * 0.045f
                val local = ((reveal - delay) / 0.55f).coerceIn(0f, 1f)
                val eased = Motion.SOFT.getInterpolation(local)
                if (local <= 0f) continue

                rect.set(x, y, x + size, y + size)
                val inset = size * (1f - eased) / 2f
                rect.inset(inset, inset)

                val heat = if (mode == Mode.HEATMAP && ratio > 0f) {
                    ColorUtils.blendARGB(emptyColor, accent, 0.25f + 0.75f * ratio.coerceAtMost(1f))
                } else {
                    emptyColor
                }
                cellPaint.color = applyAlpha(heat, bodyAlpha * if (future) 0.45f else 1f)
                canvas.drawRoundRect(rect, size * 0.3f, size * 0.3f, cellPaint)

                // 底色深到一定程度就把数字反白，保证对比度
                val onDark = mode == Mode.HEATMAP && ratio >= 0.55f
                val numColor = when {
                    future -> dimColor
                    onDark -> Color.WHITE
                    else -> inkColor
                }
                dayPaint.color = applyAlpha(numColor, bodyAlpha * eased)
                val baseline = rect.centerY() - (dayPaint.descent() + dayPaint.ascent()) / 2f
                canvas.drawText(date.dayOfMonth.toString(), rect.centerX(), baseline, dayPaint)

                // 今天 / 选中：描边（选中更粗）
                val isPicked = key == picked
                val isToday = date == today
                if (isPicked || (picked == null && isToday)) {
                    strokePaint.color = applyAlpha(accent, bodyAlpha)
                    strokePaint.strokeWidth = if (isPicked) dp(2.2f) else dp(1.6f)
                    rect.set(x, y, x + size, y + size)
                    rect.inset(-dp(1.4f), -dp(1.4f))
                    canvas.drawRoundRect(rect, size * 0.34f, size * 0.34f, strokePaint)
                }
            }
        }
        canvas.restore()
    }

    private fun applyAlpha(color: Int, a: Float): Int =
        ColorUtils.setAlphaComponent(color, (Color.alpha(color) * a.coerceIn(0f, 1f)).toInt())

    // ---------------------------------------------------------------- 触摸

    private var downX = 0f
    private var downY = 0f
    private var swiped = false
    private val slop by lazy { ViewConfiguration.get(context).scaledTouchSlop }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                downX = event.x
                downY = event.y
                swiped = false
                // 横向滑动要自己处理，先请求父级别抢（外层是 ViewPager）
                parent?.requestDisallowInterceptTouchEvent(true)
                return true
            }

            MotionEvent.ACTION_MOVE -> {
                val dx = event.x - downX
                val dy = event.y - downY
                if (!swiped && kotlin.math.abs(dx) > slop && kotlin.math.abs(dx) > kotlin.math.abs(dy)) {
                    swiped = true
                }
                return true
            }

            MotionEvent.ACTION_UP -> {
                parent?.requestDisallowInterceptTouchEvent(false)
                val dx = event.x - downX
                if (swiped) {
                    if (dx < 0) showMonth(month.plusMonths(1), animatedFrom = 1)
                    else showMonth(month.minusMonths(1), animatedFrom = -1)
                    return true
                }
                pickAt(event.x, event.y)
                return true
            }

            MotionEvent.ACTION_CANCEL -> {
                parent?.requestDisallowInterceptTouchEvent(false)
                return true
            }
        }
        return super.onTouchEvent(event)
    }

    private fun pickAt(px: Float, py: Float) {
        val size = cellSize()
        val gridTop = paddingTop + titleH + weekH
        val c = ((px - paddingLeft) / (size + gap)).toInt()
        val r = ((py - gridTop) / (size + gap)).toInt()
        if (c < 0 || c >= COLS || r < 0 || r >= ROWS) return

        val date = gridStart().plusWeeks(r.toLong()).plusDays(c.toLong())
        if (YearMonth.from(date) != month) return
        // 热力图模式下未来没有数据可看，不响应
        if (mode == Mode.HEATMAP && date.isAfter(LocalDate.now())) return

        picked = date.toString()
        invalidate()
        Motion.tick(this)
        onPick?.invoke(date.toString())
    }
}
