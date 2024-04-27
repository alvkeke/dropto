package cn.alvkeke.dropto.ui.adapter;

import android.graphics.Color;
import android.graphics.Rect;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.constraintlayout.widget.ConstraintLayout;
import androidx.recyclerview.widget.RecyclerView;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.Locale;

import cn.alvkeke.dropto.R;
import cn.alvkeke.dropto.data.ImageFile;
import cn.alvkeke.dropto.data.NoteItem;
import cn.alvkeke.dropto.storage.ImageLoader;

public class NoteListAdapter extends RecyclerView.Adapter<NoteListAdapter.ViewHolder> {

    private final ArrayList<NoteItem> noteList;

    public NoteListAdapter(ArrayList<NoteItem> list) {
        // note a real list, prevent the multi-thread race condition.
        noteList = new ArrayList<>(list);
    }

    public static class ViewHolder extends RecyclerView.ViewHolder {

        private final TextView tvText;
        private final TextView tvCreateTime;
        private final ImageView ivEdited;
        private final ConstraintLayout containerImage;
        private final ImageView[] ivImages = new ImageView[4];
        private final View guideView;

        public ViewHolder(@NonNull View itemView) {
            super(itemView);
            tvText = itemView.findViewById(R.id.rlist_item_note_text);
            tvCreateTime = itemView.findViewById(R.id.rlist_item_note_create_time);
            ivEdited = itemView.findViewById(R.id.rlist_item_note_is_edited);
            containerImage = itemView.findViewById(R.id.rlist_item_note_img_container);
            guideView = itemView.findViewById(R.id.rlist_item_note_img_guide_view);
            ivImages[0] = itemView.findViewById(R.id.rlist_item_note_img_view0);
            ivImages[1] = itemView.findViewById(R.id.rlist_item_note_img_view1);
            ivImages[2] = itemView.findViewById(R.id.rlist_item_note_img_view2);
            ivImages[3] = itemView.findViewById(R.id.rlist_item_note_img_view3);
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
                tvText.setVisibility(View.VISIBLE);
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

        private void assignImage_1() {
            ConstraintLayout.LayoutParams params =
                    (ConstraintLayout.LayoutParams) ivImages[0].getLayoutParams();
            params.startToStart = ConstraintLayout.LayoutParams.PARENT_ID;
            params.topToTop = ConstraintLayout.LayoutParams.PARENT_ID;
            params.bottomToBottom = ConstraintLayout.LayoutParams.PARENT_ID;
            params.endToEnd = ConstraintLayout.LayoutParams.PARENT_ID;
            ivImages[0].setLayoutParams(params);
            ivImages[0].setVisibility(View.VISIBLE);
            for (int i=1; i<ivImages.length; i++) {
                ivImages[i].setVisibility(View.GONE);
            }
        }
        private void assignImage_2() {
            for (int i=0; i<2; i++) {
                ConstraintLayout.LayoutParams params =
                        (ConstraintLayout.LayoutParams) ivImages[i].getLayoutParams();
                params.topToTop = ConstraintLayout.LayoutParams.PARENT_ID;
                params.bottomToBottom = ConstraintLayout.LayoutParams.PARENT_ID;
                if (i != 0) {
                    params.startToEnd = guideView.getId();
                    params.endToEnd = ConstraintLayout.LayoutParams.PARENT_ID;
                } else {
                    params.startToStart = ConstraintLayout.LayoutParams.PARENT_ID;
                    params.endToStart = guideView.getId();
                }
                ivImages[i].setLayoutParams(params);
                ivImages[i].setVisibility(View.VISIBLE);
            }
            for (int i=2; i<ivImages.length; i++) {
                ivImages[i].setVisibility(View.GONE);
            }
        }
        private void assignImage_3() {
            Log.e(this.toString(), "enter images 3");
            for (int i=0; i<3; i++) {
                ConstraintLayout.LayoutParams params =
                        (ConstraintLayout.LayoutParams) ivImages[i].getLayoutParams();
                if (i == 0) {
                    params.startToStart = ConstraintLayout.LayoutParams.PARENT_ID;
                    params.topToTop = ConstraintLayout.LayoutParams.PARENT_ID;
                    params.bottomToBottom = ConstraintLayout.LayoutParams.PARENT_ID;
                    params.endToStart = guideView.getId();
                } else {
                    params.startToEnd = guideView.getId();
                    params.endToEnd = ConstraintLayout.LayoutParams.PARENT_ID;
                    if (i == 1) {
                        params.topToTop = ConstraintLayout.LayoutParams.PARENT_ID;
                        params.bottomToTop = guideView.getId();
                    } else {
                        params.topToBottom = guideView.getId();
                        params.bottomToBottom = ConstraintLayout.LayoutParams.PARENT_ID;
                    }
                }
                ivImages[i].setLayoutParams(params);
                ivImages[i].setVisibility(View.VISIBLE);
            }
            ivImages[3].setVisibility(View.GONE);
        }
        private void assignImage_4() {
            for (int i=0; i<4; i++) {
                ConstraintLayout.LayoutParams params =
                        (ConstraintLayout.LayoutParams) ivImages[i].getLayoutParams();
                if (i < 2) {
                    params.topToTop = ConstraintLayout.LayoutParams.PARENT_ID;
                    params.bottomToTop = guideView.getId();
                } else {
                    params.topToBottom = guideView.getId();
                    params.bottomToBottom = ConstraintLayout.LayoutParams.PARENT_ID;
                }
                if (i%2 == 0) {
                    params.startToStart = ConstraintLayout.LayoutParams.PARENT_ID;
                    params.endToStart = guideView.getId();
                } else {
                    params.endToEnd = ConstraintLayout.LayoutParams.PARENT_ID;
                    params.startToEnd = guideView.getId();
                }
                ivImages[i].setLayoutParams(params);
                ivImages[i].setVisibility(View.VISIBLE);
            }
        }

        private void assignImageLayout(int count) {
            switch (count) {
                case 1:
                    assignImage_1();
                    break;
                case 2:
                    assignImage_2();
                    break;
                case 3:
                    assignImage_3();
                    break;
                default:
                    assignImage_4();
            }
        }
        private void loadNoteImageAt(NoteItem note, int index) {
            ImageFile img = note.getImageAt(index);
            ImageLoader.getInstance().loadImageAsync(img.getMd5file(), bitmap -> {
                if (bitmap == null) {
                    String errMsg = "Failed to get image file, skip this item";
                    Log.e(this.toString(), errMsg);
                    ivImages[index].setImageResource(R.drawable.img_load_error);
                    return;
                }
                ivImages[index].setImageBitmap(bitmap);
                ivImages[index].measure(View.MeasureSpec.UNSPECIFIED, View.MeasureSpec.UNSPECIFIED);
            });
        }
        private void loadNoteImages(NoteItem note) {
            int count = note.getImageCount();
            assignImageLayout(count);
            switch (count) {
                case 4:
                    loadNoteImageAt(note, 3);
                case 3:
                    loadNoteImageAt(note, 2);
                case 2:
                    loadNoteImageAt(note, 1);
                case 1:
                    loadNoteImageAt(note, 0);
                    break;
                default:
                    loadNoteImageAt(note, 0);
                    loadNoteImageAt(note, 1);
                    loadNoteImageAt(note, 2);
                    ivImages[3].setImageResource(R.drawable.icon_common_more);
                    break;
            }
        }
        public void setImageView(NoteItem note) {
            if (note.isNoImage()) {
                containerImage.setVisibility(View.GONE);
                return;
            }
            containerImage.setVisibility(View.VISIBLE);
            loadNoteImages(note);
        }

    }

