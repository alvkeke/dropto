package cn.alvkeke.dropto.ui.comonent

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.util.AttributeSet
import android.view.Menu
import android.view.MenuItem
import android.view.MotionEvent
import android.view.View
import android.widget.PopupWindow
import androidx.core.content.ContextCompat
import androidx.core.graphics.withSave
import androidx.core.view.get
import androidx.core.view.size
import cn.alvkeke.dropto.R
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

@SuppressLint("ViewConstructor")
class PopupMenu @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0,
) : PopupWindow(context, attrs, defStyleAttr) {

    private lateinit var menu: Menu

    fun setMenu(menu: Menu) {
        this.menu = menu
        resetMenuSize()
    }

    fun setMenuItemVisible(itemId: Int, visible: Boolean) {
        val menuItem = menu.findItem(itemId) ?: return
        menuItem.isVisible = visible
        resetMenuSize()
    }

    private val menuWidth: Float by lazy {
        context.resources.displayMetrics.widthPixels / 2f
    }
    private val menuItemHeight: Float by lazy {
        menuWidth / 4
    }

    private val menuHeight: Float
        get() {
            var visibleItems = 0
            for (i in 0 until menu.size) {
                if (menu[i].isVisible) {
                    visibleItems++
                }
            }
            return visibleItems * menuItemHeight
        }

    private fun resetMenuSize() {
        this.width = (menuWidth + SHADOW_PADDING * 2).toInt()
        this.height = (menuHeight + SHADOW_PADDING * 2).toInt()
        menuView.requestLayout()
    }

    private fun getVisibleMenuItem(index: Int): MenuItem? {
        if (index < 0) return null
        var visibleIndex = -1
        for (i in 0 until menu.size) {
            if (menu[i].isVisible) {
                visibleIndex++
            }
            if (visibleIndex == index) {
                return menu[i]
            }
        }
        return null
    }

    private val menuView = object : View(context) {

        private val backgroundPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = ContextCompat.getColor(context, R.color.popup_menu_background)
            setShadowLayer(
                3f, 0f, 0f,
                ContextCompat.getColor(context, R.color.popup_menu_shadow)
            )
        }
        private val backgroundRect = RectF()
        private val backgroundPath = Path()

        private val itemTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = ContextCompat.getColor(context, R.color.popup_menu_text)
            textSize = 16 *
                    context.resources.configuration.fontScale *
                    context.resources.displayMetrics.density
        }

        private val highlightPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = ContextCompat.getColor(context, R.color.popup_menu_item_selected)
        }

        override fun onDraw(canvas: Canvas) {
            super.onDraw(canvas)
            canvas.translate(SHADOW_PADDING.toFloat(), SHADOW_PADDING.toFloat())
            val radius = ROUND_CONNER_RADIUS.toFloat()

            backgroundRect.set(
                0f, 0f,
                menuWidth, menuHeight
            )
            canvas.drawRoundRect(backgroundRect, radius, radius, backgroundPaint)

            if (downIndex in 0 until menu.size) {
                val top = downIndex * menuItemHeight
                val bottom = top + menuItemHeight
                highlightPaint.alpha = (animateRatio.coerceIn(0f, 1f) * 255).toInt()
                backgroundPath.addRoundRect(
                    backgroundRect,
                    radius,
                    radius,
                    Path.Direction.CW
                )
                if (animationRunning) {
                    canvas.withSave {
                        canvas.clipPath(backgroundPath)
                        canvas.clipRect(0f, top, menuWidth, bottom) // item area
                        val cx = menuWidth / 2
                        val cy = (top + bottom) / 2
                        val maxRadius = menuWidth * 0.6f
                        val currentRadius = maxRadius * animateRatio.coerceIn(0f, 1f)
                        canvas.drawCircle(cx, cy, currentRadius, highlightPaint)
                    }
                } else {
                    canvas.withSave {
                        canvas.clipPath(backgroundPath)
                        canvas.drawRect(
                            0f, top,
                            menuWidth, bottom,
                            highlightPaint
                        )
                    }
                }
            }

            var drawIdx = 0
            for (i in 0 until menu.size) {
                val menuItem = menu[i]
                if (!menuItem.isVisible) continue

                val text = menuItem.title.toString()
                val x = (menuWidth) / 4
                val y = (drawIdx++ + 0.5f) * menuItemHeight + (itemTextPaint.textSize / 2 - itemTextPaint.descent())
                canvas.drawText(text, x, y, itemTextPaint)
            }
        }

        private var _animationScope: CoroutineScope? = null
        private val animationScope: CoroutineScope
            get() {
                if (_animationScope == null || _animationScope?.isActive != true) {
                    _animationScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
                }
                return _animationScope!!
            }

        private var animateJob: Job? = null
        @Volatile private var animateRatio = 0f
        @Volatile private var animationRunning = false
        @Volatile private var animationIsEnter = true
        private fun animateStart(
            isEnter: Boolean = true
        ) {
            if (animationRunning && isEnter == animationIsEnter) return

            val totalFrames = ANIMATION_DURATION / ANIMATION_INTERVAL
            val step: Float
            val isEnd: (Float) -> Boolean
            when (isEnter) {
                true -> {
                    step = 1f / totalFrames
                    isEnd = { ratio -> ratio >= 1f }
                }
                false -> {
                    step = -1f / totalFrames
                    isEnd = { ratio -> ratio <= 0f }
                }
            }

            if (animateJob?.isActive != true) {
                animateRatio = if (isEnter) { 0f } else { 1f }
            } else {
                animateJob?.cancel()
            }
            animationIsEnter = isEnter
            animateJob = animationScope.launch {
                animationRunning = true
                while(true) {
                    animateRatio = (animateRatio + step)
                        .coerceIn(0f, 1f)
                    invalidate()
                    if (isEnd(animateRatio)) break
                    kotlinx.coroutines.delay(ANIMATION_INTERVAL)
                }
                animationRunning = false
            }
        }

        private fun getTouchedVisibleMenuItemIndex(x: Float, y: Float): Int {
            if (x !in 0f..menuWidth || y < 0 || y > menuHeight) {
                return -1
            }
            return (y / menuItemHeight).toInt()
        }

        @Volatile private var downIndex = -1
        @Volatile private var outCount = 0
        @SuppressLint("ClickableViewAccessibility")
        override fun onTouchEvent(event: MotionEvent): Boolean {
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    downIndex = getTouchedVisibleMenuItemIndex(
                        event.x - SHADOW_PADDING,
                        event.y - SHADOW_PADDING
                    )
                    outCount = 0
                    animateStart()
                    return true
                }
                MotionEvent.ACTION_UP -> {
                    val index = getTouchedVisibleMenuItemIndex(
                        event.x - SHADOW_PADDING,
                        event.y - SHADOW_PADDING
                    )
                    if (index == downIndex &&
                        outCount == 0 &&
                        index in 0 until menu.size) {
                        val menuItem = getVisibleMenuItem(index) ?: return true
                        animateStart(false)
                        menuListener?.onMenuItemClick(menuItem)
                        dismiss()
                    }
                    return true
                }
                MotionEvent.ACTION_MOVE -> {
                    val index = getTouchedVisibleMenuItemIndex(
                        event.x - SHADOW_PADDING,
                        event.y - SHADOW_PADDING
                    )
                    if (index != downIndex) {
                        if (outCount < 10) outCount++           // prevent overflow
                        if (outCount == 1) animateStart(false)    // run only once
                    }
                    return true
                }
            }
            return super.onTouchEvent(event)
        }

        fun resetAnimations() {
            animationScope.cancel()
            animateRatio = 0f
            animationRunning = false
            downIndex = -1
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

    override fun dismiss() {
        super.dismiss()
        menuView.resetAnimations()
    }

    companion object {
        const val ROUND_CONNER_RADIUS = 16
        const val SHADOW_PADDING = 5

        const val ANIMATION_DURATION = 100L
        const val ANIMATION_INTERVAL = 5L
    }
}