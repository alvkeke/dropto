package cn.alvkeke.dropto.ui.adapter;

import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentActivity;
import androidx.viewpager2.adapter.FragmentStateAdapter;

import cn.alvkeke.dropto.data.Category;
import cn.alvkeke.dropto.ui.fragment.CategoryFragment;
import cn.alvkeke.dropto.ui.fragment.NoteListFragment;

public class MainFragmentAdapter extends FragmentStateAdapter {

    public enum FragmentType {
        Category,
        NoteList,
    }

    private final Fragment[] fragments = new Fragment[2];

    public MainFragmentAdapter(@NonNull FragmentActivity fragmentActivity) {
        super(fragmentActivity);
        CategoryFragment fragment = new CategoryFragment();
        fragments[FragmentType.Category.ordinal()] = fragment;
    }

    @NonNull
    @Override
    public Fragment createFragment(int i) {
        assert i < fragments.length;
        Fragment fragment = fragments[i];
        if (fragment == null)
            return fragments[FragmentType.Category.ordinal()];
        return fragment;
    }

    @Override
    public int getItemCount() {
        int n = 0;
        for (Fragment f : fragments) {
            if (f ==null) break;
            n++;
        }
        return n;
    }

    public void createNoteListFragment(Category c) {
        NoteListFragment fragment = new NoteListFragment(c);
        removeFragment(FragmentType.NoteList);
        fragments[FragmentType.NoteList.ordinal()] = fragment;
        notifyItemChanged(FragmentType.NoteList.ordinal());
    }

    public void removeFragment(FragmentType type) {
        Fragment fragment = fragments[type.ordinal()];
        if (fragment == null) return;
        fragments[type.ordinal()].onDestroy();
        fragments[type.ordinal()] = null;
        // TODO: release the memory for fragment that no-need anymore, or try to not create new fragment
        notifyItemRemoved(type.ordinal());
    }

    public NoteListFragment getNoteListFragment() {
        return (NoteListFragment) fragments[FragmentType.NoteList.ordinal()];
    }

    public CategoryFragment getCategoryFragment() {
        return (CategoryFragment) fragments[FragmentType.Category.ordinal()];
    }

    public Fragment getFragmentAt(int pos) {
        return fragments[pos];
    }
}
