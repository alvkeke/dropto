package cn.alvkeke.dropto.ui.adapter;

import android.util.Log;

import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentActivity;
import androidx.viewpager2.adapter.FragmentStateAdapter;

import cn.alvkeke.dropto.data.Category;
import cn.alvkeke.dropto.data.NoteItem;
import cn.alvkeke.dropto.ui.fragment.CategoryFragment;
import cn.alvkeke.dropto.ui.fragment.NoteDetailFragment;
import cn.alvkeke.dropto.ui.fragment.NoteListFragment;

public class MainFragmentAdapter extends FragmentStateAdapter {

    private final Fragment[] fragments = {
            new CategoryFragment(),
            new NoteListFragment(),
            new NoteDetailFragment(),
    };

    private final CategoryFragment categoryFragment = (CategoryFragment) fragments[0];
    private final NoteListFragment noteListFragment = (NoteListFragment) fragments[1];
    private final NoteDetailFragment noteDetailFragment = (NoteDetailFragment) fragments[2];

    public MainFragmentAdapter(@NonNull FragmentActivity fragmentActivity) {
        super(fragmentActivity);
    }

    @NonNull
    @Override
    public Fragment createFragment(int i) {
        if (i < fragments.length) {
            return fragments[i];
        }
        return fragments[0];
    }

    @Override
    public int getItemCount() {
        return fragments.length;
    }

    public void setCurrentCategory(int index, Category c) {
        noteListFragment.setCategory(c);
    }

    public void setCurrentNote(int index, NoteItem e) {
        noteDetailFragment.setItem(index, e);
    }
}
