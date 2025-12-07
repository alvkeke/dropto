package cn.alvkeke.dropto.ui.adapter;

import android.util.Log;

import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;

public abstract class SelectableListAdapter<E, H extends RecyclerView.ViewHolder> extends RecyclerView.Adapter<H> {

    private final ArrayList<E> elements = new ArrayList<>();

    @Override
    public int getItemCount() {
        return elements.size();
    }

    public void setList(ArrayList<E> elements) {
        notifyItemRangeRemoved(0, this.elements.size());
        this.elements.clear();
        this.elements.addAll(elements);
        notifyItemRangeInserted(0, this.elements.size());
    }

    public int add(E e) {
        if (elements.contains(e)) return -1;
        boolean result = elements.add(e);
        if (!result) {
            return -1;
        }
        int index = elements.indexOf(e);
        notifyItemInserted(index);
        notifyItemRangeChanged(index, elements.size() - index);
        return index;
    }

    public boolean add(int index, E e) {
        int idx = elements.indexOf(e);
        if (idx >= 0) {
            if (idx != index) {
                Log.e(this.toString(), "element exist with mismatch index: "+index+":"+idx);
            }
            return false;
        }
        elements.add(index, e);
        notifyItemInserted(index);
        notifyItemRangeChanged(index, elements.size()-index);
        return true;
    }

    public void remove(E e) {
        int index = elements.indexOf(e);
        elements.remove(e);
        notifyItemRemoved(index);
        notifyItemRangeChanged(index, elements.size()-index);
    }

    public void clear() {
        notifyItemRangeRemoved(0, elements.size());
        elements.clear();
    }

    public void update(int index) {
        notifyItemChanged(index);
    }

    public void update(E e) {
        int index = elements.indexOf(e);
        if (index != -1)
            notifyItemChanged(index);
    }

    public E get(int index) {
        return elements.get(index);
    }

    public interface SelectListener {
        void onSelectEnter();
        void onSelectExit();
        void onSelect(int index);
        void onUnSelect(int index);
    }

    private ArrayList<E> selectedItems = new ArrayList<>();
    private SelectListener selectListener = null;

    public void setSelectListener(SelectListener selectListener) {
        this.selectListener = selectListener;
    }

    public void toggleSelectItems(int index) {
        E item = elements.get(index);
        if (selectedItems.contains(item)) {
            selectedItems.remove(item);
            if (selectListener != null) {
                selectListener.onUnSelect(index);
                if (selectedItems.isEmpty()) selectListener.onSelectExit();
            }
        } else {
            boolean empty = selectedItems.isEmpty();
            selectedItems.add(item);
            if (selectListener != null) {
                if (empty) selectListener.onSelectEnter();
                selectListener.onSelect(index);
            }
        }
        notifyItemChanged(index);
    }

    public void clearSelectItems() {
        if (selectedItems.isEmpty()) return;
        selectedItems = new ArrayList<>();
        if (selectListener != null) {
            selectListener.onSelectExit();
        }
        notifyItemRangeChanged(0, elements.size());
    }

    public ArrayList<E> getSelectedItems() {
        return selectedItems;
    }

    public int getSelectedCount() {
        return selectedItems.size();
    }

    public boolean isSelectMode() {
        return !selectedItems.isEmpty();
    }

    public boolean isSelected(int index) {
        if (index >= elements.size()) return false;
        return isSelected(elements.get(index));
    }

    public boolean isSelected(E elem) {
        return selectedItems.contains(elem);
    }

}
