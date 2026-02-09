package cn.alvkeke.dropto.ui.activity

import android.content.res.Configuration
import android.os.Bundle
import android.util.Log
import android.view.WindowManager
import androidx.activity.OnBackPressedCallback
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import cn.alvkeke.dropto.R
import cn.alvkeke.dropto.data.Category
import cn.alvkeke.dropto.storage.DataLoader.loadCategories
import cn.alvkeke.dropto.ui.fragment.CategoryListFragment
import cn.alvkeke.dropto.ui.intf.FragmentOnBackListener

class MainActivity : AppCompatActivity() {

    private var _categoryListFragment: CategoryListFragment? = null
    private var categoryListFragment: CategoryListFragment
        get() {
            if (_categoryListFragment == null) {
                _categoryListFragment = CategoryListFragment()
            }
            return _categoryListFragment!!
        }
        set(value) {
            _categoryListFragment = value
        }

    private val viewModel: MainViewModel by lazy {
        ViewModelProvider(this)[MainViewModel::class.java]
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        this.enableEdgeToEdge()
        setContentView(R.layout.activity_main)

        window.setFlags(
            WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS
        )

        onBackPressedDispatcher.addCallback(this, OnFragmentBackPressed(true))

        for (f in supportFragmentManager.fragments) {
            when (f) {
                is CategoryListFragment -> categoryListFragment = f
            }
        }

        if (!categoryListFragment.isAdded) {
            startFragment(categoryListFragment)
        }

        if (savedInstanceState == null) {
            Log.v(TAG, "onCreate: fresh start")
            val categories: ArrayList<Category> = loadCategories(this)
            viewModel.setCategoriesList(categories)
        } else {
            Log.v(TAG, "onCreate: restore from savedInstanceState")
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        val nightMode = newConfig.uiMode and Configuration.UI_MODE_NIGHT_MASK
        when (nightMode) {
            Configuration.UI_MODE_NIGHT_YES -> {
                AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES)
            }

            Configuration.UI_MODE_NIGHT_NO -> {
                AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO)
            }
        }
        recreate()
    }

    private fun startFragment(fragment: Fragment) {
        supportFragmentManager.beginTransaction()
            .add(R.id.main_container, fragment, null)
            .addToBackStack(fragment.javaClass.simpleName)
            .commit()
    }

    internal inner class OnFragmentBackPressed(enabled: Boolean) : OnBackPressedCallback(enabled) {
        override fun handleOnBackPressed() {
            val fragment = supportFragmentManager.findFragmentById(R.id.main_container)
            var ret = false
            if (fragment is FragmentOnBackListener) {
                ret = (fragment as FragmentOnBackListener).onBackPressed()
            }
            if (!ret) {
                this@MainActivity.finish()
            }
        }
    }

    companion object {
        const val TAG: String = "MainActivity"
    }
}
