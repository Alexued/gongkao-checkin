package com.gongkao.checkin.view

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Shader
import android.util.AttributeSet
import android.view.View
import com.gongkao.checkin.R
import com.gongkao.checkin.data.PetData
import com.gongkao.checkin.data.PetShop
import kotlin.math.sin

/**
 * 宠物显示视图：绘制仓鼠精灵 + 装备 + 背景
 */
class PetView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyle: Int = 0
) : View(context, attrs, defStyle) {

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
    }

    private var spriteBitmap: Bitmap? = null
    private var petData: PetData = PetData()
    private var currentAnimation = "idle"
    private var animationFrame = 0L
    private var isAnimating = false

    // 精灵图帧映射（3x3 网格，9 个帧）
    private val frameMap = mapOf(
        "idle" to 0,      // 第 1 帧：站立
        "walk" to 1,      // 第 2 帧：走路
        "run" to 2,       // 第 3 帧：跑步
        "eating" to 3,    // 第 4 帧：吃东西
        "happy" to 4,     // 第 5 帧：开心
        "celebrate" to 5, // 第 6 帧：庆祝
        "wave" to 6,      // 第 7 帧：挥手
        "think" to 7,     // 第 8 帧：思考
        "side" to 8       // 第 9 帧：侧面
    )

    init {
        // 加载精灵图
        spriteBitmap = BitmapFactory.decodeResource(resources, R.drawable.hamster_sprite)
        startAnimation()
    }

    fun setPetData(data: PetData) {
        petData = data
        invalidate()
    }

    fun playAnimation(name: String) {
        currentAnimation = name
        invalidate()
    }

    private fun startAnimation() {
        isAnimating = true
        postDelayed(object : Runnable {
            override fun run() {
                if (isAnimating) {
                    animationFrame++
                    invalidate()
                    postDelayed(this, 83) // ~12 FPS
                }
            }
        }, 83)
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        isAnimating = false
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        val w = width.toFloat()
        val h = height.toFloat()

        // 绘制背景
        drawBackground(canvas, w, h)

        // 绘制宠物本体
        drawPet(canvas, w, h)

        // 绘制装备
        drawEquipment(canvas, w, h)

        // 绘制宠物名字
        drawName(canvas, w, h)

        // 绘制星星数
        drawStars(canvas, w, h)
    }

    private fun drawBackground(canvas: Canvas, w: Float, h: Float) {
        val bg = petData.equipped["background"] ?: ""

        val gradient = when (bg) {
            "grass" -> LinearGradient(0f, 0f, 0f, h,
                intArrayOf(0xFF87CEEB.toInt(), 0xFF90EE90.toInt()),
                null, Shader.TileMode.CLAMP)
            "beach" -> LinearGradient(0f, 0f, 0f, h,
                intArrayOf(0xFF87CEEB.toInt(), 0xFFF4A460.toInt()),
                null, Shader.TileMode.CLAMP)
            "space" -> LinearGradient(0f, 0f, 0f, h,
                intArrayOf(0xFF000033.toInt(), 0xFF191970.toInt()),
                null, Shader.TileMode.CLAMP)
            else -> LinearGradient(0f, 0f, 0f, h,
                intArrayOf(0xFFF4F6FB.toInt(), 0xFFEEF1F8.toInt()),
                null, Shader.TileMode.CLAMP)
        }

        paint.shader = gradient
        canvas.drawRect(0f, 0f, w, h, paint)
        paint.shader = null
    }

    private fun drawPet(canvas: Canvas, w: Float, h: Float) {
        val sprite = spriteBitmap ?: return

        val centerX = w / 2
        val centerY = h / 2 + 20

        // 轻微的上下浮动
        val offset = sin(animationFrame * 0.2) * 5

        // 获取当前动画帧
        val frameIndex = frameMap[currentAnimation] ?: 0

        // 3x3 网格，每个帧的尺寸
        val frameWidth = sprite.width / 3
        val frameHeight = sprite.height / 3

        val col = frameIndex % 3
        val row = frameIndex / 3

        val srcRect = android.graphics.Rect(
            col * frameWidth,
            row * frameHeight,
            (col + 1) * frameWidth,
            (row + 1) * frameHeight
        )

        // 绘制仓鼠，尺寸约 120dp
        val petSize = 120f * resources.displayMetrics.density
        val dstRect = RectF(
            centerX - petSize / 2,
            centerY - petSize / 2 + offset.toFloat(),
            centerX + petSize / 2,
            centerY + petSize / 2 + offset.toFloat()
        )

        canvas.drawBitmap(sprite, srcRect, dstRect, paint)
    }

    private fun drawEquipment(canvas: Canvas, w: Float, h: Float) {
        val centerX = w / 2
        val centerY = h / 2 + 20

        textPaint.textSize = 32f * resources.displayMetrics.density

        // 绘制服装（帽子在上方）
        petData.equipped["outfit"]?.let { itemId ->
            PetShop.findItem("outfits", itemId)?.let { item ->
                canvas.drawText(item.icon, centerX, centerY - 80f * resources.displayMetrics.density, textPaint)
            }
        }

        // 绘制配饰（右侧）
        petData.equipped["accessory"]?.let { itemId ->
            PetShop.findItem("accessories", itemId)?.let { item ->
                canvas.drawText(item.icon, centerX + 60f * resources.displayMetrics.density, centerY, textPaint)
            }
        }

        // 绘制交通工具（下方）
        petData.equipped["vehicle"]?.let { itemId ->
            PetShop.findItem("vehicles", itemId)?.let { item ->
                canvas.drawText(item.icon, centerX, centerY + 80f * resources.displayMetrics.density, textPaint)
            }
        }
    }

    private fun drawName(canvas: Canvas, w: Float, h: Float) {
        textPaint.color = Color.parseColor("#0E1526")
        textPaint.textSize = 16f * resources.displayMetrics.density
        val name = petData.name.ifBlank { "未命名" }
        canvas.drawText(name, w / 2, h - 20f * resources.displayMetrics.density, textPaint)
    }

    private fun drawStars(canvas: Canvas, w: Float, h: Float) {
        textPaint.color = Color.parseColor("#FFD700")
        textPaint.textSize = 18f * resources.displayMetrics.density
        textPaint.textAlign = Paint.Align.LEFT
        val text = "⭐ ${petData.stars}"
        canvas.drawText(text, 16f * resources.displayMetrics.density, 30f * resources.displayMetrics.density, textPaint)
        textPaint.textAlign = Paint.Align.CENTER
    }
}
