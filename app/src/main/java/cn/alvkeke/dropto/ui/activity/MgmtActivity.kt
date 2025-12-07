package cn.alvkeke.dropto.ui.activity

import android.os.Bundle
import android.view.View
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.viewpager2.widget.ViewPager2
import cn.alvkeke.dropto.R
import cn.alvkeke.dropto.ui.adapter.MgmtViewPagerAdapter

class MgmtActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        this.enableEdgeToEdge()
        setContentView(R.layout.activity_management)

        val mgmtParent = findViewById<View>(R.id.mgmt_parent)
        val viewPager = findViewById<ViewPager2>(R.id.mgmt_viewpager)
        val status = findViewById<View>(R.id.mgmt_status_bar)
        val navi = findViewById<View>(R.id.mgmt_navi_bar)
        val toolbar = findViewById<Toolbar>(R.id.mgmt_toolbar)

        setSystemBarHeight(mgmtParent, status, navi)

        toolbar.setNavigationOnClickListener { _: View -> finish() }


        val adapter = MgmtViewPagerAdapter(this)
        viewPager.setAdapter(adapter)
    }

    private fun setSystemBarHeight(parent: View, status: View, navi: View) {
        ViewCompat.setOnApplyWindowInsetsListener(
            parent
        ) { _: View, insets: WindowInsetsCompat ->
            val statusHei: Int = insets.getInsets(WindowInsetsCompat.Type.statusBars()).top
            val naviHei: Int = insets.getInsets(WindowInsetsCompat.Type.navigationBars()).bottom
            status.layoutParams.height = statusHei
            navi.layoutParams.height = naviHei
            WindowInsetsCompat.CONSUMED
        }
    }
}