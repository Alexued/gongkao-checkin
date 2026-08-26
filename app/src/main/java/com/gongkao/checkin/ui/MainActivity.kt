package com.gongkao.checkin.ui

import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity
import androidx.viewpager.widget.PagerAdapter
import androidx.viewpager.widget.ViewPager
import com.gongkao.checkin.R
import com.gongkao.checkin.anim.Motion
import com.gongkao.checkin.data.DateUtil
import com.gongkao.checkin.data.Repo
import com.gongkao.checkin.ui.page.RecitePage
import com.gongkao.checkin.ui.page.StatsPage
import com.gongkao.checkin.ui.page.TimerPage
import com.gongkao.checkin.ui.page.TodayPage
import com.gongkao.checkin.view.CelebrationView

/**
 * 四个标签的宿主。页面可以左右滑动切换，也可以点标签跳转。
 *
 * 用 ViewPager1 而不是 ViewPager2：页面视图必须常驻（计时器不能被回收重建），
 * PagerAdapter 允许直接把已有的 [Page.view] 交给它，ViewPager2 的 RecyclerView 回收做不到这点。
 */
class MainActivity : AppCompatActivity() {

    private lateinit var pager: ViewPager
    private lateinit var tabBar: LinearLayout
    private lateinit var tabIndicator: View
    lateinit var celebration: CelebrationView
        private set
    private lateinit var celebrateText: TextView

    private val tabs = arrayOfNulls<LinearLayout>(4)
    private val icons = arrayOfNulls<ImageView>(4)
    private val labels = arrayOfNulls<TextView>(4)

    private lateinit var pages: List<Page>
    private lateinit var tabOrder: List<Int>
    private var current = -1

    private val onData: () -> Unit = { pages.forEach { if (it.created) it.refresh() } }

    companion object {
        const val EXTRA_TAB = "tab"

        /** 语义化的 tab 名。实际下标随模式变（通用模式没有背诵），用 [pageIndexOf] 换算。 */
        const val TAB_TODAY = "today"
        const val TAB_TIMER = "timer"
        const val TAB_RECITE = "recite"
        const val TAB_STATS = "stats"
    }

