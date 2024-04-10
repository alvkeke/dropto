package cn.alvkeke.dropto.ui.listener;

import android.annotation.SuppressLint;
import android.os.Handler;
import android.view.MotionEvent;
import android.view.View;

import androidx.recyclerview.widget.RecyclerView;


public class OnRecyclerViewTouchListener implements View.OnTouchListener {


    private static final long TIME_THRESHOLD_CLICK = 200;
    private static final long TIME_THRESHOLD_LONG_CLICK = 500;
    private static final int THRESHOLD_SLIDE = 45;
    private static final int THRESHOLD_NO_MOVED = 20;
    long timeDown;
    float downRawX, downRawY, deltaRawX, deltaRawY;
    boolean isSliding = false;
    boolean isSlidable = false;
    boolean isLongClickHold = false;
    boolean isShortClick = false;
    @SuppressLint("ClickableViewAccessibility")
    public boolean onTouch(View view, MotionEvent motionEvent) {
        RecyclerView recyclerView = (RecyclerView) view;
        View itemView;
        switch (motionEvent.getAction()) {
            case MotionEvent.ACTION_DOWN:
                downRawX = motionEvent.getRawX();
                downRawY = motionEvent.getRawY();
                timeDown = System.currentTimeMillis();
                longPressItemView = recyclerView.
                        findChildViewUnder(motionEvent.getX(), motionEvent.getY());
                longPressView = view;
                handler.postDelayed(longPressRunnable, TIME_THRESHOLD_LONG_CLICK);
                isShortClick = true;
                isSlidable = true;
                break;
            case MotionEvent.ACTION_MOVE:
                deltaRawX = motionEvent.getRawX() - downRawX;
                deltaRawY = motionEvent.getRawY() - downRawY;
                if (Math.abs(deltaRawX) > THRESHOLD_NO_MOVED || Math.abs(deltaRawY) > THRESHOLD_NO_MOVED) {
                    handler.removeCallbacks(longPressRunnable);
                    isShortClick = false;
                }
                if (isLongClickHold) return true; // block all event
                if (isSliding) {
                    if (onSlideOnGoing(view, motionEvent, deltaRawX, deltaRawY)) return true;
                }
                if (Math.abs(deltaRawY) > THRESHOLD_NO_MOVED)
                    isSlidable = false;
                if (isSlidable && deltaRawX > THRESHOLD_SLIDE)
                    isSliding = true;
                break;
            case MotionEvent.ACTION_UP:
                handler.removeCallbacks(longPressRunnable);
                if (isLongClickHold) {
                    isLongClickHold = false;
                    return true;
                }
                if (isShortClick && System.currentTimeMillis() - timeDown < TIME_THRESHOLD_CLICK) {
                    isShortClick = false;
                    itemView = recyclerView.findChildViewUnder(motionEvent.getX(), motionEvent.getY());
                    if (itemView != null) {
                        if (handleItemClick(recyclerView, itemView, motionEvent)) return true;
                        if (handleItemClick(recyclerView, itemView, null)) return true;
//                        if (handleListItemClick(recyclerView, itemView, false)) return true;
                    }
                    if (onClick(view, motionEvent)) return true;
                }
                if (isSliding) {
                    deltaRawX = motionEvent.getRawX() - downRawX;
                    deltaRawY = motionEvent.getRawY() - downRawY;
                    isSliding = false;
                    if (onSlideEnd(view, motionEvent, deltaRawX, deltaRawY)) return true;
                }
                break;
        }
        return false;
    }

    public boolean onSlideEnd(View v, MotionEvent e, float deltaX, float deltaY) {
        return false;
    }

    public boolean onSlideOnGoing(View v, MotionEvent e, float deltaX, float deltaY) {
        return false;
    }

    public boolean onClick(View ignored, MotionEvent ignored1) {
        return false;
    }

    private final Handler handler = new Handler();
    View longPressView;
    View longPressItemView;
    private final Runnable longPressRunnable = new Runnable() {
        @Override
        public void run() {
            if (longPressItemView != null) {
                if (handleItemLongClick(longPressView, longPressItemView)) {
                    isLongClickHold = true;
                    return;
                }
            }
            if (onLongClick(longPressView)) {
                isLongClickHold = true;
            }
        }
    };

    public boolean onLongClick(View ignored) {
        return false;
    }

    private boolean handleItemLongClick(View parent, View itemView) {
        RecyclerView recyclerView = (RecyclerView) parent;
        int index = recyclerView.getChildLayoutPosition(itemView);
        assert index != -1;
        return onItemLongClick(itemView, index);
    }

    private boolean handleItemClick(View parent, View itemView, MotionEvent e) {
        RecyclerView recyclerView = (RecyclerView) parent;
        int index = recyclerView.getChildLayoutPosition(itemView);
        assert index != -1;
        if (e == null) {
            return onItemClick(itemView, index);
        } else {
            return onItemClickAt(itemView, index, e);
        }
    }

    public boolean onItemClick(View v, int index) {
        return false;
    }

    public boolean onItemClickAt(View v, int index, MotionEvent event) {
        return false;
    }

    public boolean onItemLongClick(View v, int index) {
        return false;
    }

}
