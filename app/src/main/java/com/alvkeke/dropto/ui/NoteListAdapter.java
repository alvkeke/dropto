package com.alvkeke.dropto.ui;

import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.alvkeke.dropto.R;
import com.alvkeke.dropto.data.NoteItem;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.Locale;

public class NoteListAdapter extends RecyclerView.Adapter<NoteListAdapter.ViewHolder> {

    ArrayList<NoteItem> mList;

    public NoteListAdapter(ArrayList<NoteItem> list) {
        mList = list;
    }

    public static class ViewHolder extends RecyclerView.ViewHolder {

        private final TextView tvText;
        private final TextView tvCreateTime;
        private final ImageView ivEdited;

        public ViewHolder(@NonNull View itemView) {
            super(itemView);
            tvText = itemView.findViewById(R.id.rlist_item_note_text);
            tvCreateTime = itemView.findViewById(R.id.rlist_item_note_create_time);
            ivEdited = itemView.findViewById(R.id.rlist_item_note_is_edited);
        }

        private void showView(View v) {
            v.setVisibility(View.VISIBLE);
        }
        private void hideView(View v) {
            v.setVisibility(View.GONE);
        }

        static SimpleDateFormat sdf = new SimpleDateFormat("yyyy/MM/dd HH:mm", Locale.CHINESE);
        private String timeFormat(long timestamp) {
            return sdf.format(new Date(timestamp));
        }

        public void setText(String text) {
            tvText.setText(text);
        }

        public void setCreateTime(long time) {
            if (time < 0) {
                hideView(tvCreateTime);
                return;
            }
            showView(tvCreateTime);
            tvCreateTime.setText(timeFormat(time));
        }

        public void setIsEdited(boolean foo) {
            tvCreateTime.measure(0, 0);
            int he = tvCreateTime.getMeasuredHeight();
            Log.e(this.toString(), "size: " + he);
            ivEdited.getLayoutParams().height = he;
            ivEdited.getLayoutParams().width = he;
            ivEdited.requestLayout();
            ivEdited.setVisibility(foo? View.VISIBLE : View.INVISIBLE);
        }

    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.rlist_item_note, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        NoteItem note = mList.get(position);
        if (note == null) {
            Log.e(this.toString(), "cannot find list item at : " + position);
            return;
        }
        holder.setText(note.getText());
        holder.setCreateTime(note.getCreateTime());
        holder.setIsEdited(note.isEdited());
    }

    @Override
    public int getItemCount() {
        if (mList == null) return 0;
        return mList.size();
    }
}
