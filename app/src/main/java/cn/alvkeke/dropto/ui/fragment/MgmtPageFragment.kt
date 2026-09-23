package cn.alvkeke.dropto.ui.fragment

import android.annotation.SuppressLint
import android.content.Context
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import cn.alvkeke.dropto.DroptoApplication
import cn.alvkeke.dropto.R
import cn.alvkeke.dropto.ui.activity.MainActivity
import cn.alvkeke.dropto.ui.activity.MainViewModel
import cn.alvkeke.dropto.ui.component.MgmtItemView
import cn.alvkeke.dropto.ui.intf.FragmentOnBackListener
import cn.alvkeke.dropto.ui.intf.HorizontalDragListener
import cn.alvkeke.dropto.ui.listener.ReleaseVelocitySampler
import com.google.android.material.appbar.MaterialToolbar
import kotlin.math.abs

class MgmtPageFragment : Fragment(), FragmentOnBackListener {

    companion object {
        const val TAG = "MgmtPageFragment"
    }

    private val app: DroptoApplication
        get() = requireActivity().application as DroptoApplication
    private lateinit var context: Context
    private lateinit var viewModel: MainViewModel
    private lateinit var fragmentParent: View
    private lateinit var toolbar: MaterialToolbar

    private lateinit var itemStorage: MgmtItemView
    private lateinit var itemNotes: MgmtItemView
    private lateinit var itemReactions: MgmtItemView

    var revealGestureListener: HorizontalDragListener? = null

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        fragmentParent = inflater.inflate(
            R.layout.fragment_mgmt_page, container, false
        )
        return fragmentParent
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        Log.e(TAG, "onViewCreated")
        context = requireContext()
        viewModel = ViewModelProvider(requireActivity())[MainViewModel::class.java]

        toolbar = view.findViewById(R.id.mgmt_page_toolbar)
        itemStorage = view.findViewById(R.id.mgmt_page_item_storage)
        itemNotes = view.findViewById(R.id.mgmt_page_item_notes)
        itemReactions = view.findViewById(R.id.mgmt_page_item_reactions)

        val dragListener = MgmtPageDragListener()
        view.setOnTouchListener(dragListener)
        itemStorage.setOnTouchListener(dragListener)
        itemNotes.setOnTouchListener(dragListener)
        itemReactions.setOnTouchListener(dragListener)

        toolbar.setTitle("Management")

        itemStorage.setTitle("Manage Storage")
        itemStorage.setIcon(R.drawable.icon_mgmt_storage)
        itemStorage.setOnClickListener {
            openMgmtPage(MgmtStorageFragment())
        }
        itemNotes.setTitle("Manage Notes")
        itemNotes.setIcon(R.drawable.icon_mgmt_storage)
        itemNotes.setOnClickListener {
            openMgmtPage(MgmtNotesFragment())
        }
        itemReactions.setTitle("Manage Reactions")
        itemReactions.setIcon(R.drawable.icon_mgmt_storage)
        itemReactions.setOnClickListener {
            openMgmtPage(MgmtReactionFragment())
        }

    }

    private inner class MgmtPageDragListener : View.OnTouchListener {
        private val touchSlop = ViewConfiguration.get(context).scaledTouchSlop
        private val velocitySampler = ReleaseVelocitySampler()

        private var downX = 0f
        private var downY = 0f
        private var dragging = false

        @SuppressLint("ClickableViewAccessibility")
        override fun onTouch(v: View, event: MotionEvent): Boolean {
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    downX = event.rawX
                    downY = event.rawY
                    dragging = false
                    velocitySampler.clear()
                    velocitySampler.add(event.rawX, event.rawY)
                }

                MotionEvent.ACTION_MOVE -> {
                    velocitySampler.add(event.rawX, event.rawY)
                    val deltaX = event.rawX - downX
                    val deltaY = event.rawY - downY
                    if (!dragging) {
                        if (abs(deltaX) > touchSlop && abs(deltaX) > abs(deltaY)) {
                            dragging = true
                            revealGestureListener?.onDragStart(deltaX)
                            // the row is already pressed down, drop it, so no
                            // click is delivered when the finger lifts
                            v.isPressed = false
                        } else if (abs(deltaY) > touchSlop) {
                            return false
                        }
                    }
                    if (dragging) {
                        revealGestureListener?.onDragging(deltaX)
                        return true
                    }
                }

                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    if (dragging) {
                        dragging = false
                        val velocity = velocitySampler.recentReleaseVelocity().first
                        revealGestureListener
                            ?.onDragEnd(event.rawX - downX, velocity)
                        return true
                    }
                }
            }
            return false
        }
    }

    private fun openMgmtPage(fragment: MgmtBottomSheetFragment) {
        val manager = parentFragmentManager
        if (manager.isStateSaved) return
        fragment.show(manager, fragment.javaClass.simpleName)
    }

    override fun onBackPressed(): Boolean {
        finish()
        return true
    }

    fun finish() {
        (activity as? MainActivity)?.closeMgmtReveal()
    }

}