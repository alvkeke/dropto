package cn.alvkeke.dropto.ui.comonent

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.util.AttributeSet
import android.util.Log
import android.view.Menu
import android.view.MenuItem
import android.view.MotionEvent
import android.view.View
import android.widget.PopupWindow
import androidx.core.content.ContextCompat
import androidx.core.view.get
import androidx.core.view.size
import cn.alvkeke.dropto.R
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

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

    private val highlightPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = ContextCompat.getColor(context, R.color.popup_menu_item_selected)
    }

    private val menuView = object : View(context) {

        private var downIndex = -1
        private var animaRatio = 0f

        override fun onDraw(canvas: Canvas) {
            super.onDraw(canvas)
            canvas.translate(SHADOW_PADDING.toFloat(), SHADOW_PADDING.toFloat())
            val radius = ROUND_CONNER_RADIUS.toFloat()

            canvas.drawRoundRect(
                0F, 0F,
                menuWidth, menuHeight,
                radius, radius, backgroundPaint
            )

            if (downIndex in 0 until menu.size) {
                val top = downIndex * menuItemHeight
                val bottom = top + menuItemHeight
                highlightPaint.alpha = (animaRatio.coerceIn(0f, 1f) * 255).toInt()
                canvas.drawRoundRect(
                    0f, top,
                    menuWidth, bottom,
                    radius, radius, highlightPaint
                )
            }

            for (i in 0 until menu.size) {
                val menuItem = menu[i]
                val text = menuItem.title.toString()
                val x = (menuWidth) / 4
                val y = (i + 0.5f) * menuItemHeight + (itemTextPaint.textSize / 2 - itemTextPaint.descent())
                canvas.drawText(text, x, y, itemTextPaint)
            }
        }

        private fun getTouchedMenuItemIndex(x: Float, y: Float): Int {
            if (x !in 0f..menuWidth || y < 0 || y > menuHeight) {
                return -1
            }
            return (y / menuItemHeight).toInt()
        }

        private var animateEnterJob: Job? = null
        private var startTime = 0L
        private var animationIn = true
        private val animationInterval = 200
        private fun animateEnterStart() {
            animateEnterJob?.cancel()
            animationIn = true
            startTime = System.currentTimeMillis()
            animateEnterJob = CoroutineScope(Dispatchers.Main).launch {
                while(true) {
                    val currentTime = System.currentTimeMillis()
                    val diff = currentTime - startTime
                    animaRatio = diff / animationInterval.toFloat()
                    invalidate()
                    if (animaRatio >= 1f) break
                    kotlinx.coroutines.delay(15L)
                }
            }
        }

        @Volatile private var animateExitJob: Job? = null
        private fun animateExitStart() {
            if (animateExitJob?.isActive == true) return
            Log.e(TAG, "animate out start")
            animateExitJob = CoroutineScope(Dispatchers.Main).launch {
                animateEnterJob?.join()

                animationIn = false
                startTime = System.currentTimeMillis()
                while(true) {
                    val currentTime = System.currentTimeMillis()
                    val diff = currentTime - startTime
                    animaRatio = (1f - diff / animationInterval.toFloat())
                            .coerceAtLeast(0f)
                    invalidate()
                    if (diff >= animationInterval) break
                    kotlinx.coroutines.delay(15L)
                }
                animateExitJob = null
            }
        }

        @Volatile private var outCount = 0
        @SuppressLint("ClickableViewAccessibility")
        override fun onTouchEvent(event: MotionEvent): Boolean {
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    downIndex = getTouchedMenuItemIndex(
                        event.x - SHADOW_PADDING,
                        event.y - SHADOW_PADDING
                    )
                    outCount = 0
                    animateEnterStart()
                    return true
                }
                MotionEvent.ACTION_UP -> {
                    val index = getTouchedMenuItemIndex(
                        event.x - SHADOW_PADDING,
                        event.y - SHADOW_PADDING
                    )
                    if (index == downIndex &&
                        outCount == 0 &&
                        index in 0 until menu.size) {
                        val menuItem = menu[index]
                        animateExitStart()
                        menuListener?.onMenuItemClick(menuItem)
                        dismiss()
                    }
                    return true
                }
                MotionEvent.ACTION_MOVE -> {
                    val index = getTouchedMenuItemIndex(
                        event.x - SHADOW_PADDING,
                        event.y - SHADOW_PADDING
                    )
                    if (index != downIndex) {
                        if (outCount < 10) outCount++           // prevent overflow
                        if (outCount == 1) animateExitStart()    // run only once
                    }
                    return true
                }
            }
            return super.onTouchEvent(event)
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