    /** 语义名 → 当前模式下的页面下标。背诵在通用模式不存在，退回统计。 */
    private fun pageIndexOf(name: String?): Int {
        val general = Repo.appMode().isGeneral
        return when (name) {
            TAB_TIMER -> 1
            TAB_RECITE -> if (general) if (pages.size > 2) 2 else 0 else 2
            TAB_STATS -> pages.size - 1
            else -> 0
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        edgeToEdge()
        Repo.init(this)
        setContentView(R.layout.activity_main)

        pager = findViewById(R.id.pager)
        tabBar = findViewById(R.id.tabBar)
        tabIndicator = findViewById(R.id.tabIndicator)
        celebration = findViewById(R.id.celebration)
        celebrateText = findViewById(R.id.celebrateText)

        tabs[0] = findViewById(R.id.tab0); icons[0] = findViewById(R.id.tab0Icon); labels[0] = findViewById(R.id.tab0Text)
        tabs[1] = findViewById(R.id.tab1); icons[1] = findViewById(R.id.tab1Icon); labels[1] = findViewById(R.id.tab1Text)
        tabs[2] = findViewById(R.id.tab2); icons[2] = findViewById(R.id.tab2Icon); labels[2] = findViewById(R.id.tab2Text)
        tabs[3] = findViewById(R.id.tab3); icons[3] = findViewById(R.id.tab3Icon); labels[3] = findViewById(R.id.tab3Text)

        tabBar.padBottomInset()
        celebration.isClickable = false

        // 通用模式下背诵训练用不上，直接不给这个 tab（记录仍在库里，切回考公照旧可见）
        val general = Repo.appMode().isGeneral
        pages = if (general) {
            listOf(TodayPage(this), TimerPage(this), StatsPage(this))
        } else {
            listOf(TodayPage(this), TimerPage(this), RecitePage(this), StatsPage(this))
        }
        // XML 里的 tab 下标 → 页面下标。通用模式抽掉背诵那格，统计顶上来。
        tabOrder = if (general) listOf(0, 1, 3) else listOf(0, 1, 2, 3)
        if (general) tabs[2]?.show(false)

        pager.adapter = PagesAdapter()
        // 四页全部保活，切页不重建（计时器、滚动位置都要留住）
        pager.offscreenPageLimit = pages.size - 1
        pager.setPageTransformer(false, ParallaxTransformer())
        pager.addOnPageChangeListener(pageListener)

        tabOrder.forEachIndexed { pageIndex, xmlIndex ->
            tabs[xmlIndex]?.tap { select(pageIndex) }
        }

        // 指示条宽度 = 屏宽 / 4，等布局完成后才知道实际宽度
        tabBar.post {
            val w = tabBar.width / pages.size
            if (w > 0) {
                tabIndicator.layoutParams = tabIndicator.layoutParams.apply { width = w }
                tabIndicator.requestLayout()
                moveIndicator(pager.currentItem, 0f)
            }
        }

        // 页面 0 的首次 refresh 由 adapter 负责，onShow 由 onResume 负责
        current = 0
        paintTabs()
        onBackPressedDispatcher.addCallback(this, backToToday)
        Repo.addListener(onData)
        openRequestedTab(intent)
    }

    override fun onNewIntent(intent: android.content.Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        openRequestedTab(intent)
    }

    /** 概览页的建议按钮会带 EXTRA_TAB 回来，直接跳到对应 tab。 */
    private fun openRequestedTab(intent: android.content.Intent?) {
        val name = intent?.getStringExtra(EXTRA_TAB) ?: return
        intent.removeExtra(EXTRA_TAB)
        val target = pageIndexOf(name)
        if (target != pager.currentItem) pager.setCurrentItem(target, false)
    }

    override fun onDestroy() {
        Repo.removeListener(onData)
        pages.forEach { if (it.created) it.onDestroy() }
        super.onDestroy()
    }

    override fun onResume() {
        super.onResume()
        // 跨零点回到前台才重建：把昨天没做完的累加到今天，然后落盘 + 通知
        if (Repo.read { it.lastRolledDate } != DateUtil.todayStr()) {
            Repo.edit { Repo.ensureDays() }
        }
        if (current in pages.indices && pages[current].created) pages[current].onShow()
    }

    // ------------------------------------------------------------ 标签切换

    /** 点标签跳转。带平滑滚动，滑动手势走 ViewPager 自己那套。 */
    fun select(index: Int) {
        if (index !in pages.indices) return
        if (index == pager.currentItem) return
        pager.setCurrentItem(index, true)
    }

    /** 把已有的 Page.view 直接交给 ViewPager，不做任何回收。 */
    private inner class PagesAdapter : PagerAdapter() {

        override fun getCount() = pages.size

        override fun isViewFromObject(view: View, obj: Any) = view === obj

        override fun instantiateItem(container: ViewGroup, position: Int): Any {
            val page = pages[position]
            val fresh = !page.created
            val v = page.view
            if (v.parent == null) {
                container.addView(v, ViewGroup.LayoutParams(-1, -1))
                // 布局里已留 86dp 给标签栏，这里再叠上导航栏高度
                v.padBottomInset()
            }
            if (fresh) page.refresh()
            return v
        }

        /** 页面常驻，不销毁。 */
        override fun destroyItem(container: ViewGroup, position: Int, obj: Any) = Unit
    }

    private val pageListener = object : ViewPager.OnPageChangeListener {

        override fun onPageScrolled(position: Int, offset: Float, offsetPx: Int) {
            moveIndicator(position, offset)
        }

        override fun onPageSelected(position: Int) {
            val old = current
            if (old == position) return
            current = position
            if (old in pages.indices && pages[old].created) pages[old].onHide()
            // created 守卫：部分页面的 refresh/onShow 直接碰 lateinit 字段
            if (pages[position].created) pages[position].onShow()
            paintTabs()
            backToToday.isEnabled = position != 0
        }

        override fun onPageScrollStateChanged(state: Int) = Unit
    }

    /** 指示条跟着滑动连续移动，不是切完才跳。 */
    private fun moveIndicator(position: Int, offset: Float) {
        val w = tabIndicator.width
        if (w <= 0) return
        tabIndicator.translationX = (position + offset) * w
    }

    /**
     * 滑动时的视差：当前页缩放归位、相邻页轻微缩小并反向平移一点，
     * 让翻页有层次而不是整块平移。
     */
    private class ParallaxTransformer : ViewPager.PageTransformer {
        override fun transformPage(page: View, position: Float) {
            val p = position.coerceIn(-1f, 1f)
            val away = kotlin.math.abs(p)
            page.scaleX = Motion.lerp(1f, 0.94f, away)
            page.scaleY = Motion.lerp(1f, 0.94f, away)
            page.alpha = Motion.lerp(1f, 0.55f, away)
            // 内容反向挪一点，制造纵深
            page.translationX = -p * page.width * 0.06f
        }
    }

    private fun paintTabs() {
        val on = getColor(R.color.accent)
        val off = getColor(R.color.ink_dim)
        tabOrder.forEachIndexed { pageIndex, i ->
            val active = pageIndex == current
            icons[i]?.let {
                it.setColorFilter(if (active) on else off)
                Motion.springTo(it, androidx.dynamicanimation.animation.DynamicAnimation.SCALE_X, if (active) 1.12f else 1f)
                Motion.springTo(it, androidx.dynamicanimation.animation.DynamicAnimation.SCALE_Y, if (active) 1.12f else 1f)
                Motion.springTo(it, androidx.dynamicanimation.animation.DynamicAnimation.TRANSLATION_Y, if (active) (-2f).dp else 0f)
            }
            labels[i]?.setTextColor(if (active) on else off)
        }
    }

    // ------------------------------------------------------------ 庆祝

    /** 单个任务完成时的小爆发。坐标是屏幕坐标。 */
    fun burst(screenX: Float, screenY: Float) {
        val loc = IntArray(2)
        celebration.getLocationOnScreen(loc)
        celebration.burstAt(screenX - loc[0], screenY - loc[1], 24)
    }

    /** 一天全部完成：全屏礼花 + 鼓励文案。 */
    fun celebrateDay(screenX: Float, screenY: Float, message: String) {
        val loc = IntArray(2)
        celebration.getLocationOnScreen(loc)
        celebration.celebrate(screenX - loc[0], screenY - loc[1])

        celebrateText.text = message
        celebrateText.alpha = 0f
        celebrateText.scaleX = 0.82f
        celebrateText.scaleY = 0.82f
        celebrateText.animate().cancel()
        celebrateText.animate()
            .alpha(1f).scaleX(1f).scaleY(1f)
            .setStartDelay(120)
            .setDuration(620)
            .setInterpolator(Motion.BOUNCY)
            .withEndAction {
                celebrateText.animate()
                    .alpha(0f).scaleX(0.95f).scaleY(0.95f)
                    .setStartDelay(1500).setDuration(340)
                    .setInterpolator(Motion.EXIT).start()
            }
            .start()
        Motion.confirm(celebration)
    }

    /**
     * 非首页时返回先回到今天。targetSdk 36 下 onBackPressed 已不再回调，
     * 用 dispatcher 注册；enabled 随当前页切换，交回系统才能正常退出。
     */
    private val backToToday = object : OnBackPressedCallback(false) {
        override fun handleOnBackPressed() = select(0)
    }
}
