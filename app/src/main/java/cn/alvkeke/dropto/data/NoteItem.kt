package cn.alvkeke.dropto.data

import android.util.Log
import java.io.Serializable

class NoteItem : Serializable, Cloneable {
    @JvmField
    var id: Long
    @JvmField
    var categoryId: Long = 0
    var text: String
        private set
    val createTime: Long
    var isEdited: Boolean = false
        private set
    private var imageFiles: ArrayList<ImageFile> = ArrayList()

    /**
     * construct a new NoteItem instance, with auto generated create_time
     * @param text the content of the item
     */
    constructor(text: String) {
        this.id = ID_NOT_ASSIGNED
        this.text = text
        this.createTime = System.currentTimeMillis()
    }

    /**
     * construct a new NoteItem instance, with a specific create_time
     * this should be use to restore the items from database
     * @param text content of the item
     * @param createTime the specific create_time
     */
    constructor(text: String, createTime: Long) {
        this.id = ID_NOT_ASSIGNED
        this.text = text
        this.createTime = createTime
    }

    fun update(item: NoteItem, edited: Boolean) {
        if (this === item) return  // prevent update in place

        setText(item.text, edited)
        if (!item.isNoImage) useImageFiles(item.imageFiles)
        this.categoryId = item.categoryId
    }

    public override fun clone(): NoteItem {
        val noteItem = NoteItem(text)
        noteItem.update(this, false)
        return noteItem
    }

    fun setText(text: String, edited: Boolean) {
        this.text = text
        if (edited) {
            isEdited = true
        }
    }

    private fun isImageFileInvalid(image: ImageFile): Boolean {
        if (imageFiles.contains(image)) {
            Log.d(this.toString(), "image exist, return invalid")
            return true
        }

        return false
    }

    val isNoImage: Boolean
        get() {
            return imageFiles.isEmpty()
        }

    val imageCount: Int
        get() {
            return imageFiles.size
        }

    fun useImageFiles(imageFiles: ArrayList<ImageFile>) {
        clearImages()
        this.imageFiles.addAll(imageFiles)
    }

    fun clearImages() {
        this.imageFiles.clear()
    }

    fun addImageFile(imageFile: ImageFile): Boolean {
        if (isImageFileInvalid(imageFile)) return false
        imageFiles.add(imageFile)
        return true
    }

    fun getImageAt(index: Int): ImageFile {
        return imageFiles[index]
    }

    fun indexOf(imageFile: ImageFile): Int {
        return imageFiles.indexOf(imageFile)
    }

    fun iterateImages(): Iterator<ImageFile> {
        return imageFiles.iterator()
    }

    companion object {
        const val ID_NOT_ASSIGNED: Long = -1
    }
}
