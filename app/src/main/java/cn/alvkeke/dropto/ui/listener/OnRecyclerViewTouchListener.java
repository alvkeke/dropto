package cn.alvkeke.dropto.ui.listener;

import android.annotation.SuppressLint;
import android.os.Handler;
import android.util.Log;
import android.view.MotionEvent;
import android.view.View;

import androidx.recyclerview.widget.RecyclerView;


public class OnRecyclerViewTouchListener implements View.OnTouchListener {


    private static final long TIME_THRESHOLD_CLICK = 200;
    private static final long TIME_THRESHOLD_LONG_CLICK = 500;
    private static final int THRESHOLD_SLIDE = 30;
    private static final int THRESHOLD_NO_MOVED = 10;
    long timeDown;
    float sX, sY, dX, dY;
    boolean isSliding = false;
    boolean isSlidable = false;
    boolean longClickHold = false;
    boolean isShortClick = false;
    @SuppressLint("ClickableViewAccessibility")
    public boolean onTouch(View view, MotionEvent motionEvent) {
        boolean ret;
        switch (motionEvent.getAction()) {
            case MotionEvent.ACTION_DOWN:
                sX = motionEvent.getRawX();
                sY = motionEvent.getRawY();
                timeDown = System.currentTimeMillis();
                longPressView = view;
                longPressEvent = motionEvent;
                handler.postDelayed(longPressRunnable, TIME_THRESHOLD_LONG_CLICK);
                isShortClick = true;
                isSlidable = true;
                break;
            case MotionEvent.ACTION_MOVE:
                handler.removeCallbacks(longPressRunnable);
                isShortClick = false;
                if (longClickHold) return true; // block all event
                dX = motionEvent.getRawX() - sX;
                dY = motionEvent.getRawY() - sY;
                if (isSliding) {
                    ret = onSlideOnGoing(view, motionEvent, dX, dY);
                    if (ret) return true;
                }
                if (Math.abs(dY) > THRESHOLD_NO_MOVED)
                    isSlidable = false;
                if (isSlidable && dX > THRESHOLD_SLIDE)
                    isSliding = true;
                break;
            case MotionEvent.ACTION_UP:
                handler.removeCallbacks(longPressRunnable);
                if (longClickHold) {
                    longClickHold = false;
                    return true;
                }
                if (System.currentTimeMillis() - timeDown < TIME_THRESHOLD_CLICK &&
                        isShortClick) {
                    isShortClick = false;
                    ret = handleListItemClick(view, motionEvent, false);
                    if (ret) return true;
                    ret = onClick(view, motionEvent);
                    if (ret) return true;
                }
                if (isSliding) {
                    dX = motionEvent.getRawX() - sX;
                    dY = motionEvent.getRawY() - sY;
                    isSliding = false;
                    ret = onSlideEnd(view, motionEvent, dX, dY);
                    if (ret) return true;
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

    public boolean onClick(View v, MotionEvent e) {
        return false;
    }

    private final Handler handler = new Handler();
    View longPressView;
    MotionEvent longPressEvent;
    private final Runnable longPressRunnable = new Runnable() {
        @Override
        public void run() {
            boolean ret = handleListItemClick(longPressView, longPressEvent, true);
            if (ret) {
                longClickHold = true;
                return;
            }
            ret = onLongClick(longPressView, longPressEvent);
            if (ret) {
                longClickHold = true;
                return;
            }
            Log.e(this.toString(), "long pressed");
        }

    };

    public boolean onLongClick(View v, MotionEvent e) {
        return false;
    }

    private boolean handleListItemClick(View v, MotionEvent e, boolean isLong) {
        RecyclerView recyclerView = (RecyclerView) v;
        RecyclerView.LayoutManager layoutManager = recyclerView.getLayoutManager();
        View itemView = recyclerView.findChildViewUnder(e.getX(), e.getY());
        if (itemView == null) return false;
        if (layoutManager == null) return false;
        int index = layoutManager.getPosition(itemView);
        if (index == -1) return false;
        if (isLong) {
            return onItemLongClick(itemView, index);
        } else {
            return onItemClick(itemView, index);
        }
    }

    public boolean onItemClick(View v, int index) {
        return false;
    }

    public boolean onItemLongClick(View v, int index) {
        return false;
    }

}
