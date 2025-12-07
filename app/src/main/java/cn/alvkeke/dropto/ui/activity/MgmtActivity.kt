package cn.alvkeke.dropto.ui.activity;

import android.os.Bundle;
import android.view.View;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.viewpager2.widget.ViewPager2;

import cn.alvkeke.dropto.R;
import cn.alvkeke.dropto.ui.adapter.MgmtViewPagerAdapter;

public class MgmtActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_management);

        View mgmtParent = findViewById(R.id.mgmt_parent);
        ViewPager2 viewPager = findViewById(R.id.mgmt_viewpager);
        View status = findViewById(R.id.mgmt_status_bar);
        View navi = findViewById(R.id.mgmt_navi_bar);
        Toolbar toolbar = findViewById(R.id.mgmt_toolbar);

        setSystemBarHeight(mgmtParent, status, navi);

        toolbar.setNavigationOnClickListener(view -> finish());



        MgmtViewPagerAdapter adapter = new MgmtViewPagerAdapter(this);
        viewPager.setAdapter(adapter);
    }

    private void setSystemBarHeight(View parent, View status, View navi) {
        ViewCompat.setOnApplyWindowInsetsListener(parent, (v, insets) -> {
            int statusHei, naviHei;
            statusHei = insets.getInsets(WindowInsetsCompat.Type.statusBars()).top;
            naviHei = insets.getInsets(WindowInsetsCompat.Type.navigationBars()).bottom;
            status.getLayoutParams().height = statusHei;
            navi.getLayoutParams().height = naviHei;
            return WindowInsetsCompat.CONSUMED;
        });
    }


}