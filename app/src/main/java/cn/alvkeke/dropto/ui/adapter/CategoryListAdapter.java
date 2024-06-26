package cn.alvkeke.dropto.ui.adapter;

import android.graphics.Color;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import cn.alvkeke.dropto.R;
import cn.alvkeke.dropto.data.Category;

public class CategoryListAdapter extends SelectableListAdapter<Category, CategoryListAdapter.ViewHolder> {

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
        Category c = this.get(i);
        if (c == null) {
            Log.e(this.toString(), "cannot get category at " + i);
            return;
        }

        viewHolder.setTitle(c.getTitle());
        viewHolder.setPreview(c.getPreviewText());
        viewHolder.setType(c.getType());
        if (isSelected(i)) {
            // TODO: use another color for selected item
            viewHolder.itemView.setBackgroundColor(Color.LTGRAY);
        } else {
            viewHolder.itemView.setBackgroundColor(Color.TRANSPARENT);
        }
    }

}
