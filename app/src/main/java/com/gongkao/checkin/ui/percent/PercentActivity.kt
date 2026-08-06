package com.gongkao.checkin.ui.percent

import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity
import androidx.dynamicanimation.animation.DynamicAnimation
import com.gongkao.checkin.ui.AppDialog
import com.gongkao.checkin.R
import com.gongkao.checkin.anim.Motion
import com.gongkao.checkin.data.DateUtil
import com.gongkao.checkin.data.PercentData
import com.gongkao.checkin.data.PercentEntry
import com.gongkao.checkin.data.PercentItem
import com.gongkao.checkin.data.PercentSession
import com.gongkao.checkin.data.Repo
import com.gongkao.checkin.ui.dp
import com.gongkao.checkin.ui.edgeToEdge
import com.gongkao.checkin.ui.padBottomInset
import com.gongkao.checkin.ui.padTopInset
import com.gongkao.checkin.ui.tap
import com.gongkao.checkin.view.CelebrationView

/**
 * 百化分背诵。给百分数写分数，自制小键盘输入，等值分数判对。
 * mode = FULL 按固定顺序过一遍，RANDOM 打乱抽取。
 */
class PercentActivity : AppCompatActivity() {

    private lateinit var modeText: TextView
    private lateinit var progressText: TextView
    private lateinit var elapsedText: TextView
    private lateinit var progressBar: View
    private lateinit var percentText: TextView
    private lateinit var numText: TextView
    private lateinit var denText: TextView
    private lateinit var fracBox: LinearLayout
    private lateinit var feedbackText: TextView
    private lateinit var keypad: LinearLayout
    private lateinit var btnConfirm: TextView
    private lateinit var root: FrameLayout
    private lateinit var celebration: CelebrationView

    private var mode = "FULL"
    private lateinit var queue: List<PercentEntry>
    private var cursor = 0

    /** 输入焦点：true 在分子，false 在分母。 */
    private var editingNum = true
    private var numBuf = ""
    private var denBuf = ""

    private var sessionStart = 0L
    private var itemStart = 0L
    private var answered = false
    private val items = mutableListOf<PercentItem>()

