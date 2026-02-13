package cn.alvkeke.dropto.data

import android.util.Log
import cn.alvkeke.dropto.storage.FileHelper
import java.io.Serializable

class NoteItem : Serializable, Cloneable {
    @JvmField
    var id: Long
    @JvmField
    var categoryId: Long = 0
    var text: String
    val createTime: Long

    var sender: String? = null
        private set

    var isEdited: Boolean = false
    var isDeleted: Boolean = false
    var isSynced: Boolean = false

    val medias: ArrayList<AttachmentFile> = ArrayList()
    val files: ArrayList<AttachmentFile> = ArrayList()

    val attachments: MutableList<AttachmentFile> = object : MutableList<AttachmentFile> {
        override fun add(element: AttachmentFile): Boolean {
            return when (element.type) {
                AttachmentFile.Type.MEDIA -> medias.add(element)
                AttachmentFile.Type.FILE -> files.add(element)
            }
        }

        override fun remove(element: AttachmentFile): Boolean {
            return when (element.type) {
                AttachmentFile.Type.MEDIA -> medias.remove(element)
                AttachmentFile.Type.FILE -> files.remove(element)
            }
        }

        override fun addAll(elements: Collection<AttachmentFile>): Boolean {
            var changed = false
            for (element in elements) {
                val result = when (element.type) {
                    AttachmentFile.Type.MEDIA -> medias.add(element)
                    AttachmentFile.Type.FILE -> files.add(element)
                }
                if (result) {
                    changed = true
                }
            }
            return changed
        }

        override fun addAll(index: Int, elements: Collection<AttachmentFile>): Boolean {
            val retImages = medias.addAll(index, elements.filter { it.type == AttachmentFile.Type.MEDIA })
            val retFiles = files.addAll(index, elements.filter { it.type == AttachmentFile.Type.FILE })
            return retImages || retFiles
        }

        override fun removeAll(elements: Collection<AttachmentFile>): Boolean {
            val retImages = medias.removeAll(elements.filter { it.type == AttachmentFile.Type.MEDIA }
                .toSet())
            val retFiles = files.removeAll(elements.filter { it.type == AttachmentFile.Type.FILE }
                .toSet())
            return retImages || retFiles
        }

        override fun retainAll(elements: Collection<AttachmentFile>): Boolean {
            var imagesRetained = medias.retainAll(elements.filter { it.type == AttachmentFile.Type.MEDIA }
                .toSet())
            var filesRetained = files.retainAll(elements.filter { it.type == AttachmentFile.Type.FILE }
                .toSet())
            return imagesRetained || filesRetained
        }

        override fun clear() {
            medias.clear()
            files.clear()
        }

        override fun set(index: Int, element: AttachmentFile): AttachmentFile {
            val old = get(index)
            removeAt(index)
            add(index, element)
            return old
        }

        override fun add(index: Int, element: AttachmentFile) {
            when (element.type) {
                AttachmentFile.Type.MEDIA -> medias.add(index, element)
                AttachmentFile.Type.FILE -> files.add(index, element)
            }
        }

        override fun removeAt(index: Int): AttachmentFile {
            val element = get(index)
            remove(element)
            return element
        }

        override fun listIterator(): MutableListIterator<AttachmentFile> {
            return (medias + files).toMutableList().listIterator()
        }

        override fun listIterator(index: Int): MutableListIterator<AttachmentFile> {
            return (medias + files).toMutableList().listIterator(index)
        }

        override fun subList(fromIndex: Int, toIndex: Int): MutableList<AttachmentFile> {
            return (medias + files).subList(fromIndex, toIndex).toMutableList()
        }

        override val size: Int
            get() = medias.size + files.size

        override fun isEmpty(): Boolean {
            return medias.isEmpty() && files.isEmpty()
        }

        override fun contains(element: AttachmentFile): Boolean {
            return when (element.type) {
                AttachmentFile.Type.MEDIA -> medias.contains(element)
                AttachmentFile.Type.FILE -> files.contains(element)
            }
        }

        override fun containsAll(elements: Collection<AttachmentFile>): Boolean {
            return elements.all { contains(it) }
        }

        override fun get(index: Int): AttachmentFile {
            if (index !in 0..<size) throw IndexOutOfBoundsException()

            return if (index < medias.size) {
                medias[index]
            } else {
                files[index - medias.size]
            }
        }

        override fun indexOf(element: AttachmentFile): Int {
            return when (element.type) {
                AttachmentFile.Type.MEDIA -> {
                    val idx = medias.indexOf(element)
                    if (idx == -1) -1 else idx
                }
                AttachmentFile.Type.FILE -> {
                    val idx = files.indexOf(element)
                    if (idx == -1) -1 else medias.size + idx
                }
            }
        }

        override fun lastIndexOf(element: AttachmentFile): Int {
            return when (element.type) {
                AttachmentFile.Type.MEDIA -> {
                    val idx = medias.lastIndexOf(element)
                    if (idx == -1) -1 else idx
                }
                AttachmentFile.Type.FILE -> {
                    val idx = files.lastIndexOf(element)
                    if (idx == -1) -1 else medias.size + idx
                }
            }
        }

        override fun iterator(): MutableIterator<AttachmentFile> {
            return (medias + files).toMutableList().iterator()
        }
    }

