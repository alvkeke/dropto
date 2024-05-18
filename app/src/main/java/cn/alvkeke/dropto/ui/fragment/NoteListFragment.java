package cn.alvkeke.dropto.ui.fragment;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ObjectAnimator;
import android.annotation.SuppressLint;
import android.content.Context;
import android.net.Uri;
import android.os.Bundle;
import android.util.Log;
import android.view.HapticFeedbackConstants;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.ImageView;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.PickVisualMediaRequest;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.widget.PopupMenu;
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

import java.io.File;
import java.util.ArrayList;
import java.util.List;

import cn.alvkeke.dropto.R;
import cn.alvkeke.dropto.data.Category;
import cn.alvkeke.dropto.mgmt.Global;
import cn.alvkeke.dropto.data.ImageFile;
import cn.alvkeke.dropto.data.NoteItem;
import cn.alvkeke.dropto.storage.FileHelper;
import cn.alvkeke.dropto.ui.adapter.NoteListAdapter;
import cn.alvkeke.dropto.ui.comonent.MyPopupMenu;
import cn.alvkeke.dropto.ui.intf.ErrorMessageHandler;
import cn.alvkeke.dropto.ui.intf.FragmentOnBackListener;
import cn.alvkeke.dropto.ui.intf.ListNotification;
import cn.alvkeke.dropto.ui.intf.NoteAttemptListener;
import cn.alvkeke.dropto.ui.listener.OnRecyclerViewTouchListener;

public class NoteListFragment extends Fragment implements ListNotification<NoteItem>, FragmentOnBackListener {

    private Context context;
    private NoteAttemptListener listener;
    Category category;
    NoteListAdapter noteItemAdapter;
    private View fragmentParent;
    private View fragmentView;
    private EditText etInputText;
    private ImageView imgAttachClear;
    private ConstraintLayout contentContainer;
    private View naviBar;
    private MaterialToolbar toolbar;
    private RecyclerView rlNoteList;

    public NoteListFragment() {
    }

    public void setCategory(Category category) {
        this.category = category;
        if (noteItemAdapter != null) {
            noteItemAdapter.setList(category.getNoteItems());
        }
    }

    public Category getCategory() {
        return this.category;
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        fragmentParent = inflater.inflate(R.layout.fragment_note_list, container, false);
        return fragmentParent;
    }

    @SuppressLint("ClickableViewAccessibility")
    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        context = requireContext();
        listener = (NoteAttemptListener) context;
        assert category != null;

        fragmentView = view.findViewById(R.id.note_list_fragment_container);
        rlNoteList = view.findViewById(R.id.note_list_listview);
        ImageButton btnAddNote = view.findViewById(R.id.note_list_input_button);
        ImageButton btnAttach = view.findViewById(R.id.note_list_input_attach);
        imgAttachClear = view.findViewById(R.id.note_list_input_attach_clear);
        etInputText = view.findViewById(R.id.note_list_input_box);
        contentContainer = view.findViewById(R.id.note_list_content_container);
        View statusBar = view.findViewById(R.id.note_list_status_bar);
        naviBar = view.findViewById(R.id.note_list_navigation_bar);
        toolbar = view.findViewById(R.id.note_list_toolbar);

        setSystemBarHeight(view, statusBar, naviBar);
        setIMEViewChange(view);

        toolbar.setTitle(category.getTitle());
        toolbar.setNavigationIcon(R.drawable.icon_common_back);
        toolbar.setNavigationOnClickListener(new OnNavigationIconClick());
        toolbar.setOnMenuItemClickListener(new NoteListMenuListener());

        noteItemAdapter = new NoteListAdapter();
        LinearLayoutManager layoutManager = new LinearLayoutManager(context);
        layoutManager.setReverseLayout(true);
        noteItemAdapter.setList(category.getNoteItems());

        rlNoteList.setAdapter(noteItemAdapter);
        rlNoteList.setLayoutManager(layoutManager);
        rlNoteList.addItemDecoration(new DividerItemDecoration(context, DividerItemDecoration.VERTICAL));

