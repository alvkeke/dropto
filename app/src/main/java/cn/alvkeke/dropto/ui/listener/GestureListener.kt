package cn.alvkeke.dropto.ui.listener;

import android.annotation.SuppressLint;
import android.os.Handler;
import android.util.Log;
import android.view.MotionEvent;
import android.view.MotionEvent.PointerCoords;
import android.view.View;

public class GestureListener implements View.OnTouchListener{

    private static class Point {
        float x;
        float y;
        public Point() {
        }

        public void set(float x, float y) {
            this.x = x;
            this.y = y;
        }

        @SuppressWarnings("unused")
        public float distance(Point point) {
            float xx, yy;
            xx = point.x - x;
            yy = point.y - y;
            return (float) Math.sqrt(xx*xx + yy*yy);
        }
    }

    private Point pointFromEvent(MotionEvent e) {
        Point point = new Point();
        point.set(e.getRawX(), e.getRawY());
        return point;
    }

    private enum GestureMode {
        NONE,
        SCROLL_V,
        SCROLL_H,
        DRAG,
        ZOOM,
        INVALID,
    }

    private static final int DISTANCE_NO_MOVE = 25;
    private static final int DISTANCE_SCROLL = 45;
    private GestureMode getMode(float deltaX, float deltaY) {
        if (deltaX <= DISTANCE_NO_MOVE && deltaY >= DISTANCE_SCROLL) {
            return GestureMode.SCROLL_V;
        } else if (deltaX >= DISTANCE_SCROLL && deltaY <= DISTANCE_NO_MOVE) {
            return GestureMode.SCROLL_H;
        } else if (deltaX >= DISTANCE_NO_MOVE && deltaY >= DISTANCE_NO_MOVE) {
            return GestureMode.DRAG;
        }
        return GestureMode.NONE;
    }
    private GestureMode getMode(Point p1, Point p2) {
        float dx, dy;
        dx = p1.x - p2.x;
        dy = p1.y - p2.y;
        return getMode(Math.abs(dx), Math.abs(dy));
    }


    private Point pointOld;
    private void handleGesture(View view, GestureMode mode, MotionEvent event) {
        boolean ret = false;
        Point current = pointFromEvent(event);
        if (mode == GestureMode.SCROLL_H)
            ret = onScrollHorizontal(view, current.x - pointOld.x);
        else if (mode == GestureMode.SCROLL_V)
            ret = onScrollVertical(view, current.y - pointOld.y);
        if (!ret)
            onDrag(view, current.x - pointOld.x, current.y - pointOld.y);
        pointOld = current;
    }

    private void handleGestureEnd(View view, MotionEvent event, GestureMode mode) {
        boolean ret = false;
        if (mode == GestureMode.SCROLL_H)
            ret = onScrollHorizontalEnd(view, event);
        else if (mode == GestureMode.SCROLL_V)
            ret = onScrollVerticalEnd(view, event);
        if (!ret)
            onDragEnd(view, event);
    }

    private float getCurrentDistance(MotionEvent motionEvent) {
        PointerCoords c1 = new PointerCoords();
        PointerCoords c2 = new PointerCoords();
        motionEvent.getPointerCoords(0, c1);
        motionEvent.getPointerCoords(1, c2);
        float xx = c1.x - c2.x;
        float yy = c1.y - c2.y;
        return (float) Math.sqrt(xx*xx + yy*yy);
    }

    private float oldDist;
    private void handleZoom(View view, MotionEvent motionEvent) {
        float newDist = getCurrentDistance(motionEvent);
        float ratio = newDist / oldDist;
        oldDist = newDist;
        onZoom(view, ratio);
    }

    private void handleZoomEnd(View view) {
        onZoomEnd(view);
    }

    private final Handler handler = new Handler();

    private static final long CLICK_TIME_THRESHOLD = 200;
    private long downTime;
    private int downCount = 0;
    private void handleClick(View view, MotionEvent e) {
        if (downCount > 1) return;
        if (System.currentTimeMillis() - downTime < CLICK_TIME_THRESHOLD) {
            clickView = view;
            clickEvent = e;
            handler.postDelayed(SingleTapRunnable, CLICK_TIME_THRESHOLD);
        } else {
            downCount = 0;
        }
    }

