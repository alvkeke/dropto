package cn.alvkeke.dropto;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.DividerItemDecoration;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import cn.alvkeke.dropto.data.Category;
import cn.alvkeke.dropto.data.Global;
import cn.alvkeke.dropto.data.NoteItem;
import cn.alvkeke.dropto.debug.DebugFunction;
import cn.alvkeke.dropto.storage.DataBaseHelper;
import cn.alvkeke.dropto.ui.CategoryListAdapter;

public class CategoryActivity extends Fragment {

    private CategoryListAdapter categoryListAdapter;

    @Override
    public void onResume() {
        super.onResume();

        ArrayList<Category> categories = Global.getInstance().getCategories();
        for (int i=0; i<categories.size(); i++) {
            Category c = categories.get(i);
            if (c.needUpdate()) {
                categoryListAdapter.notifyItemChanged(i);
                c.setUpdated();
            }
        }
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.activity_category, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        Activity activity = requireActivity();

        activity.setTitle("");

        RecyclerView rlCategory = view.findViewById(R.id.rlist_category);

        ArrayList<Category> categories = Global.getInstance().getCategories();
        categoryListAdapter = new CategoryListAdapter(categories);
        categoryListAdapter.setCategories(categories);

        rlCategory.setAdapter(categoryListAdapter);
        RecyclerView.LayoutManager layoutManager = new LinearLayoutManager(activity);
        rlCategory.setLayoutManager(layoutManager);
        rlCategory.addItemDecoration(new DividerItemDecoration(activity, DividerItemDecoration.VERTICAL));
        categoryListAdapter.setItemClickListener(new onListItemClick());

    }

    class onListItemClick implements CategoryListAdapter.OnItemClickListener {

        @Override
        public void onItemClick(int index, View v) {
            Log.d(this.toString(), "Category clicked on " + index);
            Bundle bundle = new Bundle();
            bundle.putInt(NoteListActivity.CATEGORY_INDEX, index);
            NoteListActivity fragment = new NoteListActivity();
            fragment.setArguments(bundle);
            getParentFragmentManager().beginTransaction()
                    .replace(R.id.fragment_container, fragment)
                    .addToBackStack(null)
                    .commit();
        }
    }
}