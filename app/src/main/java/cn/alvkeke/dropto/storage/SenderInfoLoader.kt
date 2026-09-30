package cn.alvkeke.dropto.storage

import android.content.Context
import android.graphics.drawable.Drawable
import android.util.Log
import java.util.concurrent.ConcurrentHashMap


object SenderInfoLoader {

    const val TAG: String = "SenderInfoLoader"

    private val iconCache = ConcurrentHashMap<String, Drawable>()
    private val labelCache = ConcurrentHashMap<String, String>()

    fun loadSenderIcon(context: Context, sender: String): Drawable? {
        iconCache[sender]?.let { return it.freshCopy() }

        val icon = try {
            context.packageManager.getApplicationIcon(sender)
        } catch (e: Exception) {
            Log.e(TAG, "loadSenderIcon: failed to get the icon of $sender, error: $e")
            return null
        }
        iconCache[sender] = icon
        return icon.freshCopy()
    }


    fun loadSenderLabel(context: Context, sender: String): String {
        labelCache[sender]?.let { return it }

        val label = try {
            val info = context.packageManager.getApplicationInfo(sender, 0)
            context.packageManager.getApplicationLabel(info).toString()
        } catch (e: Exception) {
            Log.e(TAG, "loadSenderLabel: failed to get the label of $sender, error: $e")
            return sender
        }
        labelCache[sender] = label
        return label
    }

    private fun Drawable.freshCopy(): Drawable =
        constantState?.newDrawable()?.mutate() ?: this
}
