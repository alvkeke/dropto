package cn.alvkeke.dropto.ui.fragment;

import android.content.Context;
import android.os.Bundle;
import android.util.TypedValue;
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
import cn.alvkeke.dropto.ui.adapter.CategoryListAdapter;
import cn.alvkeke.dropto.ui.intf.ListNotification;
import cn.alvkeke.dropto.ui.intf.SysBarColorNotify;
import cn.alvkeke.dropto.ui.intf.SystemKeyListener;

public class CategoryListFragment extends Fragment implements SystemKeyListener, ListNotification {

    public interface AttemptListener {
        enum Attempt {
            CREATE,
            DETAIL,
            EXPAND,
        }
        void onAttemptRecv(AttemptListener.Attempt attempt, Category category);
        void onErrorRecv(String errorMessage);
    }

    private Context context;
    private final AttemptListener listener;
    private CategoryListAdapter categoryListAdapter;
    private final ArrayList<Category> categories;

    public CategoryListFragment(AttemptListener listener, ArrayList<Category> categories) {
        this.listener = listener;
        this.categories = categories;
    }

    @Override
    public void onResume() {
        super.onResume();

        for (int i=0; i<categories.size(); i++) {
            Category c = categories.get(i);
            if (c.needUpdate()) {
                categoryListAdapter.notifyItemChanged(i);
                c.setUpdated();
            }
        }

        if (context instanceof SysBarColorNotify) {
            SysBarColorNotify notify = (SysBarColorNotify) context;
            TypedValue value = new TypedValue();
            context.getTheme().resolveAttribute(com.google.android.material.R.attr.colorPrimary, value, true);
            notify.setNavigationBarColor(value.data);
        }
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_category_list, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        context = requireContext();

        RecyclerView rlCategory = view.findViewById(R.id.rlist_category);
        MaterialToolbar toolbar = view.findViewById(R.id.toolbar_category);

        toolbar.inflateMenu(R.menu.category_toolbar);
        toolbar.setOnMenuItemClickListener(new CategoryMenuListener());

        categoryListAdapter = new CategoryListAdapter(categories);
        categoryListAdapter.setCategories(categories);

        rlCategory.setAdapter(categoryListAdapter);
        RecyclerView.LayoutManager layoutManager = new LinearLayoutManager(context);
        rlCategory.setLayoutManager(layoutManager);
        rlCategory.addItemDecoration(new DividerItemDecoration(context, DividerItemDecoration.VERTICAL));
        categoryListAdapter.setItemClickListener(new onListItemClick());

    }

    class CategoryMenuListener implements Toolbar.OnMenuItemClickListener {

        @Override
        public boolean onMenuItemClick(MenuItem item) {
            int menuId = item.getItemId();
            if (menuId == R.id.category_menu_item_add) {
                listener.onAttemptRecv(AttemptListener.Attempt.CREATE, null);
            } else if (menuId == R.id.category_menu_item_edit) {
                listener.onErrorRecv("Try edit categories");
            } else {
                listener.onErrorRecv("Unknown menu id: " + menuId);
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
            Category e = categories.get(index);
            listener.onAttemptRecv(AttemptListener.Attempt.EXPAND, e);
        }

        @Override
        public boolean onItemLongClick(int index, View v) {
            Category e = categories.get(index);
            listener.onAttemptRecv(AttemptListener.Attempt.DETAIL, e);
            return true;
        }
    }

    @Override
    public void notifyItemListChanged(ListNotification.Notify notify, int index, Object object) {
        Category category = (Category) object;
        if (notify != Notify.REMOVED && categories.get(index) != category) {
            listener.onErrorRecv("target Category not exist");
            return;
        }

        switch (notify) {
            case CREATED:
                categoryListAdapter.notifyItemInserted(index);
                categoryListAdapter.notifyItemRangeChanged(index,
                        categoryListAdapter.getItemCount()-index);
                break;
            case UPDATED:
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