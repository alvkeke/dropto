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


private const val RELEASE_SPEED_WINDOW_MS = 60L
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

    private var downRawX = 0f       // for move distance
    private var downRawY = 0f       // for move distance
    private val moveTimes = ArrayList<Long>()
    private val moveXs = ArrayList<Float>()
    private val moveYs = ArrayList<Float>()
    private lateinit var longPressParentView: View
    private var longPressItemView: View? = null

    private var lastHoldItemIndex: Int = -1
    private var lastSlideOnStatus: Boolean = false
    private var gestureState = GestureState.IDLE


    private fun clearMoveSample() {
        moveTimes.clear()
        moveXs.clear()
        moveYs.clear()
    }
    private fun addMoveSample(rawX: Float, rawY: Float) {
        val now = System.currentTimeMillis()
        moveTimes.add(now)
        moveXs.add(rawX)
        moveYs.add(rawY)
        while (moveTimes.size > 1 &&
            now - moveTimes[0] > RELEASE_SPEED_WINDOW_MS
        ) {
            moveTimes.removeAt(0)
            moveXs.removeAt(0)
            moveYs.removeAt(0)
        }
    }

    private fun recentReleaseVelocity(): Pair<Float, Float> {
        val n = moveTimes.size
        if (n < 2) return 0f to 0f
        val last = n - 1
        val tEnd = moveTimes[last]
        val xEnd = moveXs[last]
        val yEnd = moveYs[last]
        var start = 0
        while (start < last &&
            tEnd - moveTimes[start] > RELEASE_SPEED_WINDOW_MS
        ) {
            start++
        }
        val dt = tEnd - moveTimes[start]
        if (dt <= 0L) return 0f to 0f
        return ((xEnd - moveXs[start]) / dt) to
            ((yEnd - moveYs[start]) / dt)
    }

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
                clearMoveSample()
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
                addMoveSample(motionEvent.rawX, motionEvent.rawY)
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
            MotionEvent.ACTION_UP -> {
                addMoveSample(motionEvent.rawX, motionEvent.rawY)
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
                        val deltaRawX = motionEvent.rawX - downRawX
                        val deltaRawY = motionEvent.rawY - downRawY
                        if (abs(deltaRawX) < touchSlop && abs(deltaRawY) < touchSlop) {
                            if (onClick(view, motionEvent)) {
                                return true
                            }
                        }
                        return true
                    }
                    GestureState.DRAG_X -> {
                        deltaRawX = motionEvent.rawX - downRawX
                        val (vx, _) = recentReleaseVelocity()
                        if (onDragHorizontalEnd(view, motionEvent, deltaRawX, vx)) {
                            return true
                        }
                    }
                    GestureState.DRAG_Y -> {
                        deltaRawY = motionEvent.rawY - downRawY
                        val (_, vy) = recentReleaseVelocity()
                        if (onDragVerticalEnd(view, motionEvent, deltaRawY, vy)) {
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
            MotionEvent.ACTION_CANCEL -> {
                handler.removeCallbacks(longPressRunnable)
                gestureState = GestureState.IDLE
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
