package cn.alvkeke.dropto.ui.fragment;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.Intent;
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
import cn.alvkeke.dropto.ui.activity.ManagementActivity;
import cn.alvkeke.dropto.ui.adapter.CategoryListAdapter;
import cn.alvkeke.dropto.ui.intf.CategoryAttemptListener;
import cn.alvkeke.dropto.ui.intf.ErrorMessageHandler;
import cn.alvkeke.dropto.ui.intf.ListNotification;
import cn.alvkeke.dropto.ui.listener.OnRecyclerViewTouchListener;

public class CategoryListFragment extends Fragment implements ListNotification {

    private Context context;
    private CategoryAttemptListener listener;
    private CategoryListAdapter categoryListAdapter;
    private ArrayList<Category> categories;

    public CategoryListFragment() {
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
        context = requireContext();
        listener = (CategoryAttemptListener) context;
        assert categories != null;
//        assert !categories.isEmpty();

        RecyclerView rlCategory = view.findViewById(R.id.category_list_listview);
        MaterialToolbar toolbar = view.findViewById(R.id.category_list_toolbar);
        View statusBar = view.findViewById(R.id.category_list_status_bar);
        View navigationBar = view.findViewById(R.id.category_list_navigation_bar);
        setSystemBarHeight(view, statusBar, navigationBar);

        toolbar.setNavigationIcon(R.drawable.icon_common_menu);
        toolbar.setNavigationOnClickListener(new OnCategoryListMenuClick());
        toolbar.inflateMenu(R.menu.category_toolbar);
        toolbar.setOnMenuItemClickListener(new CategoryMenuListener());

        categoryListAdapter = new CategoryListAdapter(categories);

        rlCategory.setAdapter(categoryListAdapter);
        RecyclerView.LayoutManager layoutManager = new LinearLayoutManager(context);
        rlCategory.setLayoutManager(layoutManager);
        rlCategory.addItemDecoration(new DividerItemDecoration(context, DividerItemDecoration.VERTICAL));
        rlCategory.setOnTouchListener(new OnListItemClickListener());
    }

    private class OnCategoryListMenuClick implements View.OnClickListener {
        @Override
        public void onClick(View view) {
            Intent intent = new Intent(context, ManagementActivity.class);
            startActivity(intent);
        }
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

    private void throwErrorMessage(String msg) {
        if (!(listener instanceof ErrorMessageHandler)) return;
        ErrorMessageHandler handler = (ErrorMessageHandler) listener;
        handler.onError(msg);
    }

    private class CategoryMenuListener implements Toolbar.OnMenuItemClickListener {

        @Override
        public boolean onMenuItemClick(MenuItem item) {
            int menuId = item.getItemId();
            if (menuId == R.id.category_menu_item_add) {
                listener.onAttempt(CategoryAttemptListener.Attempt.SHOW_CREATE, null);
            } else if (menuId == R.id.category_menu_item_edit) {
                throwErrorMessage("Try edit categories");
            } else {
                throwErrorMessage("Unknown menu id: " + menuId);
                return false;
            }
            return true;
        }
    }

    private class OnListItemClickListener extends OnRecyclerViewTouchListener {

        @Override
        public boolean onItemClick(View v, int index) {
            Category category = categories.get(index);
            listener.onAttempt(CategoryAttemptListener.Attempt.SHOW_EXPAND, category);
            return true;
        }

        @Override
        public boolean onItemLongClick(View v, int index) {
            v.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS);
            Category category = categories.get(index);
            listener.onAttempt(CategoryAttemptListener.Attempt.SHOW_DETAIL, category);
            return true;
        }
    }

    @Override
    public void notifyItemListChanged(ListNotification.Notify notify, int index, Object object) {
        Category category = (Category) object;
        if (notify != Notify.REMOVED && categories.get(index) != category) {
            throwErrorMessage("target Category not exist");
            return;
        }

        switch (notify) {
            case INSERTED:
                categoryListAdapter.add(index, category);
                break;
            case UPDATED:
                categoryListAdapter.update(category);
                break;
            case REMOVED:
                categoryListAdapter.remove(category);
                break;
            default:
        }
    }

}