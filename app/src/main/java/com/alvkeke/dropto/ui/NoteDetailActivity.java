package com.alvkeke.dropto.ui;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;

import androidx.appcompat.app.AppCompatActivity;

import com.alvkeke.dropto.R;

public class NoteDetailActivity extends AppCompatActivity {


    public static final String ITEM_INFO_TEXT = "ITEM_INFO_TEXT";

    private EditText etNoteItemText;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_note_detail);

        Button btnOk = findViewById(R.id.note_detail_btn_ok);
        Button btnCancel = findViewById(R.id.note_detail_btn_cancel);
        etNoteItemText = findViewById(R.id.note_detail_text);

        btnOk.setOnClickListener(new ItemAddOk());
        btnCancel.setOnClickListener(new ItemAddCanceled());

    }


    class ItemAddOk implements View.OnClickListener{

        @Override
        public void onClick(View v) {

            Intent intent = getIntent();

            String text = etNoteItemText.getText().toString();
            intent.putExtra(ITEM_INFO_TEXT, text);

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
}