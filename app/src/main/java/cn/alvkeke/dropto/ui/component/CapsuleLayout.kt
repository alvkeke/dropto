package cn.alvkeke.dropto.ui.component

import android.content.Context
import android.util.AttributeSet
import android.view.View
import androidx.constraintlayout.widget.ConstraintLayout
import cn.alvkeke.dropto.R

open class CapsuleLayout @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0,
) : ConstraintLayout(context, attrs, defStyleAttr) {

    init {
        if (background == null) setBackgroundResource(R.drawable.bg_solid_control)
        clipToOutline = true
        minimumHeight = resources.getDimensionPixelSize(R.dimen.capsule_size)
    }
}


fun View.applyCapsule(circle: Boolean = false) {
    setBackgroundResource(R.drawable.bg_solid_control)
    val size = resources.getDimensionPixelSize(R.dimen.capsule_size)
    layoutParams?.let {
        if (circle) {
            it.width = size
        }
        it.height = size
    }
    requestLayout()
}


fun View.applyCapsuleSize() {
    val size = resources.getDimensionPixelSize(R.dimen.capsule_size)
    layoutParams?.let {
        it.width = size
        it.height = size
    }
    requestLayout()
}
