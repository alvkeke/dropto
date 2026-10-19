package cn.alvkeke.dropto.data

/** The id is used as the name of the folder that holds the data of that user. */
data class User(val id: String, val name: String) {

    companion object {

        const val LOCAL_ID: String = "local.user"

        val LOCAL: User = User(LOCAL_ID, "Local User")

        // only the local id may contain a dot, so a remote id can never become "." or ".."
        private val ID_PATTERN = Regex("^[A-Za-z0-9_]{6,18}$")

        fun isValidId(id: String): Boolean = id == LOCAL_ID || ID_PATTERN.matches(id)
    }
}
