package cn.alvkeke.dropto.ui.adapter;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import cn.alvkeke.dropto.R;
import cn.alvkeke.dropto.data.Category;

public class CategoryTypeSpinnerAdapter extends ArrayAdapter<Category.Type> {

    private final Category.Type[] types;
    public CategoryTypeSpinnerAdapter(@NonNull Context context, int resource, @NonNull Category.Type[] objects) {
        super(context, resource, objects);
        this.types = objects;
    }

    @Override
    public int getCount() {
        return types.length;
    }

    @NonNull
    @Override
    public View getView(int position, @Nullable View convertView, @NonNull ViewGroup parent) {
        return getCustomView(position, convertView, parent);
    }

    @Override
    public View getDropDownView(int position, @Nullable View convertView, @NonNull ViewGroup parent) {
        return getCustomView(position, convertView, parent);
    }

    private View getCustomView(int pos, View view, ViewGroup parent) {
        ViewHolder holder;
        if (view == null) {
            view = LayoutInflater.from(getContext()).
                    inflate(R.layout.spinner_item_category_type, parent, false);
            holder = new ViewHolder();
            holder.text = view.findViewById(R.id.spinner_category_type_text);
            holder.icon = view.findViewById(R.id.spinner_category_type_icon);
            view.setTag(holder);
        } else {
            holder = (ViewHolder) view.getTag();
        }

        Category.Type type = types[pos];
        holder.text.setText(Category.typeToName(type));
        holder.icon.setImageResource(Category.typeToIconResource(type));
        return view;
    }

    private static class ViewHolder {
        ImageView icon;
        TextView text;
    }


}
