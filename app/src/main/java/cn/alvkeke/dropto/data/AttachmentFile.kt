package cn.alvkeke.dropto.data

import java.io.File

class AttachmentFile {

    enum class Type {
        IMAGE,
        FILE
    }

    val type: Type

    @JvmField
    val md5file: File
    @JvmField
    var name: String
    val md5: String
        get() = md5file.name

    constructor(folder: File, md5: String, name: String, type: Type) {
        this.md5file = File(folder, md5)
        this.name = name
        this.type = type
    }

    constructor(md5File: File, imgName: String, type: Type) {
        this.md5file = md5File
        this.name = imgName
        this.type = type
    }

    companion object {
        @JvmStatic
        fun from(md5file: File, name: String, type: Type): AttachmentFile {
            return AttachmentFile(md5file, name, type)
        }

        @JvmStatic
        fun from(folder: File, md5: String, name: String, type: Type): AttachmentFile {
            return AttachmentFile(folder, md5, name, type)
        }
    }
}
