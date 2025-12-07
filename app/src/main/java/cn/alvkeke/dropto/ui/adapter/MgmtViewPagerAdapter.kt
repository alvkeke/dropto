package cn.alvkeke.dropto.ui.adapter

import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentActivity
import androidx.viewpager2.adapter.FragmentStateAdapter
import cn.alvkeke.dropto.ui.fragment.MgmtStorageFragment

class MgmtViewPagerAdapter(fragmentActivity: FragmentActivity) :
    FragmentStateAdapter(fragmentActivity) {
    override fun createFragment(i: Int): Fragment {
        return MgmtStorageFragment()
    }

    override fun getItemCount(): Int {
        return 1
    }
}
