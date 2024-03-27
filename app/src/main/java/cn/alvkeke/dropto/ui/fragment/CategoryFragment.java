package cn.alvkeke.dropto.ui.fragment;

import android.app.Activity;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.widget.Toolbar;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.DividerItemDecoration;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.appbar.MaterialToolbar;

import java.util.ArrayList;

import cn.alvkeke.dropto.R;
import cn.alvkeke.dropto.data.Category;
import cn.alvkeke.dropto.data.Global;
import cn.alvkeke.dropto.ui.intf.ListNotification;
import cn.alvkeke.dropto.ui.intf.SystemKeyListener;
import cn.alvkeke.dropto.ui.adapter.CategoryListAdapter;

public class CategoryFragment extends Fragment implements SystemKeyListener, ListNotification {

    public interface CategoryEventListener {
        void onNoteListShow(Category category);
        void OnCategoryAdd();
        void onCategoryDetail(Category c);
    }

    private CategoryListAdapter categoryListAdapter;
    private CategoryEventListener listener;

    @Override
    public void onResume() {
        super.onResume();

        ArrayList<Category> categories = Global.getInstance().getCategories();
        for (int i=0; i<categories.size(); i++) {
            Category c = categories.get(i);
            if (c.needUpdate()) {
                categoryListAdapter.notifyItemChanged(i);
                c.setUpdated();
            }
        }
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_category, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        Activity activity = requireActivity();
        listener = (CategoryEventListener) activity;

        RecyclerView rlCategory = view.findViewById(R.id.rlist_category);
        MaterialToolbar toolbar = view.findViewById(R.id.toolbar_category);

        toolbar.inflateMenu(R.menu.category_toolbar);
        toolbar.setOnMenuItemClickListener(new CategoryMenuListener());

        ArrayList<Category> categories = Global.getInstance().getCategories();
        categoryListAdapter = new CategoryListAdapter(categories);
        categoryListAdapter.setCategories(categories);

        rlCategory.setAdapter(categoryListAdapter);
        RecyclerView.LayoutManager layoutManager = new LinearLayoutManager(activity);
        rlCategory.setLayoutManager(layoutManager);
        rlCategory.addItemDecoration(new DividerItemDecoration(activity, DividerItemDecoration.VERTICAL));
        categoryListAdapter.setItemClickListener(new onListItemClick());

    }

    class CategoryMenuListener implements Toolbar.OnMenuItemClickListener {

        @Override
        public boolean onMenuItemClick(MenuItem item) {
            int menuId = item.getItemId();
            if (menuId == R.id.category_menu_item_add) {
                Log.e(this.toString(), "Try add category");
                listener.OnCategoryAdd();
            } else if (menuId == R.id.category_menu_item_edit) {
                Log.e(this.toString(), "Try edit categories");
            } else {
                Log.e(this.toString(), "Unknown menu id: " + menuId);
                return false;
            }
            return true;
        }
    }

    @Override
    public boolean onBackPressed() {
        return false;
    }

    class onListItemClick implements CategoryListAdapter.OnItemClickListener {

        @Override
        public void onItemClick(int index, View v) {
            Log.d(this.toString(), "Category clicked on " + index);
            CategoryEventListener listener = (CategoryEventListener) requireContext();
            Category e = Global.getInstance().getCategories().get(index);
            listener.onNoteListShow(e);
        }

        @Override
        public boolean onItemLongClick(int index, View v) {
            Category e = Global.getInstance().getCategories().get(index);
            listener.onCategoryDetail(e);
            return true;
        }
    }

    @Override
    public void notifyItemListChanged(ListNotification.Notify notify, int index, Object object) {
        ArrayList<Category> categories = Global.getInstance().getCategories();
        Category category = (Category) object;
        if (notify != Notify.REMOVED && categories.get(index) != category) {
            Log.e(this.toString(), "target Category not exist");
            return;
        }

        switch (notify) {
            case CREATED:
                categoryListAdapter.notifyItemInserted(index);
                categoryListAdapter.notifyItemRangeChanged(index,
                        categoryListAdapter.getItemCount()-index);
                break;
            case MODIFIED:
                categoryListAdapter.notifyItemChanged(index);
                break;
            case REMOVED:
                categoryListAdapter.notifyItemRemoved(index);
                categoryListAdapter.notifyItemRangeChanged(index,
                        categoryListAdapter.getItemCount()-index);
                break;
            default:
        }
    }

}