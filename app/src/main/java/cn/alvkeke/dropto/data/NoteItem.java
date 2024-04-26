package cn.alvkeke.dropto.data;

import java.io.Serializable;
import java.util.ArrayList;

public class NoteItem implements Serializable {

    public static final long ID_NOT_ASSIGNED = -1;
    private long id;
    private long categoryId;
    private String text;
    private long createTimeMs;
    private boolean isEdited;
    private ArrayList<ImageFile> imageFiles = null;

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

    public void update(NoteItem item, boolean set_edited) {
        if (this == item) return;   // prevent update in place
        setText(item.getText(), set_edited);
        setCreateTime(item.getCreateTime());
        useImageFiles(item.imageFiles);
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

    private boolean isImageFileInvalid(ImageFile image) {
        if (image == null)
            return true;
        if (!image.getMd5file().exists())
            return true;
        if (!image.getMd5file().isFile())
            return true;
        // FIXME: for debug only!!!! please uncomment these codes before merge to master branch
//        if (imageFiles != null && imageFiles.contains(image))
//            return true;

        return false;
    }

    public boolean isNoImage() {
        if (imageFiles == null) return true;
        return imageFiles.isEmpty();
    }

    public int getImageCount() {
        if (imageFiles == null) return 0;
        return imageFiles.size();
    }

    public void useImageFiles(ArrayList<ImageFile> imageFiles) {
        this.imageFiles.clear();
        this.imageFiles.addAll(imageFiles);
    }

    public void clearImages() {
        this.imageFiles.clear();
    }

    public boolean addImageFile(ImageFile imageFile) {
        if (isImageFileInvalid(imageFile))
            return false;
        if (imageFiles == null)
            imageFiles = new ArrayList<>();
        imageFiles.add(imageFile);
        return true;
    }

    public ImageFile getImageAt(int index) {
        if (getImageCount() > index) {
            return imageFiles.get(index);
        }
        return null;
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
