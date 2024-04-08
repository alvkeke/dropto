package cn.alvkeke.dropto.ui.comonent;


import android.content.Context;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.PopupWindow;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

public class PopupMenu {

    public interface MenuItemOnClickListener {
        void onClick(MenuItem menuItem);
    }

    private final Context context;
    private final Menu menu;

    public PopupMenu(Context context, Menu menu) {
        this.context = context;
        this.menu = menu;
    }

    public void show(View anchorView) {
        RecyclerView popupView = new RecyclerView(context);
        PopupMenuListAdapter adapter = new PopupMenuListAdapter();
        popupView.setAdapter(adapter);

        // Set up the popup window and its content view
        PopupWindow popupWindow = new PopupWindow(popupView, WindowManager.LayoutParams.WRAP_CONTENT, WindowManager.LayoutParams.WRAP_CONTENT);
        popupWindow.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));

        // Show the popup window
        popupWindow.showAsDropDown(anchorView);
    }

    private class PopupMenuListAdapter extends RecyclerView.Adapter<PopupMenuListAdapter.ViewHolder> {

        @NonNull
        @Override
        public ViewHolder onCreateViewHolder(@NonNull ViewGroup viewGroup, int i) {
            return null;
        }

        @Override
        public void onBindViewHolder(@NonNull ViewHolder viewHolder, int i) {
            MenuItem item = menu.getItem(i);
        }

        @Override
        public int getItemCount() {
            return menu.size();
        }

        class ViewHolder extends RecyclerView.ViewHolder {

            public ViewHolder(@NonNull View itemView) {
                super(itemView);
            }
        }

    }

}