        btnAddNote.setOnClickListener(new onItemAddClick());
        btnAttach.setOnClickListener(new OnItemAttachClick());
        btnAttach.setOnLongClickListener(new OnItemAttachLongClick());
        rlNoteList.setOnTouchListener(new NoteListTouchListener());
    }

    @Override
    public boolean onBackPressed() {
        finish();
        return true;
    }

    private class OnNavigationIconClick implements View.OnClickListener {
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
        listener.onAttemptBatch(NoteAttemptListener.Attempt.REMOVE, items);
    }

    private void handleMenuCopy() {
        ArrayList<NoteItem> items = noteItemAdapter.getSelectedItems();
        exitSelectMode();
        listener.onAttemptBatch(NoteAttemptListener.Attempt.COPY, items);
    }

    private void handleMenuShare() {
        ArrayList<NoteItem> items = noteItemAdapter.getSelectedItems();
        exitSelectMode();
        listener.onAttemptBatch(NoteAttemptListener.Attempt.SHOW_SHARE, items);
    }

    private void handleMenuForward() {
        ArrayList<NoteItem> items = noteItemAdapter.getSelectedItems();
        exitSelectMode();
        listener.onAttemptBatch(NoteAttemptListener.Attempt.SHOW_FORWARD, items);
    }

    private boolean isInSelectMode = false;
    private class NoteListMenuListener implements Toolbar.OnMenuItemClickListener {
        @Override
        public boolean onMenuItemClick(MenuItem item) {
            int menu_id = item.getItemId();
            if (R.id.note_list_menu_delete == menu_id) {
                handleMenuDelete();
            } else if (R.id.note_list_menu_copy == menu_id) {
                handleMenuCopy();
            } else if (R.id.note_list_menu_share == menu_id) {
                handleMenuShare();
            } else if (R.id.note_list_menu_forward == menu_id) {
                handleMenuForward();
            }
            return false;
        }
    }

    private void hideToolbarMenu() {
        toolbar.getMenu().clear();
    }

    private void showToolbarMenu() {
        toolbar.inflateMenu(R.menu.fragment_note_list_toolbar);
    }

    private void enterSelectMode() {
        if (isInSelectMode) return;
        isInSelectMode = true;
        showToolbarMenu();
        toolbar.setNavigationIcon(R.drawable.icon_common_cross);
    }

    private void exitSelectMode() {
        if (!isInSelectMode) return;
        hideToolbarMenu();
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

    private class NoteListTouchListener extends OnRecyclerViewTouchListener {

        @Override
        public boolean onItemClickAt(View v, int index, MotionEvent event) {
            if (isInSelectMode) {
                tryToggleItemSelect(index);
            } else {
                int x = (int) event.getRawX();
                int y = (int) event.getRawY();
                int imgIdx = NoteListAdapter.checkImageClicked(v, x, y);

                if (imgIdx >= 0) {
                    showImageView(index, imgIdx, x, y);
                } else {
                    showItemPopMenu(index, v, x, y);
                }
            }
            return true;
        }

        @Override
        public boolean onItemLongClick(View v, int index) {
            v.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS);
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
                moveFragmentView(deltaX);
            } else {
                moveFragmentView(0);
            }
            return true;
        }
    }

    private void setMaskTransparent(float targetX) {
        int width = fragmentView.getWidth();
        float alpha = (width - targetX)/ width;
        fragmentParent.getBackground().setAlpha((int) (alpha * 255 / 2));
    }

    private void moveFragmentView(float targetX) {
        setMaskTransparent(targetX);
        fragmentView.setTranslationX(targetX);
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
                imgUris.clear();
                getParentFragmentManager().beginTransaction()
                        .remove(NoteListFragment.this).commit();
            }
        });
        animator.addUpdateListener(valueAnimator -> {
            float deltaX = (float) valueAnimator.getAnimatedValue();
            setMaskTransparent(deltaX);
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

    private final ArrayList<Uri> imgUris = new ArrayList<>();
    private void addAttachment(Uri uri) {
        if (!imgUris.contains(uri)) {
            imgUris.add(uri);
        }
        imgAttachClear.setImageResource(getAttachIcon());
        imgAttachClear.setVisibility(View.VISIBLE);
    }
    private void clearAttachment() {
        imgUris.clear();
        imgAttachClear.setVisibility(View.GONE);
    }
    private final ActivityResultLauncher<PickVisualMediaRequest> imagePicker =
            registerForActivityResult(new ActivityResultContracts.
                            PickMultipleVisualMedia(9), uris-> {
                for (Uri uri : uris) {
                    if (uri == null) continue;
                    addAttachment(uri);
                }
            });

    private int getAttachIcon() {
        switch (imgUris.size()) {
            case 1:
                return R.drawable.icon_attach_1;
            case 2:
                return R.drawable.icon_attach_2;
            case 3:
                return R.drawable.icon_attach_3;
            case 4:
                return R.drawable.icon_attach_4;
            case 5:
                return R.drawable.icon_attach_5;
            case 6:
                return R.drawable.icon_attach_6;
            case 7:
                return R.drawable.icon_attach_7;
            case 8:
                return R.drawable.icon_attach_8;
            case 9:
                return R.drawable.icon_attach_9;
            default:
                return R.drawable.icon_attach_9p;
        }
    }

    private class OnItemAttachClick implements View.OnClickListener {
        @Override
        public void onClick(View view) {
            imagePicker.launch(new PickVisualMediaRequest.Builder()
                    .setMediaType(ActivityResultContracts.PickVisualMedia.ImageOnly.INSTANCE)
                    .build());
        }
    }
    private class OnItemAttachLongClick implements View.OnLongClickListener {
        @Override
        public boolean onLongClick(View view) {
            clearAttachment();
            return true;
        }
    }

    private class onItemAddClick implements View.OnClickListener {
        @Override
        public void onClick(View v) {
            String content = etInputText.getText().toString().trim();
            NoteItem item = new NoteItem(content);
            item.setCategoryId(category.getId());
            if (imgUris.isEmpty()) {
                if (content.isEmpty()) return;
            } else {
                for (Uri imgUri: imgUris) {
                    File folder = Global.getInstance().getFolderImage(context);
                    File md5file = FileHelper.saveUriToFile(context, imgUri, folder);
                    String imgName = FileHelper.getFileNameFromUri(context, imgUri);
                    ImageFile imageFile = ImageFile.from(md5file, imgName);
                    item.addImageFile(imageFile);
                }
            }
            setPendingItem(item);
            listener.onAttempt(NoteAttemptListener.Attempt.CREATE, item);
        }
    }

    private void showImageView(int index, int imageIndex, int ignore, int ignore1) {
        NoteItem noteItem = category.getNoteItem(index);
        if (noteItem.getImageCount() > 4 && imageIndex == 3) {
            listener.onAttempt(NoteAttemptListener.Attempt.SHOW_DETAIL, noteItem);
        } else {
            listener.onAttempt(NoteAttemptListener.Attempt.SHOW_IMAGE, noteItem, imageIndex);
        }
    }

    private void throwErrorMessage(String msg) {
        if (!(listener instanceof ErrorMessageHandler)) return;
        ErrorMessageHandler handler = (ErrorMessageHandler) listener;
        handler.onError(msg);
    }

    private MyPopupMenu myPopupMenu = null;
    private void showItemPopMenu(int index, View v, int x, int y) {
        NoteItem noteItem = category.getNoteItem(index);
        if (noteItem == null) {
            throwErrorMessage("Failed to get note item at " + index + ", abort");
            return;
        }
        if (myPopupMenu == null) {
            Menu menu = new PopupMenu(context, v).getMenu();
            requireActivity().getMenuInflater().inflate(R.menu.item_pop_menu, menu);
            myPopupMenu = new MyPopupMenu(context).setMenu(menu).setListener((menuItem, obj)-> {
                NoteItem note = (NoteItem) obj;
                int item_id = menuItem.getItemId();
                if (R.id.item_pop_m_delete == item_id) {
                    listener.onAttempt(NoteAttemptListener.Attempt.REMOVE, note);
                } else if (R.id.item_pop_m_pin == item_id) {
                    throwErrorMessage("try to Pin item at " + index);
                } else if (R.id.item_pop_m_edit == item_id) {
                    listener.onAttempt(NoteAttemptListener.Attempt.SHOW_DETAIL, note);
                } else if (R.id.item_pop_m_copy_text == item_id) {
                    listener.onAttempt(NoteAttemptListener.Attempt.COPY, note);
                } else if (R.id.item_pop_m_share == item_id) {
                    listener.onAttempt(NoteAttemptListener.Attempt.SHOW_SHARE, note);
                } else if (R.id.item_pop_m_forward == item_id) {
                    listener.onAttempt(NoteAttemptListener.Attempt.SHOW_FORWARD, note);
                } else {
                    throwErrorMessage( "Unknown menu id: " +
                            getResources().getResourceEntryName(item_id));
                }
            });
        }
        myPopupMenu.setData(noteItem).show(v, x, y);
    }

    private NoteItem pendingNoteItem = null;
    private void setPendingItem(NoteItem item) {
        pendingNoteItem = item;
    }
    private void clearPendingItem() {
        pendingNoteItem = null;
    }

    @Override
    public void notifyItemListChanged(Notify notify, int index, NoteItem note) {
        if (note != null && note.getCategoryId() != category.getId()) {
            Log.e(this.toString(), "target NoteItem not exist in current category");
            return;
        }
        switch (notify) {
            case INSERTED:
                if (!noteItemAdapter.add(index, note)) break;
                rlNoteList.smoothScrollToPosition(index);
                if (note == pendingNoteItem) {
                    clearPendingItem();
                    // clear input box text for manually added item
                    etInputText.setText("");
                    clearAttachment();
                }
                break;
            case UPDATED:
                noteItemAdapter.update(index);
                break;
            case REMOVED:
                noteItemAdapter.remove(note);
                break;
            case CLEARED:
                noteItemAdapter.clear();
                break;
            default:
        }
    }

}