package cn.alvkeke.dropto.ui.comonent

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.util.AttributeSet
import android.view.Menu
import android.view.MenuItem
import android.view.MotionEvent
import android.view.View
import android.widget.PopupWindow
import androidx.core.content.ContextCompat
import androidx.core.view.get
import androidx.core.view.size
import cn.alvkeke.dropto.R

@SuppressLint("ViewConstructor")
class PopupMenu @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0,
) : PopupWindow(context, attrs, defStyleAttr) {

    private val menuWidth: Float by lazy {
        context.resources.displayMetrics.widthPixels / 2f
    }
    private val menuItemHeight: Float by lazy {
        menuWidth / 4
    }
    private val menuHeight: Float by lazy {
        menu.size * menuItemHeight
    }

    private lateinit var menu: Menu
    fun setMenu(menu: Menu): PopupMenu {
        this.menu = menu
        width = (menuWidth + SHADOW_PADDING * 2).toInt()
        height = (menuHeight + SHADOW_PADDING * 2).toInt()
        menuView.requestLayout()
        return this
    }

    private val backgroundPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = ContextCompat.getColor(context, R.color.popup_menu_background)
        setShadowLayer(
            3f, 0f, 0f,
            ContextCompat.getColor(context, R.color.popup_menu_shadow)
        )
    }

    private val itemTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = ContextCompat.getColor(context, R.color.popup_menu_text)
        textSize = 16 *
                context.resources.configuration.fontScale *
                context.resources.displayMetrics.density
    }

    private val menuView = object : View(context) {
        override fun onDraw(canvas: Canvas) {
            super.onDraw(canvas)
            canvas.translate(SHADOW_PADDING.toFloat(), SHADOW_PADDING.toFloat())
            val radius = ROUND_CONNER_RADIUS.toFloat()

            canvas.drawRoundRect(
                0F, 0F,
                menuWidth, menuHeight,
                radius, radius, backgroundPaint
            )

            for (i in 0 until menu.size) {
                val menuItem = menu[i]
                val text = menuItem.title.toString()
                val x = (menuWidth) / 4
                val y = (i + 0.5f) * menuItemHeight + (itemTextPaint.textSize / 2 - itemTextPaint.descent())
                canvas.drawText(text, x, y, itemTextPaint)
            }
        }

    }

    init {
        contentView = menuView
        isFocusable = true
        isOutsideTouchable = true
    }

    interface MenuListener {
        fun onMenuItemClick(menuItem: MenuItem)
    }
    private var menuListener: MenuListener? = null

    fun setMenuListener(listener: MenuListener): PopupMenu {
        this.menuListener = listener
        return this
    }

    companion object {
        const val ROUND_CONNER_RADIUS = 16
        const val SHADOW_PADDING = 5
    }
}