package cn.alvkeke.dropto;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.util.Log;
import android.view.MenuItem;
import android.view.View;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.PopupMenu;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.FileProvider;
import androidx.recyclerview.widget.DividerItemDecoration;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import cn.alvkeke.dropto.data.NoteItem;
import cn.alvkeke.dropto.ui.NoteDetailActivity;
import cn.alvkeke.dropto.ui.NoteListAdapter;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.lang.reflect.Field;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Random;

public class MainActivity extends AppCompatActivity {

    private boolean extract_raw_file(int id, File o_file) {
        if (o_file.exists()) {
            // file exist, return true to indicate can be load
            Log.d(this.toString(), "file exist, don't extract:" + o_file);
            return true;
        }
        if (android.os.Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.O) {
            Log.e(this.toString(), "SDK_VERSION error: " + Build.VERSION.SDK_INT);
            return false;
        }
        byte[] buffer = new byte[1024];
        try {
            InputStream is = getResources().openRawResource(id);
            OutputStream os = Files.newOutputStream(o_file.toPath());
            int len;
            while((len = is.read(buffer)) > 0) {
                os.write(buffer, 0, len);
            }
            os.flush();
            os.close();
            is.close();
        } catch (IOException e) {
            Log.e(this.toString(), "Failed to extract res: " +
                    getResources().getResourceEntryName(id) + " to " + o_file);
            return false;
        }
        return true;
    }

    private List<File> try_extract_res_images(File folder) {

        List<Integer> rawIds = new ArrayList<>();
        Field[] fields = R.raw.class.getFields();
        for (Field f : fields) {
            if (f.getType() == int.class) {
                try {
                    int id = f.getInt(null);
                    rawIds.add(id);
                } catch (IllegalAccessException e) {
                    Log.e(this.toString(), "failed to get resource ID of raw:" + f);
                }
            }
        }

        List<File> ret_files = new ArrayList<>();
        for (int id : rawIds) {
            File o_file = new File(folder, getResources().getResourceEntryName(id) + ".png");
            if (extract_raw_file(id, o_file))
                ret_files.add(o_file);
        }

        return ret_files;
    }

    private void dbg_fill_list(ArrayList<NoteItem> list) {

        Log.e(this.toString(), "sdcard: " + Environment.getExternalStorageDirectory());

        int idx = 0;
        Random r = new Random();
        File img_folder = this.getExternalFilesDir("imgs");
        if (img_folder == null) {
            Log.e(this.toString(), "Failed to get image folder, exit!!");
            return;
        }
        Log.d(this.toString(), "image folder path: " + img_folder);
        if (!img_folder.exists() && img_folder.mkdir()) {
            Log.e(this.toString(), "failed to create folder: " + img_folder);
        }
        List<File> img_files = try_extract_res_images(img_folder);

        for (int i=0; i<15; i++) {
            NoteItem e = new NoteItem("ITEM" + i + i, new Date().getTime());
            if (r.nextBoolean()) {
                e.setText(e.getText(), true);
            }
            if (idx < img_files.size() && r.nextBoolean()) {
                File img_file = img_files.get(idx);
                idx++;
                if (img_file.exists()) {
                    Log.d(this.toString(), "add image file: " + img_file);
                } else {
                    Log.e(this.toString(), "add image file failed, not exist: " + img_file);
                }

                e.setImageFile(img_file);
            }
            list.add(e);
            noteItemAdapter.notifyItemInserted(i);
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

    private boolean handleItemCopy(NoteItem item) {
        ClipboardManager clipboardManager =
                (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
        if (clipboardManager == null) {
            Log.e(this.toString(), "Failed to get ClipboardManager");
            return false;
        }
        ClipData data = ClipData.newPlainText("text", item.getText());
        clipboardManager.setPrimaryClip(data);
        return true;
    }

    private boolean handleItemShare(NoteItem item) {
        Intent sendIntent = new Intent(Intent.ACTION_SEND);

        // add item text for sharing
        sendIntent.setType("text/plain");
        sendIntent.putExtra(Intent.EXTRA_TEXT, item.getText());
        Log.d(this.toString(), "no image, share text: " + item.getText());

        if (item.getImageFile() != null) {
            // add item image for sharing if exist
            sendIntent.setType("image/*");
            Uri fileUri = FileProvider.getUriForFile(MainActivity.this,
                    getPackageName() + ".fileprovider", item.getImageFile());
            sendIntent.putExtra(Intent.EXTRA_STREAM, fileUri);
            sendIntent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            Log.d(this.toString(), "share image Uri: " + fileUri);
        }
        Intent shareIntent = Intent.createChooser(sendIntent, "Share to");

        try {
            MainActivity.this.startActivity(shareIntent);
        } catch (Exception e) {
            Log.e(this.toString(), "Failed to create share Intent: " + e);
            return false;
        }
        return true;
    }

    private void showItemPopMenu(int index, View v) {
        PopupMenu menu = new PopupMenu(this, v);
        NoteItem noteItem = noteItems.get(index);
        if (noteItem == null) {
            Log.e(this.toString(), "Failed to get note item at " + index + ", abort");
            return;
        }
        menu.setOnMenuItemClickListener(new PopupMenu.OnMenuItemClickListener() {
            @Override
            public boolean onMenuItemClick(MenuItem menuItem) {
                int item_id = menuItem.getItemId();
                if (R.id.item_pop_m_delete == item_id) {
                    Log.d(this.toString(), "try to delete item at " + index);
                    handleItemDelete(index);
                } else if (R.id.item_pop_m_pin == item_id) {
                    Log.d(this.toString(), "try to Pin item at " + index);
                } else if (R.id.item_pop_m_edit == item_id) {
                    triggerItemEdit(noteItem, index);
                } else if (R.id.item_pop_m_copy_text == item_id) {
                    Log.d(this.toString(), "copy item text at " + index +
                            ", content: " + noteItem.getText());
                    return handleItemCopy(noteItem);
                } else if (R.id.item_pop_m_share == item_id) {
                    return handleItemShare(noteItem);
                } else {
                    Log.e(this.toString(),
                            "Unknown menu id: " + getResources().getResourceEntryName(item_id));
                    return false;
                }
                return true;
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