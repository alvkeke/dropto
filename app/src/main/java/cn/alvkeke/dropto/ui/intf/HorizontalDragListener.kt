package cn.alvkeke.dropto.ui.intf

interface HorizontalDragListener {
    fun onDragStart(deltaX: Float)
    fun onDragging(deltaX: Float)
    fun onDragEnd(deltaX: Float, velocityPxPerMs: Float)
}
