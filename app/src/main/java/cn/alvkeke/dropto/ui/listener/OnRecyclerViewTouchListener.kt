package cn.alvkeke.dropto.ui.listener

import android.annotation.SuppressLint
import android.os.Handler
import android.view.MotionEvent
import android.view.View
import android.view.View.OnTouchListener
import androidx.recyclerview.widget.RecyclerView
import kotlin.math.abs

open class OnRecyclerViewTouchListener : OnTouchListener {
    private var timeDown: Long = 0
    private var downRawX = 0f
    private var downRawY = 0f
    private var isSliding = false
    private var isSlidable = false
    private var isLongClickHold = false
    private var isShortClick = false

    private val handler = Handler()
    private lateinit var longPressView: View
    private var longPressItemView: View? = null

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouch(view: View, motionEvent: MotionEvent): Boolean {
        val recyclerView = view as RecyclerView
        val itemView: View?
        val deltaRawX: Float
        val deltaRawY: Float
        when (motionEvent.action) {
            MotionEvent.ACTION_DOWN -> {
                downRawX = motionEvent.rawX
                downRawY = motionEvent.rawY
                timeDown = System.currentTimeMillis()
                longPressView = view
                longPressItemView =
                    recyclerView.findChildViewUnder(motionEvent.x, motionEvent.y)
                handler.postDelayed(longPressRunnable, TIME_THRESHOLD_LONG_CLICK)
                isShortClick = true
                isSlidable = true
            }

            MotionEvent.ACTION_MOVE -> {
                deltaRawX = motionEvent.rawX - downRawX
                deltaRawY = motionEvent.rawY - downRawY
                if (abs(deltaRawX) > THRESHOLD_NO_MOVED || abs(deltaRawY) > THRESHOLD_NO_MOVED) {
                    handler.removeCallbacks(longPressRunnable)
                    isShortClick = false
                }
                if (isLongClickHold) {
                    return true // block all event
                }

                if (isSliding) {
                    if (onSlideOnGoing(view, motionEvent, deltaRawX, deltaRawY)) {
                        return true
                    }
                }
                if (abs(deltaRawY) > THRESHOLD_NO_MOVED) {
                    isSlidable = false
                }
                if (isSlidable && deltaRawX > THRESHOLD_SLIDE) {
                    isSliding = true
                }
            }

            MotionEvent.ACTION_UP -> {
                handler.removeCallbacks(longPressRunnable)
                if (isLongClickHold) {
                    isLongClickHold = false
                    return true
                }
                if (isShortClick && System.currentTimeMillis() - timeDown < TIME_THRESHOLD_CLICK) {
                    isShortClick = false
                    itemView =
                        recyclerView.findChildViewUnder(motionEvent.x, motionEvent.y)
                    if (itemView != null) {
                        if (handleItemClick(recyclerView, itemView, motionEvent)) {
                            return true
                        }
                        if (handleItemClick(recyclerView, itemView, null)) {
                            return true
                        }
//                        if (handleListItemClick(recyclerView, itemView, false)) return true;
                    }
                    if (onClick(view, motionEvent)) {
                        return true
                    }
                }
                if (isSliding) {
                    deltaRawX = motionEvent.rawX - downRawX
                    deltaRawY = motionEvent.rawY - downRawY
                    isSliding = false
                    if (onSlideEnd(view, motionEvent, deltaRawX, deltaRawY)) {
                        return true
                    }
                }
            }
        }
        return false
    }

    open fun onSlideEnd(v: View, e: MotionEvent, deltaX: Float, deltaY: Float): Boolean {
        return false
    }

    open fun onSlideOnGoing(v: View, e: MotionEvent, deltaX: Float, deltaY: Float): Boolean {
        return false
    }

    fun onClick(ignored: View, ignored1: MotionEvent): Boolean {
        return false
    }

    private val longPressRunnable: Runnable = object : Runnable {
        override fun run() {
            if (longPressItemView != null) {
                if (handleItemLongClick(longPressView, longPressItemView!!)) {
                    isLongClickHold = true
                    return
                }
            }
            if (onLongClick(longPressView)) {
                isLongClickHold = true
            }
        }
    }

    fun onLongClick(ignored: View): Boolean {
        return false
    }

    private fun handleItemLongClick(parent: View, itemView: View): Boolean {
        val recyclerView = parent as RecyclerView
        val index = recyclerView.getChildLayoutPosition(itemView)
        assert(index != -1)
        return onItemLongClick(itemView, index)
    }

    private fun handleItemClick(parent: View, itemView: View, e: MotionEvent?): Boolean {
        val recyclerView = parent as RecyclerView
        val index = recyclerView.getChildLayoutPosition(itemView)
        assert(index != -1)
        return if (e == null) {
            onItemClick(itemView, index)
        } else {
            onItemClickAt(itemView, index, e)
        }
    }

    open fun onItemClick(v: View, index: Int): Boolean {
        return false
    }

    open fun onItemClickAt(v: View, index: Int, event: MotionEvent): Boolean {
        return false
    }

    open fun onItemLongClick(v: View, index: Int): Boolean {
        return false
    }

    companion object {
        private const val TIME_THRESHOLD_CLICK: Long = 200
        private const val TIME_THRESHOLD_LONG_CLICK: Long = 500
        private const val THRESHOLD_SLIDE = 45
        private const val THRESHOLD_NO_MOVED = 20
    }
}
