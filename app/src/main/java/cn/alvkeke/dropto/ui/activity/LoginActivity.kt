package cn.alvkeke.dropto.ui.activity

import android.app.Activity
import android.os.Bundle
import android.view.View
import androidx.activity.OnBackPressedCallback
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentActivity
import androidx.viewpager2.adapter.FragmentStateAdapter
import androidx.viewpager2.widget.ViewPager2
import cn.alvkeke.dropto.R
import cn.alvkeke.dropto.ui.UserInterfaceHelper.applyPadding
import cn.alvkeke.dropto.ui.UserInterfaceHelper.hideSoftKeyboard
import cn.alvkeke.dropto.ui.fragment.LoginFragment
import cn.alvkeke.dropto.ui.fragment.RegisterFragment

private const val PAGE_LOGIN = 0
private const val PAGE_REGISTER = 1

class LoginActivity : AppCompatActivity() {

    private lateinit var pager: ViewPager2

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_login)

        val parent = findViewById<View>(R.id.login_parent)
        // the forms are centered, the keyboard would cover them without the ime inset
        ViewCompat.setOnApplyWindowInsetsListener(parent) { view, insets ->
            val status = insets.getInsets(WindowInsetsCompat.Type.statusBars()).top
            val bottom = maxOf(
                insets.getInsets(WindowInsetsCompat.Type.navigationBars()).bottom,
                insets.getInsets(WindowInsetsCompat.Type.ime()).bottom,
            )
            view.applyPadding(status, bottom)
            insets
        }

        pager = findViewById(R.id.login_pager)
        pager.adapter = AuthPagerAdapter(this)
        // keep both forms alive, the typed user id survives a switch between them
        pager.offscreenPageLimit = 1
        // only the links in the forms switch the page, a half swiped form is no use
        pager.isUserInputEnabled = false

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (pager.currentItem == PAGE_REGISTER) showLogin() else finish()
            }
        })
    }

    fun showLogin() {
        pager.hideSoftKeyboard()
        pager.setCurrentItem(PAGE_LOGIN, true)
    }

    fun showRegister() {
        pager.hideSoftKeyboard()
        pager.setCurrentItem(PAGE_REGISTER, true)
    }

    fun onLoginSucceeded() {
        setResult(RESULT_OK)
        finish()
    }

    private class AuthPagerAdapter(activity: FragmentActivity) : FragmentStateAdapter(activity) {

        override fun getItemCount(): Int = PAGE_REGISTER + 1

        override fun createFragment(position: Int): Fragment {
            return if (position == PAGE_REGISTER) RegisterFragment() else LoginFragment()
        }

    }

}
