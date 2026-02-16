package cn.alvkeke.dropto.ui.fragment

import android.annotation.SuppressLint
import android.graphics.Color
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.RecyclerView
import cn.alvkeke.dropto.R
import cn.alvkeke.dropto.ui.UserInterfaceHelper
import cn.alvkeke.dropto.ui.UserInterfaceHelper.animateRemoveFromParent
import cn.alvkeke.dropto.ui.intf.FragmentOnBackListener
import com.google.android.material.appbar.MaterialToolbar

class MgmtReactionFragment: Fragment(), FragmentOnBackListener {

    private lateinit var fragmentView: View
    private lateinit var statusBar: View
    private lateinit var toolbar: MaterialToolbar
    private lateinit var navBar: View
    private lateinit var rlReaction: RecyclerView
    private lateinit var adapter: ReactionListAdapter
    private lateinit var itemTouchHelper: ItemTouchHelper

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        fragmentView = inflater.inflate(
            R.layout.fragment_mgmt_reaction, container, false
        )
        return fragmentView
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        statusBar = view.findViewById(R.id.mgmt_reaction_status_bar)
        toolbar = view.findViewById(R.id.mgmt_reaction_toolbar)
        navBar = view.findViewById(R.id.mgmt_reaction_nav_bar)
        rlReaction = view.findViewById(R.id.mgmt_reaction_list)

        UserInterfaceHelper.setSystemBarHeight(view, statusBar, navBar)

        toolbar.setNavigationOnClickListener { finish() }

        // TODO: for debug now, load from database in feature
        val reactionList = mutableListOf("Like", "Love", "Haha", "Wow", "Sad", "Angry", "😀")
        adapter = ReactionListAdapter(reactionList)
        rlReaction.adapter = adapter
        val layoutManager = androidx.recyclerview.widget.LinearLayoutManager(
            requireContext(),
            RecyclerView.VERTICAL,
            false
        )
        rlReaction.layoutManager = layoutManager

        itemTouchHelper = ItemTouchHelper(object : ItemTouchHelper.SimpleCallback(
            ItemTouchHelper.UP or ItemTouchHelper.DOWN, 0) {
            override fun onMove(
                recyclerView: RecyclerView,
                viewHolder: RecyclerView.ViewHolder,
                target: RecyclerView.ViewHolder
            ): Boolean {
                val from = viewHolder.adapterPosition
                val to = target.adapterPosition
                adapter.moveItem(from, to)
                return true
            }
            override fun onSwiped(viewHolder: RecyclerView.ViewHolder, direction: Int) {}
            override fun isLongPressDragEnabled(): Boolean = false
        })
        itemTouchHelper.attachToRecyclerView(rlReaction)
        adapter.setItemTouchHelper(itemTouchHelper)
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

    private class ReactionListAdapter(
        private val reactionList: MutableList<String>
    ): RecyclerView.Adapter<ReactionListAdapter.ViewHolder>() {
        private var itemTouchHelper: ItemTouchHelper? = null
        fun setItemTouchHelper(helper: ItemTouchHelper) {
            this.itemTouchHelper = helper
        }

        class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
            val textView: TextView
            val imageView: ImageView
            init {
                val layout = view as ConstraintLayout
                var tv: TextView? = null
                var iv: ImageView? = null
                for (i in 0 until layout.childCount) {
                    val v = layout.getChildAt(i)
                    if (v is TextView) tv = v
                    if (v is ImageView) iv = v
                }
                textView = tv ?: throw IllegalStateException("TextView not found")
                imageView = iv ?: throw IllegalStateException("ImageView not found")
            }
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val context = parent.context
            val layout = ConstraintLayout(context)
            val textView = TextView(context)
            val imageView = ImageView(context)
            val divider1 = View(context)
            val divider2 = View(context)
            val divColor = ContextCompat.getColor(context, R.color.color_text_sub)
            divider1.setBackgroundColor(divColor)
            divider2.setBackgroundColor(divColor)
            divider1.id = View.generateViewId()
            divider2.id = View.generateViewId()
            layout.addView(divider1)
            layout.addView(divider2)
            layout.addView(textView)
            layout.addView(imageView)

            val lp = ConstraintLayout.LayoutParams(
                ConstraintLayout.LayoutParams.MATCH_PARENT,
                ConstraintLayout.LayoutParams.WRAP_CONTENT
            )
            layout.layoutParams = lp

            val margin8 = (8 * context.resources.displayMetrics.density).toInt()

            val tvLp = ConstraintLayout.LayoutParams(
                ConstraintLayout.LayoutParams.WRAP_CONTENT,
                ConstraintLayout.LayoutParams.WRAP_CONTENT
            )
            tvLp.leftToLeft = ConstraintLayout.LayoutParams.PARENT_ID
            tvLp.topToTop = ConstraintLayout.LayoutParams.PARENT_ID
            tvLp.bottomToBottom = ConstraintLayout.LayoutParams.PARENT_ID
            tvLp.setMargins(margin8, margin8, 0, margin8)
            textView.layoutParams = tvLp
            textView.setTextColor(ContextCompat.getColor(context, R.color.color_text_main))
            textView.textSize =32f

            val ivLp = ConstraintLayout.LayoutParams(
                ConstraintLayout.LayoutParams.WRAP_CONTENT,
                ConstraintLayout.LayoutParams.WRAP_CONTENT
            )
            ivLp.rightToRight = ConstraintLayout.LayoutParams.PARENT_ID
            ivLp.topToTop = ConstraintLayout.LayoutParams.PARENT_ID
            ivLp.bottomToBottom = ConstraintLayout.LayoutParams.PARENT_ID
            ivLp.setMargins(0, margin8, margin8, margin8)
            imageView.layoutParams = ivLp
            imageView.setImageResource(R.drawable.icon_common_menu)

            val dividerLp1 = ConstraintLayout.LayoutParams(
                ConstraintLayout.LayoutParams.MATCH_PARENT,
                (context.resources.displayMetrics.density).toInt() // 1dp
            )
            dividerLp1.topToTop = ConstraintLayout.LayoutParams.PARENT_ID
            divider1.layoutParams = dividerLp1
            val dividerLp2 = ConstraintLayout.LayoutParams(
                ConstraintLayout.LayoutParams.MATCH_PARENT,
                (context.resources.displayMetrics.density).toInt() // 1dp
            )
            dividerLp2.bottomToBottom = ConstraintLayout.LayoutParams.PARENT_ID
            divider2.layoutParams = dividerLp2

            return ViewHolder(layout)
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            val reaction = reactionList[position]
            holder.textView.text = reaction
            holder.imageView.setOnLongClickListener {
                itemTouchHelper?.startDrag(holder)
                true
            }
        }

        override fun getItemCount(): Int = reactionList.size

        fun moveItem(from: Int, to: Int) {
            if (from == to) return
            val item = reactionList.removeAt(from)
            reactionList.add(to, item)
            notifyItemMoved(from, to)
        }
    }

}