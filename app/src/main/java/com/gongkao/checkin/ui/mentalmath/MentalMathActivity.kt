package com.gongkao.checkin.ui.mentalmath

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
import com.gongkao.checkin.data.MentalMath
import com.gongkao.checkin.data.MentalMathData
import com.gongkao.checkin.data.MentalMathItemRecord
import com.gongkao.checkin.data.MentalMathSession
import com.gongkao.checkin.data.Repo
import com.gongkao.checkin.ui.dp
import com.gongkao.checkin.ui.Themes
import com.gongkao.checkin.ui.edgeToEdge
import com.gongkao.checkin.ui.padBottomInset
import com.gongkao.checkin.ui.padTopInset
import com.gongkao.checkin.ui.show
import com.gongkao.checkin.ui.tap
import com.gongkao.checkin.ui.transparentBlack
import com.gongkao.checkin.view.CelebrationView

/**
 * 速算技巧讲解回顾。先看「技巧名 + 适用场景」，心里回忆方法，点开遮罩对照方法说明，再自评记住/模糊。
 * mode = FULL 按顺序过一遍，RANDOM 打乱；category 限定分类。
 * 结构照抄 FormulaActivity——同一套「盖住答案卡→翻牌→自评→下一题」交互，只是数据源换成 MentalMathData。
 */
class MentalMathActivity : AppCompatActivity() {

    private lateinit var modeText: TextView
    private lateinit var progressText: TextView
    private lateinit var progressBar: View
    private lateinit var categoryText: TextView
    private lateinit var nameText: TextView
    private lateinit var scenarioText: TextView
    private lateinit var answerCard: LinearLayout
    private lateinit var methodText: TextView
    private lateinit var tipText: TextView
    private lateinit var cover: LinearLayout
    private lateinit var judgeRow: LinearLayout
    private lateinit var btnVague: TextView
    private lateinit var btnKnown: TextView
    private lateinit var root: FrameLayout
    private lateinit var celebration: CelebrationView

    private var mode = "FULL"
    private var category = "全部"
    private lateinit var queue: List<MentalMath>
    private var cursor = 0

    private var sessionStart = 0L
    private var itemStart = 0L
    private var revealed = false
    private var judged = false
    private val items = mutableListOf<MentalMathItemRecord>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        edgeToEdge()
        Repo.init(this)
        Themes.apply(this)
        mode = intent.getStringExtra("mode") ?: "FULL"
        category = intent.getStringExtra("category") ?: "全部"

        root = FrameLayout(this)
        layoutInflater.inflate(R.layout.activity_mental_math, root, true)
        celebration = CelebrationView(this).apply {
            layoutParams = FrameLayout.LayoutParams(-1, -1)
            isClickable = false
        }
        root.addView(celebration)
        setContentView(root)
        Themes.installBackdrop(this)

