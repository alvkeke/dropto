package cn.alvkeke.dropto.ui.adapter;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;

public class ImageListAdapter extends RecyclerView.Adapter<ImageListAdapter.ViewHolder> {


    public interface OnItemClickListener {
        void OnClick(int index);
        boolean OnLongClick(int index);
    }

    private final ArrayList<String> images = new ArrayList<>();
    private OnItemClickListener listener;

    private final LayoutInflater inflater;
    public ImageListAdapter(Context context) {
        inflater = LayoutInflater.from(context);
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int i) {
        View v = inflater.inflate(android.R.layout.simple_list_item_1, parent, false);
        return new ViewHolder(v);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder viewHolder, int i) {
        viewHolder.setText(images.get(i));
        viewHolder.setOnClickListener(i);
    }

    @Override
    public int getItemCount() {
        return images.size();
    }

    public void add(String file) {
        images.add(file);
        int index = images.indexOf(file);
        if (index >= 0)
            this.notifyItemInserted(index);
    }

    public void remove(int index) {
        images.remove(index);
        notifyItemRemoved(index);
        notifyItemRangeChanged(index, images.size()-index);
    }

    public String get(int index) {
        if (index >= images.size())
            return null;
        return images.get(index);
    }

    public void emptyList() {
        this.notifyItemRangeRemoved(0, images.size());
        images.clear();
    }

    public class ViewHolder extends RecyclerView.ViewHolder {

        private final TextView textView;
        public ViewHolder(@NonNull View itemView) {
            super(itemView);
            textView = itemView.findViewById(android.R.id.text1);
        }

        public void setText(String text) {
            textView.setText(text);
        }

        public void setOnClickListener(int pos) {
            if (listener != null) {
                textView.setOnClickListener(view -> listener.OnClick(pos));
                textView.setOnLongClickListener(view -> listener.OnLongClick(pos));
            }
        }
    }

    public void setItemClickListener(OnItemClickListener listener) {
        this.listener = listener;
    }
}
