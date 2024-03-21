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
import androidx.fragment.app.FragmentManager;

import cn.alvkeke.dropto.R;
import cn.alvkeke.dropto.data.NoteItem;

public class NoteDetailFragment extends Fragment {


    public static final String REQUEST_KEY = "NoteDetailRequestKey";
    public static final String ITEM_OPERATION = "ITEM_OPERATION";
    public static final String ITEM_OBJECT = "ITEM_OBJECT";
    public static final String ITEM_INDEX = "ITEM_INDEX";

    public enum Operation {
        CANCELED,
        OK,
        DELETE,
    }

    private EditText etNoteItemText;
    private NoteItem item = null;
    private int targetIndex;
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

        btnOk.setOnClickListener(new ItemAddOk());
        btnCancel.setOnClickListener(new ItemAddCanceled());

        Bundle bundle = requireArguments();
        targetIndex = bundle.getInt(ITEM_INDEX, ITEM_INDEX_NONE);
        if (targetIndex != ITEM_INDEX_NONE) {
            item = (NoteItem) bundle.getSerializable(ITEM_OBJECT);
            assert item != null;
            loadItemData(item);

            btnDel.setOnClickListener(new ItemDelete());
        } else {
            // create a new item
            btnDel.setVisibility(View.INVISIBLE);
        }

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

            Bundle bundle = requireArguments();

            String text = etNoteItemText.getText().toString();
            if (item == null) {
                item = new NoteItem(text, System.currentTimeMillis());
            } else {
                item.setText(text, true);
            }
            bundle.putInt(ITEM_OPERATION, Operation.OK.ordinal());
            bundle.putInt(ITEM_INDEX, targetIndex);
            bundle.putSerializable(ITEM_OBJECT, item);


            FragmentManager manager = getParentFragmentManager();
            manager.setFragmentResult(REQUEST_KEY, bundle);
            manager.popBackStack();
        }
    }

    class ItemAddCanceled implements View.OnClickListener {

        @Override
        public void onClick(View v) {
            Bundle bundle = requireArguments();
            bundle.putInt(ITEM_OPERATION, Operation.CANCELED.ordinal());
            FragmentManager manager = getParentFragmentManager();
            manager.setFragmentResult(REQUEST_KEY, bundle);
            manager.popBackStack();
        }
    }

    class ItemDelete implements View.OnClickListener {

        @Override
        public void onClick(View v) {
            Bundle bundle = requireArguments();
            bundle.putInt(ITEM_OPERATION, Operation.DELETE.ordinal());
            bundle.putInt(ITEM_INDEX, targetIndex);
            FragmentManager manager = getParentFragmentManager();
            manager.setFragmentResult(REQUEST_KEY, bundle);
            manager.popBackStack();
        }
    }
}