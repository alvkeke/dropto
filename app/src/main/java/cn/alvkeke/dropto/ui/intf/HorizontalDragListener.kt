package cn.alvkeke.dropto.ui.intf

interface HorizontalDragListener {
    fun onDragStart()
    fun onDragging(deltaX: Float)
    fun onDragEnd(deltaX: Float, velocityPxPerMs: Float)
}
