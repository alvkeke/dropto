package cn.alvkeke.dropto.ui.fragment

import android.annotation.SuppressLint
import android.content.Context
import android.content.DialogInterface
import android.os.Bundle
import android.util.Log
import android.view.HapticFeedbackConstants
import android.view.LayoutInflater
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.widget.Toolbar
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.DividerItemDecoration
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import cn.alvkeke.dropto.DroptoApplication
import cn.alvkeke.dropto.R
import cn.alvkeke.dropto.data.AttachmentFile
import cn.alvkeke.dropto.data.Category
import cn.alvkeke.dropto.data.NoteItem
import cn.alvkeke.dropto.debug.DebugFunction.tryExtractResImages
import cn.alvkeke.dropto.service.CoreServiceListener
import cn.alvkeke.dropto.storage.DataLoader
import cn.alvkeke.dropto.storage.FileHelper
import cn.alvkeke.dropto.ui.UserInterfaceHelper
import cn.alvkeke.dropto.ui.UserInterfaceHelper.startFragmentAnime
import cn.alvkeke.dropto.ui.activity.MainViewModel
import cn.alvkeke.dropto.ui.adapter.CategoryListAdapter
import cn.alvkeke.dropto.ui.comonent.SelectableRecyclerView
import cn.alvkeke.dropto.ui.comonent.SelectableRecyclerView.SelectListener
import cn.alvkeke.dropto.ui.listener.OnRecyclerViewTouchListener
import com.google.android.material.appbar.MaterialToolbar
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.Random

