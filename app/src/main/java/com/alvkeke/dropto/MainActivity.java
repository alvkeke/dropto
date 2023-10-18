package com.alvkeke.dropto;

import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.ImageButton;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.DividerItemDecoration;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.alvkeke.dropto.data.NoteItem;
import com.alvkeke.dropto.ui.NoteDetailActivity;
import com.alvkeke.dropto.ui.NoteListAdapter;

import java.util.ArrayList;
import java.util.Date;
import java.util.Random;

public class MainActivity extends AppCompatActivity {

    private void dbg_fill_list(ArrayList<NoteItem> list) {
        Random r = new Random();
        for (int i=0; i<10; i++) {
            NoteItem e = new NoteItem("ITEM" + i + i, new Date().getTime());
            if (r.nextBoolean()) {
                e.setText(e.getText(), true);
            }
            list.add(e);
        }
    }

    ArrayList<NoteItem> noteItems;
    NoteListAdapter noteItemAdapter;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        RecyclerView noteList = findViewById(R.id.rlist_notes);
        ImageButton btnAddNote = findViewById(R.id.btn_note_add);

        noteItems = new ArrayList<>();
        noteItemAdapter = new NoteListAdapter(noteItems);
        RecyclerView.LayoutManager layoutManager = new LinearLayoutManager(this);

        noteList.setAdapter(noteItemAdapter);
        noteList.setLayoutManager(layoutManager);
        noteList.addItemDecoration(new DividerItemDecoration(this, DividerItemDecoration.VERTICAL));

        btnAddNote.setOnClickListener(new onItemAddClick());

        if (BuildConfig.DEBUG) {
            dbg_fill_list(noteItems);
            noteItemAdapter.notifyDataSetChanged();
        }

    }

    ActivityResultLauncher<Intent> NoteAddActivityLauncher = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(),
            result -> {
                int resultCode = result.getResultCode();
                Log.d(this.toString(), "add note item result code: " + resultCode);
                if (resultCode == RESULT_CANCELED) {
                    Log.i(this.toString(), "Add NoteItem canceled.");
                    return;
                }
                assert resultCode == RESULT_OK;

                Intent intent = result.getData();
                if (intent == null) {
                    Log.e(this.toString(), "Failed to get Intent instance, item adding abort");
                    return;
                }

                String text = intent.getStringExtra(NoteDetailActivity.ITEM_INFO_TEXT);

                Log.d(this.toString(), "text: " + text);
                NoteItem item = new NoteItem(text, new Date().getTime());
                noteItems.add(item);
                noteItemAdapter.notifyItemInserted(noteItemAdapter.getItemCount());

            }
    );

    class onItemAddClick implements View.OnClickListener {

        @Override
        public void onClick(View v) {
            Log.e(this.toString(), "add btn clicked");

            Intent intent = new Intent(MainActivity.this, NoteDetailActivity.class);
            NoteAddActivityLauncher.launch(intent);
        }
    }

}