package cn.alvkeke.dropto.data

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

    val images: ArrayList<AttachmentFile> = ArrayList()
    val files: ArrayList<AttachmentFile> = ArrayList()

    val attachments: MutableList<AttachmentFile> = object : MutableList<AttachmentFile> {
        override fun add(element: AttachmentFile): Boolean {
            return when (element.type) {
                AttachmentFile.Type.IMAGE -> images.add(element)
                AttachmentFile.Type.FILE -> files.add(element)
            }
        }

        override fun remove(element: AttachmentFile): Boolean {
            return when (element.type) {
                AttachmentFile.Type.IMAGE -> images.remove(element)
                AttachmentFile.Type.FILE -> files.remove(element)
            }
        }

        override fun addAll(elements: Collection<AttachmentFile>): Boolean {
            var changed = false
            for (element in elements) {
                val result = when (element.type) {
                    AttachmentFile.Type.IMAGE -> images.add(element)
                    AttachmentFile.Type.FILE -> files.add(element)
                }
                if (result) {
                    changed = true
                }
            }
            return changed
        }

        override fun addAll(index: Int, elements: Collection<AttachmentFile>): Boolean {
            val retImages = images.addAll(index, elements.filter { it.type == AttachmentFile.Type.IMAGE })
            val retFiles = files.addAll(index, elements.filter { it.type == AttachmentFile.Type.FILE })
            return retImages || retFiles
        }

        override fun removeAll(elements: Collection<AttachmentFile>): Boolean {
            val retImages = images.removeAll(elements.filter { it.type == AttachmentFile.Type.IMAGE }
                .toSet())
            val retFiles = files.removeAll(elements.filter { it.type == AttachmentFile.Type.FILE }
                .toSet())
            return retImages || retFiles
        }

        override fun retainAll(elements: Collection<AttachmentFile>): Boolean {
            var imagesRetained = images.retainAll(elements.filter { it.type == AttachmentFile.Type.IMAGE }
                .toSet())
            var filesRetained = files.retainAll(elements.filter { it.type == AttachmentFile.Type.FILE }
                .toSet())
            return imagesRetained || filesRetained
        }

        override fun clear() {
            images.clear()
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
                AttachmentFile.Type.IMAGE -> images.add(index, element)
                AttachmentFile.Type.FILE -> files.add(index, element)
            }
        }

        override fun removeAt(index: Int): AttachmentFile {
            val element = get(index)
            remove(element)
            return element
        }

        override fun listIterator(): MutableListIterator<AttachmentFile> {
            return (images + files).toMutableList().listIterator()
        }

        override fun listIterator(index: Int): MutableListIterator<AttachmentFile> {
            return (images + files).toMutableList().listIterator(index)
        }

        override fun subList(fromIndex: Int, toIndex: Int): MutableList<AttachmentFile> {
            return (images + files).subList(fromIndex, toIndex).toMutableList()
        }

        override val size: Int
            get() = images.size + files.size

        override fun isEmpty(): Boolean {
            return images.isEmpty() && files.isEmpty()
        }

        override fun contains(element: AttachmentFile): Boolean {
            return when (element.type) {
                AttachmentFile.Type.IMAGE -> images.contains(element)
                AttachmentFile.Type.FILE -> files.contains(element)
            }
        }

        override fun containsAll(elements: Collection<AttachmentFile>): Boolean {
            return elements.all { contains(it) }
        }

        override fun get(index: Int): AttachmentFile {
            if (index !in 0..<size) throw IndexOutOfBoundsException()

            return if (index < images.size) {
                images[index]
            } else {
                files[index - images.size]
            }
        }

        override fun indexOf(element: AttachmentFile): Int {
            return when (element.type) {
                AttachmentFile.Type.IMAGE -> {
                    val idx = images.indexOf(element)
                    if (idx == -1) -1 else idx
                }
                AttachmentFile.Type.FILE -> {
                    val idx = files.indexOf(element)
                    if (idx == -1) -1 else images.size + idx
                }
            }
        }

        override fun lastIndexOf(element: AttachmentFile): Int {
            return when (element.type) {
                AttachmentFile.Type.IMAGE -> {
                    val idx = images.lastIndexOf(element)
                    if (idx == -1) -1 else idx
                }
                AttachmentFile.Type.FILE -> {
                    val idx = files.lastIndexOf(element)
                    if (idx == -1) -1 else images.size + idx
                }
            }
        }

        override fun iterator(): MutableIterator<AttachmentFile> {
            return (images + files).toMutableList().iterator()
        }
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

    fun update(target: NoteItem, edited: Boolean) {
        if (this === target) return  // prevent update in place

        setText(target.text, edited)
        if (!target.attachments.isNotEmpty()) {
            target.attachments.clear()
            target.attachments.addAll(target.attachments)
        }
        this.categoryId = target.categoryId
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

    companion object {
        const val ID_NOT_ASSIGNED: Long = -1
    }
}
