package cn.alvkeke.dropto.ui.component

import android.content.Context
import android.util.AttributeSet
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.appcompat.R as AppCompatR
import androidx.appcompat.widget.ActionMenuView
import cn.alvkeke.dropto.R
import com.google.android.material.appbar.MaterialToolbar
import androidx.core.view.isVisible

class CapsuleToolbar @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = AppCompatR.attr.toolbarStyle,
) : MaterialToolbar(context, attrs, defStyleAttr) {

    private var menuCapsule: ActionMenuView? = null
    private var menuCapsuleShown = false

    private val capsuleElevation = dpToPx(CAPSULE_ELEVATION_DP).toFloat()

    init {

        setOnHierarchyChangeListener(object : OnHierarchyChangeListener {
            override fun onChildViewAdded(parent: View?, child: View?) = styleChild(child)

            override fun onChildViewRemoved(parent: View?, child: View?) = Unit
        })
        // the Toolbar constructor adds the title/menu views (app:title, app:menu) before this listener
        // exists, so those children have to be picked up once here
        for (i in 0 until childCount) styleChild(getChildAt(i))
    }

    private fun styleChild(child: View?) {
        when (child) {
            is ActionMenuView -> styleAsMenuCapsule(child)
            is TextView -> styleAsTitleCapsule(child)
            else -> Unit
        }
    }

    override fun onLayout(changed: Boolean, left: Int, top: Int, right: Int, bottom: Int) {
        super.onLayout(changed, left, top, right, bottom)
        // menu buttons come and go (selection mode, overflow), the capsule follows them
        refreshMenuCapsule()
    }

    private fun styleAsMenuCapsule(menu: ActionMenuView) {
        menu.applyCapsule()
        menu.elevation = capsuleElevation
        val pad = dpToPx(MENU_CAPSULE_PADDING_DP)
        menu.setPadding(pad, menu.paddingTop, pad, menu.paddingBottom)
        for (i in 0 until menu.childCount) styleAsMenuButton(menu.getChildAt(i))
        // a menu button can be added later (an action moving into the overflow button)
        menu.setOnHierarchyChangeListener(object : OnHierarchyChangeListener {
            override fun onChildViewAdded(parent: View?, child: View?) {
                child?.let { styleAsMenuButton(it) }
            }

            override fun onChildViewRemoved(parent: View?, child: View?) = Unit
        })
        menuCapsule = menu
        menuCapsuleShown = true
        refreshMenuCapsule()
    }

    private fun styleAsMenuButton(button: View) {
        button.applyCapsuleSize()
        val margin = dpToPx(MENU_BUTTON_MARGIN_DP)
        (button.layoutParams as? MarginLayoutParams)?.let {
            if (it.marginStart == margin) return
            it.marginStart = margin
            button.requestLayout()
        }
    }

    private fun styleAsTitleCapsule(title: TextView) {
        title.applyCapsule()
        title.elevation = capsuleElevation
        title.gravity = Gravity.CENTER_VERTICAL
        val padH = dpToPx(TITLE_CAPSULE_PADDING_DP)
        title.setPadding(padH, 0, padH, 0)
    }

    private fun refreshMenuCapsule() {
        val menu = menuCapsule ?: return
        val needed = (0 until menu.childCount).any { menu.getChildAt(it).isVisible }
        if (needed == menuCapsuleShown) return
        menuCapsuleShown = needed
        menu.setBackgroundResource(if (needed) R.drawable.bg_solid_control else 0)
        menu.elevation = if (needed) capsuleElevation else 0f
    }

    private fun dpToPx(dp: Int): Int = (dp * resources.displayMetrics.density).toInt()

    private companion object {
        const val MENU_CAPSULE_PADDING_DP = 4
        const val MENU_BUTTON_MARGIN_DP = 8
        const val TITLE_CAPSULE_PADDING_DP = 12
        const val CAPSULE_ELEVATION_DP = 8
    }
}
