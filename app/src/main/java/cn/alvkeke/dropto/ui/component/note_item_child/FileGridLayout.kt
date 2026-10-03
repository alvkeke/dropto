package cn.alvkeke.dropto.ui.component.note_item_child

import android.content.Context
import android.util.AttributeSet
import android.view.View
import android.widget.GridLayout
import kotlin.math.roundToInt


class FileGridLayout @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null
) : GridLayout(context, attrs) {

    var columnWidthExpect: Int = 0

    private var placedColumns = 0
    private var placementDirty = true

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val width = MeasureSpec.getSize(widthMeasureSpec)
        val columns = if (columnWidthExpect > 0) {
            (width.toFloat() / columnWidthExpect).roundToInt().coerceAtLeast(1)
        } else {
            1
        }

        if (placementDirty || columns != placedColumns) {
            placementDirty = false
            placedColumns = columns
            columnCount = columns
            placeChildren(columns)
        }
        super.onMeasure(widthMeasureSpec, heightMeasureSpec)
    }

    override fun onViewAdded(child: View) {
        super.onViewAdded(child)
        placementDirty = true
    }

    override fun onViewRemoved(child: View) {
        super.onViewRemoved(child)
        placementDirty = true
    }

    private fun placeChildren(columns: Int) {
        for (position in 0 until childCount) {
            val child = getChildAt(position)
            val params = child.layoutParams as? LayoutParams ?: continue
            params.apply {
                columnSpec = spec(position % columns, 1, COLUMN_WEIGHT)
                rowSpec = spec(position / columns, 1)
            }
            child.layoutParams = params
        }
    }

    companion object {
        private const val COLUMN_WEIGHT = 1f
    }
}
