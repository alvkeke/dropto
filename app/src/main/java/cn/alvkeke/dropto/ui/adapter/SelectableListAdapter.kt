package cn.alvkeke.dropto.ui.adapter

import android.util.Log
import androidx.recyclerview.widget.RecyclerView

abstract class SelectableListAdapter<E, H : RecyclerView.ViewHolder> : RecyclerView.Adapter<H>() {
    private val elements = ArrayList<E>()

    override fun getItemCount(): Int {
        return elements.size
    }

    fun setList(elements: ArrayList<E>) {
        notifyItemRangeRemoved(0, this.elements.size)
        this.elements.clear()
        this.elements.addAll(elements)
        notifyItemRangeInserted(0, this.elements.size)
    }

    fun add(e: E): Int {
        if (elements.contains(e)) {
            return -1
        }
        val result = elements.add(e)
        if (!result) {
            return -1
        }
        val index = elements.indexOf(e)
        notifyItemInserted(index)
        notifyItemRangeChanged(index, elements.size - index)
        return index
    }

    fun add(index: Int, e: E): Boolean {
        val idx = elements.indexOf(e)
        if (idx >= 0) {
            if (idx != index) {
                Log.e(this.toString(), "element exist with mismatch index: $index:$idx")
            }
            return false
        }
        elements.add(index, e)
        notifyItemInserted(index)
        notifyItemRangeChanged(index, elements.size - index)
        return true
    }

    fun remove(e: E) {
        val index = elements.indexOf(e)
        elements.remove(e)
        notifyItemRemoved(index)
        notifyItemRangeChanged(index, elements.size - index)
    }

    fun clear() {
        notifyItemRangeRemoved(0, elements.size)
        elements.clear()
    }

    fun update(index: Int) {
        notifyItemChanged(index)
    }

    fun update(e: E) {
        val index = elements.indexOf(e)
        if (index != -1) {
            notifyItemChanged(index)
        }
    }

    fun get(index: Int): E {
        return elements[index]
    }

    interface SelectListener {
        fun onSelectEnter()
        fun onSelectExit()
        fun onSelect(index: Int)
        fun onUnSelect(index: Int)
    }

    var selectedItems: ArrayList<E> = ArrayList()
        private set
    private var selectListener: SelectListener? = null

    fun setSelectListener(selectListener: SelectListener?) {
        this.selectListener = selectListener
    }

    fun toggleSelectItems(index: Int) {
        val item = elements[index]
        if (selectedItems.contains(item)) {
            selectedItems.remove(item)
            selectListener?.onUnSelect(index)
            if (selectedItems.isEmpty()) selectListener?.onSelectExit()
        } else {
            val empty = selectedItems.isEmpty()
            selectedItems.add(item)
            if (empty) selectListener?.onSelectEnter()
            selectListener?.onSelect(index)
        }
        notifyItemChanged(index)
    }

    fun selectItem(index: Int) {
        val item = elements[index]
        if (selectedItems.contains(item)) {
            return
        }
        val empty = selectedItems.isEmpty()
        selectedItems.add(item)
        if (empty) selectListener?.onSelectEnter()
        selectListener?.onSelect(index)
        notifyItemChanged(index)
    }

    fun unselectItem(index: Int) {
        val item = elements[index]
        if (!selectedItems.contains(item)) {
            return
        }
        selectedItems.remove(item)
        selectListener?.onUnSelect(index)
        if (selectedItems.isEmpty()) selectListener?.onSelectExit()
        notifyItemChanged(index)
    }

    fun clearSelectItems() {
        if (selectedItems.isEmpty()) {
            return
        }
        selectedItems = ArrayList()
        selectListener?.onSelectExit()
        notifyItemRangeChanged(0, elements.size)
    }

    val selectedCount: Int
        get() = selectedItems.size

    val isSelectMode: Boolean
        get() = !selectedItems.isEmpty()

    fun isSelected(index: Int): Boolean {
        if (index >= elements.size) {
            return false
        }
        return isSelected(elements[index])
    }

    fun isSelected(elem: E): Boolean {
        return selectedItems.contains(elem)
    }
}
