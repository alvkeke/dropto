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
        void onNoteExit();
        void onNoteAdd(NoteItem item);
    }

    private NoteEventListener listener;
    private EditText etNoteItemText;
    private NoteItem item;
    private final int index;

    public NoteDetailFragment(int index, NoteItem item) {
        this.index = index;
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
        if (item != null) loadItemData(item);

        listener = (NoteEventListener) requireContext();

        btnOk.setOnClickListener(new ItemAddOk());
        btnCancel.setOnClickListener(new ItemAddCanceled());
        btnDel.setOnClickListener(new ItemDelete());
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
                listener.onNoteAdd(item);
            } else {
                item.setText(text, true);
                listener.onNoteEdit(index, item);
            }
            listener.onNoteExit();
        }
    }

    class ItemAddCanceled implements View.OnClickListener {

        @Override
        public void onClick(View v) {
            listener.onNoteExit();
        }
    }

    class ItemDelete implements View.OnClickListener {

        @Override
        public void onClick(View v) {
            listener.onNoteDelete(index, item);
            listener.onNoteExit();
        }
    }
}