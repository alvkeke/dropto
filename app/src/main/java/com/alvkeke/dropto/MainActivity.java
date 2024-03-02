package com.alvkeke.dropto;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.view.Gravity;
import android.view.MenuItem;
import android.view.View;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.PopupMenu;
import android.widget.PopupWindow;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.DividerItemDecoration;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.alvkeke.dropto.data.NoteItem;
import com.alvkeke.dropto.ui.NoteDetailActivity;
import com.alvkeke.dropto.ui.NoteListAdapter;

import java.io.File;
import java.util.ArrayList;
import java.util.Date;
import java.util.Random;

public class MainActivity extends AppCompatActivity {

    private void dbg_fill_list(ArrayList<NoteItem> list) {

        final String[] imglist = { "Screenshot_20240301_215505.png",
                "Screenshot_20240301_215513.png", "Screenshot_20240301_215520.png",
                "Screenshot_20240301_235659.png", "Screenshot_20240302_000246.png",
                "Screenshot_20240302_001052.png",
        };
        int idx = 0;
        Random r = new Random();
        File img_folder = this.getExternalFilesDir("imgs");
        Log.d(this.toString(), "image folder path: " + img_folder);
        if (img_folder != null && !img_folder.exists() && img_folder.mkdir()) {
            Log.e(this.toString(), "failed to create folder: " + img_folder);
        }

        for (int i=0; i<15; i++) {
            NoteItem e = new NoteItem("ITEM" + i + i, new Date().getTime());
            if (r.nextBoolean()) {
                e.setText(e.getText(), true);
            }
            if (idx < imglist.length && r.nextBoolean()) {
                File img_file = new File(img_folder, imglist[idx++]);
                Log.d(this.toString(), "add image file: " + img_file);
                e.setImageFile(img_file);
            }
            list.add(e);
        }
    }

    ArrayList<NoteItem> noteItems;
    NoteListAdapter noteItemAdapter;
    private EditText etInputText;
    private RecyclerView rlNoteList;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        rlNoteList = findViewById(R.id.rlist_notes);
        ImageButton btnAddNote = findViewById(R.id.input_send);
        etInputText = findViewById(R.id.input_text);

        noteItems = new ArrayList<>();
        noteItemAdapter = new NoteListAdapter(noteItems);
        RecyclerView.LayoutManager layoutManager = new LinearLayoutManager(this);

        rlNoteList.setAdapter(noteItemAdapter);
        rlNoteList.setLayoutManager(layoutManager);
        rlNoteList.addItemDecoration(new DividerItemDecoration(this, DividerItemDecoration.VERTICAL));
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
                Log.d(this.toString(), "NoteItem modify result code: " + resultCode);
                if (resultCode == RESULT_CANCELED) {
                    Log.i(this.toString(), "NoteItem modify canceled.");
                    return;
                }

                Intent intent = result.getData();
                if (intent == null) {
                    Log.e(this.toString(), "Failed to get Intent instance, item modify abort");
                    return;
                }

                int index = intent.getIntExtra(NoteDetailActivity.ITEM_INDEX, -1);
                if (index == -1) {
                    Log.e(this.toString(), "Failed to get item index, abort!");
                    return;
                }

                if (RESULT_OK == resultCode) {
                    NoteItem item = (NoteItem) intent.getSerializableExtra(NoteDetailActivity.ITEM_OBJECT);

                    if (item == null) {
                        Log.e(this.toString(), "Null item for result, should not happen, FIX THIS!!");
                        return;
                    }

                    handleItemEdit(index, item);
                } else if (NoteDetailActivity.RESULT_DELETED == resultCode) {
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
            String content = etInputText.getText().toString();
            NoteItem item = new NoteItem(content);
            handleItemAdd(item);
            // clear input box
            etInputText.setText("");
            // scroll to bottom
            rlNoteList.smoothScrollToPosition(noteItemAdapter.getItemCount()-1);
        }
    }

    void triggerItemEdit(NoteItem e, int pos) {
        Log.d(this.toString(), "item editing triggered");

        Intent intent = new Intent(MainActivity.this, NoteDetailActivity.class);
        intent.putExtra(NoteDetailActivity.ITEM_INDEX, pos);
        intent.putExtra(NoteDetailActivity.ITEM_OBJECT, e.clone());
        noteDetailActivityLauncher.launch(intent);
    }

    private void showItemPopMenu(int index, View v) {
        PopupMenu menu = new PopupMenu(this, v);
        menu.setOnMenuItemClickListener(new PopupMenu.OnMenuItemClickListener() {
            @Override
            public boolean onMenuItemClick(MenuItem item) {
                switch (item.getItemId()) {
                    case R.id.item_pop_m_delete:
                        Log.d(this.toString(), "try to delete item");
                        return true;
                    case R.id.item_pop_m_pin:
                        Log.d(this.toString(), "Pin");
                        return true;
                    case R.id.item_pop_m_edit:
                        NoteItem e = noteItems.get(index);
                        if (e == null) {
                            Log.e(this.toString(), "Failed to get note item at " + index);
                            return false;
                        }
                        triggerItemEdit(e, index);
                        return true;
                    default:
                        return false;
                }
            }
        });
        menu.inflate(R.menu.item_pop_menu);
        menu.show();
    }

    class onListItemClick implements NoteListAdapter.OnItemClickListener {

        @Override
        public void onItemClick(int index, View v) {
            showItemPopMenu(index, v);
        }
    }

}