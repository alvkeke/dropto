package cn.alvkeke.dropto.ui.activity

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import cn.alvkeke.dropto.DroptoApplication
import cn.alvkeke.dropto.data.AttachmentFile
import cn.alvkeke.dropto.data.AttachmentFile.Type
import cn.alvkeke.dropto.data.Category
import cn.alvkeke.dropto.data.NoteItem
import cn.alvkeke.dropto.service.Task.Companion.createNote
import cn.alvkeke.dropto.storage.DataLoader.categories
import cn.alvkeke.dropto.storage.FileHelper
import cn.alvkeke.dropto.storage.FileHelper.getFileNameFromUri
import cn.alvkeke.dropto.storage.FileHelper.saveUriToFile
import cn.alvkeke.dropto.ui.fragment.CategorySelectorFragment

class ShareRecvActivity : AppCompatActivity(), CategorySelectorFragment.CategorySelectListener {

    private val app: DroptoApplication
        get() = application as DroptoApplication

    private val categorySelectorFragment: CategorySelectorFragment by lazy {
        CategorySelectorFragment()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        this.enableEdgeToEdge()

        if (savedInstanceState != null) {
            this.finish()
            // just end after change to dark mode
            return
        }

        val intent = getIntent()
        val action = intent.action
        if (action == null) {
            Toast.makeText(this, "action is null", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        if (!isActionAllow(action)) {
            Toast.makeText(this, "unknown action: $action", Toast.LENGTH_SHORT).show()
            finish()
        }

        pendingText = handleTextInfo(intent)
        val type = intent.type
        pendingType = intent.type ?: "*/*"
        Log.e(TAG, "received type: $type")
        pendingUris = handleReceivedUris(intent)

        categorySelectorFragment.prepare(categories, this)
        supportFragmentManager.beginTransaction()
            .add(categorySelectorFragment, null)
            .commit()
    }

    private fun isActionAllow(action: String): Boolean {
        when (action) {
            Intent.ACTION_SEND, Intent.ACTION_SEND_MULTIPLE -> return true
        }
        return false
    }

    private lateinit var pendingText: String
    private lateinit var pendingUris: ArrayList<Uri>
    private lateinit var pendingType: String

    override fun onSelected(index: Int, category: Category) {
        if (pendingText.isEmpty() && pendingUris.isEmpty()) {
            Toast.makeText(this, "Empty item, abort", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        Log.v(TAG, "selected category: ${category.title}")

        val recvNote = NoteItem(pendingText)
        recvNote.categoryId = category.id
        for (uri in pendingUris) {
            val imageFile = extractAttachmentFromUri(uri)
            if (imageFile == null) {
                Log.e(this.toString(), "Failed to extract attachment from uri: $uri")
                continue
            }
            recvNote.attachments.add(imageFile)
        }

        if (recvNote.attachments.size != pendingUris.size) {
            val message = "Failed to create note, Some or all shared files cannot be saved"
            Log.e(TAG, message + ", ${recvNote.attachments.size} / ${pendingUris.size}")
            Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
        } else if (recvNote.attachments.isEmpty() && recvNote.text.isEmpty()) {
            val message = "Failed to create note, no available content"
            Log.e(TAG, message)
            Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
        } else {
            app.service?.queueTask(createNote(recvNote))
        }
        finish()
    }

    private fun handleTextInfo(intent: Intent): String {
        val text = intent.getStringExtra(Intent.EXTRA_TEXT)
        if (text == null) {
            Log.e(this.toString(), "Failed to get shared text")
            return ""
        }
        return text.trim { it <= ' ' }
    }

    private fun handleReceivedUris(intent: Intent): ArrayList<Uri> {
        return when (intent.action) {
            Intent.ACTION_SEND -> {
                val uris = ArrayList<Uri>()
                val uri = intent.getParcelableExtra(
                    Intent.EXTRA_STREAM,
                    Uri::class.java)
                if (uri != null) uris.add(uri)
                uris
            }
            Intent.ACTION_SEND_MULTIPLE -> {
                intent.getParcelableArrayListExtra(
                    Intent.EXTRA_STREAM,
                    Uri::class.java)?: ArrayList()
            }
            else -> error("action not allowed: ${intent.action}, should not reach here")
        }
    }

    private fun extractAttachmentFromUri(uri: Uri): AttachmentFile? {
        val folder = FileHelper.attachmentStorage
        val md5file = saveUriToFile(this, uri, folder)
        if (md5file == null) {
            Log.e(this.toString(), "Failed to save uri to file: $uri")
            return null
        }
        val fileName = getFileNameFromUri(this, uri)
        val mimeType = FileHelper.mimeTypeFromFileName(fileName)
        val attachmentType = if (mimeType.startsWith("image/") ||
            mimeType.startsWith("video/")) {
            Type.MEDIA
        } else {
            Type.FILE
        }

        return AttachmentFile(md5file, fileName, attachmentType)
    }

    companion object {
        private const val TAG = "ShareRecvActivity"
    }

}