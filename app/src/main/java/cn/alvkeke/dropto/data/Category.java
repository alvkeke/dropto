package cn.alvkeke.dropto.data;

import java.util.ArrayList;

public class Category {

    public static final long ID_NOT_ASSIGNED = -1;

    public enum Type {
        LOCAL_CATEGORY,
        REMOTE_SELF_DEV,
        REMOTE_USERS,
    }

    private long id;
    private String title;
    private final Type type;
    private String previewText = null;
    private final ArrayList<NoteItem> noteItems;
    private boolean needUpdate = false;

    public Category(String title, Type type) {
        this.id = ID_NOT_ASSIGNED;
        this.title = title;
        this.type = type;
        this.noteItems = new ArrayList<>();
    }

    public ArrayList<NoteItem> getNoteItems() {
        return noteItems;
    }

    /**
     * add a new item into category, and return its new index
     * @param item new item object
     * @return index of the new item
     */
    public int addNoteItem(NoteItem item) {
        noteItems.add(0, item);
        previewText = item.getText();
        needUpdate = true;
        return 0;   // return the index of the new item
    }

    public int indexNoteItem(NoteItem e) {
        return noteItems.indexOf(e);
    }

    public void delNoteItem(NoteItem item) {
        noteItems.remove(item);
        // TODO: update previewText
        needUpdate = true;
    }

    public NoteItem findNoteItem(long id) {
        for (NoteItem e: noteItems) {
            if (e.getId() == id) return e;
        }
        return null;
    }

    public NoteItem getNoteItem(int index) {
        if (index >= noteItems.size()) return null;
        return noteItems.get(index);
    }

    public void setUpdated() {
        needUpdate = false;
    }

    public boolean needUpdate() {
        return needUpdate;
    }

    public void setPreviewText(String previewText) {
        this.previewText = previewText;
    }

    public String getPreviewText() {
        return previewText == null ? "" : previewText;
    }

    public String getTitle() {
        return this.title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public Type getType() {
        return this.type;
    }

    public long getId() {
        return id;
    }

    public void setId(long id) {
        this.id = id;
    }
}
