package cn.alvkeke.dropto.provider.model

/**
 * A note as a provider stores it. The state flags are single fields here, their bit layout is a
 * storage detail that stays inside each provider.
 */
data class NoteRecord(
    val id: Long,
    val categoryId: Long,
    val text: String?,
    val createTime: Long,
    val sender: String?,
    val deleted: Boolean,
    val edited: Boolean,
    val synced: Boolean,
    val forceNewGroup: Boolean,
    val reactions: List<String>,
    val attachments: List<AttachmentRecord>,
)
