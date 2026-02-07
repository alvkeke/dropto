package cn.alvkeke.dropto.ui.comonent

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.util.AttributeSet
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
import kotlinx.coroutines.cancel
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

    private val highlightPaint: Paint = Paint().apply {
        color = Color.BLACK
    }
    private val highlightPath: Path = Path()

    protected override fun dispatchDraw(canvas: Canvas) {
        super.dispatchDraw(canvas)

        for (i in 0 until childCount) {
            val child = getChildAt(i)
            if (child.isInvisible || child.isGone) {
                continue
            }
            val pos = getChildAdapterPosition(child)

            val info: SelectAnimationInfo? = _selectAnimationMap[pos]
            canvas.withSave {
                if (info != null && info.type != null) {
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

            if (highlightIndex == pos) {
                canvas.withSave {
                    highlightPaint.alpha = (50 * highlightRatio).toInt()
                    highlightPath.reset()

                    if (child is HighlightAble) {
                        val path = child.getHighlightArea()
                        highlightPath.addPath(path)
                        highlightPath.offset(child.left.toFloat(), child.top.toFloat())
                    } else {
                        highlightPath.addRect(
                            child.left.toFloat(), child.top.toFloat(),
                            child.right.toFloat(), child.bottom.toFloat(),
                            Path.Direction.CW
                        )
                    }
                    canvas.clipOutPath(highlightPath)
                    canvas.drawRect(
                        0f, 0f,
                        width.toFloat(), height.toFloat(),
                        highlightPaint
                    )
                }
            }
        }
    }


    private enum class SelectAnimationType {
        SELECT,
        UNSELECT
    }
    private class SelectAnimationInfo(
        @Volatile var ratio: Float = 0f,
        @Volatile var type: SelectAnimationType? = null,
        var job: Job? = null,
    )
    private val _selectAnimationMap = HashMap<Int, SelectAnimationInfo>()
    private fun getSelectAnimationInfo(index: Int): SelectAnimationInfo {
        return _selectAnimationMap.getOrPut(index) {
            SelectAnimationInfo()
        }
    }

    private var _selectAnimationScope: CoroutineScope? = null
    private val selectAnimationScope: CoroutineScope
        get() {
            if (_selectAnimationScope == null || _selectAnimationScope?.isActive != true) {
                _selectAnimationScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
            }
            return _selectAnimationScope!!
        }

    private fun animateSelect(
        index: Int,
        type: SelectAnimationType,
        runOnFinish: (() -> Unit)? = null
    ) {
        val info = getSelectAnimationInfo(index)

        val totalFrames = (ANIMATION_DURATION / ANIMATION_INTERVAL).toInt()
        val step: Float
        val isEnd: (Float) -> Boolean
        when (type) {
            SelectAnimationType.SELECT -> {
                step = 1f / totalFrames
                isEnd = { ratio -> ratio >= 1f }
            }
            SelectAnimationType.UNSELECT -> {
                step = -1f / totalFrames
                isEnd = { ratio -> ratio <= 0f }
            }
        }

        if (info.job?.isActive != true) {
            info.ratio = when (type) {
                SelectAnimationType.SELECT -> 0f
                SelectAnimationType.UNSELECT -> 1f
            }
        } else {
            info.job?.cancel()
        }

        info.job = selectAnimationScope.launch {
            info.type = type

            while (info.ratio <= 1f) {
                info.ratio = (info.ratio + step).coerceIn(0f, 1f)
                invalidate()
                if (isEnd(info.ratio)) {
                    break
                }
                kotlinx.coroutines.delay(ANIMATION_INTERVAL)
            }
            runOnFinish?.invoke()
            info.type = null
        }
    }

    private val selectedMap = HashMap<Int, Boolean>()
    val selectedCount: Int
        get() = selectedMap.filter { e -> e.value }.size
    val isSelectMode: Boolean
        get() = selectedMap.any { e -> e.value }
    val selectedIndexes: List<Int>
        get() = selectedMap.filter { e -> e.value }.map { e -> e.key }

    fun isItemSelected(index: Int): Boolean {
        return selectedMap[index] ?: false
    }

    fun selectItem(index: Int) {
        if (isItemSelected(index)) {
            return
        }
        val lastCount = selectedCount
        selectedMap[index] = true
        animateSelect(index, SelectAnimationType.SELECT)
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
        animateSelect(index, SelectAnimationType.UNSELECT) {
            selectedMap[index] = false
            post {
                selectListener?.onUnSelect(index)
                if (lastCount > 0 && selectedCount == 0) {
                    selectListener?.onSelectExit()
                }
            }
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
    private var highlightRatio = 0f
    private var highlightJob: Job? = null
    private var highlightAnimating = false

    private fun animateHighlight(
        isExit: Boolean = false,
        runOnFinish: (() -> Unit)? = null
    ) {
        val totalFrames = (ANIMATION_DURATION / ANIMATION_INTERVAL / 2).toInt()
        val step: Float
        val isEnd: (Float) -> Boolean
        when (isExit) {
            true -> {
                step = -1f / totalFrames
                isEnd = { ratio -> ratio <= 0f }
            }
            false -> {
                step = 1f / totalFrames
                isEnd = { ratio -> ratio >= 1f }
            }
        }
        highlightJob?.cancel()

        if (highlightJob?.isActive != true) {
            highlightRatio = when (isExit) {
                true -> 1f
                false -> 0f
            }
        } else {
            highlightJob?.cancel()
        }

        highlightJob = selectAnimationScope.launch {
            highlightAnimating = true
            while (true) {
                highlightRatio = (highlightRatio + step).coerceIn(0f, 1f)
                invalidate()
                if (isEnd(highlightRatio)) {
                    break
                }
                kotlinx.coroutines.delay(ANIMATION_INTERVAL)
            }
            highlightAnimating = false
            runOnFinish?.invoke()
        }
    }

    fun clearHighLight() {
        animateHighlight(true) {
            highlightIndex = -1
        }
    }

    fun highLight(index: Int) {
        highlightIndex = index
        animateHighlight()
    }

    interface HighlightAble {
        fun getHighlightArea(): Path
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        _selectAnimationScope?.cancel()
    }

    companion object {
        const val TAG = "SelectableRecyclerView"

        const val ANIMATION_DURATION = 300L
        const val ANIMATION_INTERVAL = 5L
    }
}