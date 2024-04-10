package cn.alvkeke.dropto.ui.comonent;


import android.animation.ValueAnimator;
import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.drawable.GradientDrawable;
import android.util.DisplayMetrics;
import android.view.Menu;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.PopupWindow;
import android.widget.TextView;

import androidx.appcompat.widget.PopupMenu;

import cn.alvkeke.dropto.R;

public class MyPopupMenu extends PopupWindow{

    private final Context context;
    private PopupMenu.OnMenuItemClickListener listener;
    private Menu menu;

    public MyPopupMenu(Context context) {
        super(context);
        this.context = context;
    }

    public MyPopupMenu setListener(PopupMenu.OnMenuItemClickListener listener) {
        this.listener = listener;
        return this;
    }

    public MyPopupMenu setMenu(Menu menu) {
        this.menu = menu;
        return this;
    }

    private int getTextViewMargin() {
        DisplayMetrics displayMetrics = context.getResources().getDisplayMetrics();
        return Math.round((float) 15 * (displayMetrics.xdpi / DisplayMetrics.DENSITY_DEFAULT));
    }

    private boolean isPointInView(View view, float x, float y) {
        int viewWidth = view.getWidth();
        int viewHeight = view.getHeight();
        return (x >= 0 && x < viewWidth && y >= 0 && y < viewHeight);
    }

    private static final int RoundCornerRadius = 18;
    @SuppressLint("ClickableViewAccessibility")
    private TextView setupItemForPopupWindow(MenuItem item) {
        TextView textView = new TextView(context);
        textView.setText(item.getTitle());
        int margin = getTextViewMargin();
        textView.setPadding(margin, margin, margin, margin);
        GradientDrawable drawable = new GradientDrawable();
        drawable.setShape(GradientDrawable.RECTANGLE);
        drawable.setCornerRadius(RoundCornerRadius);
        drawable.setColor(context.getColor(R.color.popup_menu_item_selected));
        textView.setBackground(drawable);
        textView.getBackground().setAlpha(0);
        textView.setOnTouchListener(new OnItemTouchListener(item));
        return textView;
    }

    class OnItemTouchListener implements View.OnTouchListener {

        private ValueAnimator animatorOut = null;
        private boolean canClick = false;
        private final MenuItem item;

        OnItemTouchListener(MenuItem item) {
            this.item = item;
        }

        @SuppressLint("ClickableViewAccessibility")
        @Override
        public boolean onTouch(View view, MotionEvent motionEvent) {
            if (animatorOut == null) {
                animatorOut = new ValueAnimator();
                animatorOut.addUpdateListener(valueAnimator -> {
                    int alpha = (int) valueAnimator.getAnimatedValue();
                    view.getBackground().setAlpha(alpha);
                });
            }
            switch (motionEvent.getAction()) {
                case MotionEvent.ACTION_DOWN:
                {
                    canClick = true;
                    ValueAnimator animatorDown = ValueAnimator.ofInt(0, 255);
                    animatorDown.addUpdateListener(valueAnimator -> {
                        int alpha = (int) valueAnimator.getAnimatedValue();
                        view.getBackground().setAlpha(alpha);
                    });
                    animatorDown.setDuration(200);
                    animatorDown.start();
                    return true;
                }
                case MotionEvent.ACTION_MOVE:
                {
                    if (!isPointInView(view, motionEvent.getX(), motionEvent.getY())) {
                        canClick = false;
                        if (!animatorOut.isStarted()) {
                            int startAlpha = view.getBackground().getAlpha();
                            if (startAlpha != 0) {
                                animatorOut.setIntValues(startAlpha, 0);
                                animatorOut .setDuration(200);
                                animatorOut.start();
                            }
                        }
                        return true;
                    }
                }
                break;
                case MotionEvent.ACTION_UP:
                {
                    if (canClick) {
                        if (listener != null) {
                            listener.onMenuItemClick(item);
                        }
                        dismiss();
                        return true;
                    }
                }
            }
            return false;
        }
    }

    public void show(View anchorView) {

        LinearLayout linearLayout = new LinearLayout(context);
        linearLayout.setOrientation(LinearLayout.VERTICAL);
        linearLayout.setMinimumWidth(context.getResources().getDisplayMetrics().widthPixels / 2);

        for (int i = 0; i < menu.size(); i++) {
            MenuItem item = menu.getItem(i);
            TextView textView = setupItemForPopupWindow(item);
            linearLayout.addView(textView);
        }
        GradientDrawable drawable = new GradientDrawable();
        drawable.setShape(GradientDrawable.RECTANGLE);
        drawable.setCornerRadius(RoundCornerRadius);
        drawable.setColor(context.getColor(R.color.popup_menu_background));

        setBackgroundDrawable(drawable);
        setContentView(linearLayout);
        setFocusable(true);
        setOutsideTouchable(false);
        showAsDropDown(anchorView);
    }

}
