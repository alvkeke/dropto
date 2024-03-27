package cn.alvkeke.dropto.ui.fragment;

import android.app.AlertDialog;
import android.app.Dialog;
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

    @NonNull
    @Override
    public Dialog onCreateDialog(@Nullable Bundle savedInstanceState) {
        BottomSheetDialog dialog = (BottomSheetDialog) super.onCreateDialog(savedInstanceState);
        dialog.setOnShowListener(dialogInterface -> {
            // TODO: find another way, this seems ugly
            View sheet = dialog.findViewById(com.google.android.material.R.id.design_bottom_sheet);
            assert sheet != null;
            int displayHei= requireActivity().getResources().getDisplayMetrics().heightPixels;
            int peekHei = (int) (displayHei* 0.35);
            BottomSheetBehavior<View> behavior = BottomSheetBehavior.from(sheet);
            behavior.setPeekHeight(peekHei);

            ViewGroup.LayoutParams layoutParams = sheet.getLayoutParams();
            layoutParams.height = displayHei;
        });

        return dialog;
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
            btnDel.setOnLongClickListener(new DeleteButtonLongClick());
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

    private void showWarningDialog(Result result) {
        AlertDialog.Builder builder = new AlertDialog.Builder(requireContext());
        String title, message;
        switch (result) {
            case FULL_DELETE:
                title = "FULL_DELETE";
                message = "Do you really want to delete this category, include its all noteItems?";
                break;
            case DELETE:
                title = "DELETE";
                message = "Do you want to delete this category(keep noteItems)";
                break;
            default:
                return;
        }
        builder.setTitle(title)
                .setMessage(message)
                .setNegativeButton(R.string.string_cancel, null)
                .setPositiveButton(R.string.string_ok, (dialogInterface, i) -> {
                    listener.onCategoryDetailFinish(result, category);
                    finish();
                }).create().show();
    }

    private class DeleteButtonClick implements View.OnClickListener {
        @Override
        public void onClick(View view) {
            showWarningDialog(Result.DELETE);
        }
    }

    private class DeleteButtonLongClick implements View.OnLongClickListener {
        @Override
        public boolean onLongClick(View view) {
            showWarningDialog(Result.FULL_DELETE);
            return true;
        }
    }
}
