package cn.alvkeke.dropto.data

import java.io.File

class AttachmentFile {
    @JvmField
    val md5file: File
    @JvmField
    var name: String

    constructor(folder: File, md5: String, name: String) {
        this.md5file = File(folder, md5)
        this.name = name
    }

    constructor(md5File: File, imgName: String) {
        this.md5file = md5File
        this.name = imgName
    }

    val md5: String
        get() = md5file.name

    companion object {
        @JvmStatic
        fun from(md5file: File, name: String): AttachmentFile {
            return AttachmentFile(md5file, name)
        }

        @JvmStatic
        fun from(folder: File, md5: String, name: String): AttachmentFile {
            return AttachmentFile(folder, md5, name)
        }
    }
}
