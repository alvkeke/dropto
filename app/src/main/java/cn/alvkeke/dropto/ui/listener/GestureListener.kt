package cn.alvkeke.dropto.ui.listener

import android.annotation.SuppressLint
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.MotionEvent
import android.view.MotionEvent.PointerCoords
import android.view.View
import android.view.View.OnTouchListener
import kotlin.math.abs
import kotlin.math.sqrt

open class GestureListener : OnTouchListener {
    private class Point {
        var x: Float = 0f
        var y: Float = 0f
        fun set(x: Float, y: Float) {
            this.x = x
            this.y = y
        }

        @Suppress("unused")
        fun distance(point: Point): Float {
            val xx: Float = point.x - x
            val yy: Float = point.y - y
            return sqrt((xx * xx + yy * yy).toDouble()).toFloat()
        }
    }

    private fun pointFromEvent(e: MotionEvent): Point {
        val point = Point()
        point.set(e.rawX, e.rawY)
        return point
    }

    private enum class GestureMode {
        NONE,
        SCROLL_V,
        SCROLL_H,
        DRAG,
        ZOOM,
        INVALID,
    }

    private fun getMode(deltaX: Float, deltaY: Float): GestureMode {
        if (deltaX <= DISTANCE_NO_MOVE && deltaY >= DISTANCE_SCROLL) {
            return GestureMode.SCROLL_V
        } else if (deltaX >= DISTANCE_SCROLL && deltaY <= DISTANCE_NO_MOVE) {
            return GestureMode.SCROLL_H
        } else if (deltaX >= DISTANCE_NO_MOVE && deltaY >= DISTANCE_NO_MOVE) {
            return GestureMode.DRAG
        }
        return GestureMode.NONE
    }

    private fun getMode(p1: Point, p2: Point): GestureMode {
        val dx: Float = p1.x - p2.x
        val dy: Float = p1.y - p2.y
        return getMode(abs(dx), abs(dy))
    }


    private lateinit var pointOld: Point
    private fun handleGesture(view: View, mode: GestureMode, event: MotionEvent) {
        var ret = false
        val current = pointFromEvent(event)
        if (mode == GestureMode.SCROLL_H) {
            ret = onScrollHorizontal(view, current.x - pointOld.x)
        } else if (mode == GestureMode.SCROLL_V) {
            ret = onScrollVertical(view, current.y - pointOld.y)
        }
        if (!ret) {
            onDrag(view, current.x - pointOld.x, current.y - pointOld.y)
        }
        pointOld = current
    }

    private fun handleGestureEnd(view: View, event: MotionEvent, mode: GestureMode) {
        var ret = false
        if (mode == GestureMode.SCROLL_H) {
            ret = onScrollHorizontalEnd(view, event)
        } else if (mode == GestureMode.SCROLL_V) {
            ret = onScrollVerticalEnd(view, event)
        }
        if (!ret) {
            onDragEnd(view, event)
        }
    }

    private fun getCurrentDistance(motionEvent: MotionEvent): Float {
        val c1 = PointerCoords()
        val c2 = PointerCoords()
        motionEvent.getPointerCoords(0, c1)
        motionEvent.getPointerCoords(1, c2)
        val xx = c1.x - c2.x
        val yy = c1.y - c2.y
        return sqrt((xx * xx + yy * yy).toDouble()).toFloat()
    }

    private var oldDist = 0f
    private fun handleZoom(view: View, motionEvent: MotionEvent) {
        val newDist = getCurrentDistance(motionEvent)
        val ratio = newDist / oldDist
        oldDist = newDist
        onZoom(view, ratio)
    }

    private fun handleZoomEnd(view: View) {
        onZoomEnd(view)
    }

    private val handler = Handler(Looper.getMainLooper())

    private var downTime: Long = 0
    private var downCount = 0
    private fun handleClick(view: View, e: MotionEvent) {
        if (downCount > 1) return
        if (System.currentTimeMillis() - downTime < CLICK_TIME_THRESHOLD) {
            clickView = view
            clickEvent = e
            handler.postDelayed(singleTapRunnable, CLICK_TIME_THRESHOLD)
        } else {
            downCount = 0
        }
    }

