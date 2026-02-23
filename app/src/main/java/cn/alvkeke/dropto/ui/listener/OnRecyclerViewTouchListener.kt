package cn.alvkeke.dropto.ui.listener

import android.annotation.SuppressLint
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.MotionEvent
import android.view.View
import android.view.View.OnTouchListener
import android.view.ViewConfiguration
import androidx.recyclerview.widget.RecyclerView
import kotlin.math.abs

open class OnRecyclerViewTouchListener(val context: Context) : OnTouchListener {


    private val handler = Handler(Looper.getMainLooper())

    private val longClickTimeout = ViewConfiguration.getLongPressTimeout().toLong()
    private val touchSlop = ViewConfiguration.get(context).scaledTouchSlop

    private enum class GestureState {
        IDLE,
        STARTED,
        DRAG_X,
        DRAG_Y,
        LONG_PRESS_HOLDING,   // long click is triggered, but not yet released
    }

    private var timeDown: Long = 0  // for move speed
    private var downRawX = 0f       // for move distance
    private var downRawY = 0f       // for move distance
    private lateinit var longPressParentView: View
    private var longPressItemView: View? = null

    private var lastHoldItemIndex: Int = -1
    private var lastSlideOnStatus: Boolean = false
    private var gestureState = GestureState.IDLE


    @SuppressLint("ClickableViewAccessibility")
    override fun onTouch(view: View, motionEvent: MotionEvent): Boolean {
        val recyclerView = view as RecyclerView
        val itemView: View?
        var deltaRawX: Float
        var deltaRawY: Float
        when (motionEvent.action) {
            MotionEvent.ACTION_DOWN -> {
                downRawX = motionEvent.rawX
                downRawY = motionEvent.rawY
                timeDown = System.currentTimeMillis()
                longPressParentView = view
                longPressItemView = recyclerView.findChildViewUnder(motionEvent.x, motionEvent.y)
                if (longPressItemView != null) {
                    lastHoldItemIndex = recyclerView.getChildLayoutPosition(longPressItemView!!)
                    Log.v(this.toString(), "lastHoldSlideView set to $lastHoldItemIndex")
                    handler.postDelayed(longPressRunnable, longClickTimeout)
                }
                gestureState = GestureState.STARTED
                lastSlideOnStatus = false
            }

            MotionEvent.ACTION_MOVE -> {
                deltaRawX = motionEvent.rawX - downRawX
                deltaRawY = motionEvent.rawY - downRawY

                when (gestureState) {
                    GestureState.STARTED -> {
                        if (abs(deltaRawY) > touchSlop) {
                            gestureState = GestureState.DRAG_Y     // vertical move have higher priority
                            handler.removeCallbacks(longPressRunnable)
                            return onDraggingVertical(view, motionEvent, deltaRawY)
                        } else if (abs(deltaRawX) > touchSlop) {
                            gestureState = GestureState.DRAG_X
                            handler.removeCallbacks(longPressRunnable)
                            return onDraggingHorizontal(view, motionEvent, deltaRawX)
                        }
                    }
                    GestureState.DRAG_X -> {
                        return onDraggingHorizontal(view, motionEvent, deltaRawX)
                    }
                    GestureState.DRAG_Y -> {
                        return onDraggingVertical(view, motionEvent, deltaRawY)
                    }
                    GestureState.LONG_PRESS_HOLDING -> {
                        itemView = recyclerView.findChildViewUnder(motionEvent.x, motionEvent.y)
                            ?: return lastSlideOnStatus
                        val index = recyclerView.getChildLayoutPosition(itemView)
                        if (index != lastHoldItemIndex) {
                            lastHoldItemIndex = index
                            Log.v(this.toString(), "lastHoldSlideView set to $lastHoldItemIndex")
                            lastSlideOnStatus = onItemLongClickSlideOn(itemView, index)
                        }
                        return lastSlideOnStatus
                    }
                    GestureState.IDLE -> {
                        return false        // this should not happen, but just in case
                    }
                }
            }

            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                handler.removeCallbacks(longPressRunnable)
                itemView = recyclerView.findChildViewUnder(motionEvent.x, motionEvent.y)
                val state = gestureState
                gestureState = GestureState.IDLE

                when (state) {
                    GestureState.IDLE -> return false
                    GestureState.STARTED -> {
                        // single click
                        if (itemView != null) {
                            if (handleItemClick(recyclerView, itemView, motionEvent)) {
                                return true
                            }
                            if (handleItemClick(recyclerView, itemView, null)) {
                                return true
                            }
                        }
                        if (onClick(view, motionEvent)) {
                            return true
                        }
                    }
                    GestureState.DRAG_X, -> {
                        deltaRawX = motionEvent.rawX - downRawX
                        val currentTime = System.currentTimeMillis()
                        val speed = deltaRawX / (currentTime - timeDown)
                        if (onDragHorizontalEnd(view, motionEvent, deltaRawX, speed)) {
                            return true
                        }
                    }
                    GestureState.DRAG_Y -> {
                        deltaRawY = motionEvent.rawY - downRawX
                        val currentTime = System.currentTimeMillis()
                        val speed = deltaRawY / (currentTime - timeDown)
                        if (onDragVerticalEnd(view, motionEvent, deltaRawY, speed)) {
                            return true
                        }
                    }
                    GestureState.LONG_PRESS_HOLDING -> {
                        if (itemView == null) {
                            return true
                        }
                        val index = recyclerView.getChildLayoutPosition(itemView)
                        val ret = onItemLongClickRelease(itemView, index)
                        if (ret) return true
                    }
                }

            }
        }
        return false
    }

    open fun onDragHorizontalEnd(v: View, e: MotionEvent, delta: Float, speed: Float): Boolean {
        return false
    }

    open fun onDraggingHorizontal(v: View, e: MotionEvent, delta: Float): Boolean {
        return false
    }

    open fun onDragVerticalEnd(v: View, e: MotionEvent, delta: Float, speed: Float): Boolean {
        return false
    }

    open fun onDraggingVertical(v: View, e: MotionEvent, delta: Float): Boolean {
        return false
    }

    open fun onClick(v: View, e: MotionEvent): Boolean {
        return false
    }

    private val longPressRunnable: Runnable = object : Runnable {
        override fun run() {
            if (gestureState != GestureState.STARTED) {
                // if other gesture is already triggered, do not trigger long click
                return
            }
            gestureState = GestureState.LONG_PRESS_HOLDING
            if (longPressItemView != null) {
                lastSlideOnStatus = true
                if (handleItemLongClick(
                        longPressParentView,
                        longPressItemView!!,
                    downRawX,
                    downRawY
                )) {
                    return
                }
            }
            onLongClick(longPressParentView)
        }
    }

    open fun onLongClick(v: View): Boolean {
        return false
    }

    private fun handleItemLongClick(parent: View, itemView: View, rawX: Float, rawY: Float): Boolean {
        val recyclerView = parent as RecyclerView
        val index = recyclerView.getChildLayoutPosition(itemView)
        assert(index != -1)
        return onItemLongClick(itemView, index, rawX, rawY)
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

    open fun onItemLongClick(v: View, index: Int, rawX: Float, rawY: Float): Boolean {
        return false
    }

    open fun onItemLongClickSlideOn(v: View, index: Int): Boolean {
        return false
    }

    open fun onItemLongClickRelease(itemView: View, index: Int): Boolean {
        return false
    }

}
