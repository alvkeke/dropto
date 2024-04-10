package cn.alvkeke.dropto.ui.adapter;

import android.graphics.Bitmap;
import android.graphics.Color;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.constraintlayout.widget.ConstraintLayout;
import androidx.recyclerview.widget.RecyclerView;

import java.io.File;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.Locale;

import cn.alvkeke.dropto.R;
import cn.alvkeke.dropto.data.NoteItem;
import cn.alvkeke.dropto.storage.ImageLoader;

public class NoteListAdapter extends RecyclerView.Adapter<NoteListAdapter.ViewHolder> {

    ArrayList<NoteItem> noteList;

    public NoteListAdapter(ArrayList<NoteItem> list) {
        // note a real list, prevent the multi-thread race condition.
        noteList = new ArrayList<>(list);
    }

    public static class ViewHolder extends RecyclerView.ViewHolder {

        private final TextView tvText;
        private final TextView tvCreateTime;
        private final ImageView ivEdited;
        private final TextView tvImageFile;
        private final ImageView ivImage;

        public ViewHolder(@NonNull View itemView) {
            super(itemView);
            tvText = itemView.findViewById(R.id.rlist_item_note_text);
            tvCreateTime = itemView.findViewById(R.id.rlist_item_note_create_time);
            ivEdited = itemView.findViewById(R.id.rlist_item_note_is_edited);
            tvImageFile = itemView.findViewById(R.id.rlist_item_note_img_file);
            ivImage = itemView.findViewById(R.id.rlist_item_note_img_view);
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
            if (text == null || text.isEmpty()) {
                tvText.setVisibility(View.GONE);
            } else {
                tvText.setText(text);
            }
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
            ivEdited.getLayoutParams().height = he;
            ivEdited.getLayoutParams().width = he;
            ivEdited.requestLayout();
            ivEdited.setVisibility(foo? View.VISIBLE : View.INVISIBLE);
        }

        public void setImageFile(File imgfile) {
            if (imgfile == null) {
                ivImage.setVisibility(View.GONE);
                return;
            }
            Bitmap bitmap = ImageLoader.getInstance().loadImage(imgfile);
            if (bitmap == null) {
                Log.e(this.toString(), "Failed to get image file, skip this item");
                ivImage.setVisibility(View.GONE);
                return;
            }
            ivImage.setImageBitmap(bitmap);
            ivImage.measure(View.MeasureSpec.UNSPECIFIED, View.MeasureSpec.UNSPECIFIED);
            ConstraintLayout.LayoutParams params =
                    (ConstraintLayout.LayoutParams) ivImage.getLayoutParams();
            if (bitmap.getHeight() < ivImage.getMinimumHeight()) {
                params.height = ivImage.getMinimumHeight();
            } else {
                params.height = ConstraintLayout.LayoutParams.WRAP_CONTENT;
            }
            ivImage.setLayoutParams(params);
            ivImage.setVisibility(View.VISIBLE);
        }

        public void setImageName(String name) {
            if (name == null || name.isEmpty()) {
                tvImageFile.setVisibility(View.GONE);
                return;
            }
            tvImageFile.setMaxWidth(ivImage.getMeasuredWidth());
            tvImageFile.setText(name);
            tvImageFile.setVisibility(View.VISIBLE);
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
        NoteItem note = noteList.get(position);
        if (note == null) {
            Log.e(this.toString(), "cannot find list item at : " + position);
            return;
        }
        holder.setText(note.getText());
        holder.setCreateTime(note.getCreateTime());
        holder.setIsEdited(note.isEdited());
        holder.setImageFile(note.getImageFile());
        holder.setImageName(note.getImageName());
        if (selectedItems.contains(note)) {
            // TODO: use another color for selected item
            holder.itemView.setBackgroundColor(Color.LTGRAY);
        } else {
            holder.itemView.setBackgroundColor(Color.TRANSPARENT);
        }
    }

    @Override
    public int getItemCount() {
        if (noteList == null) return 0;
        return noteList.size();
    }

    public boolean add(NoteItem e) {
        boolean result = noteList.add(e);
        int index = noteList.indexOf(e);
        notifyItemInserted(index);
        notifyItemRangeChanged(index, noteList.size()-index);
        return result;
    }

    public void add(int index, NoteItem e) {
        noteList.add(index, e);
        notifyItemInserted(index);
        notifyItemRangeChanged(index, noteList.size()-index);
    }

    public void remove(NoteItem e) {
        int index = noteList.indexOf(e);
        noteList.remove(e);
        notifyItemRemoved(index);
        notifyItemRangeChanged(index, noteList.size()-index);
    }

    public void update(int index) {
        notifyItemChanged(index);
    }

    public void update(NoteItem e) {
        int index = noteList.indexOf(e);
        if (index != -1)
            notifyItemChanged(index);
    }

    private ArrayList<NoteItem> selectedItems = new ArrayList<>();

    public int toggleSelectItems(int index) {
        NoteItem item = noteList.get(index);
        if (selectedItems.contains(item)) {
            selectedItems.remove(item);
        } else {
            selectedItems.add(item);
        }
        notifyItemChanged(index);
        return selectedItems.size();
    }
    public void clearSelectItems() {
        if (selectedItems.isEmpty()) return;
        selectedItems = new ArrayList<>();
        notifyItemRangeChanged(0, noteList.size());
    }
    public ArrayList<NoteItem> getSelectedItems() {
        return selectedItems;
    }

}
