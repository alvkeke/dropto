package cn.alvkeke.dropto.ui.activity

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ValueAnimator
import android.annotation.SuppressLint
import android.content.res.Configuration
import android.os.Bundle
import android.util.Log
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.view.ViewGroup
import android.view.WindowManager
import androidx.activity.OnBackPressedCallback
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentContainerView
import androidx.lifecycle.ViewModelProvider
import cn.alvkeke.dropto.R
import cn.alvkeke.dropto.data.Category
import cn.alvkeke.dropto.storage.DataLoader
import cn.alvkeke.dropto.ui.fragment.CategoryListFragment
import cn.alvkeke.dropto.ui.fragment.MgmtPageFragment
import cn.alvkeke.dropto.ui.fragment.NoteListFragment
import cn.alvkeke.dropto.ui.intf.FragmentOnBackListener
import cn.alvkeke.dropto.ui.intf.HorizontalDragListener
import cn.alvkeke.dropto.ui.listener.ReleaseVelocitySampler
import kotlin.math.abs

class MainActivity : AppCompatActivity(),
    CategoryListFragment.EventListener,
    NoteListFragment.EventListener {

    private var _categoryListFragment: CategoryListFragment? = null
    private var categoryListFragment: CategoryListFragment
        get() = _categoryListFragment ?: CategoryListFragment().also { _categoryListFragment = it }
        set(value) { _categoryListFragment = value }

    private var _mgmtPageFragment: MgmtPageFragment? = null
    private var mgmtPageFragment: MgmtPageFragment
        get() = _mgmtPageFragment ?: MgmtPageFragment().also { _mgmtPageFragment = it }
        set(value) { _mgmtPageFragment = value }

    private var _noteListFragment: NoteListFragment? = null
    private var noteListFragment: NoteListFragment
        get() = _noteListFragment ?: NoteListFragment().also { _noteListFragment = it }
        set(value) { _noteListFragment = value }

    private lateinit var rootLayout: ViewGroup
    private lateinit var mgmtContainer: FragmentContainerView
    private lateinit var cateContainer: FragmentContainerView
    private lateinit var noteContainer: FragmentContainerView

    private lateinit var movementGate: View
    private var categoryElevationPx = 0f
    private var expandDipPx = 0f

    private val mgmtWidthRatio = 3f / 4f
    private var mgmtPanelWidth = 0

    private val viewModel: MainViewModel by lazy {
        ViewModelProvider(this)[MainViewModel::class.java]
    }

    private val mgmtSlide by lazy {
        PageSlider(cateContainer, { mgmtPanelWidth.toFloat() }, ::onMgmtRevealProgress)
    }
    private val noteSlide by lazy {
        PageSlider(noteContainer, { rootLayout.width.toFloat() }, ::onNoteListProgress)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        this.enableEdgeToEdge()
        setContentView(R.layout.activity_main)
        rootLayout = findViewById(R.id.main_root)
        mgmtContainer = findViewById(R.id.mgmt_container)
        cateContainer = findViewById(R.id.category_container)
        noteContainer = findViewById(R.id.note_container)
        movementGate = findViewById(R.id.movement_gate)
        categoryElevationPx = resources.getDimension(R.dimen.elevation_category_page)
        expandDipPx = resources.getDimension(R.dimen.z_expand_dip_pages)

        window.setFlags(
            WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS
        )

        onBackPressedDispatcher.addCallback(this, OnFragmentBackPressed(true))

        for (f in supportFragmentManager.fragments) {
            when (f) {
                is CategoryListFragment -> categoryListFragment = f
                is MgmtPageFragment -> mgmtPageFragment = f
                is NoteListFragment -> noteListFragment = f
            }
        }

        if (!mgmtPageFragment.isAdded) {
            bindFragment(R.id.mgmt_container, mgmtPageFragment)
        }
        if (!categoryListFragment.isAdded) {
            bindFragment(R.id.category_container, categoryListFragment)
        }
        if (!noteListFragment.isAdded) {
            bindFragment(R.id.note_container, noteListFragment)
        }

        categoryListFragment.eventListener = this
        noteListFragment.eventListener = this
        categoryListFragment.horizontalDragListener = mgmtDragListener
        mgmtPageFragment.horizontalDragListener = mgmtDragListener
        noteListFragment.horizontalDragListener = noteDragListener
        movementGate.setOnClickListener { animateCloseMgmt() }
        movementGate.setOnTouchListener(GateDragListener())

        val noteListOpened = savedInstanceState?.getBoolean(STATE_NOTE_LIST_OPEN) == true
        rootLayout.post {
            updateMgmtContainerWidth()
            onMgmtRevealProgress(cateContainer.translationX)
            if (noteListOpened) {
                noteContainer.isVisible = true
                onNoteListProgress(noteContainer.translationX)
            }
        }
        rootLayout.addOnLayoutChangeListener { _, left, top, right, bottom,
                                               oldLeft, oldTop, oldRight, oldBottom ->
            if (right - left != oldRight - oldLeft ||
                bottom - top != oldBottom - oldTop
            ) {
                updateMgmtContainerWidth()
            }
        }

        if (savedInstanceState == null) {
            Log.v(TAG, "onCreate: fresh start")
            val categories: ArrayList<Category> = DataLoader.loadCategories(this)
            viewModel.setCategoriesList(categories)
        } else {
            Log.v(TAG, "onCreate: restore from savedInstanceState")
        }
    }

    private fun bindFragment(viewId: Int, fragment: Fragment) {
        supportFragmentManager.beginTransaction()
            .add(viewId, fragment, null)
            .commit()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putBoolean(STATE_NOTE_LIST_OPEN, isNoteListOpen())
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        val nightMode = newConfig.uiMode and Configuration.UI_MODE_NIGHT_MASK
        when (nightMode) {
            Configuration.UI_MODE_NIGHT_YES -> {
                AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES)
            }

            Configuration.UI_MODE_NIGHT_NO -> {
                AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO)
            }
        }
        recreate()
    }

    private fun updateMgmtContainerWidth() {
        val rootWidth = rootLayout.width
        if (rootWidth <= 0) return
        val target = (rootWidth * mgmtWidthRatio).toInt()
        if (target == mgmtPanelWidth) return
        mgmtPanelWidth = target
        mgmtContainer.layoutParams = mgmtContainer.layoutParams.apply {
            width = target
        }
    }

    private fun calcDuration(
        velocityPxPerMs: Float?,
        distancePx: Float,
    ): Long {
        if (velocityPxPerMs == null || velocityPxPerMs == 0f ||
            distancePx <= 0f
        ) {
            return MAX_DURATION_FRAGMENT_ANIME
        }
        val ms = (distancePx / abs(velocityPxPerMs)).toLong()
        return ms
    }

    private inner class PageSlider(
        private val view: View,
        private val maxOffset: () -> Float,
        private val onProgress: (Float) -> Unit,
    ) {
        private var animator: ValueAnimator? = null
        private var dragBase = 0f

        fun translate(left: Float) {
            val max = maxOffset()
            if (max <= 0f) return
            val target = left.coerceIn(0f, max)
            view.translationX = target
            onProgress(target)
        }

        fun cancel() {
            animator?.let {
                animator = null
                it.cancel()
            }
        }

        fun animateTo(targetX: Float, velocityPxPerMs: Float?, onEnd: () -> Unit = {}) {
            val startX = view.translationX
            if (startX == targetX) {
                animator = null
                translate(targetX)
                onEnd()
                return
            }
            cancel()
            val anim = ValueAnimator.ofFloat(startX, targetX).apply {
                duration = calcDuration(
                    velocityPxPerMs,
                    abs(targetX - startX),
                ).coerceAtMost(MAX_DURATION_FRAGMENT_ANIME)
                addUpdateListener {
                    translate(it.animatedValue as Float)
                }
                addListener(object : AnimatorListenerAdapter() {
                    override fun onAnimationEnd(animation: Animator) {
                        if (animator !== animation) return
                        animator = null
                        onEnd()
                    }
                })
            }
            animator = anim
            anim.start()
        }

        fun dragListener(onRelease: (left: Float, velocityPxPerMs: Float) -> Unit): HorizontalDragListener =
            object : HorizontalDragListener {
                override fun onDragStart() {
                    cancel()
                    dragBase = view.translationX
                }

                override fun onDragging(deltaX: Float) {
                    translate(dragBase + deltaX)
                }

                override fun onDragEnd(deltaX: Float, velocityPxPerMs: Float) {
                    onRelease(dragBase + deltaX, velocityPxPerMs)
                }
            }
    }

    private fun applyPageScale(view: View?, ratio: Float) {
        view ?: return
        view.pivotX = view.width / 2f
        view.pivotY = view.height / 2f
        val scale = PAGE_SCALE_MIN + (1f - PAGE_SCALE_MIN) * ratio
        view.scaleX = scale
        view.scaleY = scale
    }

    private fun toggleMgmtReveal() {
        if (cateContainer.translationX > REVEAL_EPS) {
            animateCloseMgmt()
        } else {
            animateOpenMgmt()
        }
    }

    private fun onMgmtRevealProgress(categoryListLeft: Float) {
        val panel = mgmtPanelWidth
        if (panel <= 0) return
        val ratio = (categoryListLeft / panel).coerceIn(0f, 1f)

        applyPageScale(mgmtContainer, ratio)
        mgmtContainer.translationZ = expandDipPx * (ratio - 1f)
        movementGate.translationX = categoryListLeft
        movementGate.isVisible = ratio > 0f
        mgmtContainer.visibility = if (ratio > 0f) View.VISIBLE else View.INVISIBLE
    }

    private fun animateOpenMgmt(velocityPxPerMs: Float? = null) =
        mgmtSlide.animateTo(mgmtPanelWidth.toFloat(), velocityPxPerMs)

    private fun animateCloseMgmt(velocityPxPerMs: Float? = null) =
        mgmtSlide.animateTo(0f, velocityPxPerMs)

    private val mgmtDragListener by lazy {
        mgmtSlide.dragListener { left, velocityPxPerMs ->
            val open = if (abs(velocityPxPerMs) > REVEAL_FLING_SPEED) {
                velocityPxPerMs > 0f
            } else {
                left > mgmtPanelWidth / 2f
            }
            if (open) animateOpenMgmt(velocityPxPerMs)
            else animateCloseMgmt(velocityPxPerMs)
        }
    }

    private inner class GateDragListener : View.OnTouchListener {
        private val touchSlop = ViewConfiguration.get(this@MainActivity).scaledTouchSlop
        private val velocitySampler = ReleaseVelocitySampler()

        private var downX = 0f
        private var downY = 0f
        private var dragging = false
        private var dragStartDeltaX = 0f

        @SuppressLint("ClickableViewAccessibility")
        override fun onTouch(v: View, event: MotionEvent): Boolean {
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    downX = event.rawX
                    downY = event.rawY
                    dragging = false
                    velocitySampler.clear()
                    velocitySampler.add(event.rawX, event.rawY)
                }

                MotionEvent.ACTION_MOVE -> {
                    velocitySampler.add(event.rawX, event.rawY)
                    val deltaX = event.rawX - downX
                    val deltaY = event.rawY - downY
                    if (!dragging) {
                        if (abs(deltaX) > touchSlop && abs(deltaX) > abs(deltaY)) {
                            dragging = true
                            dragStartDeltaX = deltaX
                            mgmtDragListener.onDragStart()
                            v.isPressed = false
                        } else if (abs(deltaY) > touchSlop) {
                            return false
                        }
                    }
                    if (dragging) {
                        mgmtDragListener.onDragging(deltaX - dragStartDeltaX)
                        return true
                    }
                }

                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    velocitySampler.add(event.rawX, event.rawY)
                    if (dragging) {
                        dragging = false
                        val velocity = velocitySampler.recentReleaseVelocity().first
                        mgmtDragListener.onDragEnd(
                            event.rawX - downX - dragStartDeltaX,
                            velocity,
                        )
                        return true
                    }
                }
            }
            return false
        }
    }

    private fun onNoteListProgress(noteListLeft: Float) {
        val width = rootLayout.width
        if (width <= 0) return
        val ratio = (noteListLeft / width).coerceIn(0f, 1f)

        applyPageScale(cateContainer, ratio)
        cateContainer.translationZ = expandDipPx * (ratio - 1f)
    }

    private fun animateOpenNoteList(velocityPxPerMs: Float? = null) {
        val width = rootLayout.width
        if (width <= 0) return
        noteSlide.cancel()
        if (!noteContainer.isVisible) {
            noteContainer.isVisible = true
            noteContainer.translationX = width.toFloat()
        }
        noteSlide.animateTo(0f, velocityPxPerMs)
    }

    private fun animateCloseNoteList(velocityPxPerMs: Float? = null) {
        if (!noteContainer.isVisible) return
        val width = rootLayout.width
        if (width <= 0) {
            noteContainer.isVisible = false
            return
        }
        noteSlide.animateTo(width.toFloat(), velocityPxPerMs) {
            noteContainer.isVisible = false
        }
    }

    private fun isNoteListOpen(): Boolean = noteContainer.isVisible

    private val noteDragListener by lazy {
        noteSlide.dragListener { left, velocityPxPerMs ->
            val open = if (abs(velocityPxPerMs) > REVEAL_FLING_SPEED) {
                velocityPxPerMs < 0f
            } else {
                left < rootLayout.width / 3f
            }
            if (open) animateOpenNoteList(velocityPxPerMs)
            else animateCloseNoteList(velocityPxPerMs)
        }
    }

    override fun onCategoryShow(category: Category) {
        if (!DataLoader.loadCategoryNotes(this, category)) {
            Log.e(TAG, "Failed to get noteList from database")
        }
        viewModel.setCategory(category)
        animateOpenNoteList()
    }

    override fun onNavigationClick() {
        toggleMgmtReveal()
    }

    override fun onNoteListClose() {
        animateCloseNoteList()
    }

    internal inner class OnFragmentBackPressed(enabled: Boolean) : OnBackPressedCallback(enabled) {
        override fun handleOnBackPressed() {
            if (cateContainer.translationX > REVEAL_EPS) {
                animateCloseMgmt()
                return
            }
            if (isNoteListOpen()) {
                if (!noteListFragment.onBackPressed()) {
                    animateCloseNoteList()
                }
                return
            }
            val fragment = supportFragmentManager.findFragmentById(R.id.category_container)
            var ret = false
            if (fragment is FragmentOnBackListener) {
                ret = (fragment as FragmentOnBackListener).onBackPressed()
            }
            if (!ret) {
                this@MainActivity.finish()
            }
        }
    }

    companion object {
        const val TAG: String = "MainActivity"
        private const val MAX_DURATION_FRAGMENT_ANIME = 200L
        // px tolerance used to treat the reveal as fully open / fully closed
        private const val REVEAL_EPS = 1f
        private const val REVEAL_FLING_SPEED = 0.2f
        // mgmt page scale when the reveal just starts (fully open scale = 1)
        private const val PAGE_SCALE_MIN = 0.9f
        private const val STATE_NOTE_LIST_OPEN = "note_list_open"
    }
}
