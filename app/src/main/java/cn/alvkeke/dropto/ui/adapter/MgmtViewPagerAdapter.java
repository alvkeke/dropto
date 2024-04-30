package cn.alvkeke.dropto.ui.adapter;

import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentActivity;
import androidx.viewpager2.adapter.FragmentStateAdapter;

import cn.alvkeke.dropto.ui.fragment.MgmtStorageFragment;

public class MgmtViewPagerAdapter extends FragmentStateAdapter {

    public MgmtViewPagerAdapter(@NonNull FragmentActivity fragmentActivity) {
        super(fragmentActivity);
    }

    @NonNull
    @Override
    public Fragment createFragment(int i) {
        return new MgmtStorageFragment();
    }

    @Override
    public int getItemCount() {
        return 1;
    }

}
