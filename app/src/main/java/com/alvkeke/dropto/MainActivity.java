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
        noteItemAdapter.setItemClickListener(new onListItemClick());

        btnAddNote.setOnClickListener(new onItemAddClick());

        if (BuildConfig.DEBUG) {
            dbg_fill_list(noteItems);
            noteItemAdapter.notifyDataSetChanged();
        }

    }

    ActivityResultLauncher<Intent> noteDetailActivityLauncher = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(),
            result -> {
                int resultCode = result.getResultCode();
                Log.d(this.toString(), "add note item result code: " + resultCode);
                if (resultCode == RESULT_CANCELED) {
                    Log.i(this.toString(), "Add NoteItem canceled.");
                    return;
                }

                Intent intent = result.getData();
                if (intent == null) {
                    Log.e(this.toString(), "Failed to get Intent instance, item adding abort");
                    return;
                }

                if (RESULT_OK == resultCode) {
                    int index = intent.getIntExtra(NoteDetailActivity.ITEM_INDEX, -1);
                    NoteItem item = (NoteItem) intent.getSerializableExtra(NoteDetailActivity.ITEM_OBJECT);

                    if (item == null) {
                        Log.e(this.toString(), "Null item for result, should not happen, FIX THIS!!");
                        return;
                    }

                    if (index == -1) {
                        // cannot get index, it's going to create a new item;
                        handleItemAdd(item);
                    } else {
                        handleItemEdit(index, item);
                    }
                } else if (NoteDetailActivity.RESULT_DELETED == resultCode) {
                    int index = intent.getIntExtra(NoteDetailActivity.ITEM_INDEX, -1);
                    handleItemDelete(index);
                } else {
                    Log.e(this.toString(), "got a wrong resultCode: " + resultCode);
                }

            }
    );

    private void handleItemDelete(int index) {
        Log.d(this.toString(), "trying to delete item: " + index);

        if (index == -1) {
            Log.e(this.toString(), "Failed to get item index for deleting");
            return;
        }

        try {
            noteItems.remove(index);
            noteItemAdapter.notifyItemRemoved(index);
            noteItemAdapter.notifyItemRangeChanged(index, noteItemAdapter.getItemCount());
        } catch (IndexOutOfBoundsException e) {
            Log.e(this.toString(), "Failed to remove item at index: " + index);
            e.printStackTrace();
        }
    }

    private void handleItemEdit(int index, NoteItem item) {
        NoteItem e = noteItems.get(index);
        if (e == null) {
            Log.e(this.toString(), "Failed to get note item at: "+ index);
            return;
        }
        e.setText(item.getText(), true);
        noteItemAdapter.notifyItemChanged(index);
    }

    private void handleItemAdd(NoteItem item) {
        int index = noteItems.size();
        noteItems.add(item);
        noteItemAdapter.notifyItemInserted(index);
        noteItemAdapter.notifyItemRangeChanged(index, noteItemAdapter.getItemCount());
    }

    class onItemAddClick implements View.OnClickListener {

        @Override
        public void onClick(View v) {
            Log.e(this.toString(), "add btn clicked");

            Intent intent = new Intent(MainActivity.this, NoteDetailActivity.class);
            noteDetailActivityLauncher.launch(intent);
        }
    }

    void triggerItemEdit(NoteItem e, int pos) {
        Log.d(this.toString(), "item editing triggered");

        Intent intent = new Intent(MainActivity.this, NoteDetailActivity.class);
        intent.putExtra(NoteDetailActivity.ITEM_INDEX, pos);
        intent.putExtra(NoteDetailActivity.ITEM_OBJECT, e.clone());
        noteDetailActivityLauncher.launch(intent);
    }

    class onListItemClick implements NoteListAdapter.OnItemClickListener {

        @Override
        public void onItemClick(int index) {
            NoteItem e = noteItems.get(index);
            if (e == null) {
                Log.e(this.toString(), "Failed to get note item at " + index);
                return;
            }
            triggerItemEdit(e, index);
        }
    }

}