    fun getAttachmentMimeType(): String {
        return if (attachments.isEmpty()) {
            "text/plain"
        } else if (files.isEmpty()) {
            var firstTypeMain: String? = null
            var firstTypeSub: String? = null
            for (file in attachments) {
                val fileMimeType = FileHelper.mimeTypeFromFileName(file.name)
                Log.v(TAG, "attachment mime type: $fileMimeType")
                val main = fileMimeType.substringBefore('/')
                val sub = fileMimeType.substringAfter('/')
                if (firstTypeMain == null) {
                    firstTypeMain = main
                    firstTypeSub = sub
                    continue
                }

                if (firstTypeMain != main) {
                    Log.d(TAG, "different main type: $firstTypeMain vs $main")
                    return "*/*"
                }
                if (firstTypeSub != sub) { firstTypeSub = "*" }
            }
            val ret = "$firstTypeMain/$firstTypeSub"
            Log.e(TAG, "final attachment mime type: $ret")
            ret
        } else {
            var firstTypeMain: String? = null
            var firstTypeSub: String? = null

            for (file in attachments) {
                val fileMimeType = FileHelper.mimeTypeFromFileName(file.name)
                Log.e(TAG, "attachment mime type: $fileMimeType")
                val mainType = fileMimeType.substringBefore('/')
                val subType = fileMimeType.substringAfter('/')
                if (firstTypeMain == null) {
                    firstTypeMain = mainType
                    firstTypeSub = subType
                    continue
                }

                if (firstTypeMain != mainType) { return "*/*" }
                if (firstTypeSub != subType) { firstTypeSub = "*" }
            }

            if (firstTypeMain != "image" && medias.isNotEmpty()) {
                "*/*"
            } else {
                "$firstTypeMain/$firstTypeSub"
            }
        }
    }

    /**
     * construct a new NoteItem instance, with auto generated create_time
     * @param text the content of the item
     */
    constructor(text: String, sender: String? = null) {
        this.id = ID_NOT_ASSIGNED
        this.text = text
        this.createTime = System.currentTimeMillis()
        this.sender = sender
    }

    /**
     * construct a new NoteItem instance, with a specific create_time
     * this should be use to restore the items from database
     * @param text content of the item
     * @param createTime the specific create_time
     */
    constructor(text: String, createTime: Long, sender: String? = null) {
        this.id = ID_NOT_ASSIGNED
        this.text = text
        this.createTime = createTime
        this.sender = sender
    }

    fun updateFrom(src: NoteItem) {
        if (this === src) return  // prevent update in place

        this.text = src.text
        if (src.attachments.isNotEmpty()) {
            src.attachments.clear()
            this.attachments.addAll(src.attachments)
        }
        this.isDeleted = src.isDeleted
        this.isEdited = src.isEdited
        this.isSynced = src.isSynced
        this.categoryId = src.categoryId
    }

    public override fun clone(): NoteItem {
        val noteItem = NoteItem(text)
        noteItem.attachments.addAll(this.attachments)
        noteItem.categoryId = this.categoryId
        return noteItem
    }

    companion object {
        const val TAG: String = "NoteItem"
        const val ID_NOT_ASSIGNED: Long = -1
    }
}
