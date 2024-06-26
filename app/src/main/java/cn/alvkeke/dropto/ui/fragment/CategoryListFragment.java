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
import androidx.appcompat.app.AlertDialog;
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
import cn.alvkeke.dropto.ui.activity.MgmtActivity;
import cn.alvkeke.dropto.ui.adapter.CategoryListAdapter;
import cn.alvkeke.dropto.ui.adapter.SelectableListAdapter;
import cn.alvkeke.dropto.ui.intf.CategoryAttemptListener;
import cn.alvkeke.dropto.ui.intf.ErrorMessageHandler;
import cn.alvkeke.dropto.ui.intf.ListNotification;
import cn.alvkeke.dropto.ui.listener.OnRecyclerViewTouchListener;

public class CategoryListFragment extends Fragment implements ListNotification<Category> {

    private Context context;
    private CategoryAttemptListener listener;
    private CategoryListAdapter categoryListAdapter;
    private MaterialToolbar toolbar;

    public CategoryListFragment() {
    }

    private ArrayList<Category> categories = null;
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

        RecyclerView rlCategory = view.findViewById(R.id.category_list_listview);
        toolbar = view.findViewById(R.id.category_list_toolbar);
        View statusBar = view.findViewById(R.id.category_list_status_bar);
        View navigationBar = view.findViewById(R.id.category_list_navigation_bar);
        setSystemBarHeight(view, statusBar, navigationBar);

        toolbar.setNavigationIcon(R.drawable.icon_common_menu);
        toolbar.setNavigationOnClickListener(new OnCategoryListMenuClick());
        toolbar.inflateMenu(R.menu.category_toolbar);
        toolbar.setOnMenuItemClickListener(new CategoryMenuListener());

        categoryListAdapter = new CategoryListAdapter();
        if (categories != null) {
            categoryListAdapter.setList(categories);
        }
        categoryListAdapter.setSelectListener(new CategorySelectListener());
        setMenuBySelectedCount();

        rlCategory.setAdapter(categoryListAdapter);
        RecyclerView.LayoutManager layoutManager = new LinearLayoutManager(context);
        rlCategory.setLayoutManager(layoutManager);
        rlCategory.addItemDecoration(new DividerItemDecoration(context, DividerItemDecoration.VERTICAL));
        rlCategory.setOnTouchListener(new OnListItemClickListener());
    }

    private class OnCategoryListMenuClick implements View.OnClickListener {
        @Override
        public void onClick(View view) {
            Intent intent = new Intent(context, MgmtActivity.class);
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
                Category category = categoryListAdapter.getSelectedItems().get(0);
                listener.onAttempt(CategoryAttemptListener.Attempt.SHOW_DETAIL, category);
                categoryListAdapter.clearSelectItems();
            } else if (R.id.category_menu_item_remove == menuId) {
                new AlertDialog.Builder(context)
                        .setTitle(R.string.dialog_category_delete_selected_title)
                        .setMessage(R.string.dialog_category_delete_selected_message)
                        .setNegativeButton(R.string.string_cancel, null)
                        .setPositiveButton(R.string.string_ok, (dialogInterface, i) -> {
                            ArrayList<Category> selected = categoryListAdapter.getSelectedItems();
                            categoryListAdapter.clearSelectItems();
                            for (Category c : selected) {
                                listener.onAttempt(CategoryAttemptListener.Attempt.REMOVE, c);
                            }
                        }).create().show();
            } else if (R.id.category_menu_item_debug == menuId) {
                listener.onAttempt(CategoryAttemptListener.Attempt.DEBUG_ADD_DATA, null);
            } else {
                throwErrorMessage("Unknown menu id: " + menuId);
                return false;
            }
            return true;
        }
    }

    private void setMenuItemVisible(int id, boolean visible) {
        MenuItem menuItem = toolbar.getMenu().findItem(id);
        menuItem.setVisible(visible);
    }
    private void setMenuBySelectedCount() {
        int count = categoryListAdapter.getSelectedCount();
        if (count == 0) {
            setMenuItemVisible(R.id.category_menu_item_add, true);
            setMenuItemVisible(R.id.category_menu_item_debug, true);
            setMenuItemVisible(R.id.category_menu_item_remove, false);
            setMenuItemVisible(R.id.category_menu_item_edit, false);
        } else if (count == 1) {
            setMenuItemVisible(R.id.category_menu_item_add, false);
            setMenuItemVisible(R.id.category_menu_item_debug, false);
            setMenuItemVisible(R.id.category_menu_item_edit, true);
            setMenuItemVisible(R.id.category_menu_item_remove, true);
        } else {
            setMenuItemVisible(R.id.category_menu_item_add, false);
            setMenuItemVisible(R.id.category_menu_item_debug, false);
            setMenuItemVisible(R.id.category_menu_item_edit, false);
            setMenuItemVisible(R.id.category_menu_item_remove, true);
        }
    }

    private class CategorySelectListener implements SelectableListAdapter.SelectListener {
        @Override
        public void onSelectEnter() { }
        @Override
        public void onSelectExit() { }

        @Override
        public void onSelect(int index) { setMenuBySelectedCount(); }

        @Override
        public void onUnSelect(int index) { setMenuBySelectedCount(); }
    }

    private class OnListItemClickListener extends OnRecyclerViewTouchListener {

        @Override
        public boolean onItemClick(View v, int index) {
            if (categoryListAdapter.isSelectMode()) {
                categoryListAdapter.toggleSelectItems(index);
                return true;
            }
            Category category = categoryListAdapter.get(index);
            listener.onAttempt(CategoryAttemptListener.Attempt.SHOW_EXPAND, category);
            return true;
        }

        @Override
        public boolean onItemLongClick(View v, int index) {
            v.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS);
            categoryListAdapter.toggleSelectItems(index);
            return true;
        }
    }

    @Override
    public void notifyItemListChanged(ListNotification.Notify notify, int index, Category category) {
        if (notify == Notify.UPDATED && categoryListAdapter.get(index) != category) {
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
            case CLEARED:
                categoryListAdapter.clear();
                break;
            default:
        }
    }

}