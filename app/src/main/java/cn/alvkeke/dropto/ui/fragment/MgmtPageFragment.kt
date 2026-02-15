package cn.alvkeke.dropto.ui.fragment

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ObjectAnimator
import android.animation.ValueAnimator
import android.content.Context
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import cn.alvkeke.dropto.DroptoApplication
import cn.alvkeke.dropto.R
import cn.alvkeke.dropto.ui.activity.MainViewModel
import cn.alvkeke.dropto.ui.comonent.MgmtItemView
import cn.alvkeke.dropto.ui.intf.FragmentOnBackListener
import com.google.android.material.appbar.MaterialToolbar

class MgmtPageFragment : Fragment(), FragmentOnBackListener {

    companion object {
        const val TAG = "MgmtPageFragment"
        const val PROP_NAME = "translationX"
    }

    private val app: DroptoApplication
        get() = requireActivity().application as DroptoApplication
    private lateinit var context: Context
    private lateinit var viewModel: MainViewModel
    private lateinit var fragmentParent: View
    private lateinit var fragmentView: View
    private lateinit var toolbar: MaterialToolbar

    private lateinit var itemStorage: MgmtItemView
    private lateinit var itemNotes: MgmtItemView

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

        val statusBar = view.findViewById<View>(R.id.mgmt_page_status_bar)
        val navigationBar = view.findViewById<View>(R.id.mgmt_page_navigation_bar)
        setSystemBarHeight(view, statusBar, navigationBar)

        toolbar.setTitle("Management")
        toolbar.setNavigationIcon(R.drawable.icon_common_back)
        toolbar.setNavigationOnClickListener { finish() }

        itemStorage.setTitle("Manage Storage")
        itemStorage.setIcon(R.drawable.icon_mgmt_storage)
        itemNotes.setTitle("Manage Notes")
        itemNotes.setIcon(R.drawable.icon_mgmt_storage)

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


    override fun onBackPressed(): Boolean {
        finish()
        return true
    }

    @JvmOverloads
    fun finish(duration: Long = 200) {
        val startX = fragmentView.translationX
        val width = - fragmentView.width.toFloat()

        val animator = ObjectAnimator.ofFloat(
            fragmentView,
            PROP_NAME, startX, width
        ).setDuration(duration)
        animator.addListener(object : AnimatorListenerAdapter() {
            override fun onAnimationEnd(animation: Animator) {
                super.onAnimationEnd(animation)
                getParentFragmentManager().beginTransaction()
                    .remove(this@MgmtPageFragment).commit()
            }
        })
        animator.start()
    }

}