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
    private var attachedFiles: ArrayList<AttachmentFile> = ArrayList()
    val attachments
        get() = attachedFiles
    val attachmentCount: Int
        get() {
            return attachedFiles.size
        }

    enum class AttachmentType {
        NONE,
        IMAGE,
        FILE
    }

    var attachmentType: AttachmentType = AttachmentType.NONE
        set(type) {
            assert(type != AttachmentType.NONE) // assign the type to NONE manually is not allowed
            field = type
        }

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
        if (!item.noAttachment) useImageFiles(item.attachedFiles, item.attachmentType)
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

    private fun isAttachmentInvalid(image: AttachmentFile): Boolean {
        if (attachedFiles.contains(image)) {
            Log.d(this.toString(), "image exist, return invalid")
            return true
        }

        return false
    }

    val noAttachment: Boolean
        get() {
            return attachedFiles.isEmpty()
        }


    fun clearAttachments() {
        this.attachedFiles.clear()
        this.attachmentType = AttachmentType.NONE
    }

    fun useImageFiles(attachments: ArrayList<AttachmentFile>, type: AttachmentType) {
        clearAttachments()
        this.attachedFiles.addAll(attachments)
        this.attachmentType = type
    }

    fun addAttachment(attachment: AttachmentFile): Boolean {
        if (isAttachmentInvalid(attachment)) return false
        attachedFiles.add(attachment)
        return true
    }

    fun indexOf(imageFile: AttachmentFile): Int {
        return attachedFiles.indexOf(imageFile)
    }

    fun iterateImages(): Iterator<AttachmentFile> {
        return attachedFiles.iterator()
    }

    companion object {
        const val ID_NOT_ASSIGNED: Long = -1
    }
}
