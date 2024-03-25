package cn.alvkeke.dropto.ui.fragment;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.DialogFragment;

import com.google.android.material.appbar.MaterialToolbar;

import cn.alvkeke.dropto.R;
import cn.alvkeke.dropto.data.Category;

public class CategoryDetailFragment extends DialogFragment{

    public enum Result {
        CREATE,
        DELETE,
    }

    public interface CategoryDetailEvent {
        void onCategoryDetailFinish(Result result, Category category);
    }

    private CategoryDetailEvent listener;
    private EditText etCategoryTitle;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_category_detail, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        MaterialToolbar toolbar = view.findViewById(R.id.category_detail_toolbar);
        Button btnOk = view.findViewById(R.id.category_detail_ok);
        Button btnCancel = view.findViewById(R.id.category_detail_cancel);
        etCategoryTitle = view.findViewById(R.id.category_detail_title);

        listener = (CategoryDetailEvent) requireContext();

        toolbar.setTitle("New Category:");
        btnOk.setOnClickListener(new CategoryDetailOk());
        btnCancel.setOnClickListener(new CategoryDetailCancel());
    }

    private void finish() {
        requireActivity().getSupportFragmentManager().beginTransaction()
                .remove(this).commit();
    }

    private class CategoryDetailOk implements View.OnClickListener {
        @Override
        public void onClick(View view) {
            String title = etCategoryTitle.getText().toString();
            // TODO: fix category type
            Category category = new Category(title, Category.Type.LOCAL_CATEGORY);
            listener.onCategoryDetailFinish(Result.CREATE, category);
            finish();
        }
    }

    private class CategoryDetailCancel implements View.OnClickListener {
        @Override
        public void onClick(View view) {
            finish();
        }
    }
}
