package cn.alvkeke.dropto.ui.fragment

import android.content.Context
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import cn.alvkeke.dropto.DroptoApplication
import cn.alvkeke.dropto.R
import cn.alvkeke.dropto.ui.UserInterfaceHelper
import cn.alvkeke.dropto.ui.UserInterfaceHelper.animateRemoveFromParent
import cn.alvkeke.dropto.ui.UserInterfaceHelper.startFragmentAnime
import cn.alvkeke.dropto.ui.activity.MainViewModel
import cn.alvkeke.dropto.ui.comonent.MgmtItemView
import cn.alvkeke.dropto.ui.intf.FragmentOnBackListener
import com.google.android.material.appbar.MaterialToolbar

class MgmtPageFragment : Fragment(), FragmentOnBackListener {

    companion object {
        const val TAG = "MgmtPageFragment"
    }

    private val app: DroptoApplication
        get() = requireActivity().application as DroptoApplication
    private lateinit var context: Context
    private lateinit var viewModel: MainViewModel
    private lateinit var fragmentParent: View
    private lateinit var fragmentView: View
    private lateinit var toolbar: MaterialToolbar

    private lateinit var itemStorage: MgmtItemView
    private var storageFragment: MgmtStorageFragment? = null
    private lateinit var itemNotes: MgmtItemView
    private var noteFragment: MgmtNotesFragment? = null
    private lateinit var itemReactions: MgmtItemView
    private var reactionFragment: MgmtReactionFragment? = null

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

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        Log.e(TAG, "onViewCreated")
        context = requireContext()
        viewModel = ViewModelProvider(requireActivity())[MainViewModel::class.java]

        fragmentView = view.findViewById(R.id.mgmt_page_container)
        toolbar = view.findViewById(R.id.mgmt_page_toolbar)
        itemStorage = view.findViewById(R.id.mgmt_page_item_storage)
        itemNotes = view.findViewById(R.id.mgmt_page_item_notes)
        itemReactions = view.findViewById(R.id.mgmt_page_item_reactions)

        val statusBar = view.findViewById<View>(R.id.mgmt_page_status_bar)
        val navigationBar = view.findViewById<View>(R.id.mgmt_page_navigation_bar)
        UserInterfaceHelper.setSystemBarHeight(view, statusBar, navigationBar)

        toolbar.setTitle("Management")
        toolbar.setNavigationOnClickListener { finish() }

        itemStorage.setTitle("Manage Storage")
        itemStorage.setIcon(R.drawable.icon_mgmt_storage)
        itemStorage.setOnClickListener {
            if (storageFragment == null) {
                storageFragment = MgmtStorageFragment()
            }
            parentFragmentManager.startFragmentAnime(
                storageFragment!!,
                R.id.main_container,
                false
            )
        }
        itemNotes.setTitle("Manage Notes")
        itemNotes.setIcon(R.drawable.icon_mgmt_storage)
        itemNotes.setOnClickListener {
            if (noteFragment == null) {
                noteFragment = MgmtNotesFragment()
            }
            parentFragmentManager.startFragmentAnime(
                noteFragment!!,
                R.id.main_container,
                false
            )
        }
        itemReactions.setTitle("Manage Reactions")
        itemReactions.setIcon(R.drawable.icon_mgmt_storage)
        itemReactions.setOnClickListener {
            if (reactionFragment == null) {
                reactionFragment = MgmtReactionFragment()
            }
            parentFragmentManager.startFragmentAnime(
                reactionFragment!!,
                R.id.main_container,
                false
            )
        }




    }

    override fun onBackPressed(): Boolean {
        finish()
        return true
    }

    @JvmOverloads
    fun finish(duration: Long = 200) {
        animateRemoveFromParent(fragmentView, duration = duration, closeToRight = false)
    }

}