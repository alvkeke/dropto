package cn.alvkeke.dropto.ui.activity

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ObjectAnimator
import android.content.res.Configuration
import android.os.Bundle
import android.util.Log
import android.view.View
import android.view.WindowManager
import androidx.activity.OnBackPressedCallback
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import cn.alvkeke.dropto.R
import cn.alvkeke.dropto.data.Category
import cn.alvkeke.dropto.storage.DataLoader.loadCategories
import cn.alvkeke.dropto.ui.fragment.CategoryListFragment
import cn.alvkeke.dropto.ui.fragment.MgmtPageFragment
import cn.alvkeke.dropto.ui.intf.FragmentOnBackListener

class MainActivity : AppCompatActivity() {

    private var _categoryListFragment: CategoryListFragment? = null
    private var categoryListFragment: CategoryListFragment
        get() {
            if (_categoryListFragment == null) {
                _categoryListFragment = CategoryListFragment()
            }
            return _categoryListFragment!!
        }
        set(value) {
            _categoryListFragment = value
        }

    private var _mgmtPageFragment: MgmtPageFragment? = null
    private var mgmtPageFragment: MgmtPageFragment
        get() {
            if (_mgmtPageFragment == null) {
                _mgmtPageFragment = MgmtPageFragment()
            }
            return _mgmtPageFragment!!
        }
        set(value) {
            _mgmtPageFragment = value
        }

    private lateinit var rootLayout: View
    private lateinit var mgmtContainer: View
    private lateinit var categoryEdgeShadow: View
    private lateinit var movementGate: View
    private var categoryShadowWidth = 0

    private val mgmtWidthRatio = 3f / 4f
    private var mgmtPanelWidth = 0

    private val viewModel: MainViewModel by lazy {
        ViewModelProvider(this)[MainViewModel::class.java]
    }

    private var revealAnimation: ObjectAnimator? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        this.enableEdgeToEdge()
        setContentView(R.layout.activity_main)
        rootLayout = findViewById(R.id.main_root)
        mgmtContainer = findViewById(R.id.mgmt_container)
        categoryEdgeShadow = findViewById(R.id.category_edge_shadow)
        movementGate = findViewById(R.id.movement_gate)
        categoryShadowWidth =
            (CATEGORY_SHADOW_WIDTH_DP * resources.displayMetrics.density).toInt()
        categoryEdgeShadow.layoutParams =
            categoryEdgeShadow.layoutParams.apply { width = categoryShadowWidth }

        window.setFlags(
            WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS
        )

        onBackPressedDispatcher.addCallback(this, OnFragmentBackPressed(true))

        for (f in supportFragmentManager.fragments) {
            when (f) {
                is CategoryListFragment -> categoryListFragment = f
                is MgmtPageFragment -> mgmtPageFragment = f
            }
        }

        if (!mgmtPageFragment.isAdded) {
            supportFragmentManager.beginTransaction()
                .add(R.id.mgmt_container, mgmtPageFragment, null)
                .commit()
        }
        if (!categoryListFragment.isAdded) {
            startFragment(categoryListFragment)
        }

        rootLayout.post {
            updateMgmtContainerWidth()
            onMgmtRevealProgress(categoryListFragment.view?.translationX ?: 0f)
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
            val categories: ArrayList<Category> = loadCategories(this)
            viewModel.setCategoriesList(categories)
        } else {
            Log.v(TAG, "onCreate: restore from savedInstanceState")
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
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

    private fun startFragment(fragment: Fragment) {
        supportFragmentManager.beginTransaction()
            .add(R.id.main_container, fragment, null)
            .addToBackStack(fragment.javaClass.simpleName)
            .commit()
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

    internal fun getMgmtPanelWidth(): Int = mgmtPanelWidth

    private fun categoryLeftX(): Float =
        categoryListFragment.view?.translationX ?: 0f

    internal fun isMgmtRevealed(): Boolean =
        mgmtPanelWidth > 0 &&
            categoryLeftX() >= mgmtPanelWidth - REVEAL_EPS

    fun openMgmtReveal() {
        animateRevealTo(open = true)
    }

    fun closeMgmtReveal() {
        animateRevealTo(open = false)
    }

    fun toggleMgmtReveal() {
        if (categoryLeftX() > REVEAL_EPS) {
            closeMgmtReveal()
        } else {
            openMgmtReveal()
        }
    }

    internal fun onMgmtDragStarted() {
        revealAnimation?.let {
            revealAnimation = null
            it.cancel()
        }
    }

    internal fun onMgmtRevealProgress(categoryListLeft: Float) {
        val panel = mgmtPanelWidth
        if (panel <= 0) return
        val ratio = (categoryListLeft / panel).coerceIn(0f, 1f)

        categoryListFragment.setContentAlpha(ratio)
        mgmtPageFragment.view?.let { v ->
            v.pivotX = v.width / 2f
            v.pivotY = v.height / 2f
            val scale = MGMT_SCALE_MIN + (1f - MGMT_SCALE_MIN) * ratio
            v.scaleX = scale
            v.scaleY = scale
        }

        categoryEdgeShadow.isVisible = categoryListLeft > 0f
        categoryEdgeShadow.translationX = categoryListLeft - categoryShadowWidth

        val moving = ratio > 0f && ratio < 1f
        movementGate.isVisible = moving
    }

    private fun animateRevealTo(open: Boolean) {
        if (mgmtPanelWidth <= 0) return
        val targetX = if (open) mgmtPanelWidth.toFloat() else 0f
        val view = categoryListFragment.view ?: return
        if (view.translationX == targetX) {
            revealAnimation = null
            onMgmtRevealProgress(targetX)
            return
        }
        revealAnimation?.cancel()
        revealAnimation = null

        val anim = ObjectAnimator.ofFloat(
            view, PROP_TRANSLATION_X, view.translationX, targetX
        ).apply {
            duration = REVEAL_DURATION
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

    internal inner class OnFragmentBackPressed(enabled: Boolean) : OnBackPressedCallback(enabled) {
        override fun handleOnBackPressed() {
            if (categoryLeftX() > REVEAL_EPS) {
                closeMgmtReveal()
                return
            }
            val fragment = supportFragmentManager.findFragmentById(R.id.main_container)
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
        private const val REVEAL_DURATION = 200L
        // px tolerance used to treat the reveal as fully open / fully closed
        private const val REVEAL_EPS = 1f
        // mgmt page scale when the reveal just starts (fully open scale = 1)
        private const val MGMT_SCALE_MIN = 0.97f
        private const val CATEGORY_SHADOW_WIDTH_DP = 8
    }
}
