package com.alvkeke.dropto.data;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.media.ThumbnailUtils;
import android.util.Size;

import java.io.File;
import java.util.ArrayList;

public class NoteItem {

    private String _text;
    private File _img_file;
    private Bitmap _img_thumb;
    private long _time_ms;
    private boolean _alert;
    private ArrayList<File> _files;

    public NoteItem(String text) {
        _text = text;
        _img_file = null;
        _img_thumb = null;
        _time_ms = -1;
        _alert = false;
        _files = new ArrayList<>();
    }

    public NoteItem() {
        _text = null;
        _img_file = null;
        _img_thumb = null;
        _time_ms = -1;
        _alert = false;
        _files = new ArrayList<>();
    }

    public void setText(String text) {
        _text = text;
    }

    public String getText() {
        return _text;
    }

    public void setImgFile(File file) {
        _img_file = file;
    }

    public File getImgFile() {
        return _img_file;
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

    private void genImgThumb() {
        assert (_img_file != null);
        if (_img_thumb != null) return;
        Bitmap full = BitmapFactory.decodeFile(_img_file.getAbsolutePath());
        Size newSize = adjustSize(full.getWidth(), full.getHeight());
        _img_thumb = ThumbnailUtils.extractThumbnail(full, newSize.getWidth(), newSize.getHeight());
    }

    public Bitmap getImgThumb() {
        if (_img_file == null) return null;
        if (_img_thumb == null) genImgThumb();
        return _img_thumb;
    }

    public void setTime(long ms) {
        this._time_ms = ms;
    }

    public long getTime() {
        return _time_ms;
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
