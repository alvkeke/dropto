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

    private ArrayList<Category> categories;

    public CategoryListAdapter(ArrayList<Category> categories) {
        this.categories = categories;
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
            switch (type) {
                case LOCAL_CATEGORY:
                    ivIcon.setImageResource(R.drawable.icon_category_local);
                    break;
                case REMOTE_USERS:
                    ivIcon.setImageResource(R.drawable.icon_category_remote_peers);
                    break;
                case REMOTE_SELF_DEV:
                    ivIcon.setImageResource(R.drawable.icon_category_remote_dev);
                    break;
                default:
                    ivIcon.setImageResource(R.drawable.icon_category_unknown);
                    Log.e(this.toString(), "Unknown type: " + type);
            }
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
        if (categories == null) return 0;
        return categories.size();
    }

    public void setCategories(ArrayList<Category> categories) {
        this.categories = categories;
    }

}
