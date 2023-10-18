package com.alvkeke.dropto.data;

import android.util.Size;

public class NoteItem implements Cloneable{

    private String _text;
    private long _create_time_ms;
    private NoteItem _history;

    public NoteItem(String text, long create_time) {
        _text = text;
        _create_time_ms = create_time;
        _history = null;
    }

    @Override
    public NoteItem clone() {
        return new NoteItem(_text, _create_time_ms);
    }

    public void setText(String text, boolean set_edited) {
        _text = text;
        if (set_edited) {
            _history = this.clone();
        }
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

    public boolean isEdited() {
        return !(_history == null);
    }

}
