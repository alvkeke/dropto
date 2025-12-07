package cn.alvkeke.dropto.ui.fragment

import android.app.AlertDialog
import android.content.DialogInterface
import android.os.Bundle
import android.view.LayoutInflater
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.Spinner
import androidx.appcompat.widget.Toolbar
import cn.alvkeke.dropto.R
import cn.alvkeke.dropto.data.Category
import cn.alvkeke.dropto.ui.adapter.CategoryTypeSpinnerAdapter
import cn.alvkeke.dropto.ui.intf.CategoryAttemptListener
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.bottomsheet.BottomSheetDialogFragment

class CategoryDetailFragment : BottomSheetDialogFragment {
    private lateinit var listener: CategoryAttemptListener
    private lateinit var etCategoryTitle: EditText
    private lateinit var spinnerType: Spinner
    private var category: Category? = null

    constructor()

    constructor(category: Category?) {
        this.category = category
    }

    fun setCategory(category: Category) {
        this.category = category
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        return inflater.inflate(R.layout.fragment_category_detail, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val toolbar = view.findViewById<MaterialToolbar>(R.id.category_detail_toolbar)
        etCategoryTitle = view.findViewById(R.id.category_detail_title)
        spinnerType = view.findViewById(R.id.category_detail_type_spinner)
        listener = requireContext() as CategoryAttemptListener

        setPeekHeight(view)
        fillTypeSpinner()

        if (category == null) {
            toolbar.setTitle("New Category:")
            toolbar.setNavigationIcon(R.drawable.icon_common_cross)
            toolbar.setNavigationOnClickListener { _: View -> finish() }
        } else {
            toolbar.setTitle("Edit Category:")
            toolbar.setNavigationIcon(R.drawable.icon_common_remove)
            loadCategory()
            toolbar.setNavigationOnClickListener(DeleteButtonClick())
        }
        toolbar.inflateMenu(R.menu.fragment_category_detail)
        toolbar.setOnMenuItemClickListener(MenuListener())
    }

    private fun fillTypeSpinner() {
        val adapter = CategoryTypeSpinnerAdapter(
            requireContext(),
            R.layout.spinner_item_category_type, Category.Type.entries.toTypedArray()
        )
        spinnerType.adapter = adapter
    }

    private fun setPeekHeight(view: View) {
        val dialog = requireDialog() as BottomSheetDialog
        val displayHei = requireActivity().resources.displayMetrics.heightPixels
        val peekHei = displayHei * 35 / 100
        val behavior = dialog.behavior
        behavior.peekHeight = peekHei

        val layoutParams = view.layoutParams
        layoutParams.height = displayHei
    }

    private fun loadCategory() {
        etCategoryTitle.setText(category!!.title)
        spinnerType.setSelection(category!!.type.ordinal)
    }

    private fun finish() {
        this.dismiss()
    }

    fun handleOkClick() {
        val title = etCategoryTitle.text.toString()
        if (title.isEmpty()) {
            finish()
            return
        }
        val type = spinnerType.selectedItem as Category.Type
        if (category == null) {
            category = Category(title, type)
            listener.onAttempt(CategoryAttemptListener.Attempt.CREATE, category)
        } else {
            category!!.title = title
            category!!.type = type
            listener.onAttempt(CategoryAttemptListener.Attempt.UPDATE, category)
        }
        finish()
    }

    private inner class MenuListener : Toolbar.OnMenuItemClickListener {
        override fun onMenuItemClick(item: MenuItem): Boolean {
            val menuId = item.itemId
            if (R.id.category_detail_menu_ok == menuId) {
                handleOkClick()
            } else {
                return false
            }
            return true
        }
    }

    private fun showDeletingConfirm() {
        val context = requireContext()
        val builder = AlertDialog.Builder(context)
        builder.setTitle(R.string.dialog_category_deleting_title)
        builder.setMessage(R.string.dialog_category_deleting_message)
        builder.setNegativeButton(R.string.string_cancel, null)
        builder.setPositiveButton(
            R.string.string_ok
        ) { _: DialogInterface, _: Int ->
            listener.onAttempt(CategoryAttemptListener.Attempt.REMOVE, category)
            finish()
        }

        builder.create().show()
    }

    private inner class DeleteButtonClick : View.OnClickListener {
        override fun onClick(view: View) {
            showDeletingConfirm()
        }
    }
}
