package com.alvkeke.dropto;

import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.DividerItemDecoration;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import android.os.Bundle;

import com.alvkeke.dropto.data.NoteItem;
import com.alvkeke.dropto.ui.NoteListAdapter;

import java.util.ArrayList;
import java.util.Date;

public class MainActivity extends AppCompatActivity {

    private void dbg_fill_list(ArrayList<NoteItem> list) {
        for (int i=0; i<10; i++) {
            NoteItem e = new NoteItem("ITEM" + i + i);
            if (i % 2 == 0) {
                e.setTime(new Date().getTime());
            }
            list.add(e);
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        RecyclerView noteList = findViewById(R.id.rlist_notes);

        ArrayList<NoteItem> noteItems = new ArrayList<>();
        NoteListAdapter adapter = new NoteListAdapter(noteItems);
        RecyclerView.LayoutManager layoutManager = new LinearLayoutManager(this);

        noteList.setAdapter(adapter);
        noteList.setLayoutManager(layoutManager);
        noteList.addItemDecoration(new DividerItemDecoration(this, DividerItemDecoration.VERTICAL));

        if (BuildConfig.DEBUG) {
            dbg_fill_list(noteItems);
            adapter.notifyDataSetChanged();
        }

    }
}