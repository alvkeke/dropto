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

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.widget.Toolbar;

import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.bottomsheet.BottomSheetBehavior;
import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.google.android.material.bottomsheet.BottomSheetDialogFragment;

import cn.alvkeke.dropto.R;
import cn.alvkeke.dropto.data.Category;

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

        listener = (CategoryDetailEvent) requireContext();
        setPeekHeight();

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

    private void setPeekHeight() {
        // TODO: find another way, this seems ugly
        BottomSheetDialog dialog = (BottomSheetDialog) requireDialog();
        View sheet = dialog.findViewById(com.google.android.material.R.id.design_bottom_sheet);
        assert sheet != null;
        int displayHei= requireActivity().getResources().getDisplayMetrics().heightPixels;
        int peekHei = (int) (displayHei* 0.35);
        BottomSheetBehavior<View> behavior = BottomSheetBehavior.from(sheet);
        behavior.setPeekHeight(peekHei);

        ViewGroup.LayoutParams layoutParams = sheet.getLayoutParams();
        layoutParams.height = displayHei;
    }

    private void loadCategory() {
        etCategoryTitle.setText(category.getTitle());
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
