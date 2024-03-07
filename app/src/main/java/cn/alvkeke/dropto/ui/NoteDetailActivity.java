package cn.alvkeke.dropto.ui;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;

import androidx.appcompat.app.AppCompatActivity;

import cn.alvkeke.dropto.R;
import cn.alvkeke.dropto.data.NoteItem;

import java.util.Date;

public class NoteDetailActivity extends AppCompatActivity {


    public static final String ITEM_OBJECT = "ITEM_OBJECT";
    public static final String ITEM_INDEX = "ITEM_INDEX";
    public static final int RESULT_DELETED = RESULT_FIRST_USER;

    private EditText etNoteItemText;
    private NoteItem item = null;
    private int targetIndex;
    public static final int ITEM_INDEX_NONE = -1;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_note_detail);

        Button btnOk = findViewById(R.id.note_detail_btn_ok);
        Button btnCancel = findViewById(R.id.note_detail_btn_cancel);
        Button btnDel = findViewById(R.id.note_detail_btn_del);
        etNoteItemText = findViewById(R.id.note_detail_text);

        btnOk.setOnClickListener(new ItemAddOk());
        btnCancel.setOnClickListener(new ItemAddCanceled());

        Intent intent = getIntent();
        targetIndex = intent.getIntExtra(ITEM_INDEX, ITEM_INDEX_NONE);
        if (targetIndex != ITEM_INDEX_NONE) {
            // edit exist item
            item = (NoteItem) intent.getSerializableExtra(ITEM_OBJECT);
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

            Intent intent = getIntent();

            String text = etNoteItemText.getText().toString();
            if (item == null) {
                item = new NoteItem(text, new Date().getTime());
            } else {
                item.setText(text, true);
            }
            intent.putExtra(ITEM_OBJECT, item);
            intent.putExtra(ITEM_INDEX, targetIndex);

            setResult(RESULT_OK, intent);
            NoteDetailActivity.this.finish();
        }
    }

    class ItemAddCanceled implements View.OnClickListener {

        @Override
        public void onClick(View v) {
            setResult(RESULT_CANCELED);
            NoteDetailActivity.this.finish();
        }
    }

    class ItemDelete implements View.OnClickListener {

        @Override
        public void onClick(View v) {
            Intent intent = getIntent();

            intent.putExtra(ITEM_INDEX, targetIndex);

            setResult(RESULT_DELETED, intent);
            NoteDetailActivity.this.finish();
        }
    }
}