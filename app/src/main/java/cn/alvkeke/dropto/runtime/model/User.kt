package cn.alvkeke.dropto.runtime.model

data class User(
    val domain: String,
    val id: String,
    var name: String? = null,
    var avatar: String? = null,
) {

    val fullUserId: String get() = "$domain.$id"

    companion object {

        const val LOCAL_DOMAIN: String = "local"
        const val DEFAULT_LOCAL_USER_ID: String = "default"
        const val DEFAULT_LOCAL_USER_PWD: String = ""

        val LOCAL: User = User(LOCAL_DOMAIN, DEFAULT_LOCAL_USER_ID, "Local User")

        private val ID_PATTERN = Regex("^[A-Za-z0-9_]{6,10}$")
        fun isValidId(id: String): Boolean = ID_PATTERN.matches(id)
    }
}
