package cn.alvkeke.dropto.ui.fragment

import android.annotation.SuppressLint
import android.content.Context
import android.os.Bundle
import android.util.Log
import android.view.HapticFeedbackConstants
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.viewpager2.adapter.FragmentStateAdapter
import androidx.viewpager2.widget.ViewPager2
import cn.alvkeke.dropto.DroptoApplication
import cn.alvkeke.dropto.R
import cn.alvkeke.dropto.data.Category
import cn.alvkeke.dropto.data.NoteItem
import cn.alvkeke.dropto.service.Task
import cn.alvkeke.dropto.storage.DataLoader
import cn.alvkeke.dropto.ui.UserInterfaceHelper
import cn.alvkeke.dropto.ui.UserInterfaceHelper.animateRemoveFromParent
import cn.alvkeke.dropto.ui.adapter.NoteListAdapter
import cn.alvkeke.dropto.ui.comonent.SelectableRecyclerView
import cn.alvkeke.dropto.ui.fragment.NoteListFragment.Companion.TAG
import cn.alvkeke.dropto.ui.intf.FragmentOnBackListener
import cn.alvkeke.dropto.ui.listener.OnRecyclerViewTouchListener
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.tabs.TabLayout
import com.google.android.material.tabs.TabLayoutMediator
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

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

    class SimpleNoteListFragment(val category: Category) : Fragment(), Task.ResultListener {
        private val app: DroptoApplication
            get() = requireActivity().application as DroptoApplication

        val adapter = NoteListAdapter()
        lateinit var noteList : SelectableRecyclerView

        override fun onCreateView(
            inflater: LayoutInflater,
            container: ViewGroup?,
            savedInstanceState: Bundle?
        ): View {
            noteList = SelectableRecyclerView(requireContext())
            noteList.layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
            noteList.overScrollMode = View.OVER_SCROLL_NEVER
            return noteList
        }

        @SuppressLint("ClickableViewAccessibility")
        override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
            super.onViewCreated(view, savedInstanceState)

            adapter.showDeleted = true
            DataLoader.loadCategoryNotes(requireContext(), category)
            adapter.setList(category.noteItems)
            noteList.adapter = adapter
            val layoutManager = LinearLayoutManager(requireContext())
            layoutManager.setReverseLayout(true)
            noteList.layoutManager = layoutManager

            noteList.setOnTouchListener(OnListTouchListener(requireContext()))
            app.addTaskListener(this)
        }

        override fun onDestroyView() {
            super.onDestroyView()
            app.delTaskListener(this)
        }

        override fun onTaskFinish(task: Task) {
            if (task.type != Task.Type.NoteItem) {
                Log.v(TAG, "ignore non-note-item task finish: ${task.type}")
                return
            }

            val item = task.taskObj as NoteItem
            if (item.categoryId != category.id) {
                Log.d(TAG, "target NoteItem not exist in current category, ignore task finish")
                return
            }
            val index = task.result

            viewLifecycleOwner.lifecycleScope.launch(Dispatchers.Main) {
                when (task.job) {
                    Task.Job.CREATE -> {
                        adapter.add(index, item)
                        noteList.smoothScrollToPosition(0)
                    }
                    Task.Job.REMOVE,
                    Task.Job.RESTORE,
                    Task.Job.UPDATE -> {
                        adapter.update(item)
                    }
                }
            }
        }

        private inner class OnListTouchListener(context: Context) : OnRecyclerViewTouchListener(context) {
            override fun onItemLongClick(v: View, index: Int): Boolean {
                v.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
                val item = adapter.get(index)
                if (item.isDeleted) {
                    app.service?.queueTask(Task.restoreNote(item))
                } else {
                    app.service?.queueTask(Task.removeNote(item))
                }

                return true
            }
        }

    }
}