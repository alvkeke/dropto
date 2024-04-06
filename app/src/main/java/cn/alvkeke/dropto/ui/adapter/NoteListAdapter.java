package cn.alvkeke.dropto.ui.adapter;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import java.io.File;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.Locale;

import cn.alvkeke.dropto.R;
import cn.alvkeke.dropto.data.NoteItem;

public class NoteListAdapter extends RecyclerView.Adapter<NoteListAdapter.ViewHolder> {

    ArrayList<NoteItem> mList;

    public NoteListAdapter(ArrayList<NoteItem> list) {
        mList = list;
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
            ivEdited.getLayoutParams().height = he;
            ivEdited.getLayoutParams().width = he;
            ivEdited.requestLayout();
            ivEdited.setVisibility(foo? View.VISIBLE : View.INVISIBLE);
        }

        private void resizeImage(Bitmap bitmap) {
            final int minHeight = 200;

            LinearLayout.LayoutParams params = (LinearLayout.LayoutParams) ivImage.getLayoutParams();
            if (bitmap.getHeight() < minHeight) {
                params.height = minHeight;
            } else {
                params.height = LinearLayout.LayoutParams.WRAP_CONTENT;
            }
            ivImage.setLayoutParams(params);
        }

        public void setImageFile(File imgfile) {
            if (imgfile == null) {
                ivImage.setVisibility(View.GONE);
                return;
            }
            Log.d(this.toString(), "Set image file: " + imgfile.getPath());
            Bitmap bitmap = BitmapFactory.decodeFile(imgfile.getAbsolutePath());
            if (bitmap == null) {
                Log.e(this.toString(), "Failed to get image file, skip this item");
                return;
            }
            resizeImage(bitmap);
            ivImage.setImageBitmap(bitmap);
            ivImage.setVisibility(View.VISIBLE);
        }

        public void setImageName(String name) {
            if (name == null) {
                tvImageFile.setVisibility(View.GONE);
                return;
            }
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
        NoteItem note = mList.get(position);
        if (note == null) {
            Log.e(this.toString(), "cannot find list item at : " + position);
            return;
        }
        holder.setText(note.getText());
        holder.setCreateTime(note.getCreateTime());
        holder.setIsEdited(note.isEdited());
        holder.setImageFile(note.getImageFile());
        holder.setImageName(note.getImageName());
        if (isSelect(position)) {
            // TODO: use another color for selected item
            holder.itemView.setBackgroundColor(Color.LTGRAY);
        } else {
            holder.itemView.setBackgroundColor(Color.TRANSPARENT);
        }
    }

    @Override
    public int getItemCount() {
        if (mList == null) return 0;
        return mList.size();
    }

    private ArrayList<Integer> selectedIndex = new ArrayList<>();
    private Integer findSelect(int i) {
        for (Integer I : selectedIndex) {
            if (I == i) return I;
        }
        return null;
    }
    private boolean isSelect(int index) {
        for (Integer I : selectedIndex) {
            if (I == index) return true;
        }
        return false;
    }
    public int toggleItemSelect(int index) {
        Integer I = findSelect(index);
        if (I == null) {
            selectedIndex.add(index);
        } else {
            selectedIndex.remove(I);
        }
        notifyItemChanged(index);
        return selectedIndex.size();
    }
    public void clearItemSelect() {
        ArrayList<Integer> tmp = selectedIndex;
        selectedIndex = new ArrayList<>();
        for (Integer I : tmp) {
            notifyItemChanged(I);
        }
    }

}
