package cn.alvkeke.dropto.ui.comonent

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.util.AttributeSet
import android.util.Log
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

    private val rect: RectF by lazy {
        RectF(0f, 0f, menuWidth, menuHeight)
    }
    private val path: Path = Path()

    private val menuView = object : View(context) {

        private var _animationScope: CoroutineScope? = null
        private val animationScope: CoroutineScope
            get() {
                if (_animationScope == null || _animationScope?.isActive != true) {
                    _animationScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
                }
                return _animationScope!!
            }

        private var downIndex = -1
        @Volatile private var animateRatio = 0f

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
                highlightPaint.alpha = (animateRatio.coerceIn(0f, 1f) * 255).toInt()
                path.addRoundRect(rect, radius, radius, Path.Direction.CW)
                if (animationIn) {
                    canvas.withSave {
                        canvas.clipPath(path)
                        canvas.clipRect(0f, top, menuWidth, bottom)
                        val cx = menuWidth / 2
                        val cy = (top + bottom) / 2
                        val maxRadius = menuWidth * 0.6f
                        val currentRadius = maxRadius * animateRatio.coerceIn(0f, 1f)
                        canvas.drawCircle(cx, cy, currentRadius, highlightPaint)
                    }
                } else {
                    canvas.withSave {
                        canvas.clipPath(path)
                        canvas.drawRect(
                            0f, top,
                            menuWidth, bottom,
                            highlightPaint
                        )
                    }
                }
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
        @Volatile private var animationIn = true
        private fun animateEnterStart() {
            if (animateEnterJob?.isActive == true) return
            animationIn = true
            val totalFrames = ANIMATION_DURATION / ANIMATION_INTERVAL
            var currentFrame = 0

            animateEnterJob = animationScope.launch {
                while(true) {
                    animateRatio = (currentFrame.toFloat() / totalFrames)
                        .coerceIn(0f, 1f)
                    Log.e(TAG, "animateEnterStart: $animateRatio")
                    invalidate()
                    currentFrame++
                    if (currentFrame > totalFrames) break
                    kotlinx.coroutines.delay(ANIMATION_INTERVAL)
                }
            }
        }

        @Volatile private var animateExitJob: Job? = null
        private fun animateExitStart() {
            if (animateExitJob?.isActive == true) return
            animateExitJob = animationScope.launch {
                animateEnterJob?.join()

                animationIn = false
                val totalFrames = ANIMATION_DURATION / ANIMATION_INTERVAL
                var currentFrame = 0

                while(true) {
                    animateRatio = 1f - (currentFrame.toFloat() / totalFrames)
                    Log.e(TAG, "animateExitStart: $animateRatio")
                    invalidate()
                    currentFrame++
                    if (currentFrame > totalFrames) break
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
                    if (animateExitJob?.isActive == true || animateEnterJob?.isActive == true) {
                        return true
                    }
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

        fun cancelAnimations() {
            animationScope.cancel()
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
        menuView.cancelAnimations()
        super.dismiss()
    }

    companion object {
        private const val TAG = "PopupMenu"
        const val ROUND_CONNER_RADIUS = 16
        const val SHADOW_PADDING = 5

        const val ANIMATION_DURATION = 100L
        const val ANIMATION_INTERVAL = 5L
    }
}