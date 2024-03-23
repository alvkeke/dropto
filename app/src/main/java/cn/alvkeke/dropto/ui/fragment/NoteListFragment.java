package cn.alvkeke.dropto.ui.fragment;

import android.app.Activity;
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

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.FileProvider;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.DividerItemDecoration;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;

import cn.alvkeke.dropto.R;
import cn.alvkeke.dropto.data.Category;
import cn.alvkeke.dropto.data.NoteItem;
import cn.alvkeke.dropto.ui.SystemKeyListener;
import cn.alvkeke.dropto.ui.adapter.NoteListAdapter;

public class NoteListFragment extends Fragment implements SystemKeyListener {

    public interface NoteListEventListener {
        void onNoteListClose();
        void onNoteListLoad(Category c);

        /**
         * handle item creating
         * @param c target category
         * @param e new item
         * @return index for the new item, -1 for failed to create
         */
        int onNoteItemCreate(Category c, NoteItem e);

        /**
         * handle item deleting
         * @param c target category
         * @param e target item to delete
         * @return index for deleted item, -1 for failed to delete
         */
        int onNoteItemDelete(Category c, NoteItem e);
        void onNoteDetailShow(NoteItem item);
    }

    private NoteListEventListener listener;
    Category category;
    ArrayList<NoteItem> noteItems;
    NoteListAdapter noteItemAdapter;
    private EditText etInputText;
    private RecyclerView rlNoteList;

    public NoteListFragment(Category category) {
        assert category != null;
        this.category = category;
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_note_list, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        Activity activity = requireActivity();

        rlNoteList = view.findViewById(R.id.rlist_notes);
        ImageButton btnAddNote = view.findViewById(R.id.input_send);
        etInputText = view.findViewById(R.id.input_text);

        activity.setTitle(category.getTitle());

        listener = (NoteListEventListener) requireContext();
        listener.onNoteListLoad(category);

        noteItems = category.getNoteItems();
        if (noteItems == null) {
            Log.e(this.toString(), "Failed to get note list!!");
            listener.onNoteListClose();
            return;
        }

        noteItemAdapter = new NoteListAdapter(noteItems);
        RecyclerView.LayoutManager layoutManager = new LinearLayoutManager(activity);

        rlNoteList.setAdapter(noteItemAdapter);
        rlNoteList.setLayoutManager(layoutManager);
        rlNoteList.addItemDecoration(new DividerItemDecoration(activity, DividerItemDecoration.VERTICAL));
        noteItemAdapter.setItemClickListener(new onListItemClick());

        btnAddNote.setOnClickListener(new onItemAddClick());

    }

    @Override
    public boolean onBackPressed() {
        listener.onNoteListClose();
        return true;
    }

    private boolean handleItemCreate(NoteItem item) {
        int index = listener.onNoteItemCreate(category, item);
        if (index == -1) {
            return false;
        }
        noteItemAdapter.notifyItemInserted(index);
        noteItemAdapter.notifyItemRangeChanged(index, noteItemAdapter.getItemCount());
        return true;
    }

    class onItemAddClick implements View.OnClickListener {

        @Override
        public void onClick(View v) {
            Log.e(this.toString(), "add btn clicked");
            String content = etInputText.getText().toString();
            NoteItem item = new NoteItem(content);
            item.setCategoryId(category.getId());

            if (handleItemCreate(item)) {
                // clear input box
                etInputText.setText("");
                // scroll to bottom
                rlNoteList.smoothScrollToPosition(noteItemAdapter.getItemCount()-1);
            }
        }
    }

    private boolean handleItemCopy(NoteItem item) {
        ClipboardManager clipboardManager =
                (ClipboardManager) requireContext().getSystemService(Context.CLIPBOARD_SERVICE);
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

        Context context = requireContext();

        if (item.getImageFile() != null) {
            // add item image for sharing if exist
            sendIntent.setType("image/*");
            Uri fileUri = FileProvider.getUriForFile(context,
                    context.getPackageName() + ".fileprovider", item.getImageFile());
            sendIntent.putExtra(Intent.EXTRA_STREAM, fileUri);
            sendIntent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            Log.d(this.toString(), "share image Uri: " + fileUri);
        }
        Intent shareIntent = Intent.createChooser(sendIntent, "Share to");

        try {
            NoteListFragment.this.startActivity(shareIntent);
        } catch (Exception e) {
            Log.e(this.toString(), "Failed to create share Intent: " + e);
            return false;
        }
        return true;
    }

    private boolean handleItemDelete(NoteItem noteItem) {
        int index = listener.onNoteItemDelete(category, noteItem);
        if (index == -1) {
            return false;
        }
        noteItemAdapter.notifyItemRemoved(index);
        noteItemAdapter.notifyItemRangeChanged(index, noteItemAdapter.getItemCount());
        return true;
    }

    private void showItemPopMenu(int index, View v) {
        PopupMenu menu = new PopupMenu(requireContext(), v);
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
                    return handleItemDelete(noteItem);
                } else if (R.id.item_pop_m_pin == item_id) {
                    Log.d(this.toString(), "try to Pin item at " + index);
                } else if (R.id.item_pop_m_edit == item_id) {
                    listener.onNoteDetailShow(noteItem);
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

    public enum ItemListState {
        NONE,
        CREATE,
        REMOVE,
        MODIFY,
    }

    public void notifyItemListChanged(ItemListState state, int index, NoteItem note) {
        if (noteItems.get(index) != note) {
            Log.e(this.toString(), "target NoteItem not exist in current category");
            return;
        }
        switch (state) {
            case CREATE:
                noteItemAdapter.notifyItemInserted(index);
                noteItemAdapter.notifyItemRangeChanged(index, noteItemAdapter.getItemCount()-1);
                break;
            case MODIFY:
                noteItemAdapter.notifyItemChanged(index);
                break;
            case REMOVE:
                noteItemAdapter.notifyItemRemoved(index);
                noteItemAdapter.notifyItemRangeChanged(index, noteItemAdapter.getItemCount()-1);
                break;
            default:
        }
    }

}