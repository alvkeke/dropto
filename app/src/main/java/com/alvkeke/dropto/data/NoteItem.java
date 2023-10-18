package com.alvkeke.dropto.data;

import android.util.Size;

public class NoteItem {

    private String _text;
    private long _create_time_ms;

    public NoteItem(String text, long create_time) {
        _text = text;
        _create_time_ms = create_time;
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

    public void setCreateTime(long ms) {
        this._create_time_ms = ms;
    }

    public long getCreateTime() {
        return _create_time_ms;
    }

}