        bind()
        val pool = MentalMathData.byCategory(category)
        queue = if (mode == "RANDOM") pool.shuffled() else pool
        if (queue.isEmpty()) {
            finish()
            return
        }
        sessionStart = System.currentTimeMillis()
        modeText.text = getString(
            if (mode == "RANDOM") R.string.mode_random else R.string.mode_full
        )
        showItem()
        // targetSdk 36 走预测式返回，onBackPressed 不再回调，必须注册 callback
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() = askQuit()
        })
    }

    private fun bind() {
        // 布局里顶栏没有 id，取根 LinearLayout 的第一个子节点
        val page = root.getChildAt(0) as ViewGroup
        page.getChildAt(0).padTopInset()
        modeText = findViewById(R.id.modeText)
        progressText = findViewById(R.id.progressText)
        progressBar = findViewById(R.id.progressBar)
        categoryText = findViewById(R.id.categoryText)
        nameText = findViewById(R.id.nameText)
        scenarioText = findViewById(R.id.scenarioText)
        answerCard = findViewById(R.id.answerCard)
        methodText = findViewById(R.id.methodText)
        tipText = findViewById(R.id.tipText)
        cover = findViewById(R.id.cover)
        judgeRow = findViewById(R.id.judgeRow)
        btnVague = findViewById(R.id.btnVague)
        btnKnown = findViewById(R.id.btnKnown)
        judgeRow.padBottomInset(18.dp)

        findViewById<TextView>(R.id.btnExit).tap { askQuit() }
        // 返回走同一套退出确认，不直接 finish，否则这一组记录悄悄丢掉
        findViewById<android.widget.ImageView>(R.id.btnBack).tap { askQuit() }
        cover.tap { reveal() }
        answerCard.tap { if (!revealed) reveal() }
        btnKnown.tap { judge(true) }
        btnVague.tap { judge(false) }
        progressBar.pivotX = 0f
    }

    private fun showItem() {
        revealed = false
        judged = false
        itemStart = System.currentTimeMillis()
        val m = queue[cursor]
        categoryText.text = m.category
        nameText.text = m.name
        scenarioText.text = m.scenario
        methodText.text = m.method
        tipText.show(false)
        tipText.text = m.tip
        progressText.text = getString(R.string.progress_of, cursor + 1, queue.size)

        cover.show(true)
        cover.alpha = 1f
        cover.scaleX = 1f
        cover.scaleY = 1f
        // reveal() 会从 0 动画到 1；这里必须归零，否则翻牌前方法说明仍然可见
        methodText.alpha = 0f
        // 未翻牌时按钮先禁用，避免不看就点「记住了」
        setJudgeEnabled(false)

        nameText.alpha = 0f
        nameText.translationY = 14f.dp
        nameText.animate().alpha(1f).translationY(0f)
            .setDuration(360).setInterpolator(Motion.SOFT).start()
        Motion.springTo(progressBar, DynamicAnimation.SCALE_X, cursor.toFloat() / queue.size)
    }

    private fun setJudgeEnabled(on: Boolean) {
        btnKnown.isEnabled = on
        btnVague.isEnabled = on
        btnKnown.alpha = if (on) 1f else 0.4f
        btnVague.alpha = if (on) 1f else 0.4f
    }

    private fun reveal() {
        if (revealed) return
        revealed = true
        Motion.tick(cover)
        // 遮罩往外撑开淡出，像揭开一层纸
        cover.animate().alpha(0f).scaleX(1.06f).scaleY(1.06f)
            .setDuration(280).setInterpolator(Motion.EXIT)
            .withEndAction { cover.show(false) }
            .start()
        methodText.alpha = 0f
        methodText.scaleX = 0.92f
        methodText.scaleY = 0.92f
        methodText.animate().alpha(1f).scaleX(1f).scaleY(1f)
            .setStartDelay(90).setDuration(460).setInterpolator(Motion.SOFT).start()
        if (queue[cursor].tip.isNotBlank()) {
            tipText.show(true)
            tipText.alpha = 0f
            tipText.translationY = 10f.dp
            tipText.animate().alpha(1f).translationY(0f)
                .setStartDelay(180).setDuration(360).setInterpolator(Motion.SOFT).start()
        }
        setJudgeEnabled(true)
    }

    private fun judge(known: Boolean) {
        if (!revealed || judged) return
        judged = true
        val m = queue[cursor]
        items.add(
            MentalMathItemRecord(
                mentalMathId = m.id,
                title = m.name,
                known = known,
                ms = System.currentTimeMillis() - itemStart
            )
        )
        if (known) {
            Motion.confirm(btnKnown)
            val me = IntArray(2)
            val base = IntArray(2)
            answerCard.getLocationInWindow(me)
            root.getLocationInWindow(base)
            celebration.burstAt(
                me[0] - base[0] + answerCard.width / 2f,
                me[1] - base[1] + answerCard.height / 2f,
                12
            )
        } else {
            Motion.reject(btnVague)
        }
        setJudgeEnabled(false)
        answerCard.postDelayed({ next() }, 320L)
    }

    private fun next() {
        cursor++
        if (cursor >= queue.size) finishRound() else showItem()
    }

    // ------------------------------------------------------------ 收尾

    private fun finishRound() {
        val now = System.currentTimeMillis()
        val session = MentalMathSession(
            id = Repo.newId(),
            mode = mode,
            category = category,
            date = DateUtil.todayStr(),
            startAt = sessionStart,
            endAt = now,
            items = items.toMutableList()
        )
        Repo.addMentalMathSession(session)
        Motion.springTo(progressBar, DynamicAnimation.SCALE_X, 1f)
        showResult(session, now)
    }

    private fun showResult(s: MentalMathSession, endAt: Long) {
        val scrim = FrameLayout(this).apply {
            layoutParams = FrameLayout.LayoutParams(-1, -1)
            setBackgroundColor(transparentBlack(0.42f))
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
                    R.string.round_result_formula,
                    s.knownCount(), s.total(), DateUtil.human(endAt - s.startAt)
                )
            )
        )
        cardBox.addView(actionBtn(getString(R.string.again), primary = true) { restart(scrim) })
        cardBox.addView(actionBtn(getString(R.string.back_home), primary = false) { finish() })
        scrim.addView(cardBox)
        root.addView(scrim)

        scrim.animate().alpha(1f).setDuration(240).setInterpolator(Motion.EMPHASIZED).start()
        Motion.springTo(cardBox, DynamicAnimation.SCALE_X, 1f, stiffness = 520f, damping = 0.62f)
        Motion.springTo(cardBox, DynamicAnimation.SCALE_Y, 1f, stiffness = 520f, damping = 0.62f)
        if (s.knownCount() == s.total() && s.total() > 0) {
            celebration.celebrate(root.width / 2f, root.height * 0.4f)
        }
    }

    private fun restart(scrim: View) {
        root.removeView(scrim)
        items.clear()
        cursor = 0
        sessionStart = System.currentTimeMillis()
        val pool = MentalMathData.byCategory(category)
        queue = if (mode == "RANDOM") pool.shuffled() else pool
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
