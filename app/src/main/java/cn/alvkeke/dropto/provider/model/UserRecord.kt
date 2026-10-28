package cn.alvkeke.dropto.provider.model

/** An account as a provider stores it, [userId] is the id inside the domain of that provider. */
data class UserRecord(
    val userId: String,
    val nickname: String?,
    val avatar: String?,
) {
    /** The name to show, the id stands in while the account has no nickname. */
    val displayName: String get() = nickname ?: userId
}
