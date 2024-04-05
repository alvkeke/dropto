package cn.alvkeke.dropto.ui.fragment;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.ScrollView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.widget.Toolbar;
import androidx.constraintlayout.widget.ConstraintLayout;

import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.bottomsheet.BottomSheetBehavior;
import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.google.android.material.bottomsheet.BottomSheetDialogFragment;

import java.io.File;

import cn.alvkeke.dropto.R;
import cn.alvkeke.dropto.data.NoteItem;

public class NoteDetailFragment extends BottomSheetDialogFragment {

    public enum Result {
        CREATE,
        REMOVE,
        UPDATE,
    }

    public interface NoteEventListener {
        void onNoteDetailFinish(Result result, NoteItem e);
    }

    private NoteEventListener listener;
    private EditText etNoteItemText;
    private ConstraintLayout image_container;
    private ImageView image_view;
    private ImageView image_remove;
    private TextView image_name;
    private TextView image_md5;
    private NoteItem item;
    private boolean isRemoveImage = false;

    public NoteDetailFragment() {

    }

    public NoteDetailFragment(NoteItem item) {
        this.item = item;
    }

    public void setNoteItem(NoteItem item) {
        this.item = item;
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_note_detail, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        MaterialToolbar toolbar = view.findViewById(R.id.note_detail_toolbar);
        etNoteItemText = view.findViewById(R.id.note_detail_text);
        image_container = view.findViewById(R.id.note_detail_image_container);
        ScrollView scroll_view = view.findViewById(R.id.note_detail_scroll);
        image_view = view.findViewById(R.id.note_detail_image_view);
        image_remove = view.findViewById(R.id.note_detail_image_remove);
        image_name = view.findViewById(R.id.note_detail_image_name);
        image_md5 = view.findViewById(R.id.note_detail_image_md5);

        initEssentialVars();
        setPeekHeight(view);

        if (item != null) loadItemData();

        listener = (NoteEventListener) requireContext();

        toolbar.inflateMenu(R.menu.note_detail_toolbar);
        toolbar.setOnMenuItemClickListener(new NoteDetailMenuListener());
        toolbar.setNavigationIcon(R.drawable.icon_common_back);
        toolbar.setNavigationOnClickListener(new BackNavigationClick());
        scroll_view.setOnScrollChangeListener(new ScrollViewListener());
    }

    private boolean isDraggable = true;

    class ScrollViewListener implements View.OnScrollChangeListener {

        @Override
        public void onScrollChange(View view, int x, int y, int i2, int i3) {
            if (y <= 0) {
                if (!isDraggable) {
                    isDraggable = true;
                    behavior.setDraggable(true);
                }
            } else {
                if (isDraggable) {
                    isDraggable = false;
                    behavior.setDraggable(false);
                }
            }
        }
    }

    BottomSheetBehavior<FrameLayout> behavior;
    private void initEssentialVars() {
        BottomSheetDialog dialog = (BottomSheetDialog) requireDialog();
        behavior = dialog.getBehavior();
    }

    private void setPeekHeight(View view) {
        int displayHei= requireActivity().getResources().getDisplayMetrics().heightPixels;
        int peekHei = displayHei * 35 / 100;
        behavior.setPeekHeight(peekHei);
        ViewGroup.LayoutParams layoutParams = view.getLayoutParams();
        layoutParams.height = displayHei;
    }

    private void finish() {
        this.dismiss();
    }

    private class BackNavigationClick implements View.OnClickListener {
        @Override
        public void onClick(View view) {
            finish();
        }
    }

    private void handleOk() {
        String text = etNoteItemText.getText().toString();
        if (item == null) {
            item = new NoteItem(text);
            listener.onNoteDetailFinish(Result.CREATE, item);
        } else {
            item.setText(text, true);
            if (isRemoveImage) {
                item.setImageName(null);
                item.setImageFile(null);
            }
            listener.onNoteDetailFinish(Result.UPDATE, item);
        }
    }

    private class NoteDetailMenuListener implements Toolbar.OnMenuItemClickListener {
        @Override
        public boolean onMenuItemClick(MenuItem menuItem) {
            int menuId = menuItem.getItemId();
            if (R.id.note_detail_menu_item_ok == menuId) {
                handleOk();
            } else if (R.id.note_detail_menu_item_delete == menuId) {
                Log.d(this.toString(), "remove item");
                listener.onNoteDetailFinish(Result.REMOVE, item);
            } else {
                Log.e(this.toString(), "got unknown menu id: " + menuId);
                return false;
            }
            finish();
            return true;
        }
    }

    /**
     * load item info to View, input item cannot be null,
     * there is no valid-check for the item.
     */
    private void loadItemData() {
        etNoteItemText.setText(item.getText());

        File imgfile = item.getImageFile();
        if (imgfile == null) {
            image_container.setVisibility(View.GONE);
            return;
        }
        image_name.setText(item.getImageName());
        image_md5.setText(item.getImageFile().getName());
        image_remove.setOnClickListener(view -> {
            isRemoveImage = true;
            image_container.setVisibility(View.GONE);
        });
        Log.d(this.toString(), "Set image file: " + imgfile.getPath());
        Bitmap bitmap = BitmapFactory.decodeFile(imgfile.getAbsolutePath());
        if (bitmap == null) {
            Log.e(this.toString(), "Failed to get image file, skip this item");
            return;
        }
        image_view.setImageBitmap(bitmap);
    }

}