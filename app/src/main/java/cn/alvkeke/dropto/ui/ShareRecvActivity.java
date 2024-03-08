package cn.alvkeke.dropto.ui;

import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.view.View;

import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.DividerItemDecoration;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;

import cn.alvkeke.dropto.R;
import cn.alvkeke.dropto.data.Category;
import cn.alvkeke.dropto.data.Global;

public class ShareRecvActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_share_recv);

        RecyclerView rlCategory = findViewById(R.id.rlist_share_recv);
        ArrayList<Category> categories = Global.getInstance().getCategories();
        CategoryListAdapter adapter = new CategoryListAdapter(categories);
        rlCategory.setAdapter(adapter);

        RecyclerView.LayoutManager layoutManager = new LinearLayoutManager(this);
        rlCategory.setLayoutManager(layoutManager);
        rlCategory.addItemDecoration(new DividerItemDecoration(this, DividerItemDecoration.VERTICAL));

        Intent intent = getIntent();
        String action = intent.getAction();
        String type = intent.getType();

        adapter.setItemClickListener(new CategoryListAdapter.OnItemClickListener() {
            @Override
            public void onItemClick(int index, View v) {
                Log.e(this.toString(), "action: " + action);
                Log.e(this.toString(), "type: " + type);
            }
        });
    }
}