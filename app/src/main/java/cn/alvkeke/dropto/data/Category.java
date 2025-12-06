package cn.alvkeke.dropto.data;

import java.util.ArrayList;

import cn.alvkeke.dropto.R;

public class Category {

    public static final long ID_NOT_ASSIGNED = -1;

    public enum Type {
        LOCAL_CATEGORY,
        REMOTE_SELF_DEV,
        REMOTE_USERS,
    }

    public static int typeToIconResource(Type type) {
        switch (type) {
            case LOCAL_CATEGORY:
                return R.drawable.icon_category_local;
            case REMOTE_USERS:
                return R.drawable.icon_category_remote_peers;
            case REMOTE_SELF_DEV:
                return R.drawable.icon_category_remote_dev;
            default:
                return R.drawable.icon_category_unknown;
        }
    }

    public static String typeToName(Type type) {
        switch (type) {
            case LOCAL_CATEGORY:
                return "Local Category";
            case REMOTE_USERS:
                return "Remote Peer";
            case REMOTE_SELF_DEV:
                return "Remote Device";
            default:
                return "(Unknown Category Type)";
        }
    }

    private long id;
    private String title;
    private Type type;
    private String previewText = "";
    private final ArrayList<NoteItem> noteItems;
    private boolean isInitialized = false;

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
        return 0;   // return the index of the new item
    }

    public int indexNoteItem(NoteItem e) {
        return noteItems.indexOf(e);
    }

    public void delNoteItem(NoteItem item) {
        noteItems.remove(item);
        if (!noteItems.isEmpty()) {
            NoteItem e = noteItems.get(0);
            previewText = e.getText();
        }
    }

    public NoteItem findNoteItem(long id) {
        for (NoteItem e: noteItems) {
            if (e.id == id) return e;
        }
        return null;
    }

    public NoteItem getNoteItem(int index) {
        if (index >= noteItems.size()) return null;
        return noteItems.get(index);
    }

    public boolean isInitialized() {
        return isInitialized;
    }

    public void setInitialized(boolean initialized) {
        isInitialized = initialized;
    }

    public void setPreviewText(String previewText) {
        this.previewText = previewText;
    }

    public String getPreviewText() {
        return previewText;
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

    public void setType(Type type) {
        this.type = type;
    }

    public long getId() {
        return id;
    }

    public void setId(long id) {
        this.id = id;
    }
}
