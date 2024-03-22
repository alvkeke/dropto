package cn.alvkeke.dropto.ui.adapter;

import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentActivity;
import androidx.viewpager2.adapter.FragmentStateAdapter;

import java.util.HashMap;
import java.util.Objects;

import cn.alvkeke.dropto.data.Category;
import cn.alvkeke.dropto.data.NoteItem;
import cn.alvkeke.dropto.ui.fragment.CategoryFragment;
import cn.alvkeke.dropto.ui.fragment.NoteDetailFragment;
import cn.alvkeke.dropto.ui.fragment.NoteListFragment;

public class MainFragmentAdapter extends FragmentStateAdapter {

    public enum FragmentType {
        CategoryList,
        NoteList,
        NoteDetail,
    }

    private final HashMap<Integer, Fragment> fragments = new HashMap<>();

    public MainFragmentAdapter(@NonNull FragmentActivity fragmentActivity) {
        super(fragmentActivity);
        CategoryFragment fragment = new CategoryFragment();
        fragments.put(FragmentType.CategoryList.ordinal(), fragment);
    }

    @NonNull
    @Override
    public Fragment createFragment(int i) {
        Fragment fragment = fragments.get(i);
        if (fragment == null)
            return Objects.requireNonNull(fragments.get(0));
        return fragment;
    }

    @Override
    public int getItemCount() {
        return fragments.size();
    }

    public void createNoteListFragment(int index, Category c) {
        NoteListFragment fragment = new NoteListFragment(index, c);
        removeFragment(FragmentType.NoteList);
        fragments.put(FragmentType.NoteList.ordinal(), fragment);
        notifyItemChanged(FragmentType.NoteList.ordinal());
    }

    public void createNoteDetailFragment(int index, NoteItem item) {
        NoteDetailFragment fragment = new NoteDetailFragment(index, item);
        removeFragment(FragmentType.NoteDetail);
        fragments.put(FragmentType.NoteDetail.ordinal(), fragment);
        notifyItemChanged(FragmentType.NoteDetail.ordinal());
    }

    public void removeFragment(FragmentType type) {
        Fragment fragment = fragments.get(type.ordinal());
        if (fragment == null) return;
        fragments.remove(type.ordinal());
        notifyItemRemoved(type.ordinal());
    }

    public NoteListFragment getNoteListFragment() {
        return (NoteListFragment) fragments.get(FragmentType.NoteList.ordinal());
    }

}
