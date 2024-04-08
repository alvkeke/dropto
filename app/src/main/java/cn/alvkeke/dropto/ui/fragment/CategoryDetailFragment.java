package cn.alvkeke.dropto.ui.fragment;

import android.app.AlertDialog;
import android.content.Context;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.Spinner;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.widget.Toolbar;

import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.bottomsheet.BottomSheetBehavior;
import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.google.android.material.bottomsheet.BottomSheetDialogFragment;

import cn.alvkeke.dropto.R;
import cn.alvkeke.dropto.data.Category;
import cn.alvkeke.dropto.ui.adapter.CategoryTypeSpinnerAdapter;

public class CategoryDetailFragment extends BottomSheetDialogFragment {

    public enum Result {
        CREATE,
        MODIFY,
        DELETE,
        FULL_DELETE,
    }

    public interface CategoryDetailEvent {
        void onCategoryDetailFinish(Result result, Category category);
    }

    private CategoryDetailEvent listener;
    private EditText etCategoryTitle;
    private Spinner spinnerType;
    private Category category;

    public CategoryDetailFragment() {

    }

    public CategoryDetailFragment(Category category) {
        this.category = category;
    }

    public void setCategory(Category category) {
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
        spinnerType = view.findViewById(R.id.category_detail_type_spinner);

        listener = (CategoryDetailEvent) requireContext();
        setPeekHeight(view);
        fillTypeSpinner();

        if (category == null) {
            toolbar.setTitle("New Category:");
            toolbar.setNavigationIcon(R.drawable.icon_common_cross);
            toolbar.setNavigationOnClickListener(view1 -> finish());
        } else {
            toolbar.setTitle("Edit Category:");
            toolbar.setNavigationIcon(R.drawable.icon_common_remove);
            loadCategory();
            toolbar.setNavigationOnClickListener(new DeleteButtonClick());
        }
        toolbar.inflateMenu(R.menu.fragment_category_detail);
        toolbar.setOnMenuItemClickListener(new MenuListener());
    }

    private void fillTypeSpinner() {
        CategoryTypeSpinnerAdapter adapter = new CategoryTypeSpinnerAdapter(requireContext(),
                R.layout.spinner_item_category_type, Category.Type.values());
        spinnerType.setAdapter(adapter);
    }

    private void setPeekHeight(View view) {
        BottomSheetDialog dialog = (BottomSheetDialog) requireDialog();
        int displayHei= requireActivity().getResources().getDisplayMetrics().heightPixels;
        int peekHei = displayHei * 35 / 100;
        BottomSheetBehavior<FrameLayout> behavior = dialog.getBehavior();
        behavior.setPeekHeight(peekHei);

        ViewGroup.LayoutParams layoutParams = view.getLayoutParams();
        layoutParams.height = displayHei;
    }

    private void loadCategory() {
        etCategoryTitle.setText(category.getTitle());
        spinnerType.setSelection(category.getType().ordinal());
    }

    private void finish() {
        this.dismiss();
    }

    public void handleOkClick() {
        String title = etCategoryTitle.getText().toString();
        if (title.isEmpty()) {
            finish();
            return;
        }
        Category.Type type = (Category.Type) spinnerType.getSelectedItem();
        if (category == null) {
            category = new Category(title, type);
            listener.onCategoryDetailFinish(Result.CREATE, category);
        } else {
            category.setTitle(title);
            category.setType(type);
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

    private void showDeletingConfirm() {
        Context context = requireContext();
        AlertDialog.Builder builder = new AlertDialog.Builder(context);
        LayoutInflater inflater = LayoutInflater.from(context);
        View dialog = inflater.inflate(R.layout.dialog_category_deleting, null);
        builder.setView(dialog);

        builder.setTitle("Delete category");
        builder.setNegativeButton(R.string.string_cancel, null);
        builder.setPositiveButton(R.string.string_ok, (dialogInterface, i) -> {
            CheckBox checkBox = dialog.findViewById(R.id.dialog_category_delete_checkbox);
            boolean full = checkBox.isChecked();
            listener.onCategoryDetailFinish(full ? Result.FULL_DELETE : Result.DELETE, category);
            finish();
        });

        builder.create().show();
    }

    private class DeleteButtonClick implements View.OnClickListener {
        @Override
        public void onClick(View view) {
            showDeletingConfirm();
        }
    }

}
