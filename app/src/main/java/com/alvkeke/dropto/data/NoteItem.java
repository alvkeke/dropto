package com.alvkeke.dropto.data;

import android.graphics.Bitmap;

import java.io.File;
import java.util.ArrayList;

public class NoteItem {

    private String _text;
    private Bitmap _bitmap;
    private long _time;
    private boolean _alert;
    private ArrayList<File> _files;

    public NoteItem(String text) {
        _text = text;
        _bitmap = null;
        _files = new ArrayList<>();
        _time = -1;
        _alert = false;
    }

    public NoteItem() {
        _text = null;
        _bitmap = null;
        _files = new ArrayList<>();
        _time = -1;
        _alert = false;
    }

    public void setText(String text) {
        _text = text;
    }

    public String getText() {
        return _text;
    }

    public void setBitmap(Bitmap img) {
        _bitmap = img;
    }

    public Bitmap getBitmap() {
        return _bitmap;
    }

    public void setTime(long time) {
        this._time = time;
    }

    public long getTime() {
        return _time;
    }

    public boolean isAlert() {
        return _alert;
    }

    public void setAlert(boolean alert) {
        this._alert = alert;
    }

    public int getFileCount() {
        return _files.size();
    }

    private boolean isFileValid(File file) {
        return file != null && file.exists() && file.isFile();
    }

    public void addFile(String filename) {
        File file = new File(filename);
        if (isFileValid(file)) {
            _files.add(file);
        }
    }

    public void addFile(File file) {
        if (isFileValid(file)) {
            _files.add(file);
        }
    }
}
