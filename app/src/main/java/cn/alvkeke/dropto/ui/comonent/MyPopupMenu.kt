package cn.alvkeke.dropto.ui.comonent

import android.animation.ValueAnimator
import android.annotation.SuppressLint
import android.content.Context
import android.graphics.drawable.GradientDrawable
import android.util.DisplayMetrics
import android.view.Gravity
import android.view.Menu
import android.view.MenuItem
import android.view.MotionEvent
import android.view.View
import android.view.View.OnTouchListener
import android.widget.LinearLayout
import android.widget.PopupWindow
import android.widget.TextView
import cn.alvkeke.dropto.R
import kotlin.math.roundToInt
import androidx.core.view.size
import androidx.core.view.get


class MyPopupMenu(private val context: Context) : PopupWindow(context) {
    interface OnMenuItemClickListener {
        fun onMenuItemClick(menuItem: MenuItem?, extraData: Any?)
    }

    private var listener: OnMenuItemClickListener? = null
    private var menu: Menu? = null

    fun setListener(listener: OnMenuItemClickListener?): MyPopupMenu {
        this.listener = listener
        return this
    }

    fun setMenu(menu: Menu): MyPopupMenu {
        this.menu = menu
        return this
    }

    private val textViewMargin: Int
        get() {
            val displayMetrics = context.resources.displayMetrics
            return (15f * (displayMetrics.xdpi / DisplayMetrics.DENSITY_DEFAULT)).roundToInt()
        }

    private fun isPointInView(view: View, x: Float, y: Float): Boolean {
        val viewWidth = view.width
        val viewHeight = view.height
        return (x >= 0 && x < viewWidth && y >= 0 && y < viewHeight)
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun setupItemForPopupWindow(item: MenuItem): TextView {
        val textView = TextView(context)
        textView.text = item.title
        val margin = this.textViewMargin
        textView.setPadding(margin, margin, margin, margin)
        val drawable = GradientDrawable()
        drawable.shape = GradientDrawable.RECTANGLE
        drawable.cornerRadius = ROUND_CORNER_RADIUS.toFloat()
        drawable.setColor(context.getColor(R.color.popup_menu_item_selected))
        textView.background = drawable
        textView.background.alpha = 0
        textView.setOnTouchListener(OnItemTouchListener(item))
        return textView
    }

    private inner class OnItemTouchListener(private val item: MenuItem?) : OnTouchListener {
        private var animatorOut: ValueAnimator? = null
        private var canClick = false

        @SuppressLint("ClickableViewAccessibility")
        override fun onTouch(view: View, motionEvent: MotionEvent): Boolean {
            if (animatorOut == null) {
                animatorOut = ValueAnimator()
                animatorOut!!.addUpdateListener { valueAnimator: ValueAnimator ->
                    val alpha = valueAnimator.animatedValue as Int
                    view.background.alpha = alpha
                }
            }
            when (motionEvent.action) {
                MotionEvent.ACTION_DOWN -> {
                    canClick = true
                    val animatorDown = ValueAnimator.ofInt(0, 255)
                    animatorDown.addUpdateListener { valueAnimator: ValueAnimator ->
                        val alpha = valueAnimator.animatedValue as Int
                        view.background.alpha = alpha
                    }
                    animatorDown.duration = 200
                    animatorDown.start()
                    return true
                }

                MotionEvent.ACTION_MOVE -> {
                    if (!isPointInView(view, motionEvent.x, motionEvent.y)) {
                        canClick = false
                        if (!animatorOut!!.isStarted) {
                            val startAlpha = view.background.alpha
                            if (startAlpha != 0) {
                                animatorOut!!.setIntValues(startAlpha, 0)
                                animatorOut!!.duration = 200
                                animatorOut!!.start()
                            }
                        }
                        return true
                    }
                }

                MotionEvent.ACTION_UP -> {
                    if (canClick) {
                        if (listener != null) {
                            listener!!.onMenuItemClick(item, `object`)
                        }
                        dismiss()
                        return true
                    }
                }
            }
            return false
        }
    }

    private var `object`: Any? = null

    fun setData(o: Any?): MyPopupMenu {
        this.`object` = o
        return this
    }

    fun show(anchorView: View?, ignore: Int, y: Int) {
        val linearLayout = LinearLayout(context)
        linearLayout.orientation = LinearLayout.VERTICAL
        val screenWidth = context.resources.displayMetrics.widthPixels
        linearLayout.minimumWidth = screenWidth / 2

        for (i in 0..<menu!!.size) {
            val item = menu!![i]
            val textView = setupItemForPopupWindow(item)
            linearLayout.addView(textView)
        }
        val drawable = GradientDrawable()
        drawable.shape = GradientDrawable.RECTANGLE
        drawable.cornerRadius = ROUND_CORNER_RADIUS.toFloat()
        drawable.setColor(context.getColor(R.color.popup_menu_background))

        setBackgroundDrawable(drawable)
        contentView = linearLayout
        isFocusable = true
        isOutsideTouchable = false

        linearLayout.measure(View.MeasureSpec.UNSPECIFIED, View.MeasureSpec.UNSPECIFIED)
        val showX = screenWidth / 6
        val showY = y - linearLayout.measuredHeight / 2
        showAtLocation(anchorView, Gravity.NO_GRAVITY, showX, showY)
    }

    companion object {
        private const val ROUND_CORNER_RADIUS = 18
    }
}
