package cn.alvkeke.dropto.ui.fragment;

import android.content.Context;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.PopupMenu;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.constraintlayout.widget.ConstraintLayout;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsAnimationCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.DividerItemDecoration;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import java.util.List;

import cn.alvkeke.dropto.R;
import cn.alvkeke.dropto.data.Category;
import cn.alvkeke.dropto.data.NoteItem;
import cn.alvkeke.dropto.ui.adapter.NoteListAdapter;
import cn.alvkeke.dropto.ui.intf.ListNotification;

public class NoteListFragment extends Fragment implements ListNotification {

    public interface AttemptListener {
        enum Attempt {
            CREATE,
            REMOVE,
            UPDATE,
            DETAIL,
            COPY,
            SHARE,
        }
        void onAttemptRecv(Attempt attempt, Category category, NoteItem e);
        void onErrorRecv(String errorMessage);
    }

    private Context context;
    private AttemptListener listener;
    Category category;
    NoteListAdapter noteItemAdapter;
    private EditText etInputText;
    private ConstraintLayout inputContainer;
    private View naviBar;
    private RecyclerView rlNoteList;

    public NoteListFragment() {
    }

    public void setListener(AttemptListener listener) {
        this.listener = listener;
    }

    public void setCategory(Category category) {
        this.category = category;
    }

    public Category getCategory() {
        return this.category;
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_note_list, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        context = requireContext();
        if (category == null) return;

        rlNoteList = view.findViewById(R.id.note_list_listview);
        ImageButton btnAddNote = view.findViewById(R.id.note_list_input_button);
        etInputText = view.findViewById(R.id.note_list_input_box);
        inputContainer = view.findViewById(R.id.note_list_input_container);
        View statusBar = view.findViewById(R.id.note_list_status_bar);
        naviBar = view.findViewById(R.id.note_list_navigation_bar);
        setSystemBarHeight(view, statusBar, naviBar);
        setIMEViewChange(view);

        noteItemAdapter = new NoteListAdapter(category.getNoteItems());
        LinearLayoutManager layoutManager = new LinearLayoutManager(context);
        layoutManager.setReverseLayout(true);

        rlNoteList.setAdapter(noteItemAdapter);
        rlNoteList.setLayoutManager(layoutManager);
        rlNoteList.addItemDecoration(new DividerItemDecoration(context, DividerItemDecoration.VERTICAL));
        noteItemAdapter.setItemClickListener(new onListItemClick());

        btnAddNote.setOnClickListener(new onItemAddClick());
    }

    private void setSystemBarHeight(View parent, View status, View navi) {
        ViewCompat.setOnApplyWindowInsetsListener(parent, (v, insets) -> {
            int statusHei, naviHei;
            statusHei = insets.getInsets(WindowInsetsCompat.Type.statusBars()).top;
            naviHei = insets.getInsets(WindowInsetsCompat.Type.navigationBars()).bottom;
            status.getLayoutParams().height = statusHei;
            navi.getLayoutParams().height = naviHei;
            return WindowInsetsCompat.CONSUMED;
        });
    }

    private void setIMEViewChange(View view) {
        ViewCompat.setWindowInsetsAnimationCallback(view,
                new WindowInsetsAnimationCompat.Callback(
                        WindowInsetsAnimationCompat.Callback.DISPATCH_MODE_STOP) {

                    @NonNull
                    @Override
                    public WindowInsetsCompat onProgress(@NonNull WindowInsetsCompat insets,
                                                         @NonNull List<WindowInsetsAnimationCompat> runningAnimations) {
                        int imeHei = insets.getInsets(WindowInsetsCompat.Type.ime()).bottom;
                        inputContainer.setTranslationY(-imeHei);
                        rlNoteList.setTranslationY(-imeHei);
                        if (imeHei <= naviBar.getHeight()) {
                            naviBar.setVisibility(View.VISIBLE);
                        } else {
                            naviBar.setVisibility(View.GONE);
                        }
                        return insets;
                    }
                });
    }

    class onItemAddClick implements View.OnClickListener {

        @Override
        public void onClick(View v) {
            String content = etInputText.getText().toString();
            NoteItem item = new NoteItem(content);
            item.setCategoryId(category.getId());
            setPendingItem(item);
            listener.onAttemptRecv(AttemptListener.Attempt.CREATE, category, item);
        }
    }

    private void showItemPopMenu(int index, View v) {
        PopupMenu menu = new PopupMenu(context, v);
        NoteItem noteItem = category.getNoteItem(index);
        if (noteItem == null) {
            listener.onErrorRecv("Failed to get note item at " + index + ", abort");
            return;
        }
        menu.setOnMenuItemClickListener(menuItem -> {
            int item_id = menuItem.getItemId();
            if (R.id.item_pop_m_delete == item_id) {
                listener.onAttemptRecv(AttemptListener.Attempt.REMOVE, category, noteItem);
            } else if (R.id.item_pop_m_pin == item_id) {
                listener.onErrorRecv("try to Pin item at " + index);
            } else if (R.id.item_pop_m_edit == item_id) {
                listener.onAttemptRecv(AttemptListener.Attempt.DETAIL, category, noteItem);
            } else if (R.id.item_pop_m_copy_text == item_id) {
                listener.onAttemptRecv(AttemptListener.Attempt.COPY, category, noteItem);
            } else if (R.id.item_pop_m_share == item_id) {
                listener.onAttemptRecv(AttemptListener.Attempt.SHARE, category, noteItem);
            } else {
                listener.onErrorRecv( "Unknown menu id: " +
                        getResources().getResourceEntryName(item_id));
                return false;
            }
            return true;
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

    private NoteItem pendingNoteItem = null;
    private void setPendingItem(NoteItem item) {
        pendingNoteItem = item;
    }
    private void clearPendingItem() {
        pendingNoteItem = null;
    }

    @Override
    public void notifyItemListChanged(Notify notify, int index, Object object) {
        NoteItem note = (NoteItem) object;
        if (note.getCategoryId() != category.getId()) {
            Log.e(this.toString(), "target NoteItem not exist in current category");
            return;
        }
        switch (notify) {
            case CREATED:
                noteItemAdapter.notifyItemInserted(index);
                noteItemAdapter.notifyItemRangeChanged(index,
                        noteItemAdapter.getItemCount() - index);
                rlNoteList.smoothScrollToPosition(index);
                if (note == pendingNoteItem) {
                    clearPendingItem();
                    // clear input box text for manually added item
                    etInputText.setText("");
                }
                break;
            case UPDATED:
                noteItemAdapter.notifyItemChanged(index);
                break;
            case REMOVED:
                noteItemAdapter.notifyItemRemoved(index);
                noteItemAdapter.notifyItemRangeChanged(index,
                        noteItemAdapter.getItemCount() - index);
                break;
            default:
        }
    }

}