package cn.alvkeke.dropto.provider.model

/**
 * A category as a provider stores it, [type] is the stored kind of the category, the provider keeps
 * it as it was handed over.
 */
data class CategoryRecord(
    val id: Long,
    val title: String,
    val type: Int,
    val previewText: String?,
)
