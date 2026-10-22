package cn.alvkeke.dropto.data

data class User(val domain: String, val id: String, var name: String? = null) {

    val fullUserId: String get() = "$domain.$id"

    companion object {

        const val LOCAL_DOMAIN: String = "local"
        const val DEFAULT_LOCAL_ID: String = "user"

        val LOCAL: User = User(LOCAL_DOMAIN, DEFAULT_LOCAL_ID, "Local User")

        private val ID_PATTERN = Regex("^[A-Za-z0-9_]{6,10}$")
        fun isValidId(id: String): Boolean = id == DEFAULT_LOCAL_ID || ID_PATTERN.matches(id)
    }
}
