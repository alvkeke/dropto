package cn.alvkeke.dropto.data;

import android.util.Log;

import androidx.annotation.NonNull;

import java.io.File;
import java.io.Serializable;

public class NoteItem implements Cloneable, Serializable {

    public static final long ID_NOT_ASSIGNED = -1;
    private long id;
    private long categoryId;
    private String text;
    private long createTimeMs;
    private boolean isEdited;
    private File imgFile;
    private String imgName;

    /**
     * construct a new NoteItem instance, with auto generated create_time
     * @param text the content of the item
     */
    public NoteItem(String text) {
        this.id = ID_NOT_ASSIGNED;
        this.text = text;
        this.createTimeMs = System.currentTimeMillis();
    }

    /**
     * construct a new NoteItem instance, with a specific create_time
     * this should be use to restore the items from database
     * @param text content of the item
     * @param create_time the specific create_time
     */
    public NoteItem(String text, long create_time) {
        this.id = ID_NOT_ASSIGNED;
        this.text = text;
        createTimeMs = create_time;
    }

    @NonNull
    @Override
    public NoteItem clone() {
        NoteItem item = new NoteItem(text, createTimeMs);
        item.setId(this.id);
        item.setCategoryId(this.categoryId);
        item.setImageFile(this.imgFile);
        item.setImageName(this.imgName);
        return item;
    }

    public void update(NoteItem item, boolean set_edited) {
        setText(item.getText(), set_edited);
        setCreateTime(item.getCreateTime());
        setImageFile(item.getImageFile());
        setImageName(item.getImageName());
        setCategoryId(item.getCategoryId());
    }

    public void setText(String text, boolean set_edited) {
        this.text = text;
        if (set_edited) {
            isEdited = true;
        }
    }

    public String getText() {
        return text;
    }

    public void setCreateTime(long ms) {
        this.createTimeMs = ms;
    }

    public long getCreateTime() {
        return createTimeMs;
    }

    public boolean setImageFile(File img) {

        if (img == null) {
            Log.d(this.toString(), "clear image");
            this.imgFile = null;
            return true;
        }
        if (!img.exists()) {
            Log.d(this.toString(), "add image abort, file not exist: " + img);
            return false;
        }
        if (!img.isFile()) {
            Log.d(this.toString(), "add image abort, not a file: " + img);
            return false;
        }

        this.imgFile = img;

        return true;
    }

    public File getImageFile() {
        return this.imgFile;
    }

    public void setImageName(String name) {
        this.imgName = name;
    }

    public String getImageName() {
        return this.imgName;
    }

    public boolean isEdited() {
        return isEdited;
    }

    public void setId(long id) {
        this.id = id;
    }

    public long getId() {
        return this.id;
    }

    public void setCategoryId(long id) {
        this.categoryId = id;
    }

    public long getCategoryId() {
        return this.categoryId;
    }
}