    private static final View[] image = new View[4];
    private static final Rect rect = new Rect();
    public static int checkImageClicked(View v, int x, int y) {
        image[0] = v.findViewById(R.id.rlist_item_note_img_view0);
        image[1] = v.findViewById(R.id.rlist_item_note_img_view1);
        image[2] = v.findViewById(R.id.rlist_item_note_img_view2);
        image[3] = v.findViewById(R.id.rlist_item_note_img_view3);
        for (int i=0; i<image.length; i++) {
            if (image[i] == null)
                continue;
            if (image[i].getVisibility() != View.VISIBLE)
                continue;

            image[i].getGlobalVisibleRect(rect);
            if (rect.contains(x, y)) {
                return i;
            }
        }
        return -1;
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
        holder.setImageView(note);
        if (selectedItems.contains(note)) {
            // TODO: use another color for selected item
            holder.itemView.setBackgroundColor(Color.LTGRAY);
        } else {
            holder.itemView.setBackgroundColor(Color.TRANSPARENT);
        }
    }

    @Override
    public int getItemCount() {
        return noteList.size();
    }

    public int add(NoteItem e) {
        if (noteList.contains(e)) return -1;
        boolean result = noteList.add(e);
        if (!result) {
            return -1;
        }
        int index = noteList.indexOf(e);
        notifyItemInserted(index);
        notifyItemRangeChanged(index, noteList.size() - index);
        return index;
    }

    public boolean add(int index, NoteItem e) {
        int idx = noteList.indexOf(e);
        if (idx >= 0) {
            if (idx != index) {
                Log.e(this.toString(), "note exist with mismatch index: "+index+":"+idx);
            }
            return false;
        }
        noteList.add(index, e);
        notifyItemInserted(index);
        notifyItemRangeChanged(index, noteList.size()-index);
        return true;
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