    private lateinit var clickView: View
    private lateinit var clickEvent: MotionEvent
    private val singleTapRunnable = Runnable {
        downCount = 0
        onClick(clickView, clickEvent)
    }

    private fun handleDoubleTap(view: View, e: MotionEvent) {
        downCount = 0
        onDoubleClick(view, e)
    }

    private val pointDown = Point()
    private var mode: GestureMode = GestureMode.NONE

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouch(view: View, motionEvent: MotionEvent): Boolean {
//        val p: Point
        when (motionEvent.actionMasked) {
            MotionEvent.ACTION_POINTER_DOWN -> {
                downCount = 0
                if (mode != GestureMode.NONE && mode != GestureMode.ZOOM) return true
                if (motionEvent.pointerCount == 2) {
                    oldDist = getCurrentDistance(motionEvent)
                    mode = GestureMode.ZOOM
                } else {
                    Log.e(this.toString(), "INVALID COUNT: " + motionEvent.pointerCount)
                    mode = GestureMode.INVALID
                }
            }

            MotionEvent.ACTION_DOWN -> {
                pointDown.set(motionEvent.rawX, motionEvent.rawY)
                pointOld = pointDown
                downTime = System.currentTimeMillis()
                downCount++
                handler.removeCallbacks(singleTapRunnable)
                if (downCount > 1) {
                    handleDoubleTap(view, motionEvent)
                    mode = GestureMode.INVALID
                    return true
                }
                mode = GestureMode.NONE
            }

            MotionEvent.ACTION_MOVE -> {
                if (mode == GestureMode.INVALID) {
                    downCount = 0
                    return true
                } else if (mode == GestureMode.ZOOM) {
                    downCount = 0
                    handleZoom(view, motionEvent)
                    return true
                } else if (mode != GestureMode.NONE) {
                    downCount = 0
                    handleGesture(view, mode, motionEvent)
                    return true
                }
                val p = pointFromEvent(motionEvent)
                mode = getMode(pointDown, p)
            }

            MotionEvent.ACTION_UP -> if (mode == GestureMode.NONE) {
                if (downCount == 1) {
                    handleClick(view, motionEvent)
                }
            } else if (mode == GestureMode.ZOOM || mode == GestureMode.INVALID) {
                return true
            } else {
                handleGestureEnd(view, motionEvent, mode)
            }

            MotionEvent.ACTION_POINTER_UP -> if (mode == GestureMode.ZOOM && motionEvent.pointerCount <= 2) {
                handleZoomEnd(view)
                mode = GestureMode.INVALID
            }

            else -> Log.e(
                this.toString(),
                "action: " + motionEvent.action + ", " + motionEvent.actionMasked
            )
        }
        return true
    }


    @Suppress("unused")
    open fun onClick(v: View, e: MotionEvent) {
    }

    @Suppress("unused")
    open fun onDoubleClick(v: View, e: MotionEvent) {
    }

    @Suppress("unused")
    open fun onScrollVertical(view: View, deltaY: Float): Boolean {
        return false
    }

    @Suppress("unused")
    open fun onScrollVerticalEnd(view: View, motionEvent: MotionEvent): Boolean {
        return false
    }

    @Suppress("unused")
    open fun onScrollHorizontal(view: View, deltaX: Float): Boolean {
        return false
    }

    @Suppress("unused")
    open fun onScrollHorizontalEnd(view: View, motionEvent: MotionEvent): Boolean {
        return false
    }

    @Suppress("unused")
    open fun onDrag(view: View, deltaX: Float, deltaY: Float) {
    }

    @Suppress("unused")
    open fun onDragEnd(view: View, motionEvent: MotionEvent) {
    }

    @Suppress("unused")
    open fun onZoom(view: View, ratio: Float) {
    }

    @Suppress("unused")
    open fun onZoomEnd(view: View) {
    }

    companion object {
        private const val DISTANCE_NO_MOVE = 25
        private const val DISTANCE_SCROLL = 45
        private const val CLICK_TIME_THRESHOLD: Long = 200
    }
}

