package cn.alvkeke.dropto.provider.model

/*
 * The records handed over between the runtime and a provider. Every provider reads and writes the
 * same set, anything a single provider needs for its own bookkeeping (row ids, etags, upload state)
 * stays inside that provider instead of being added here.
 */

/** The id a record carries while the provider still has to assign one. */
const val ID_NOT_ASSIGNED: Long = -1
