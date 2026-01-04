package cn.alvkeke.dropto.ui.fragment

import android.annotation.SuppressLint
import android.content.Context
import android.content.DialogInterface
import android.content.Intent
import android.os.Bundle
import android.view.HapticFeedbackConstants
import android.view.LayoutInflater
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.widget.Toolbar
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.DividerItemDecoration
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import cn.alvkeke.dropto.R
import cn.alvkeke.dropto.data.Category
import cn.alvkeke.dropto.ui.activity.MgmtActivity
import cn.alvkeke.dropto.ui.adapter.CategoryListAdapter
import cn.alvkeke.dropto.ui.adapter.SelectableListAdapter.SelectListener
import cn.alvkeke.dropto.ui.intf.CategoryDBAttemptListener
import cn.alvkeke.dropto.ui.intf.CategoryUIAttemptListener
import cn.alvkeke.dropto.ui.intf.ErrorMessageHandler
import cn.alvkeke.dropto.ui.intf.ListNotification
import cn.alvkeke.dropto.ui.intf.ListNotification.Notify
import cn.alvkeke.dropto.ui.listener.OnRecyclerViewTouchListener
import com.google.android.material.appbar.MaterialToolbar

class CategoryListFragment : Fragment(), ListNotification<Category> {
    private lateinit var context: Context
    private lateinit var dbListener: CategoryDBAttemptListener
    private lateinit var uiListener: CategoryUIAttemptListener
    private lateinit var categoryListAdapter: CategoryListAdapter
    private lateinit var toolbar: MaterialToolbar

    private var categories: ArrayList<Category>? = null
    fun setCategories(categories: ArrayList<Category>) {
        this.categories = categories
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        return inflater.inflate(R.layout.fragment_category_list, container, false)
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        context = requireContext()
        dbListener = context as CategoryDBAttemptListener
        uiListener = context as CategoryUIAttemptListener

        val rlCategory = view.findViewById<RecyclerView>(R.id.category_list_listview)
        toolbar = view.findViewById(R.id.category_list_toolbar)
        val statusBar = view.findViewById<View>(R.id.category_list_status_bar)
        val navigationBar = view.findViewById<View>(R.id.category_list_navigation_bar)
        setSystemBarHeight(view, statusBar, navigationBar)

        toolbar.setNavigationIcon(R.drawable.icon_common_menu)
        toolbar.setNavigationOnClickListener(OnCategoryListMenuClick())
        toolbar.inflateMenu(R.menu.category_toolbar)
        toolbar.setOnMenuItemClickListener(CategoryMenuListener())

        categoryListAdapter = CategoryListAdapter()
        if (categories != null) {
            categoryListAdapter.setList(categories!!)
        }
        categoryListAdapter.setSelectListener(CategorySelectListener())
        setMenuBySelectedCount()

        rlCategory.setAdapter(categoryListAdapter)
        val layoutManager: RecyclerView.LayoutManager = LinearLayoutManager(context)
        rlCategory.setLayoutManager(layoutManager)
        rlCategory.addItemDecoration(DividerItemDecoration(context, DividerItemDecoration.VERTICAL))
        rlCategory.setOnTouchListener(OnListItemClickListener())
    }

    private inner class OnCategoryListMenuClick : View.OnClickListener {
        override fun onClick(view: View) {
            val intent = Intent(context, MgmtActivity::class.java)
            startActivity(intent)
        }
    }

    private fun setSystemBarHeight(parent: View, status: View, navi: View) {
        ViewCompat.setOnApplyWindowInsetsListener(
            parent
        ) { _: View, winInsets: WindowInsetsCompat ->
            val statusHei: Int = winInsets.getInsets(WindowInsetsCompat.Type.statusBars()).top
            val naviHei: Int = winInsets.getInsets(WindowInsetsCompat.Type.navigationBars()).bottom
            status.layoutParams.height = statusHei
            navi.layoutParams.height = naviHei
            winInsets
        }
    }

    private fun throwErrorMessage(msg: String) {
        if (dbListener !is ErrorMessageHandler) return
        val handler = dbListener as ErrorMessageHandler
        handler.onError(msg)
    }

    private inner class CategoryMenuListener : Toolbar.OnMenuItemClickListener {
        override fun onMenuItemClick(item: MenuItem): Boolean {
            when (val menuId = item.itemId) {
                R.id.category_menu_item_add -> {
                    uiListener.onAttempt(CategoryUIAttemptListener.Attempt.SHOW_CREATE)
                }
                R.id.category_menu_item_edit -> {
                    val category = categoryListAdapter.selectedItems[0]
                    uiListener.onAttempt(
                        CategoryUIAttemptListener.Attempt.SHOW_DETAIL,
                        category
                    )
                    categoryListAdapter.clearSelectItems()
                }
                R.id.category_menu_item_remove -> {
                    val selected: ArrayList<Category> = categoryListAdapter.selectedItems
                    categoryListAdapter.clearSelectItems()
                    AlertDialog.Builder(context)
                        .setTitle(R.string.dialog_category_delete_selected_title)
                        .setMessage(R.string.dialog_category_delete_selected_message)
                        .setNegativeButton(R.string.string_cancel, null)
                        .setPositiveButton(R.string.string_ok
                        ) { _: DialogInterface, _: Int ->
                            for (c in selected) {
                                dbListener.onAttempt(
                                    CategoryDBAttemptListener.Attempt.REMOVE,
                                    c
                                )
                            }
                        }.create().show()
                }
                R.id.category_menu_item_debug -> {
                    uiListener.onAttempt(CategoryUIAttemptListener.Attempt.DEBUG_ADD_DATA)
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
        val count = categoryListAdapter.selectedCount
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
            if (categoryListAdapter.isSelectMode) {
                categoryListAdapter.toggleSelectItems(index)
                return true
            }
            val category = categoryListAdapter.get(index)
            uiListener.onAttempt(CategoryUIAttemptListener.Attempt.SHOW_EXPAND, category)
            return true
        }

        override fun onItemLongClick(v: View, index: Int): Boolean {
            v.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
            categoryListAdapter.toggleSelectItems(index)
            return true
        }
    }

    override fun notifyItemListChanged(notify: Notify, index: Int, itemObj: Category) {
        if (notify == Notify.UPDATED && categoryListAdapter.get(index) != itemObj) {
            throwErrorMessage("target Category not exist")
            return
        }

        when (notify) {
            Notify.INSERTED -> categoryListAdapter.add(index, itemObj)
            Notify.UPDATED -> categoryListAdapter.update(itemObj)
            Notify.REMOVED -> categoryListAdapter.remove(itemObj)
            Notify.CLEARED -> categoryListAdapter.clear()
        }
    }
}