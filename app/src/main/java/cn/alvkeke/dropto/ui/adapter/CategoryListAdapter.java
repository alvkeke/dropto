package cn.alvkeke.dropto.ui.adapter;

import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;

import cn.alvkeke.dropto.R;
import cn.alvkeke.dropto.data.Category;

public class CategoryListAdapter extends RecyclerView.Adapter<CategoryListAdapter.ViewHolder> {

    private final ArrayList<Category> categories;

    public CategoryListAdapter(ArrayList<Category> categories) {
        this.categories = new ArrayList<>(categories);
    }
    public static class ViewHolder extends RecyclerView.ViewHolder {

        private final ImageView ivIcon;
        private final TextView tvTitle;
        private final TextView tvPreview;

        public ViewHolder(@NonNull View itemView) {
            super(itemView);
            ivIcon = itemView.findViewById(R.id.item_category_icon);
            tvTitle = itemView.findViewById(R.id.item_category_title);
            tvPreview = itemView.findViewById(R.id.item_category_preview_text);

        }

        public void setTitle(String title) {
            tvTitle.setText(title);
        }

        public void setPreview(String preview) {
            tvPreview.setText(preview);
        }

        public void setType(Category.Type type) {
            ivIcon.setImageResource(Category.typeToIconResource(type));
        }

    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.rlist_item_category, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder viewHolder, int i) {
        Category c = categories.get(i);
        if (c == null) {
            Log.e(this.toString(), "cannot get category at " + i);
            return;
        }

        viewHolder.setTitle(c.getTitle());
        viewHolder.setPreview(c.getPreviewText());
        viewHolder.setType(c.getType());
    }

    @Override
    public int getItemCount() {
        return categories.size();
    }

    public int add(Category e) {
        if (categories.contains(e)) return -1;
        boolean result = categories.add(e);
        if (!result) {
            return -1;
        }
        int index = categories.indexOf(e);
        notifyItemInserted(index);
        notifyItemRangeChanged(index, categories.size() - index);
        return index;
    }

    public boolean add(int index, Category e) {
        int idx = categories.indexOf(e);
        if (idx >= 0) {
            if (idx != index) {
                Log.e(this.toString(), "category exist with mismatch index: "+index+":"+idx);
            }
            return false;
        }
        categories.add(index, e);
        notifyItemInserted(index);
        notifyItemRangeChanged(index, categories.size()-index);
        return true;
    }

    public Category get(int index) {
        return categories.get(index);
    }

    public void remove(Category e) {
        int index = categories.indexOf(e);
        categories.remove(e);
        notifyItemRemoved(index);
        notifyItemRangeChanged(index, categories.size()-index);
    }

    public void clear() {
        notifyItemRangeRemoved(0, categories.size());
        categories.clear();
    }

    public void update(int index) {
        notifyItemChanged(index);
    }

    public void update(Category e) {
        int index = categories.indexOf(e);
        if (index != -1)
            notifyItemChanged(index);
    }

}
