package cn.alvkeke.dropto.provider.model

/** An attachment of a note, its content is the file the provider names by [md5]. */
data class AttachmentRecord(
    val md5: String,
    val name: String,
    val type: Int,
) {
    companion object {
        const val TYPE_MEDIA: Int = 0
        const val TYPE_FILE: Int = 1
    }
}
