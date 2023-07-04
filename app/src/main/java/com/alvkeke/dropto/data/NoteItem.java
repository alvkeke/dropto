package com.alvkeke.dropto.data;

import android.util.Size;

public class NoteItem {

    private String _text;
    private long _time_ms;

    public NoteItem(String text) {
        _text = text;
        _time_ms = -1;
    }

    public NoteItem() {
        _text = null;
        _time_ms = -1;
    }

    public void setText(String text) {
        _text = text;
    }

    public String getText() {
        return _text;
    }

    private Size adjustSize(float w, float h) {
        final float MAX_SIZE = 200;
        float newW, newH;
        float ratio;
        if (w <= MAX_SIZE && h <= MAX_SIZE) {
            newW = w;
            newH = h;
        } else if (w >= MAX_SIZE && h <= MAX_SIZE) {
            newW = MAX_SIZE;
            ratio = w / h;
            newH = MAX_SIZE/ratio;
        } else if (w <= MAX_SIZE) {
            newH = MAX_SIZE;
            ratio = w / h;
            newW = ratio * MAX_SIZE;
        } else {
            if (h > w) {
                newH = MAX_SIZE;
                ratio = w / h;
                newW = ratio * MAX_SIZE;
            } else {
                newW = MAX_SIZE;
                ratio = w / h;
                newH = MAX_SIZE/ratio;
            }
        }

        return new Size((int)newW, (int)newH);
    }

    public void setTime(long ms) {
        this._time_ms = ms;
    }

    public long getTime() {
        return _time_ms;
    }

}
