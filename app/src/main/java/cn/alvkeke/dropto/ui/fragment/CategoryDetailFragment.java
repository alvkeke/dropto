package cn.alvkeke.dropto.ui.fragment;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.widget.Toolbar;
import androidx.fragment.app.DialogFragment;

import com.google.android.material.appbar.MaterialToolbar;

import cn.alvkeke.dropto.R;
import cn.alvkeke.dropto.data.Category;

public class CategoryDetailFragment extends DialogFragment{

    public enum Result {
        CREATE,
        MODIFY,
        DELETE,
    }

    public interface CategoryDetailEvent {
        void onCategoryDetailFinish(Result result, Category category);
    }

    private CategoryDetailEvent listener;
    private EditText etCategoryTitle;
    private Category category;

    public CategoryDetailFragment(Category category) {
        this.category = category;
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_category_detail, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        MaterialToolbar toolbar = view.findViewById(R.id.category_detail_toolbar);
        etCategoryTitle = view.findViewById(R.id.category_detail_title);
        Button btnDel = view.findViewById(R.id.category_detail_delete);

        listener = (CategoryDetailEvent) requireContext();

        if (category == null) {
            toolbar.setTitle("New Category:");
            btnDel.setEnabled(false);
        } else {
            toolbar.setTitle("Edit Category:");
            loadCategory();
            btnDel.setOnClickListener(new DeleteButtonClick());
        }
        toolbar.inflateMenu(R.menu.fragment_category_detail);
        toolbar.setNavigationIcon(R.drawable.icon_common_cross);
        toolbar.setNavigationOnClickListener(view1 -> finish());

        toolbar.setOnMenuItemClickListener(new MenuListener());
    }

    private void loadCategory() {
        etCategoryTitle.setText(category.getTitle());
    }

    private void finish() {
        requireActivity().getSupportFragmentManager().beginTransaction()
                .remove(this).commit();
    }

    public void handleOkClick() {
        String title = etCategoryTitle.getText().toString();
        // TODO: fix category type
        if (category == null) {
            category = new Category(title, Category.Type.LOCAL_CATEGORY);
            listener.onCategoryDetailFinish(Result.CREATE, category);
        } else {
            category.setTitle(title);
            listener.onCategoryDetailFinish(Result.MODIFY, category);
        }
        finish();
    }

    private class MenuListener implements Toolbar.OnMenuItemClickListener {
        @Override
        public boolean onMenuItemClick(MenuItem item) {
            int menuId = item.getItemId();
            if (R.id.category_detail_menu_ok == menuId) {
                handleOkClick();
            } else {
                return false;
            }
            return true;
        }
    }

    private class DeleteButtonClick implements View.OnClickListener {
        @Override
        public void onClick(View view) {
            listener.onCategoryDetailFinish(Result.DELETE, category);
            finish();
        }
    }
}
