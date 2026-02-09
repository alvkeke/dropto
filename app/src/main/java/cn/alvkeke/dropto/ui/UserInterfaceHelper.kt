package cn.alvkeke.dropto.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Context.CLIPBOARD_SERVICE
import android.content.Intent
import android.net.Uri
import android.util.Log
import android.widget.Toast
import androidx.fragment.app.Fragment
import cn.alvkeke.dropto.data.AttachmentFile
import cn.alvkeke.dropto.data.NoteItem
import cn.alvkeke.dropto.storage.FileHelper
import cn.alvkeke.dropto.ui.fragment.ImageViewerFragment
import cn.alvkeke.dropto.ui.fragment.NoteDetailFragment
import cn.alvkeke.dropto.ui.fragment.NoteListFragment.Companion.TAG

object UserInterfaceHelper {

    fun Context.shareFileToExternal(text: String, type: String, uris: ArrayList<Uri>) {
        val sendIntent = Intent(Intent.ACTION_SEND)

        sendIntent.type = type
        if (uris.isNotEmpty()) {
            sendIntent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }

        if (uris.size == 1) {
            sendIntent.putExtra(Intent.EXTRA_STREAM, uris[0])
        } else {
            sendIntent.action = Intent.ACTION_SEND_MULTIPLE
            sendIntent.putParcelableArrayListExtra(Intent.EXTRA_STREAM, uris)
        }

        sendIntent.putExtra(Intent.EXTRA_TEXT, text)
        Log.d(this.toString(), "no image, share text: $text")

        val shareIntent = Intent.createChooser(sendIntent, "Share to")
        try {
            this.startActivity(shareIntent)
        } catch (e: Exception) {
            Log.e(this.toString(), "Failed to create share Intent: $e")
        }
    }

    fun Context.copyText(text: String) {
        val clipboardManager =
            getSystemService(CLIPBOARD_SERVICE) as ClipboardManager
        val data = ClipData.newPlainText("text", text)
        clipboardManager.setPrimaryClip(data)
    }

    fun Context.openFileWithExternalApp(file: AttachmentFile) {
        val uri = FileHelper.getUriForFile(this, file.md5file)
        val mimeType = FileHelper.mimeTypeFromFileName(file.name)
        Log.v(TAG, "open file with mime type: $mimeType")

        // Special handling for APK files
        if (mimeType == "application/vnd.android.package-archive") {
            val installIntent = Intent(Intent.ACTION_VIEW)
            installIntent.setDataAndType(uri, mimeType)
            installIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            installIntent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            try {
                this.startActivity(installIntent)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to open APK installer: $e")
                Toast.makeText(
                    this,
                    "Failed to open APK installer. Please check if installation from unknown sources is enabled.",
                    Toast.LENGTH_LONG
                ).show()
            }
            return
        }

        // Normal file opening
        val openIntent = Intent(Intent.ACTION_VIEW)
        openIntent.setDataAndType(uri, mimeType)
        openIntent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        try {
            this.startActivity(openIntent)
        } catch (e: Exception) {
            Log.e(this.toString(), "Failed to open file intent: $e")
            Toast.makeText(
                this,
                "No application found to open this file type: $mimeType",
                Toast.LENGTH_SHORT
            ).show()
        }
    }

    private val imageViewerFragment: ImageViewerFragment by lazy {
        ImageViewerFragment()
    }
    fun Fragment.showMediaFragment(mediaFile: AttachmentFile) {
        if (mediaFile.isVideo) {
            // FIXME: here is a workaround for video playback, open it with external player now
            // need to implement a unified media viewer later
            this.requireContext().openFileWithExternalApp(mediaFile)
        } else {
            imageViewerFragment.setImage(mediaFile.md5file)
            imageViewerFragment.show(parentFragmentManager, null)
        }
    }

    private val noteDetailFragment: NoteDetailFragment by lazy {
        NoteDetailFragment()
    }
    fun Fragment.showNoteDetailFragment(item: NoteItem) {
        noteDetailFragment.setNoteItem(item)
        parentFragmentManager.beginTransaction()
            .add(noteDetailFragment, null)
            .commit()
    }

}