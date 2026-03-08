package cn.alvkeke.dropto.ui.adapter

import android.annotation.SuppressLint
import android.util.Log
import androidx.recyclerview.widget.RecyclerView

abstract class FilterableListAdapter<E, H : RecyclerView.ViewHolder> : RecyclerView.Adapter<H>() {
    protected val elements = ArrayList<E>()

    override fun getItemCount(): Int {
        return filteredElements.size
    }

    fun setList(elements: ArrayList<E>) {
        notifyItemRangeRemoved(0, itemCount)
        this.elements.clear()
        this.elements.addAll(elements)
        notifyItemRangeInserted(0, itemCount)
    }

    fun add(e: E): Int {
        if (elements.contains(e)) {
            return -1
        }
        val result = elements.add(e)
        if (!result) {
            return -1
        }
        val index = filteredElements.indexOf(e)
        notifyItemInserted(index)
        return index
    }

    fun add(index: Int, e: E): Boolean {
        val idx = elements.indexOf(e)
        if (idx >= 0) {
            if (filteredElements.indexOf(e) != index) {
                Log.e(this.toString(), "element exist with mismatch index: $index:$idx")
            }
            return false
        }
        val insertIndex = if (filters.isEmpty()) index else elements.indexOf(filteredElements.getOrNull(index))
        elements.add(if (insertIndex == -1) elements.size else insertIndex, e)
        notifyItemInserted(index)
        return true
    }

    fun remove(e: E) {
        val index = elements.filter {
            filters.all { filter ->
                filter(it) || it == e
            }
        }.indexOf(e)
        if (index == -1) return
        if (filters.isEmpty()) {
            elements.remove(e)
        }
        notifyItemRemoved(index)
    }

    fun removeAt(index: Int): E {
        val e = filteredElements[index]
        if (filters.isEmpty()) {
            elements.remove(e)
        }
        notifyItemRemoved(index)
        return e
    }

    fun moveItem(from: Int, to: Int) {
        if (from == to) return
        val item = filteredElements[from]
        val oldIndex = elements.indexOf(item)
        if (oldIndex == -1) return
        elements.removeAt(oldIndex)
        val targetItem = filteredElements.getOrNull(to)
        val newIndex = if (targetItem != null) elements.indexOf(targetItem) else elements.size
        elements.add(newIndex, item)
        notifyItemMoved(from, to)
    }

    fun clear() {
        notifyItemRangeRemoved(0, filteredElements.size)
        elements.clear()
    }

    fun update(index: Int) {
        notifyItemChanged(index)
    }

    fun update(e: E) {
        val index = filteredElements.indexOf(e)
        if (index != -1) {
            notifyItemChanged(index)
        }
    }

    fun get(index: Int): E {
        return filteredElements[index]
    }

    protected val filteredElements: List<E>
        get() = elements.filter {
                filters.all { filter ->
                    filter(it)
                }
            }

    private val filters = ArrayList<(E) -> Boolean>()

    @SuppressLint("NotifyDataSetChanged")
    fun addFilter(filter: (E) -> Boolean) {
        if (filters.contains(filter)) {
            return
        }
        filters.add(filter)
        notifyDataSetChanged()
    }

    @SuppressLint("NotifyDataSetChanged")
    fun removeFilter(filter: (E) -> Boolean) {
        filters.remove(filter)
        notifyDataSetChanged()
    }

    @SuppressLint("NotifyDataSetChanged")
    fun clearFilters() {
        filters.clear()
        notifyDataSetChanged()
    }
}