    private View clickView;
    private MotionEvent clickEvent;
    private final Runnable SingleTapRunnable = () -> {
        downCount = 0;
        onClick(clickView, clickEvent);
    };

    private void handleDoubleTap(View view, MotionEvent e) {
        downCount = 0;
        onDoubleClick(view, e);
    }

    private final Point pointDown = new Point();
    GestureMode mode;
    @SuppressLint("ClickableViewAccessibility")
    @Override
    public boolean onTouch(View view, MotionEvent motionEvent) {
        Point p;
        switch (motionEvent.getActionMasked()) {
            case MotionEvent.ACTION_POINTER_DOWN:
                downCount = 0;
                if (mode != GestureMode.NONE && mode != GestureMode.ZOOM) return true;
                if (motionEvent.getPointerCount() == 2) {
                    oldDist = getCurrentDistance(motionEvent);
                    mode = GestureMode.ZOOM;
                } else {
                    Log.e(this.toString(), "INVALID COUNT: " + motionEvent.getPointerCount());
                    mode = GestureMode.INVALID;
                }
                break;
            case MotionEvent.ACTION_DOWN:
                pointDown.set(motionEvent.getRawX(), motionEvent.getRawY());
                pointOld = pointDown;
                downTime = System.currentTimeMillis();
                downCount++;
                handler.removeCallbacks(SingleTapRunnable);
                if (downCount > 1) {
                    handleDoubleTap(view, motionEvent);
                    mode = GestureMode.INVALID;
                    return true;
                }
                mode = GestureMode.NONE;
                break;
            case MotionEvent.ACTION_MOVE:
                if (mode == GestureMode.INVALID) {
                    downCount = 0;
                    return true;
                } else if (mode == GestureMode.ZOOM) {
                    downCount = 0;
                    handleZoom(view, motionEvent);
                    return true;
                } else if (mode != GestureMode.NONE) {
                    downCount = 0;
                    handleGesture(view, mode, motionEvent);
                    return true;
                }
                p = pointFromEvent(motionEvent);
                mode = getMode(pointDown, p);
                break;
            case MotionEvent.ACTION_UP:
                if (mode == GestureMode.NONE) {
                    if (downCount == 1) {
                        handleClick(view, motionEvent);
                    }
                } else if (mode == GestureMode.ZOOM || mode == GestureMode.INVALID) {
                    return true;
                } else {
                    handleGestureEnd(view, motionEvent, mode);
                }
                break;
            case MotionEvent.ACTION_POINTER_UP:
                if (mode == GestureMode.ZOOM && motionEvent.getPointerCount() <= 2) {
                    handleZoomEnd(view);
                    mode = GestureMode.INVALID;
                }
                break;
            default:
                Log.e(this.toString(), "action: " + motionEvent.getAction() + ", " + motionEvent.getActionMasked());
        }
        return true;
    }


    @SuppressWarnings("unused")
    public void onClick(View v, MotionEvent e){ }

    @SuppressWarnings("unused")
    public void onDoubleClick(View v, MotionEvent e){ }

    @SuppressWarnings("unused")
    public boolean onScrollVertical(View view, float deltaY) {
        return false;
    }

    @SuppressWarnings("unused")
    public boolean onScrollVerticalEnd(View view, MotionEvent motionEvent) {
        return false;
    }

    @SuppressWarnings("unused")
    public boolean onScrollHorizontal(View view, float deltaX) {
        return false;
    }

    @SuppressWarnings("unused")
    public boolean onScrollHorizontalEnd(View view, MotionEvent motionEvent) {
        return false;
    }

    @SuppressWarnings("unused")
    public void onDrag(View view, float deltaX, float deltaY) { }

    @SuppressWarnings("unused")
    public void onDragEnd(View view, MotionEvent motionEvent) { }

    @SuppressWarnings("unused")
    public void onZoom(View view, float ratio) { }

    @SuppressWarnings("unused")
    public void onZoomEnd(View view) { }
}

