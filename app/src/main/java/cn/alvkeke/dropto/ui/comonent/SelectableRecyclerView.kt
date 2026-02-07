package cn.alvkeke.dropto.ui.comonent

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.util.AttributeSet
import android.util.Log
import androidx.core.content.ContextCompat
import androidx.core.graphics.withSave
import androidx.core.view.isGone
import androidx.core.view.isInvisible
import androidx.recyclerview.widget.RecyclerView
import cn.alvkeke.dropto.R
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

class SelectableRecyclerView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyle: Int = 0
) : RecyclerView(context, attrs, defStyle) {

    private val selectMaskPaint: Paint = Paint().apply {
        style = Paint.Style.FILL
        color = ContextCompat.getColor(context, R.color.note_item_select_mask)
        alpha = 40
    }

//    private val highlightPaint: Paint = Paint().apply {
//        color = Color.BLACK
//        alpha = 120
//    }
//    private val highlightPath: Path = Path()

    protected override fun dispatchDraw(canvas: Canvas) {
        super.dispatchDraw(canvas)

        for (i in 0 until childCount) {
            val child = getChildAt(i)
            if (child.isInvisible || child.isGone) {
                continue
            }
            val pos = getChildAdapterPosition(child)

            Log.e(TAG, "drawing select mask for pos: $pos, isSelected: ${isItemSelected(pos)}, isAnimating: ${_animationMap[pos]?.isAnimating}")
            val info: AnimationInfo? = _animationMap[pos]
            canvas.withSave {
                if (info != null && info.isAnimating) {
                    clipRect(
                        child.left.toFloat(), child.top.toFloat(),
                        child.right.toFloat(), child.bottom.toFloat(),
                    )
                    val cx = (child.left + child.right) / 2f
                    val cy = (child.top + child.bottom) / 2f
                    val radius = child.width * info.ratio
                    drawCircle(
                        cx, cy, radius, selectMaskPaint
                    )
                } else if (isItemSelected(pos)) {
                    drawRect(
                        child.left.toFloat(), child.top.toFloat(),
                        child.right.toFloat(), child.bottom.toFloat(),
                        selectMaskPaint
                    )
                }
            }

//            if (highlightStatus == HighlightStatus.Highlighted) {
//                canvas.withSave {
//                    highlightPath.addRoundRect(
//                        bubbleRect,
//                        BUBBLE_RADIUS.toFloat(),
//                        BUBBLE_RADIUS.toFloat(),
//                        Path.Direction.CW
//                    )
//                    canvas.clipOutPath(highlightPath)
//                    canvas.drawRect(
//                        0f, 0f, width.toFloat(), height.toFloat(),
//                        highlightPaint
//                    )
//                }
//            } else if (highlightStatus == HighlightStatus.Dimmed) {
//                canvas.withSave {
//                    canvas.drawRect(
//                        0f, 0f, width.toFloat(), height.toFloat(),
//                        highlightPaint
//                    )
//                }
//            }

        }
    }




    private class AnimationInfo(
        @Volatile var ratio: Float = 0f,
        @Volatile var isAnimating: Boolean = false,
        var selectJob: Job? = null,
        var unselectJob: Job? = null,
    )
    private val _animationMap = HashMap<Int, AnimationInfo>()
    private fun getAnimationInfo(index: Int): AnimationInfo {
        return _animationMap.getOrPut(index) {
            AnimationInfo()
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

    private fun selectAnimate(index: Int) {
        val info = getAnimationInfo(index)
        if (info.selectJob?.isActive == true) return

        val totalFrames = (ANIMATION_DURATION / ANIMATION_INTERVAL).toInt()
        val step = 1f / totalFrames

        info.selectJob = animationScope.launch {
            info.isAnimating = true
            if (info.unselectJob?.isActive == true) {
                info.unselectJob?.cancel()
            } else {
                info.ratio = 0f
            }

            while (info.ratio <= 1f) {
                info.ratio += step
                invalidate()
                kotlinx.coroutines.delay(ANIMATION_INTERVAL)
            }
            info.isAnimating = false
        }
    }

    private fun unselectAnimate(index: Int) {
        val info = getAnimationInfo(index)
        if (info.unselectJob?.isActive == true) return

        val totalFrames = (ANIMATION_DURATION / ANIMATION_INTERVAL).toInt()
        var currentFrame = 0
        val step = 1f / totalFrames

        info.unselectJob = animationScope.launch {
            info.isAnimating = true
            if (info.selectJob?.isActive == true) {
                info.selectJob?.cancel()
            } else {
                info.ratio = 1f
            }

            while (info.ratio >= 0f) {
                info.ratio -= step
                invalidate()
                currentFrame++
                kotlinx.coroutines.delay(ANIMATION_INTERVAL)
            }
            info.isAnimating = false
        }
    }

    private val selectedMap = HashMap<Int, Boolean>()
    val selectedCount: Int
        get() = selectedMap.filter { e -> e.value }.size
    val isSelectMode: Boolean
        get() = selectedMap.any { e -> e.value }

    fun isItemSelected(index: Int): Boolean {
        return selectedMap[index] ?: false
    }

    fun selectItem(index: Int) {
        if (isItemSelected(index)) {
            return
        }
        val lastCount = selectedCount
        selectedMap[index] = true
        selectAnimate(index)
        selectListener?.onSelect(index)
        if (lastCount == 0 && selectedCount > 0) {
            selectListener?.onSelectEnter()
        }
    }

    fun unselectItem(index: Int) {
        if (!isItemSelected(index)) {
            return
        }
        val lastCount = selectedCount
        unselectAnimate(index)
        selectedMap[index] = false
        selectListener?.onSelect(index)
        if (lastCount > 0 && selectedCount == 0) {
            selectListener?.onSelectExit()
        }
    }

    fun toggleSelectItems(index: Int) {
        if (isItemSelected(index)) {
            unselectItem(index)
        } else {
            selectItem(index)
        }
    }

    fun setRangeSelection(start: Int, end: Int, isSelect: Boolean) {
        val s = minOf(start, end)
        val e = maxOf(start, end)

        val updateFn = if (isSelect) {
            { i: Int ->
                if (!isItemSelected(i))
                    selectItem(i)
            }
        } else {
            { i: Int ->
                if (isItemSelected(i))
                    unselectItem(i)
            }
        }

        for (i in s..e) {
            updateFn(i)
        }
    }

    fun clearSelectItems() {
        selectedMap.filter { v -> v.value }.forEach { e ->
            unselectItem(e.key)
        }
    }

    interface SelectListener {
        fun onSelectEnter()
        fun onSelectExit()
        fun onSelect(index: Int)
        fun onUnSelect(index: Int)
    }

    private var selectListener: SelectListener? = null

    fun setSelectListener(selectListener: SelectListener?) {
        this.selectListener = selectListener
    }

    private var highlightIndex = -1
    fun clearHighLight() {
        highlightIndex = -1
        invalidate()
    }

    fun highLight(index: Int) {
        highlightIndex = index
        invalidate()
    }

    companion object {
        const val TAG = "SelectableRecyclerView"

        const val ANIMATION_DURATION = 300L
        const val ANIMATION_INTERVAL = 5L
    }
}