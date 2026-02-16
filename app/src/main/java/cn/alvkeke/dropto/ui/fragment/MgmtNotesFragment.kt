package cn.alvkeke.dropto.ui.fragment

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.viewpager2.adapter.FragmentStateAdapter
import androidx.viewpager2.widget.ViewPager2
import cn.alvkeke.dropto.R
import cn.alvkeke.dropto.data.Category
import cn.alvkeke.dropto.storage.DataLoader
import cn.alvkeke.dropto.ui.UserInterfaceHelper
import cn.alvkeke.dropto.ui.UserInterfaceHelper.animateRemoveFromParent
import cn.alvkeke.dropto.ui.adapter.NoteListAdapter
import cn.alvkeke.dropto.ui.intf.FragmentOnBackListener
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.tabs.TabLayout
import com.google.android.material.tabs.TabLayoutMediator

class MgmtNotesFragment: Fragment(), FragmentOnBackListener {

    private lateinit var fragmentView: View
    private lateinit var statusBar: View
    private lateinit var toolbar: MaterialToolbar
    private lateinit var navBar: View
    private lateinit var viewPager: ViewPager2
    private lateinit var pagerAdapter: NoteCategoryPagerAdapter
    private lateinit var tabLayout: TabLayout
    private lateinit var tabMediator: TabLayoutMediator


    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        fragmentView = inflater.inflate(
            R.layout.fragment_mgmt_notes, container, false
        )
        return fragmentView
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        statusBar = view.findViewById(R.id.mgmt_note_status_bar)
        toolbar = view.findViewById(R.id.mgmt_note_toolbar)
        navBar = view.findViewById(R.id.mgmt_note_navigation_bar)

        UserInterfaceHelper.setSystemBarHeight(view, statusBar, navBar)

        toolbar.setNavigationOnClickListener { finish() }

        tabLayout = view.findViewById(R.id.mgmt_note_tab)
        viewPager = view.findViewById(R.id.mgmt_note_viewpager)


        val categories = DataLoader.loadCategories(requireContext())
        pagerAdapter = NoteCategoryPagerAdapter(this, categories)
        viewPager.adapter = pagerAdapter as RecyclerView.Adapter<*>
        tabMediator = TabLayoutMediator(tabLayout, viewPager) { tab, position ->
            tab.text = categories[position].title
        }
        tabMediator.attach()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        tabMediator.detach()
    }

    override fun onBackPressed(): Boolean {
        finish()
        return true
    }

    @JvmOverloads
    fun finish(duration: Long = 200) {
        this.animateRemoveFromParent(
            fragmentView,
            duration,
            false
        )
    }

    private class NoteCategoryPagerAdapter(
        fragment: Fragment,
        private val categories: List<Category>
    ) : FragmentStateAdapter(fragment) {
        override fun getItemCount(): Int = categories.size

        override fun createFragment(position: Int): Fragment{
            val category = categories[position]
            val fragment = SimpleNoteListFragment(category)

            return fragment
        }

    }

    class SimpleNoteListFragment(val category: Category) : Fragment() {
        val adapter = NoteListAdapter()
        lateinit var noteList : RecyclerView
        override fun onCreateView(
            inflater: LayoutInflater,
            container: ViewGroup?,
            savedInstanceState: Bundle?
        ): View {
            noteList = RecyclerView(requireContext())
            noteList.layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
            noteList.overScrollMode = View.OVER_SCROLL_NEVER
            return noteList
        }

        override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
            super.onViewCreated(view, savedInstanceState)

            adapter.showDeleted = true
            DataLoader.loadCategoryNotes(requireContext(), category)
            adapter.setList(category.noteItems)
            noteList.adapter = adapter
            val layoutManager = LinearLayoutManager(requireContext())
            layoutManager.setReverseLayout(true)
            noteList.layoutManager = layoutManager
        }
    }
}