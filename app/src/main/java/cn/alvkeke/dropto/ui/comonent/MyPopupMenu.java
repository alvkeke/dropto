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

import cn.alvkeke.dropto.R;

public class MyPopupMenu {

    private static int getTextViewMargin(Context context) {
        DisplayMetrics displayMetrics = context.getResources().getDisplayMetrics();
        return Math.round((float) 15 * (displayMetrics.xdpi / DisplayMetrics.DENSITY_DEFAULT));
    }

    private static boolean isPointInView(View view, float x, float y) {
        int viewWidth = view.getWidth();
        int viewHeight = view.getHeight();
        return (x >= 0 && x < viewWidth && y >= 0 && y < viewHeight);
    }

    private static final int RoundCornerRadius = 18;
    @SuppressLint("ClickableViewAccessibility")
    private static TextView setupItemForPopupWindow(PopupWindow popupWindow, Context context, MenuItem item, androidx.appcompat.widget.PopupMenu.OnMenuItemClickListener listener) {
        TextView textView = new TextView(context);
        textView.setText(item.getTitle());
        int margin = getTextViewMargin(context);
        textView.setPadding(margin, margin, margin, margin);
        GradientDrawable drawable = new GradientDrawable();
        drawable.setShape(GradientDrawable.RECTANGLE);
        drawable.setCornerRadius(RoundCornerRadius);
        drawable.setColor(context.getColor(R.color.popup_menu_item_selected));
        textView.setBackground(drawable);
        textView.getBackground().setAlpha(0);
        ValueAnimator animatorOut = new ValueAnimator();
        animatorOut.addUpdateListener(valueAnimator -> {
            int alpha = (int) valueAnimator.getAnimatedValue();
            textView.getBackground().setAlpha(alpha);
        });

        textView.setOnTouchListener(new View.OnTouchListener() {
            private boolean canClick = false;
            @Override
            public boolean onTouch(View view, MotionEvent motionEvent) {
                switch (motionEvent.getAction()) {
                    case MotionEvent.ACTION_DOWN:
                    {
                        canClick = true;
                        ValueAnimator animatorDown = ValueAnimator.ofInt(0, 255);
                        animatorDown.addUpdateListener(valueAnimator -> {
                            int alpha = (int) valueAnimator.getAnimatedValue();
                            textView.getBackground().setAlpha(alpha);
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
                                int startAlpha = textView.getBackground().getAlpha();
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
                            popupWindow.dismiss();
                            return true;
                        }
                    }
                }
                return false;
            }
        });

        return textView;
    }

    public static void show(Context context, Menu menu, androidx.appcompat.widget.PopupMenu.OnMenuItemClickListener listener, View anchorView) {
        PopupWindow popupWindow = new PopupWindow(context);

        LinearLayout linearLayout = new LinearLayout(context);
        linearLayout.setOrientation(LinearLayout.VERTICAL);
        linearLayout.setMinimumWidth(context.getResources().getDisplayMetrics().widthPixels / 2);

        for (int i = 0; i < menu.size(); i++) {
            MenuItem item = menu.getItem(i);
            TextView textView = setupItemForPopupWindow(popupWindow, context, item, listener);
            linearLayout.addView(textView);
        }
        GradientDrawable drawable = new GradientDrawable();
        drawable.setShape(GradientDrawable.RECTANGLE);
        drawable.setCornerRadius(RoundCornerRadius);
        drawable.setColor(context.getColor(R.color.popup_menu_background));

        popupWindow.setBackgroundDrawable(drawable);
        popupWindow.setContentView(linearLayout);
        popupWindow.setFocusable(true);
        popupWindow.setOutsideTouchable(false);
        popupWindow.showAsDropDown(anchorView);
    }

}
