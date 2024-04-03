package cn.alvkeke.dropto.ui.fragment;

import android.content.Context;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.recyclerview.widget.DividerItemDecoration;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.bottomsheet.BottomSheetBehavior;
import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.google.android.material.bottomsheet.BottomSheetDialogFragment;

import java.util.ArrayList;

import cn.alvkeke.dropto.R;
import cn.alvkeke.dropto.data.Category;
import cn.alvkeke.dropto.data.Global;
import cn.alvkeke.dropto.ui.adapter.CategoryListAdapter;

public class CategorySelectorFragment extends BottomSheetDialogFragment {

    public interface CategorySelectListener {
        void onSelected(int index, Category category);
        void onError(String error);
        void onExit();
    }

    private final Context context;
    private final CategorySelectListener listener;

    public CategorySelectorFragment(Context context, CategorySelectListener listener) {
        this.context = context;
        this.listener = listener;
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_share_recv, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        RecyclerView rlCategory = view.findViewById(R.id.share_recv_rlist);
        setPeekHeight();

        ArrayList<Category> categories = Global.getInstance().getCategories();
        CategoryListAdapter adapter = new CategoryListAdapter(categories);
        rlCategory.setAdapter(adapter);

        RecyclerView.LayoutManager layoutManager = new LinearLayoutManager(context);
        rlCategory.setLayoutManager(layoutManager);
        rlCategory.addItemDecoration(new DividerItemDecoration(context, DividerItemDecoration.VERTICAL));

        adapter.setItemClickListener(new CategoryListAdapter.OnItemClickListener() {
            @Override
            public void onItemClick(int index, View v) {
                Category category = Global.getInstance().getCategory(index);
                if (category == null) {
                    listener.onError("Failed to get category in index " + index);
                    finish();
                    return;
                }

                listener.onSelected(index, category);
            }

            @Override
            public boolean onItemLongClick(int index, View v) {
                return false;
            }
        });
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

    private void finish() {
        this.dismiss();
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        listener.onExit();
    }
}