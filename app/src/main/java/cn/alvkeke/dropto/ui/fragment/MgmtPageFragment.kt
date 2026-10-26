package cn.alvkeke.dropto.ui.fragment

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import cn.alvkeke.dropto.R
import cn.alvkeke.dropto.storage.DataManager
import cn.alvkeke.dropto.ui.UserInterfaceHelper.observeSystemBarInsets
import cn.alvkeke.dropto.ui.UserInterfaceHelper.applyPadding
import cn.alvkeke.dropto.ui.UserInterfaceHelper.setTopMargin
import cn.alvkeke.dropto.ui.activity.MainViewModel
import cn.alvkeke.dropto.ui.activity.StartupActivity
import cn.alvkeke.dropto.ui.component.MgmtItemView
import cn.alvkeke.dropto.ui.intf.FragmentOnBackListener
import cn.alvkeke.dropto.ui.intf.HorizontalDragListener
import cn.alvkeke.dropto.ui.listener.ReleaseVelocitySampler
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.imageview.ShapeableImageView
import kotlin.math.abs
import kotlinx.coroutines.launch

class MgmtPageFragment : Fragment(), FragmentOnBackListener {

    companion object {
        const val TAG = "MgmtPageFragment"
    }

    private lateinit var context: Context
    private lateinit var viewModel: MainViewModel
    private lateinit var fragmentParent: View
    private lateinit var toolbar: MaterialToolbar

    private lateinit var itemStorage: MgmtItemView
    private lateinit var itemNotes: MgmtItemView
    private lateinit var itemReactions: MgmtItemView

    private lateinit var userAvatar: ShapeableImageView
    private lateinit var userName: TextView
    private lateinit var userId: TextView
    private lateinit var userLogout: ImageButton

    var horizontalDragListener: HorizontalDragListener? = null

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
        userAvatar = view.findViewById(R.id.mgmt_page_user_avatar)
        userName = view.findViewById(R.id.mgmt_page_user_name)
        userId = view.findViewById(R.id.mgmt_page_user_id)
        userLogout = view.findViewById(R.id.mgmt_page_user_logout)

        val itemList = view.findViewById<View>(R.id.mgmt_page_item_list)
        view.observeSystemBarInsets { status, navi ->
            toolbar.setTopMargin(status)
            itemList.applyPadding(status + toolbar.height, navi)
        }

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

        userAvatar.setOnClickListener {
            openMgmtPage(UserProfileFragment())
        }
        parentFragmentManager.setFragmentResultListener(
            UserProfileFragment.RESULT_PROFILE_UPDATED, viewLifecycleOwner
        ) { _, _ -> bindActiveUser() }

        bindActiveUser()

        userLogout.setOnClickListener {
            lifecycleScope.launch {
                if (!DataManager.deactivateUser()) return@launch
                requireActivity().startActivity(
                    Intent(requireActivity(), StartupActivity::class.java).apply {
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
                    }
                )
            }
        }

    }

    private inner class MgmtPageDragListener : View.OnTouchListener {
        private val touchSlop = ViewConfiguration.get(context).scaledTouchSlop
        private val velocitySampler = ReleaseVelocitySampler()

        private var downX = 0f
        private var downY = 0f
        private var dragging = false
        private var dragStartDeltaX = 0f

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
                            dragStartDeltaX = deltaX
                            horizontalDragListener?.onDragStart()
                            // the row is already pressed down, drop it, so no
                            // click is delivered when the finger lifts
                            v.isPressed = false
                        } else if (abs(deltaY) > touchSlop) {
                            return false
                        }
                    }
                    if (dragging) {
                        horizontalDragListener?.onDragging(deltaX - dragStartDeltaX)
                        return true
                    }
                }

                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    if (dragging) {
                        dragging = false
                        val velocity = velocitySampler.recentReleaseVelocity().first
                        horizontalDragListener
                            ?.onDragEnd(event.rawX - downX - dragStartDeltaX, velocity)
                        return true
                    }
                }
            }
            return false
        }
    }

    private fun bindActiveUser() {
        val user = DataManager.currentUser ?: return
        userAvatar.setImageDrawable(DataManager.currentUserAvator)
        userName.text = user.name ?: user.id
        userId.text = "${user.id}@${user.domain}"
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
//        (activity as? MainActivity)?.animateCloseMgmt()
    }

}