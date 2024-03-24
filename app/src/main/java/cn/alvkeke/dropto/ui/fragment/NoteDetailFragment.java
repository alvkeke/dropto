package cn.alvkeke.dropto.ui.fragment;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.constraintlayout.widget.ConstraintLayout;
import androidx.fragment.app.Fragment;

import java.io.File;

import cn.alvkeke.dropto.R;
import cn.alvkeke.dropto.data.NoteItem;
import cn.alvkeke.dropto.ui.SystemKeyListener;

public class NoteDetailFragment extends Fragment implements SystemKeyListener {

    public enum Result {
        CANCELED,
        CREATED,
        REMOVED,
        MODIFIED,
    }

    public interface NoteEventListener {
        void onNoteDetailExit(Result result, NoteItem e);
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

    public NoteDetailFragment(NoteItem item) {
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

        Button btnOk = view.findViewById(R.id.note_detail_btn_ok);
        Button btnCancel = view.findViewById(R.id.note_detail_btn_cancel);
        Button btnDel = view.findViewById(R.id.note_detail_btn_del);
        etNoteItemText = view.findViewById(R.id.note_detail_text);
        image_container = view.findViewById(R.id.note_detail_image_container);
        image_view = view.findViewById(R.id.note_detail_image_view);
        image_remove = view.findViewById(R.id.note_detail_image_remove);
        image_name = view.findViewById(R.id.note_detail_image_name);
        image_md5 = view.findViewById(R.id.note_detail_image_md5);

        if (item != null) loadItemData();

        listener = (NoteEventListener) requireContext();

        btnOk.setOnClickListener(new ItemAddOk());
        btnCancel.setOnClickListener(new ItemAddCanceled());
        btnDel.setOnClickListener(new ItemDelete());
    }

    @Override
    public boolean onBackPressed() {
        listener.onNoteDetailExit(Result.CANCELED, item);
        return true;
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

    class ItemAddOk implements View.OnClickListener{

        @Override
        public void onClick(View v) {
            String text = etNoteItemText.getText().toString();
            if (item == null) {
                item = new NoteItem(text, System.currentTimeMillis());
                listener.onNoteDetailExit(Result.CREATED, item);
            } else {
                item.setText(text, true);
                if (isRemoveImage) {
                    item.setImageName(null);
                    item.setImageFile(null);
                }
                listener.onNoteDetailExit(Result.MODIFIED, item);
            }
        }
    }

    class ItemAddCanceled implements View.OnClickListener {

        @Override
        public void onClick(View v) {
            listener.onNoteDetailExit(Result.CANCELED, item);
        }
    }

    class ItemDelete implements View.OnClickListener {

        @Override
        public void onClick(View v) {
            listener.onNoteDetailExit(Result.REMOVED, item);
        }
    }
}