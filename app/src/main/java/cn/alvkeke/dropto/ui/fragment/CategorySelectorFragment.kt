package cn.alvkeke.dropto.ui.fragment

import android.annotation.SuppressLint
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.DividerItemDecoration
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import cn.alvkeke.dropto.R
import cn.alvkeke.dropto.data.Category
import cn.alvkeke.dropto.ui.adapter.CategoryListAdapter
import cn.alvkeke.dropto.ui.listener.OnRecyclerViewTouchListener
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.bottomsheet.BottomSheetDialogFragment

class CategorySelectorFragment : BottomSheetDialogFragment() {

    fun interface CategorySelectListener {
        fun onSelected(index: Int, category: Category)
    }

    private lateinit var listener: CategorySelectListener
    private lateinit var categoryListAdapter: CategoryListAdapter

    private var categories: ArrayList<Category>? = null
    fun prepare(categories: ArrayList<Category>, listener: CategorySelectListener) {
        this.categories = categories
        this.listener = listener
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        return inflater.inflate(R.layout.fragment_share_recv, container, false)
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val context = requireContext()

        val rlCategory = view.findViewById<RecyclerView>(R.id.share_recv_rlist)
        setPeekHeight()

        categoryListAdapter = CategoryListAdapter()
        rlCategory.setAdapter(categoryListAdapter)
        if (categories != null) {
            categoryListAdapter.setList(categories!!)
        }

        val layoutManager: RecyclerView.LayoutManager = LinearLayoutManager(context)
        rlCategory.setLayoutManager(layoutManager)
        rlCategory.addItemDecoration(DividerItemDecoration(context, DividerItemDecoration.VERTICAL))

        rlCategory.setOnTouchListener(object : OnRecyclerViewTouchListener(context) {
            override fun onItemClick(v: View, index: Int): Boolean {
                val category = categoryListAdapter.get(index)

                listener.onSelected(index, category)
                finish()
                return true
            }
        })
    }

    private fun setPeekHeight() {
        // TODO: find another way, this seems ugly
        val dialog = requireDialog() as BottomSheetDialog
        val sheet: View =
            checkNotNull(dialog.findViewById(com.google.android.material.R.id.design_bottom_sheet))
        val displayHei = requireActivity().resources.displayMetrics.heightPixels
        val peekHei = (displayHei * 0.35).toInt()
        val behavior = BottomSheetBehavior.from<View>(sheet)
        behavior.peekHeight = peekHei

        val layoutParams = sheet.layoutParams
        layoutParams.height = displayHei
    }

    private fun finish() {
        this.dismiss()
    }
}