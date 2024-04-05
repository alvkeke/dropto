package cn.alvkeke.dropto.ui.listener;

import android.annotation.SuppressLint;
import android.view.MotionEvent;
import android.view.View;

import androidx.recyclerview.widget.RecyclerView;


public class OnRecyclerViewTouchListener implements View.OnTouchListener {


    private static final long TIME_THRESHOLD_CLICK = 200;
    private static final long TIME_THRESHOLD_LONG_CLICK = 400;
    private static final int THRESHOLD_SLIDE = 30;
    private static final int THRESHOLD_NO_MOVED = 10;
    long timeDown;
    float sX, sY, dX, dY;
    boolean isSliding = false;
    boolean longClickHold = false;
    @SuppressLint("ClickableViewAccessibility")
    public boolean onTouch(View view, MotionEvent motionEvent) {
        boolean ret;
        switch (motionEvent.getAction()) {
            case MotionEvent.ACTION_DOWN:
                sX = motionEvent.getRawX();
                sY = motionEvent.getRawY();
                timeDown = System.currentTimeMillis();
                break;
            case MotionEvent.ACTION_MOVE:
                if (longClickHold) return true; // block all event
                dX = motionEvent.getRawX() - sX;
                dY = Math.abs(motionEvent.getRawY() - sY);
                if (isSliding) {
                    ret = onSlideOnGoing(view, motionEvent, dX, dY);
                    if (ret) return true;
                }
                if (dY < THRESHOLD_NO_MOVED && dX > THRESHOLD_SLIDE)
                    isSliding = true;
                if (dY < THRESHOLD_NO_MOVED && dX < THRESHOLD_NO_MOVED) {
                    long deltaTime = System.currentTimeMillis() - timeDown;
                    if (deltaTime > TIME_THRESHOLD_LONG_CLICK) {
                        ret = handleListItemClick(view, motionEvent, true);
                        if (ret) {
                            longClickHold = true;
                            return true;
                        }
                        ret = onLongClick(view, motionEvent);
                        if (ret) {
                            longClickHold = true;
                            return true;
                        }
                    }
                }
                break;
            case MotionEvent.ACTION_UP:
                dX = motionEvent.getRawX() - sX;
                dY = Math.abs(motionEvent.getRawY() - sY);
                if (longClickHold)
                    longClickHold = false;
                if (System.currentTimeMillis() - timeDown < TIME_THRESHOLD_CLICK &&
                        dY < THRESHOLD_NO_MOVED && dX < THRESHOLD_NO_MOVED) {
                    ret = handleListItemClick(view, motionEvent, false);
                    if (ret) return true;
                    ret = onClick(view, motionEvent);
                    if (ret) return true;
                }
                if (isSliding) {
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
