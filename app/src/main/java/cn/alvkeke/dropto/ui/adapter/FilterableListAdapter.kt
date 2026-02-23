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
        notifyItemRangeChanged(index, filteredElements.size - index)
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
        notifyItemRangeChanged(index, filteredElements.size - index)
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
        notifyItemRangeChanged(index, filteredElements.size - index)
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

    private val filteredElements: List<E>
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
