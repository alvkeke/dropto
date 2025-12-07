package cn.alvkeke.dropto.data

import java.io.File

class ImageFile {
    @JvmField
    val md5file: File
    private var name: String

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

    fun getName(): String {
        return name
    }

    @Suppress("unused")
    fun setName(name: String) {
        this.name = name
    }

    companion object {
        @JvmStatic
        fun from(md5file: File, name: String): ImageFile {
            return ImageFile(md5file, name)
        }

        @JvmStatic
        fun from(folder: File, md5: String, name: String): ImageFile {
            return ImageFile(folder, md5, name)
        }
    }
}
