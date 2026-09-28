package cn.alvkeke.dropto.ui.activity

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ObjectAnimator
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

    private val mgmtWidthRatio = 3f / 4f
    private var mgmtPanelWidth = 0

    private var dragBaseLeft = 0f

    private val viewModel: MainViewModel by lazy {
        ViewModelProvider(this)[MainViewModel::class.java]
    }

    private var revealAnimation: ObjectAnimator? = null
    private var noteAnimation: ObjectAnimator? = null
    private var noteDragBase = 0f

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

        categoryListFragment.setContentAlpha(ratio)
        categoryListFragment.view?.elevation =
            if (categoryListLeft > REVEAL_EPS) categoryElevationPx else 0f
        mgmtPageFragment.view?.let { v ->
            v.pivotX = v.width / 2f
            v.pivotY = v.height / 2f
            val scale = MGMT_SCALE_MIN + (1f - MGMT_SCALE_MIN) * ratio
            v.scaleX = scale
            v.scaleY = scale
        }

        movementGate.translationX = categoryListLeft
        movementGate.isVisible = ratio > 0f
    }

    private fun animateRevealTo(open: Boolean, velocityPxPerMs: Float? = null) {
        if (mgmtPanelWidth <= 0) return
        val targetX = if (open) mgmtPanelWidth.toFloat() else 0f
        val startX = cateContainer.translationX
        if (startX == targetX) {
            revealAnimation = null
            onMgmtRevealProgress(targetX)
            return
        }
        revealAnimation?.cancel()
        revealAnimation = null

        val anim = ObjectAnimator.ofFloat(
            cateContainer, PROP_TRANSLATION_X, startX, targetX
        ).apply {
            duration = calcDuration(
                velocityPxPerMs,
                abs(targetX - startX),
            ).coerceAtMost(MAX_DURATION_FRAGMENT_ANIME)
            addUpdateListener {
                onMgmtRevealProgress(animatedValue as Float)
            }
            addListener(object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: Animator) {
                    if (revealAnimation != animation) return
                    revealAnimation = null
                    onMgmtRevealProgress(targetX)
                }
            })
        }
        revealAnimation = anim
        anim.start()
    }

    private fun animateOpenMgmt(velocityPxPerMs: Float? = null) {
        animateRevealTo(open = true, velocityPxPerMs = velocityPxPerMs)
    }

    private fun animateCloseMgmt(velocityPxPerMs: Float? = null) {
        animateRevealTo(open = false, velocityPxPerMs = velocityPxPerMs)
    }

    private fun translateCategoryFragment(left: Float) {
        val panel = mgmtPanelWidth
        if (panel <= 0) return
        val target = left.coerceIn(0f, panel.toFloat())
        cateContainer.translationX = target
        onMgmtRevealProgress(target)
    }

    private val mgmtDragListener = object : HorizontalDragListener {
        override fun onDragStart() {
            revealAnimation?.let {
                revealAnimation = null
                it.cancel()
            }
            dragBaseLeft = cateContainer.translationX
        }

        override fun onDragging(deltaX: Float) {
            translateCategoryFragment(dragBaseLeft + deltaX)
        }

        override fun onDragEnd(deltaX: Float, velocityPxPerMs: Float) {
            val left = dragBaseLeft + deltaX
            val open = if (abs(velocityPxPerMs) > REVEAL_FLING_SPEED) {
                velocityPxPerMs > 0f
            } else {
                left > mgmtPanelWidth / 2f
            }
            if (open) {
                animateOpenMgmt(velocityPxPerMs)
            } else {
                animateCloseMgmt(velocityPxPerMs)
            }
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

    private fun animateNoteListTo(
        targetX: Float,
        velocityPxPerMs: Float?,
        hideOnEnd: Boolean,
    ) {
        val startX = noteContainer.translationX
        if (startX == targetX) {
            if (hideOnEnd) {
                noteContainer.isVisible = false
            }
            return
        }
        cancelNoteAnimation()
        val anim = ObjectAnimator.ofFloat(
            noteContainer, PROP_TRANSLATION_X, startX, targetX
        ).apply {
            duration = calcDuration(
                velocityPxPerMs,
                abs(targetX - startX),
            ).coerceAtMost(MAX_DURATION_FRAGMENT_ANIME)
            addListener(object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: Animator) {
                    if (noteAnimation !== animation) return
                    noteAnimation = null
                    if (hideOnEnd) {
                        noteContainer.isVisible = false
                    }
                }
            })
        }
        noteAnimation = anim
        anim.start()
    }

    private fun animateOpenNoteList(velocityPxPerMs: Float? = null) {
        val width = rootLayout.width
        if (width <= 0) return
        cancelNoteAnimation()
        if (!noteContainer.isVisible) {
            noteContainer.isVisible = true
            noteContainer.translationX = width.toFloat()
        }
        animateNoteListTo(0f, velocityPxPerMs, hideOnEnd = false)
    }

    private fun animateCloseNoteList(velocityPxPerMs: Float? = null) {
        if (!noteContainer.isVisible) return
        val width = rootLayout.width
        if (width <= 0) {
            noteContainer.isVisible = false
            return
        }
        animateNoteListTo(width.toFloat(), velocityPxPerMs, hideOnEnd = true)
    }

    private fun cancelNoteAnimation() {
        noteAnimation?.let {
            noteAnimation = null
            it.cancel()
        }
    }

    private fun isNoteListOpen(): Boolean = noteContainer.isVisible

    private fun translateNoteFragment(left: Float) {
        val width = rootLayout.width
        if (width <= 0) return
        noteContainer.translationX = left.coerceIn(0f, width.toFloat())
    }

    private val noteDragListener = object : HorizontalDragListener {
        override fun onDragStart() {
            cancelNoteAnimation()
            noteDragBase = noteContainer.translationX
        }

        override fun onDragging(deltaX: Float) {
            translateNoteFragment(noteDragBase + deltaX)
        }

        override fun onDragEnd(deltaX: Float, velocityPxPerMs: Float) {
            val left = noteDragBase + deltaX
            val open = if (abs(velocityPxPerMs) > REVEAL_FLING_SPEED) {
                velocityPxPerMs < 0f
            } else {
                left < rootLayout.width / 3f
            }
            if (open) {
                animateOpenNoteList(velocityPxPerMs)
            } else {
                animateCloseNoteList(velocityPxPerMs)
            }
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
        private const val PROP_TRANSLATION_X = "translationX"
        private const val MAX_DURATION_FRAGMENT_ANIME = 200L
        // px tolerance used to treat the reveal as fully open / fully closed
        private const val REVEAL_EPS = 1f
        private const val REVEAL_FLING_SPEED = 0.2f
        // mgmt page scale when the reveal just starts (fully open scale = 1)
        private const val MGMT_SCALE_MIN = 0.9f
        private const val STATE_NOTE_LIST_OPEN = "note_list_open"
    }
}
