package cn.alvkeke.dropto.ui.fragment;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ObjectAnimator;
import android.annotation.SuppressLint;
import android.content.Context;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.PopupMenu;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.widget.Toolbar;
import androidx.constraintlayout.widget.ConstraintLayout;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsAnimationCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.DividerItemDecoration;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.appbar.MaterialToolbar;

import java.util.ArrayList;
import java.util.List;

import cn.alvkeke.dropto.R;
import cn.alvkeke.dropto.data.Category;
import cn.alvkeke.dropto.data.NoteItem;
import cn.alvkeke.dropto.ui.adapter.NoteListAdapter;
import cn.alvkeke.dropto.ui.intf.ListNotification;
import cn.alvkeke.dropto.ui.listener.OnRecyclerViewTouchListener;

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
        void onAttempt(Attempt attempt, NoteItem e);
        void onAttemptBatch(Attempt attempt, ArrayList<NoteItem> noteItems);
        void onError(String errorMessage);
    }

    private Context context;
    private AttemptListener listener;
    Category category;
    NoteListAdapter noteItemAdapter;
    private View fragmentView;
    private EditText etInputText;
    private ConstraintLayout contentContainer;
    private View naviBar;
    private MaterialToolbar toolbar;
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
        fragmentView = inflater.inflate(R.layout.fragment_note_list, container, false);
        return fragmentView;
    }

    @SuppressLint("ClickableViewAccessibility")
    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        context = requireContext();
        if (category == null) return;

        rlNoteList = view.findViewById(R.id.note_list_listview);
        ImageButton btnAddNote = view.findViewById(R.id.note_list_input_button);
        etInputText = view.findViewById(R.id.note_list_input_box);
        contentContainer = view.findViewById(R.id.note_list_content_container);
        View statusBar = view.findViewById(R.id.note_list_status_bar);
        naviBar = view.findViewById(R.id.note_list_navigation_bar);
        toolbar = view.findViewById(R.id.note_list_toolbar);

        setSystemBarHeight(view, statusBar, naviBar);
        setIMEViewChange(view);

        toolbar.setNavigationIcon(R.drawable.icon_common_back);
        toolbar.setNavigationOnClickListener(new OnNavigationIconClick());
        toolbar.setOnMenuItemClickListener(new NoteListMenuListener());

        noteItemAdapter = new NoteListAdapter(category.getNoteItems());
        LinearLayoutManager layoutManager = new LinearLayoutManager(context);
        layoutManager.setReverseLayout(true);

        rlNoteList.setAdapter(noteItemAdapter);
        rlNoteList.setLayoutManager(layoutManager);
        rlNoteList.addItemDecoration(new DividerItemDecoration(context, DividerItemDecoration.VERTICAL));

        btnAddNote.setOnClickListener(new onItemAddClick());
        rlNoteList.setOnTouchListener(new NoteListTouchListener());
    }

    class OnNavigationIconClick implements View.OnClickListener {
        @Override
        public void onClick(View view) {
            if (isInSelectMode) {
                exitSelectMode();
            } else {
                finish();
            }
        }
    }

    private void handleMenuDelete() {
        ArrayList<NoteItem> items = noteItemAdapter.getSelectedItems();
        exitSelectMode();
        listener.onAttemptBatch(AttemptListener.Attempt.REMOVE, items);
    }

    private void handleMenuCopy() {
        ArrayList<NoteItem> items = noteItemAdapter.getSelectedItems();
        exitSelectMode();
        listener.onAttemptBatch(AttemptListener.Attempt.COPY, items);
    }

    private void handleMenuShare() {
        ArrayList<NoteItem> items = noteItemAdapter.getSelectedItems();
        exitSelectMode();
        listener.onAttemptBatch(AttemptListener.Attempt.SHARE, items);
    }

    private boolean isInSelectMode = false;
    class NoteListMenuListener implements Toolbar.OnMenuItemClickListener {
        @Override
        public boolean onMenuItemClick(MenuItem item) {
            int menu_id = item.getItemId();
            if (R.id.note_list_menu_delete == menu_id) {
                handleMenuDelete();
            } else if (R.id.note_list_menu_copy == menu_id) {
                handleMenuCopy();
            } else if (R.id.note_list_menu_share == menu_id) {
                handleMenuShare();
            }
            return false;
        }
    }

    private void hideMenu() {
        toolbar.getMenu().clear();
    }

    private void showMenu() {
        toolbar.inflateMenu(R.menu.fragment_note_list_toolbar);
    }

    private void enterSelectMode() {
        if (isInSelectMode) return;
        isInSelectMode = true;
        showMenu();
        toolbar.setNavigationIcon(R.drawable.icon_common_cross);
    }

    private void exitSelectMode() {
        if (!isInSelectMode) return;
        hideMenu();
        toolbar.setNavigationIcon(R.drawable.icon_common_back);
        noteItemAdapter.clearSelectItems();
        isInSelectMode = false;
    }

    private void tryToggleItemSelect(int index) {
        int count = noteItemAdapter.toggleSelectItems(index);
        if (count == 0) {
            exitSelectMode();
        }
    }

    class NoteListTouchListener extends OnRecyclerViewTouchListener {

        @Override
        public boolean onItemClick(View v, int index) {
            if (isInSelectMode) {
                tryToggleItemSelect(index);
            } else {
                showItemPopMenu(index, v);
            }
            return true;
        }

        @Override
        public boolean onItemLongClick(View v, int index) {
            enterSelectMode();
            tryToggleItemSelect(index);
            return true;
        }

        @Override
        public boolean onSlideEnd(View v, MotionEvent e, float deltaX, float deltaY) {
            int width = fragmentView.getWidth();
            int threshold_exit = width/3;
            if (deltaX > threshold_exit) {
                finish();
            } else {
                resetPosition();
            }
            return true;
        }

        @Override
        public boolean onSlideOnGoing(View v, MotionEvent e, float deltaX, float deltaY) {
            if (deltaX > 0 ) {
                fragmentView.setTranslationX(deltaX);
            } else {
                fragmentView.setTranslationX(0);
            }
            return true;
        }
    }

    private static final String propName = "translationX";
    public void finish(long duration) {
        float startX = fragmentView.getTranslationX();
        float width = fragmentView.getWidth();
        ObjectAnimator animator = ObjectAnimator.ofFloat(fragmentView,
                propName, startX, width).setDuration(duration);
        animator.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animation) {
                super.onAnimationEnd(animation);
                if (isInSelectMode) {
                    exitSelectMode();
                }
                getParentFragmentManager().beginTransaction()
                        .remove(NoteListFragment.this).commit();
            }
        });
        animator.start();
    }
    public void finish() {
        finish(200);
    }
    public void resetPosition() {
        float startX = fragmentView.getTranslationX();
        if (startX == 0) return;
        ObjectAnimator.ofFloat(fragmentView,
                propName, startX, 0).setDuration(100).start();
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

            private int naviHei;
            @Override
            public void onPrepare(@NonNull WindowInsetsAnimationCompat animation) {
                super.onPrepare(animation);
                naviHei = naviBar.getHeight();
            }

            @NonNull
            @Override
            public WindowInsetsCompat onProgress(@NonNull WindowInsetsCompat insets,
                                                 @NonNull List<WindowInsetsAnimationCompat> runningAnimations) {
                int imeHei = insets.getInsets(WindowInsetsCompat.Type.ime()).bottom;
                ViewGroup.MarginLayoutParams params =
                        (ViewGroup.MarginLayoutParams) contentContainer.getLayoutParams();
                if (imeHei > naviHei) {
                    params.bottomMargin = imeHei - naviHei;
                    contentContainer.setLayoutParams(params);
                } else {
                    params.bottomMargin = 0;
                    contentContainer.setLayoutParams(params);
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
            listener.onAttempt(AttemptListener.Attempt.CREATE, item);
        }
    }

    private void showItemPopMenu(int index, View v) {
        PopupMenu menu = new PopupMenu(context, v);
        NoteItem noteItem = category.getNoteItem(index);
        if (noteItem == null) {
            listener.onError("Failed to get note item at " + index + ", abort");
            return;
        }
        menu.setOnMenuItemClickListener(menuItem -> {
            int item_id = menuItem.getItemId();
            if (R.id.item_pop_m_delete == item_id) {
                listener.onAttempt(AttemptListener.Attempt.REMOVE, noteItem);
            } else if (R.id.item_pop_m_pin == item_id) {
                listener.onError("try to Pin item at " + index);
            } else if (R.id.item_pop_m_edit == item_id) {
                listener.onAttempt(AttemptListener.Attempt.DETAIL, noteItem);
            } else if (R.id.item_pop_m_copy_text == item_id) {
                listener.onAttempt(AttemptListener.Attempt.COPY, noteItem);
            } else if (R.id.item_pop_m_share == item_id) {
                listener.onAttempt(AttemptListener.Attempt.SHARE, noteItem);
            } else {
                listener.onError( "Unknown menu id: " +
                        getResources().getResourceEntryName(item_id));
                return false;
            }
            return true;
        });
        menu.inflate(R.menu.item_pop_menu);
        menu.show();
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
                noteItemAdapter.add(index, note);
                rlNoteList.smoothScrollToPosition(index);
                if (note == pendingNoteItem) {
                    clearPendingItem();
                    // clear input box text for manually added item
                    etInputText.setText("");
                }
                break;
            case UPDATED:
                noteItemAdapter.update(index);
                break;
            case REMOVED:
                noteItemAdapter.remove(note);
                break;
            default:
        }
    }

}