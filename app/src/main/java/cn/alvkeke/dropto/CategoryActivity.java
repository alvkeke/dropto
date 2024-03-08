package cn.alvkeke.dropto;

import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.RecyclerView;

import android.os.Bundle;

public class CategoryActivity extends AppCompatActivity {

    private RecyclerView rlCategory;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_category);

        rlCategory = findViewById(R.id.rlist_category);

    }
}