class CategoryListFragment : Fragment(), CoreServiceListener {
    private val app: DroptoApplication
        get() = requireActivity().application as DroptoApplication
    private lateinit var context: Context
    private lateinit var viewModel: MainViewModel
    private lateinit var rlCategory: SelectableRecyclerView
    private lateinit var categoryListAdapter: CategoryListAdapter
    private lateinit var toolbar: MaterialToolbar

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        return inflater.inflate(R.layout.fragment_category_list, container, false)
    }

    override fun onStart() {
        super.onStart()
        app.addTaskListener(this)
    }

    override fun onStop() {
        app.delTaskListener(this)
        super.onStop()
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        context = requireContext()
        viewModel = ViewModelProvider(requireActivity())[MainViewModel::class.java]

        rlCategory = view.findViewById(R.id.category_list_listview)
        toolbar = view.findViewById(R.id.category_list_toolbar)
        val statusBar = view.findViewById<View>(R.id.category_list_status_bar)
        val navigationBar = view.findViewById<View>(R.id.category_list_navigation_bar)
        UserInterfaceHelper.setSystemBarHeight(view, statusBar, navigationBar)

        toolbar.setNavigationIcon(R.drawable.icon_common_menu)
        toolbar.setNavigationOnClickListener(OnCategoryListMenuClick())
        toolbar.inflateMenu(R.menu.category_toolbar)
        toolbar.setOnMenuItemClickListener(CategoryMenuListener())

        categoryListAdapter = CategoryListAdapter()
        viewModel.categories.observe(viewLifecycleOwner) { newCategories ->
            categoryListAdapter.setList(newCategories)
        }

        rlCategory.setSelectListener(CategorySelectListener())
        setMenuBySelectedCount()

        rlCategory.setAdapter(categoryListAdapter)
        val layoutManager: RecyclerView.LayoutManager = LinearLayoutManager(context)
        rlCategory.setLayoutManager(layoutManager)
        rlCategory.addItemDecoration(DividerItemDecoration(context, DividerItemDecoration.VERTICAL))
        rlCategory.setOnTouchListener(OnListItemClickListener())
    }

    private var mgmtPageFragment: MgmtPageFragment? = null
    private inner class OnCategoryListMenuClick : View.OnClickListener {
        override fun onClick(view: View) {
            if (mgmtPageFragment == null) {
                mgmtPageFragment = MgmtPageFragment()
            }
            parentFragmentManager.startFragmentAnime(
                mgmtPageFragment!!,
                R.id.main_container,
                false
            )
        }
    }

    private fun throwErrorMessage(msg: String) {
        Log.e(TAG, msg)
        Toast.makeText(
            context,
            "Error: $msg",
            Toast.LENGTH_SHORT
        ).show()
    }

    private fun getSelectedCategory(): ArrayList<Category> {
        val items = ArrayList<Category>()
        for (i in rlCategory.selectedIndexes) {
            items.add(categoryListAdapter.get(i))
        }
        return items
    }

    private fun getFirstSelectedCategory(): Category? {
        for (i in rlCategory.selectedIndexes) {
            return categoryListAdapter.get(i)
        }
        return null
    }

    private inner class CategoryMenuListener : Toolbar.OnMenuItemClickListener {
        override fun onMenuItemClick(item: MenuItem): Boolean {
            when (val menuId = item.itemId) {
                R.id.category_menu_item_add -> {
                    showCategoryCreatingDialog()
                }
                R.id.category_menu_item_edit -> {
                    val category = getFirstSelectedCategory() ?: run {
                        Log.e(TAG, "edit menu clicked but no category selected")
                        return false
                    }
                    handleCategoryDetailShow(category)
                    rlCategory.unSelectAllItems()
                }
                R.id.category_menu_item_remove -> {
                    val selected: ArrayList<Category> = getSelectedCategory()
                    rlCategory.unSelectAllItems()
                    AlertDialog.Builder(context)
                        .setTitle(R.string.dialog_category_delete_selected_title)
                        .setMessage(R.string.dialog_category_delete_selected_message)
                        .setNegativeButton(R.string.string_cancel, null)
                        .setPositiveButton(R.string.string_ok
                        ) { _: DialogInterface, _: Int ->
                            for (c in selected) {
                                app.service?.removeCategory(c)
                            }
                        }.create().show()
                }
                R.id.category_menu_item_debug -> {
                    addDebugData()
                }
                else -> {
                    throwErrorMessage("Unknown menu id: $menuId")
                    return false
                }
            }

            setMenuBySelectedCount()
            return true
        }
    }

    private fun setMenuItemVisible(id: Int, visible: Boolean) {
        val menuItem = toolbar.getMenu().findItem(id)
        menuItem.isVisible = visible
    }

    private fun setMenuBySelectedCount() {
        val count = rlCategory.selectedCount
        when (count) {
            0 -> {
                setMenuItemVisible(R.id.category_menu_item_add, true)
                setMenuItemVisible(R.id.category_menu_item_debug, true)
                setMenuItemVisible(R.id.category_menu_item_remove, false)
                setMenuItemVisible(R.id.category_menu_item_edit, false)
            }
            1 -> {
                setMenuItemVisible(R.id.category_menu_item_add, false)
                setMenuItemVisible(R.id.category_menu_item_debug, false)
                setMenuItemVisible(R.id.category_menu_item_edit, true)
                setMenuItemVisible(R.id.category_menu_item_remove, true)
            }
            else -> {
                setMenuItemVisible(R.id.category_menu_item_add, false)
                setMenuItemVisible(R.id.category_menu_item_debug, false)
                setMenuItemVisible(R.id.category_menu_item_edit, false)
                setMenuItemVisible(R.id.category_menu_item_remove, true)
            }
        }
    }

    private inner class CategorySelectListener : SelectListener {
        override fun onSelectEnter() {}
        override fun onSelectExit() {}

        override fun onSelect(index: Int) {
            setMenuBySelectedCount()
        }

        override fun onUnSelect(index: Int) {
            setMenuBySelectedCount()
        }
    }

    private inner class OnListItemClickListener : OnRecyclerViewTouchListener(context) {
        override fun onItemClick(v: View, index: Int): Boolean {
            if (rlCategory.isSelectMode) {
                rlCategory.toggleSelectItems(index)
                return true
            }
            val category = categoryListAdapter.get(index)
            handleCategoryExpand(category)
            return true
        }

        override fun onItemLongClick(v: View, index: Int, rawX: Float, rawY: Float): Boolean {
            v.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
            rlCategory.toggleSelectItems(index)
            return true
        }
    }

    private val noteListFragment: NoteListFragment by lazy {
        NoteListFragment()
    }
    fun handleCategoryExpand(category: Category) {
        val ret = DataLoader.loadCategoryNotes(context, category)
        if (!ret) {
            Log.e(this.toString(), "Failed to get noteList from database")
        }
        viewModel.setCategory(category)
        parentFragmentManager.startFragmentAnime(
            noteListFragment,
            R.id.main_container,
        )
    }

    private val categoryDetailFragment: CategoryDetailFragment by lazy {
        CategoryDetailFragment()
    }
    fun showCategoryCreatingDialog() {
        categoryDetailFragment.setCategory(null)
        parentFragmentManager.beginTransaction()
            .add(categoryDetailFragment, null)
            .commit()
    }

    private fun handleCategoryDetailShow(c: Category) {
        categoryDetailFragment.setCategory(c)
        parentFragmentManager.beginTransaction()
            .add(categoryDetailFragment, null)
            .commit()
    }

    private fun addDebugData() {
        app.service?.createCategory(Category("Local(Debug)", Category.Type.LOCAL_CATEGORY))
        app.service?.createCategory(Category("REMOTE USERS", Category.Type.REMOTE_USERS))
        app.service?.createCategory(Category("REMOTE SELF DEVICE", Category.Type.REMOTE_SELF_DEV))

        viewLifecycleOwner.lifecycleScope.launch(Dispatchers.IO) {
            delay(1000)
            val imgFolder = FileHelper.attachmentStorage
            val imgFiles = tryExtractResImages(context, imgFolder) ?: return@launch
            val categories: ArrayList<Category> = DataLoader.categories
            if (categories.isEmpty()) return@launch

            val r = Random()
            val cateId = categories[r.nextInt(categories.size)].id
            var idx = 0
            val now = System.currentTimeMillis()
            val millisInDay = 24 * 60 * 60 * 1000L
            for (i in 0..14) {
                // Spread notes over the past 15 days
                val daysAgo = 14 - i
                val createTime = now - daysAgo * millisInDay
                val e = NoteItem("ITEM$i$i", createTime)
                e.categoryId = cateId
                if (r.nextBoolean()) {
                    e.isEdited = true
                }
                if (idx < imgFiles.size && r.nextBoolean()) {
                    val imgFile = imgFiles[idx]
                    idx++
                    if (imgFile.exists()) {
                        val imageFile = AttachmentFile.from(
                            imgFile, imgFile.name, AttachmentFile.Type.MEDIA)
                        e.attachments.add(imageFile)
                    }
                }
                app.service?.createNote(e)
            }
        }
    }

    override fun onCategoryCreated(
        result: Int,
        category: Category,
    ) {
        categoryListAdapter.add(result, category)
    }

    override fun onCategoryUpdated(
        result: Int,
        category: Category,
    ) {
        categoryListAdapter.update(category)
    }

    override fun onCategoryRemoved(
        result: Int,
        category: Category,
    ) {
        categoryListAdapter.remove(category)
    }

    companion object {
        const val TAG = "CategoryListFragment"
    }
}