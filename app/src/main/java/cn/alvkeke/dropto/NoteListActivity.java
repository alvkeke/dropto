package cn.alvkeke.dropto;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.PopupMenu;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.FileProvider;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.DividerItemDecoration;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;

import cn.alvkeke.dropto.data.Category;
import cn.alvkeke.dropto.data.Global;
import cn.alvkeke.dropto.data.NoteItem;
import cn.alvkeke.dropto.ui.NoteDetailActivity;
import cn.alvkeke.dropto.ui.NoteListAdapter;

public class NoteListActivity extends Fragment {

    public static final String CATEGORY_INDEX = "CATEGORY_INDEX";

    ArrayList<NoteItem> noteItems;
    NoteListAdapter noteItemAdapter;
    private EditText etInputText;
    private RecyclerView rlNoteList;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.activity_note_list, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        Context context = getContext();
        assert context != null;

        rlNoteList = view.findViewById(R.id.rlist_notes);
        ImageButton btnAddNote = view.findViewById(R.id.input_send);
        etInputText = view.findViewById(R.id.input_text);

        // TODO: check if this need to be fix
        Bundle bundle = getArguments();
//        Intent intent = getIntent();
//        int index = intent.getIntExtra(CATEGORY_INDEX, -1);
        int index = bundle.getInt(CATEGORY_INDEX, -1);
        if (index == -1) {
            Log.e(this.toString(), "Failed to get category index!!");
            return;
        }
        Category category = Global.getInstance().getCategories().get(index);
        if (category == null) {
            Log.e(this.toString(), "Failed to get category at " + index);
            getParentFragmentManager().popBackStack();
            return;
        }
        // TODO: fix getActivity()
        getActivity().setTitle(category.getTitle());

        noteItems = category.getNoteItems();
        if (noteItems == null) {
            Log.e(this.toString(), "Failed to get note list!!");
            getParentFragmentManager().popBackStack();
            return;
        }

        noteItemAdapter = new NoteListAdapter(noteItems);
        // TODO: fix getContext()
        RecyclerView.LayoutManager layoutManager = new LinearLayoutManager(getContext());

        rlNoteList.setAdapter(noteItemAdapter);
        rlNoteList.setLayoutManager(layoutManager);
        // TODO: fix getContext()
        rlNoteList.addItemDecoration(new DividerItemDecoration(getContext(), DividerItemDecoration.VERTICAL));
        noteItemAdapter.setItemClickListener(new onListItemClick());

        btnAddNote.setOnClickListener(new onItemAddClick());

    }

    // TODO: fix this broken function
//    ActivityResultLauncher<Intent> noteDetailActivityLauncher = registerForActivityResult(
//            new ActivityResultContracts.StartActivityForResult(),
//            result -> {
//                int resultCode = result.getResultCode();
//                Log.d(this.toString(), "NoteItem modify result code: " + resultCode);
//                if (resultCode == RESULT_CANCELED) {
//                    Log.i(this.toString(), "NoteItem modify canceled.");
//                    return;
//                }
//
//                Intent intent = result.getData();
//                if (intent == null) {
//                    Log.e(this.toString(), "Failed to get Intent instance, item modify abort");
//                    return;
//                }
//
//                int index = intent.getIntExtra(NoteDetailActivity.ITEM_INDEX, -1);
//                if (index == -1) {
//                    Log.e(this.toString(), "Failed to get item index, abort!");
//                    return;
//                }
//
//                if (RESULT_OK == resultCode) {
//                    NoteItem item = (NoteItem) intent.getSerializableExtra(NoteDetailActivity.ITEM_OBJECT);
//
//                    if (item == null) {
//                        Log.e(this.toString(), "Null item for result, should not happen, FIX THIS!!");
//                        return;
//                    }
//
//                    handleItemEdit(index, item);
//                } else if (NoteDetailActivity.RESULT_DELETED == resultCode) {
//                    handleItemDelete(index);
//                } else {
//                    Log.e(this.toString(), "got a wrong resultCode: " + resultCode);
//                }
//
//            }
//    );

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

        // TODO: fix this broken function
//        Intent intent = new Intent(NoteListActivity.this, NoteDetailActivity.class);
//        intent.putExtra(NoteDetailActivity.ITEM_INDEX, pos);
//        intent.putExtra(NoteDetailActivity.ITEM_OBJECT, e.clone());
//        noteDetailActivityLauncher.launch(intent);
    }

    private boolean handleItemCopy(NoteItem item) {
        // TODO: fix getContext()
        ClipboardManager clipboardManager =
                (ClipboardManager) getContext().getSystemService(Context.CLIPBOARD_SERVICE);
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
            // TODO: fix getContext()
            Uri fileUri = FileProvider.getUriForFile(getContext(),
                    getContext().getPackageName() + ".fileprovider", item.getImageFile());
            sendIntent.putExtra(Intent.EXTRA_STREAM, fileUri);
            sendIntent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            Log.d(this.toString(), "share image Uri: " + fileUri);
        }
        Intent shareIntent = Intent.createChooser(sendIntent, "Share to");

        try {
            NoteListActivity.this.startActivity(shareIntent);
        } catch (Exception e) {
            Log.e(this.toString(), "Failed to create share Intent: " + e);
            return false;
        }
        return true;
    }

    private void showItemPopMenu(int index, View v) {
        // TODO: fix getContext()
        PopupMenu menu = new PopupMenu(getContext(), v);
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