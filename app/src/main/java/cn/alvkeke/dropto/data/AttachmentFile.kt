package cn.alvkeke.dropto.data

import cn.alvkeke.dropto.storage.FileHelper
import java.io.File

class AttachmentFile(val md5file: File, var name: String, val type: Type) {

    enum class Type {
        MEDIA,
        FILE
    }

    constructor(folder: File, md5: String, name: String, type: Type) :
            this(File(folder, md5), name, type)

    val md5: String
        get() = md5file.name

    val isVideo: Boolean
        get() {
            return mimeType.startsWith("video/")
        }

    val mimeType: String
        get() = FileHelper.mimeTypeFromFileName(name)

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
