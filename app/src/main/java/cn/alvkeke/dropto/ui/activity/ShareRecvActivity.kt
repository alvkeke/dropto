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

    private lateinit var senderPackageName: String
    private fun Intent.tryGetSenderPackageName(): String {
        callingPackage?.let {
            Log.d(TAG, "get sender package name from callingPackage: $it")
            return it
        }

        callingActivity?.packageName?.let {
            Log.d(TAG, "get sender package name from callingActivity: $it")
            return it
        }

        getPackageFromReferrerUri(referrer)?.let {
            Log.d(TAG, "get sender package name from getReferrer: $it")
            return it
        }

        intent.getStringExtra(Intent.EXTRA_REFERRER_NAME)?.let {
            val fromExtra = getPackageFromReferrerName(it)
            if (fromExtra != null) {
                Log.d(TAG, "get sender package name from EXTRA_REFERRER_NAME: $fromExtra")
                return fromExtra
            }
        }

        intent.getStringExtra(Intent.EXTRA_PACKAGE_NAME)?.let {
            Log.d(TAG, "get sender package name from EXTRA_PACKAGE_NAME: $it")
            return it
        }

        intent.`package`?.let {
            Log.d(TAG, "get sender package name from intent.package: $it")
            return it
        }

        this.getContentAuthorityFromIntent()?.let {
            Log.d(TAG, "get sender content authority from intent: $it")
            return it
        }

        return UNKNOWN_SENDER_PACKAGE
    }

    private fun getPackageFromReferrerUri(referrer: Uri?): String? {
        if (referrer == null) return null
        if (referrer.scheme == "android-app") return referrer.host
        return referrer.authority ?: referrer.host
    }

    private fun getPackageFromReferrerName(referrerName: String): String? {
        if (referrerName.isBlank()) return null
        return try {
            val parsed = Uri.parse(referrerName)
            when (parsed.scheme) {
                null -> referrerName
                "android-app" -> parsed.host
                else -> parsed.authority ?: parsed.host
            }
        } catch (ex: Exception) {
            Log.d(TAG, "Failed to parse EXTRA_REFERRER_NAME: $referrerName", ex)
            referrerName
        }
    }

    private fun Intent.getContentAuthorityFromIntent(): String? {
        val clipData = clipData
        if (clipData != null && clipData.itemCount > 0) {
            val uri = clipData.getItemAt(0)?.uri
            if (uri != null && uri.scheme == "content") return uri.authority
        }

        val streamUri = getParcelableExtra(Intent.EXTRA_STREAM, Uri::class.java)
        if (streamUri != null && streamUri.scheme == "content") return streamUri.authority

        return null
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        this.enableEdgeToEdge()

        val intent = getIntent()
        senderPackageName = intent.tryGetSenderPackageName()
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

        val recvNote = NoteItem(pendingText, senderPackageName)
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
            app.service?.createNote(recvNote)
        }
        finish()
    }

    override fun onCancel() {
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

        const val UNKNOWN_SENDER_PACKAGE = "UNKNOWN"
    }

}