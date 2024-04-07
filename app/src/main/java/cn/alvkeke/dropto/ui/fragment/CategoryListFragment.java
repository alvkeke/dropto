package cn.alvkeke.dropto.ui.fragment;

import android.annotation.SuppressLint;
import android.content.Context;
import android.os.Bundle;
import android.view.HapticFeedbackConstants;
import android.view.LayoutInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.widget.Toolbar;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
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
import cn.alvkeke.dropto.ui.listener.OnRecyclerViewTouchListener;

public class CategoryListFragment extends Fragment implements ListNotification {

    public interface AttemptListener {
        enum Attempt {
            CREATE,
            DETAIL,
            EXPAND,
        }
        void onAttemptRecv(AttemptListener.Attempt attempt, Category category);
        void onErrorRecv(String errorMessage);
    }

    private AttemptListener listener;
    private CategoryListAdapter categoryListAdapter;
    private ArrayList<Category> categories;

    public CategoryListFragment() {
    }

    public void setListener(AttemptListener listener) {
        this.listener = listener;
    }

    public void setCategories(ArrayList<Category> categories) {
        this.categories = categories;
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_category_list, container, false);
    }

    @SuppressLint("ClickableViewAccessibility")
    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        Context context = requireContext();

        RecyclerView rlCategory = view.findViewById(R.id.category_list_listview);
        MaterialToolbar toolbar = view.findViewById(R.id.category_list_toolbar);
        View statusBar = view.findViewById(R.id.category_list_status_bar);
        View navigationBar = view.findViewById(R.id.category_list_navigation_bar);
        setSystemBarHeight(view, statusBar, navigationBar);

        toolbar.inflateMenu(R.menu.category_toolbar);
        toolbar.setOnMenuItemClickListener(new CategoryMenuListener());

        categoryListAdapter = new CategoryListAdapter(categories);
        categoryListAdapter.setCategories(categories);

        rlCategory.setAdapter(categoryListAdapter);
        RecyclerView.LayoutManager layoutManager = new LinearLayoutManager(context);
        rlCategory.setLayoutManager(layoutManager);
        rlCategory.addItemDecoration(new DividerItemDecoration(context, DividerItemDecoration.VERTICAL));
        rlCategory.setOnTouchListener(new OnListItemClickListener());
    }

    private void setSystemBarHeight(View parent, View status, View navi) {
        ViewCompat.setOnApplyWindowInsetsListener(parent, (v, winInsets) -> {
            int statusHei, naviHei;
            statusHei = winInsets.getInsets(WindowInsetsCompat.Type.statusBars()).top;
            naviHei = winInsets.getInsets(WindowInsetsCompat.Type.navigationBars()).bottom;
            status.getLayoutParams().height = statusHei;
            navi.getLayoutParams().height = naviHei;

            return winInsets;
        });
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

    class OnListItemClickListener extends OnRecyclerViewTouchListener {

        @Override
        public boolean onItemClick(View v, int index) {
            Category category = categories.get(index);
            listener.onAttemptRecv(AttemptListener.Attempt.EXPAND, category);
            return true;
        }

        @Override
        public boolean onItemLongClick(View v, int index) {
            v.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS);
            Category category = categories.get(index);
            listener.onAttemptRecv(AttemptListener.Attempt.DETAIL, category);
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