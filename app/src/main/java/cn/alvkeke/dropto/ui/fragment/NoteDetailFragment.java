package cn.alvkeke.dropto.ui.fragment;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import cn.alvkeke.dropto.R;
import cn.alvkeke.dropto.data.NoteItem;

public class NoteDetailFragment extends Fragment {

    public interface NoteEventListener {
        void onNoteEdit(int index, NoteItem newNote);
        void onNoteDelete(int index, NoteItem noteItem);
        void onNoteCancel(int index, NoteItem noteItem);
        void onNoteAdd(NoteItem item);
    }

    public static final String REQUEST_KEY = "NoteDetailRequestKey";
    public static final String ITEM_OPERATION = "ITEM_OPERATION";
    public static final String ITEM_OBJECT = "ITEM_OBJECT";
    public static final String ITEM_INDEX = "ITEM_INDEX";

    public enum Operation {
        CANCELED,
        OK,
        DELETE,
    }

    private NoteEventListener listener;
    private EditText etNoteItemText;
    private NoteItem item = null;
    private int index = ITEM_INDEX_NONE;
    public static final int ITEM_INDEX_NONE = -1;

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

        listener = (NoteEventListener) requireContext();

        btnOk.setOnClickListener(new ItemAddOk());
        btnCancel.setOnClickListener(new ItemAddCanceled());
        btnDel.setOnClickListener(new ItemDelete());
    }

    public void setItem(int index, NoteItem item) {
        this.index = index;
        this.item = item;
    }

    /**
     * load item info to View, input item cannot be null,
     * there is no valid-check for the item.
     * @param item note item contain info.
     */
    private void loadItemData(NoteItem item) {
        etNoteItemText.setText(item.getText());
    }

    class ItemAddOk implements View.OnClickListener{

        @Override
        public void onClick(View v) {
            String text = etNoteItemText.getText().toString();
            if (item == null) {
                item = new NoteItem(text, System.currentTimeMillis());
            } else {
                item.setText(text, true);
            }
            listener.onNoteEdit(index, item);
        }
    }

    class ItemAddCanceled implements View.OnClickListener {

        @Override
        public void onClick(View v) {
            listener.onNoteCancel(index, item);
        }
    }

    class ItemDelete implements View.OnClickListener {

        @Override
        public void onClick(View v) {
            listener.onNoteDelete(index, item);
        }
    }
}