    private val ticker = object : Runnable {
        override fun run() {
            elapsedText.text = DateUtil.stopwatch(System.currentTimeMillis() - sessionStart)
            elapsedText.postDelayed(this, 100)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        edgeToEdge()
        Repo.init(this)
        mode = intent.getStringExtra("mode") ?: "FULL"

        // 用 FrameLayout 包一层，庆祝层盖在最上面
        root = FrameLayout(this)
        layoutInflater.inflate(R.layout.activity_percent, root, true)
        celebration = CelebrationView(this).apply {
            layoutParams = FrameLayout.LayoutParams(-1, -1)
            isClickable = false
        }
        root.addView(celebration)
        setContentView(root)

        bind()
        queue = if (mode == "RANDOM") PercentData.entries.shuffled() else PercentData.entries
        sessionStart = System.currentTimeMillis()
        modeText.text = getString(
            if (mode == "RANDOM") R.string.mode_random else R.string.mode_full
        )
        elapsedText.post(ticker)
        showItem()
        // targetSdk 36 走预测式返回，onBackPressed 不再回调，必须注册 callback
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() = askQuit()
        })
    }

    override fun onDestroy() {
        elapsedText.removeCallbacks(ticker)
        super.onDestroy()
    }

    private fun bind() {
        val bar = findViewById<View>(R.id.topBar)
        bar.padTopInset()
        modeText = findViewById(R.id.modeText)
        progressText = findViewById(R.id.progressText)
        elapsedText = findViewById(R.id.elapsedText)
        progressBar = findViewById(R.id.progressBar)
        percentText = findViewById(R.id.percentText)
        numText = findViewById(R.id.numText)
        denText = findViewById(R.id.denText)
        fracBox = findViewById(R.id.fracBox)
        feedbackText = findViewById(R.id.feedbackText)
        keypad = findViewById(R.id.keypad)
        btnConfirm = findViewById(R.id.btnConfirm)
        keypad.padBottomInset(16.dp)

        findViewById<TextView>(R.id.btnExit).tap { askQuit() }
        btnConfirm.tap { submit() }
        wireKeys(keypad)
        // 点分子/分母切换输入焦点，比只靠 ↑↓ 直观
        numText.tap { editingNum = true; paintFocus() }
        denText.tap { editingNum = false; paintFocus() }
        progressBar.pivotX = 0f
    }

    private fun wireKeys(group: ViewGroup) {
        for (i in 0 until group.childCount) {
            val child = group.getChildAt(i)
            if (child is ViewGroup) {
                wireKeys(child)
                continue
            }
            val tag = child.tag as? String ?: continue
            child.tap(haptic = false) { onKey(tag, it) }
            // tap 内部装的是 0.96 的按压，键盘按键要更明显，覆盖掉
            Motion.touchable(child, 0.90f)
        }
    }

    private fun onKey(tag: String, v: View) {
        if (answered) return
        Motion.tick(v)
        when (tag) {
            "SWAP" -> { editingNum = !editingNum; paintFocus() }
            "DEL" -> {
                if (editingNum) numBuf = numBuf.dropLast(1) else denBuf = denBuf.dropLast(1)
                paintInput()
            }
            else -> {
                val buf = if (editingNum) numBuf else denBuf
                if (buf.length >= 4) return
                // 不让 0 开头，避免 007 这种输入
                if (buf.isEmpty() && tag == "0") return
                if (editingNum) numBuf += tag else denBuf += tag
                paintInput()
                // 填完分子自动跳到分母，少按一次 ↑↓
                if (editingNum && numBuf.isNotEmpty() && denBuf.isEmpty()) {
                    editingNum = false
                    paintFocus()
                }
            }
        }
    }

    private fun showItem() {
        answered = false
        numBuf = ""
        denBuf = ""
        editingNum = true
        itemStart = System.currentTimeMillis()
        val e = queue[cursor]
        percentText.text = e.display
        feedbackText.text = ""
        progressText.text = getString(R.string.progress_of, cursor + 1, queue.size)
        paintInput()
        paintFocus()

        // 题面弹入
        percentText.alpha = 0f
        percentText.translationY = 18f.dp
        percentText.animate().alpha(1f).translationY(0f)
            .setDuration(380).setInterpolator(Motion.SOFT).start()
        Motion.springTo(progressBar, DynamicAnimation.SCALE_X, cursor.toFloat() / queue.size)
    }

    private fun paintInput() {
        numText.text = if (numBuf.isEmpty()) "?" else numBuf
        denText.text = if (denBuf.isEmpty()) "?" else denBuf
    }

    private fun paintFocus() {
        numText.setTextColor(getColor(if (editingNum) R.color.accent else R.color.ink))
        denText.setTextColor(getColor(if (editingNum) R.color.ink else R.color.accent))
    }

    private fun submit() {
        if (answered) return
        val n = numBuf.toIntOrNull()
        val d = denBuf.toIntOrNull()
        if (n == null || d == null || d == 0) {
            Motion.reject(btnConfirm)
            shake(fracBox)
            return
        }
        answered = true
        val e = queue[cursor]
        val correct = PercentData.check(e, n, d)
        items.add(
            PercentItem(
                display = e.display,
                expectNum = e.num,
                expectDen = e.den,
                answerNum = n,
                answerDen = d,
                correct = correct,
                ms = System.currentTimeMillis() - itemStart
            )
        )
        if (correct) {
            Motion.confirm(btnConfirm)
            feedbackText.setTextColor(getColor(R.color.teal))
            feedbackText.text = getString(R.string.percent_right)
            // 纸片从分数框中心迸出，坐标换算到 root（庆祝层与 root 同尺寸）
            val me = IntArray(2)
            val base = IntArray(2)
            fracBox.getLocationInWindow(me)
            root.getLocationInWindow(base)
            celebration.burstAt(
                me[0] - base[0] + fracBox.width / 2f,
                me[1] - base[1] + fracBox.height / 2f,
                14
            )
        } else {
            Motion.reject(btnConfirm)
            feedbackText.setTextColor(getColor(R.color.rose))
            feedbackText.text = getString(R.string.percent_wrong, e.fraction)
            shake(fracBox)
        }
        feedbackText.alpha = 0f
        feedbackText.animate().alpha(1f).setDuration(220).start()
        // 答错多留一会儿，给时间看正确答案
        feedbackText.postDelayed({ next() }, if (correct) 620L else 1400L)
    }

    private fun next() {
        cursor++
        if (cursor >= queue.size) finishRound() else showItem()
    }

    private fun shake(v: View) {
        v.animate().cancel()
        Motion.animate(340L, Motion.STANDARD) { f ->
            val amp = 9f.dp * (1f - f)
            v.translationX = amp * kotlin.math.sin(f * 3.5f * 2f * Math.PI).toFloat()
        }
    }

    // ------------------------------------------------------------ 收尾

    private fun finishRound() {
        elapsedText.removeCallbacks(ticker)
        val now = System.currentTimeMillis()
        val session = PercentSession(
            id = Repo.newId(),
            mode = mode,
            date = DateUtil.todayStr(),
            startAt = sessionStart,
            endAt = now,
            items = items.toMutableList()
        )
        Repo.addPercentSession(session)
        Motion.springTo(progressBar, DynamicAnimation.SCALE_X, 1f)
        showResult(session)
    }

    private fun showResult(s: PercentSession) {
        val scrim = FrameLayout(this).apply {
            layoutParams = FrameLayout.LayoutParams(-1, -1)
            setBackgroundColor(com.gongkao.checkin.ui.transparentBlack(0.42f))
            alpha = 0f
            isClickable = true
        }
        val cardBox = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundResource(R.drawable.bg_card)
            setPadding(22.dp, 24.dp, 22.dp, 20.dp)
            layoutParams = FrameLayout.LayoutParams(-1, -2).apply {
                gravity = android.view.Gravity.CENTER
                marginStart = 28.dp
                marginEnd = 28.dp
            }
            scaleX = 0.86f
            scaleY = 0.86f
        }
        cardBox.addView(bigText(getString(R.string.round_done)))
        cardBox.addView(
            subText(
                getString(
                    R.string.round_result,
                    s.correctCount(), s.total(), DateUtil.human(now() - s.startAt)
                )
            )
        )
        cardBox.addView(subText(getString(R.string.percent_avg_sub, DateUtil.human(s.avgMs()))))
        cardBox.addView(actionBtn(getString(R.string.again), primary = true) { restart(scrim) })
        cardBox.addView(actionBtn(getString(R.string.back_home), primary = false) { finish() })
        scrim.addView(cardBox)
        root.addView(scrim)

        scrim.animate().alpha(1f).setDuration(240).setInterpolator(Motion.EMPHASIZED).start()
        Motion.springTo(cardBox, DynamicAnimation.SCALE_X, 1f, stiffness = 520f, damping = 0.62f)
        Motion.springTo(cardBox, DynamicAnimation.SCALE_Y, 1f, stiffness = 520f, damping = 0.62f)
        // 全对才放整屏庆祝，不然显得廉价
        if (s.correctCount() == s.total() && s.total() > 0) {
            celebration.celebrate(root.width / 2f, root.height * 0.4f)
        }
    }

    private fun now() = System.currentTimeMillis()

    private fun restart(scrim: View) {
        root.removeView(scrim)
        items.clear()
        cursor = 0
        sessionStart = System.currentTimeMillis()
        queue = if (mode == "RANDOM") PercentData.entries.shuffled() else PercentData.entries
        elapsedText.post(ticker)
        showItem()
    }

    private fun bigText(t: CharSequence) = TextView(this).apply {
        text = t
        textSize = 21f
        setTextColor(getColor(R.color.ink))
        typeface = android.graphics.Typeface.create("sans-serif-medium", android.graphics.Typeface.NORMAL)
        gravity = android.view.Gravity.CENTER
        layoutParams = LinearLayout.LayoutParams(-1, -2)
    }

    private fun subText(t: CharSequence) = TextView(this).apply {
        text = t
        textSize = 13f
        setTextColor(getColor(R.color.ink_sub))
        gravity = android.view.Gravity.CENTER
        layoutParams = LinearLayout.LayoutParams(-1, -2).apply { topMargin = 8.dp }
    }

    private fun actionBtn(t: CharSequence, primary: Boolean, onTap: () -> Unit) =
        TextView(this).apply {
            text = t
            textSize = 15f
            gravity = android.view.Gravity.CENTER
            setTextColor(getColor(if (primary) R.color.surface else R.color.ink_sub))
            setBackgroundResource(if (primary) R.drawable.bg_btn_primary else R.drawable.bg_btn_ghost)
            layoutParams = LinearLayout.LayoutParams(-1, 48.dp).apply {
                topMargin = if (primary) 18.dp else 8.dp
            }
            tap { onTap() }
        }

    // ------------------------------------------------------------ 退出

    private fun askQuit() {
        if (items.isEmpty()) {
            finish()
            return
        }
        AppDialog.show(
            ctx = this,
            title = getString(R.string.quit),
            message = getString(R.string.quit_confirm),
            positive = getString(R.string.quit),
            negative = getString(R.string.keep_going),
            destructive = true
        ) { finish() }
    }

}
