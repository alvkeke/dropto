package cn.alvkeke.dropto.runtime

import android.util.Log
import cn.alvkeke.dropto.provider.local.FileHelper
import cn.alvkeke.dropto.provider.model.AttachmentRecord
import cn.alvkeke.dropto.provider.model.CategoryRecord
import cn.alvkeke.dropto.provider.model.NoteRecord
import cn.alvkeke.dropto.provider.model.UserRecord
import cn.alvkeke.dropto.runtime.model.AttachmentFile
import cn.alvkeke.dropto.runtime.model.Category
import cn.alvkeke.dropto.runtime.model.NoteItem
import cn.alvkeke.dropto.runtime.model.User

private const val TAG = "ProviderDataMapping"

/**
 * The provider hands over its [CategoryRecord] / [NoteRecord] records, the UI works on the runtime
 * models ([Category] / [NoteItem]), so everything passing between the two layers is translated
 * here. Both directions only use the shared records, so any provider can take part.
 */

internal fun CategoryRecord.toRuntime(): Category {
    val category = Category(title, categoryType(type))
    category.id = id
    category.previewText = previewText ?: ""
    return category
}

internal fun Category.toRecord(): CategoryRecord =
    CategoryRecord(id, title, type.ordinal, previewText)

internal fun AttachmentRecord.toRuntime(): AttachmentFile =
    AttachmentFile.from(FileHelper.attachmentStorage, md5, name, attachmentType(type))

internal fun AttachmentFile.toRecord(): AttachmentRecord =
    // the provider stores the ordinals, they have to stay in sync with its TYPE_* constants
    AttachmentRecord(md5, name, type.ordinal)

internal fun NoteRecord.toRuntime(): NoteItem {
    val note = NoteItem(text ?: "", createTime, sender)
    note.id = id
    note.categoryId = categoryId
    note.isDeleted = deleted
    note.isEdited = edited
    note.isSynced = synced
    note.forceNewGroup = forceNewGroup
    note.reactions.addAll(reactions)
    note.attachments.addAll(attachments.map { it.toRuntime() })
    return note
}

internal fun NoteItem.toRecord(): NoteRecord = NoteRecord(
    id = id,
    categoryId = categoryId,
    text = text,
    createTime = createTime,
    sender = sender,
    deleted = isDeleted,
    edited = isEdited,
    synced = isSynced,
    forceNewGroup = forceNewGroup,
    reactions = reactions.toList(),
    attachments = attachments.map { it.toRecord() },
)

internal fun User.toRecord(): UserRecord = UserRecord(
    userId = id,
    nickname = name,
    avatar = avatar,
)

private fun categoryType(ordinal: Int): Category.Type {
    val types = Category.Type.entries
    if (ordinal !in types.indices) {
        Log.e(TAG, "Unknown category type: $ordinal")
        return Category.Type.LOCAL_CATEGORY
    }
    return types[ordinal]
}

private fun attachmentType(type: Int): AttachmentFile.Type = when (type) {
    AttachmentRecord.TYPE_MEDIA -> AttachmentFile.Type.MEDIA
    AttachmentRecord.TYPE_FILE -> AttachmentFile.Type.FILE
    else -> {
        Log.e(TAG, "Unknown attachment type: $type")
        AttachmentFile.Type.FILE
    }
}
