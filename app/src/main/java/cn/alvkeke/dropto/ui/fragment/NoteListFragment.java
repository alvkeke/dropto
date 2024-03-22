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
import cn.alvkeke.dropto.storage.DataBaseHelper;
import cn.alvkeke.dropto.ui.adapter.NoteListAdapter;

public class NoteListFragment extends Fragment {

    public interface NoteListEventListener {
        void onNoteListClose();
        void onListDetailShow(int index, NoteItem item);
    }

    Category category;
    int index;
    ArrayList<NoteItem> noteItems;
    NoteListAdapter noteItemAdapter;
    private EditText etInputText;
    private RecyclerView rlNoteList;

    public NoteListFragment(int index, Category category) {
        assert category != null;
        this.category = category;
        this.index = index;
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

        noteItems = category.getNoteItems();
        if (noteItems == null) {
            Log.e(this.toString(), "Failed to get note list!!");
            ((NoteListEventListener)requireContext()).onNoteListClose();
            return;
        }

        // TODO: use a pool to store the data, don't retrieve data so frequently
        try (DataBaseHelper dataBaseHelper = new DataBaseHelper(requireContext())) {
            dataBaseHelper.start();
            dataBaseHelper.queryNote(-1, category.getId(), noteItems);
            dataBaseHelper.finish();
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
    public void onDestroyView() {
        super.onDestroyView();
        // TODO: use a pool, don't clear data so frequently
        noteItems.clear();
    }

    public void handleItemDelete(int index) {
        Log.d(this.toString(), "trying to delete item: " + index);

        if (index == -1) {
            Log.e(this.toString(), "Failed to get item index for deleting");
            return;
        }

        try (DataBaseHelper dbHelper = new DataBaseHelper(requireContext())){
            NoteItem item = noteItems.get(index);
            dbHelper.start();
            if (0 == dbHelper.deleteNote(item.getId()))
                Log.e(this.toString(), "no row be deleted");
            dbHelper.finish();
            noteItems.remove(index);
            noteItemAdapter.notifyItemRemoved(index);
            noteItemAdapter.notifyItemRangeChanged(index, noteItemAdapter.getItemCount());
        } catch (Exception e) {
            Log.e(this.toString(), "Failed to remove item at index: " +
                    index + ", exception: " + e);
        }
    }

    public void handleItemEdit(int index, NoteItem newItem) {
        NoteItem oldItem = noteItems.get(index);
        if (oldItem == null) {
            Log.e(this.toString(), "Failed to get note item at: "+ index);
            return;
        }
        newItem.setId(oldItem.getId());
        try (DataBaseHelper dbHelper = new DataBaseHelper(requireContext())) {
            dbHelper.start();
            if (0 == dbHelper.updateNote(newItem)) {
                Log.i(this.toString(), "no item was updated");
            }
            dbHelper.finish();
        } catch (Exception exception) {
            Log.e(this.toString(), "Failed to update note item in database: " + exception);
            return;
        }

        Log.e(this.toString(), "category id: " + newItem.getCategoryId());
        oldItem.update(newItem, true);
        noteItemAdapter.notifyItemChanged(index);
    }

    public void handleItemAdd(NoteItem item) {
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
            item.setCategoryId(category.getId());

            try (DataBaseHelper dataBaseHelper = new DataBaseHelper(requireContext())) {
                dataBaseHelper.start();
                item.setId(dataBaseHelper.insertNote(item));
                dataBaseHelper.finish();
            } catch (Exception e) {
                Log.e(this.toString(), "Failed to add new item to database!");
                return;
            }

            handleItemAdd(item);
            // clear input box
            etInputText.setText("");
            // scroll to bottom
            rlNoteList.smoothScrollToPosition(noteItemAdapter.getItemCount()-1);
        }
    }

    void triggerItemEdit(NoteItem e, int pos) {
        Log.d(this.toString(), "item editing triggered");
        NoteListEventListener listener = (NoteListEventListener) requireContext();
        listener.onListDetailShow(pos, e);
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