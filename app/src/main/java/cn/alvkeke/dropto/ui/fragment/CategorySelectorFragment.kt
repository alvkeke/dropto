package cn.alvkeke.dropto.ui.fragment;

import android.annotation.SuppressLint;
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
import cn.alvkeke.dropto.ui.adapter.CategoryListAdapter;
import cn.alvkeke.dropto.ui.intf.ListNotification;
import cn.alvkeke.dropto.ui.listener.OnRecyclerViewTouchListener;

public class CategorySelectorFragment extends BottomSheetDialogFragment implements ListNotification<Category> {

    public interface CategorySelectListener {
        void onSelected(int index, Category category);
        void onError(String error);
    }

    private CategorySelectListener listener;
    private CategoryListAdapter categoryListAdapter;

    public CategorySelectorFragment() { }

    private ArrayList<Category> categories = null;
    public void setCategories(ArrayList<Category> categories) {
        this.categories = categories;
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_share_recv, container, false);
    }

    @SuppressLint("ClickableViewAccessibility")
    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        Context context = requireContext();
        listener = (CategorySelectListener) context;

        RecyclerView rlCategory = view.findViewById(R.id.share_recv_rlist);
        setPeekHeight();

        categoryListAdapter = new CategoryListAdapter();
        rlCategory.setAdapter(categoryListAdapter);
        if (categories != null) {
            categoryListAdapter.setList(categories);
        }

        RecyclerView.LayoutManager layoutManager = new LinearLayoutManager(context);
        rlCategory.setLayoutManager(layoutManager);
        rlCategory.addItemDecoration(new DividerItemDecoration(context, DividerItemDecoration.VERTICAL));

        rlCategory.setOnTouchListener(new OnRecyclerViewTouchListener(){

            @Override
            public boolean onItemClick(View v, int index) {
                Category category = categoryListAdapter.get(index);
                if (category == null) {
                    listener.onError("Failed to get category in index " + index);
                    finish();
                    return false;
                }

                listener.onSelected(index, category);
                finish();
                return true;
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
    public void notifyItemListChanged(Notify notify, int index, Category category) {
        if (categoryListAdapter == null) {
            return;
        }

        if (notify == Notify.UPDATED && categoryListAdapter.get(index) != category) {
            listener.onError("target Category not exist");
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