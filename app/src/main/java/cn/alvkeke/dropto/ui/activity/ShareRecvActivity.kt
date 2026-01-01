package cn.alvkeke.dropto.ui.activity

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import cn.alvkeke.dropto.DroptoApplication
import cn.alvkeke.dropto.data.Category
import cn.alvkeke.dropto.data.AttachmentFile
import cn.alvkeke.dropto.data.AttachmentFile.Type
import cn.alvkeke.dropto.data.NoteItem
import cn.alvkeke.dropto.mgmt.Global.getFolderImage
import cn.alvkeke.dropto.service.Task
import cn.alvkeke.dropto.service.Task.Companion.createNote
import cn.alvkeke.dropto.service.Task.Companion.jobToNotify
import cn.alvkeke.dropto.service.Task.ResultListener
import cn.alvkeke.dropto.storage.DataLoader.categories
import cn.alvkeke.dropto.storage.FileHelper.getFileNameFromUri
import cn.alvkeke.dropto.storage.FileHelper.saveUriToFile
import cn.alvkeke.dropto.ui.fragment.CategorySelectorFragment

class ShareRecvActivity : AppCompatActivity(), CategorySelectorFragment.CategorySelectListener,
    ResultListener {

    private val app: DroptoApplication
        get() = application as DroptoApplication

    private lateinit var categorySelectorFragment: CategorySelectorFragment

    override fun onStart() {
        super.onStart()
        app.addTaskListener(this)
    }

    override fun onStop() {
        super.onStop()
        app.delTaskListener(this)
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
        if (type != null) {
            if (type.startsWith("image/")) {
                pendingUris = handleImageUris(intent)
            }
        }

        categorySelectorFragment = CategorySelectorFragment()
        categorySelectorFragment.setCategories(categories)
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

    private var pendingText: String? = null
    private var pendingUris: ArrayList<Uri>? = null
    private fun noPendingUris(): Boolean {
        return pendingUris == null || pendingUris!!.isEmpty()
    }

    override fun onSelected(index: Int, category: Category) {
        if (pendingText!!.isEmpty() && noPendingUris()) {
            Toast.makeText(this, "Empty item, abort", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        val recvNote = NoteItem(pendingText!!)
        recvNote.categoryId = category.id
        if (pendingUris != null) {
            for (uri in pendingUris) {
                val imageFile = extraImageFileFromUri(uri)
                if (imageFile != null) recvNote.attachments.add(imageFile)
            }
        }
        app.service?.queueTask(createNote(recvNote))
        finish()
    }

    override fun onError(error: String) {
        Toast.makeText(this@ShareRecvActivity, error, Toast.LENGTH_SHORT).show()
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

    private fun handleImageUris(intent: Intent): ArrayList<Uri>? {
        val uris = ArrayList<Uri>()
        val action = intent.action ?: return null

        when (action) {
            Intent.ACTION_SEND -> uris.add(handleSingleImageUri(intent)!!)
            Intent.ACTION_SEND_MULTIPLE -> handleMultipleImageUris(intent, uris)
        }

        return uris
    }

    private fun handleSingleImageUri(intent: Intent): Uri? {
        return intent.getParcelableExtra(Intent.EXTRA_STREAM, Uri::class.java)
    }

    private fun handleMultipleImageUris(intent: Intent, uris: ArrayList<Uri>) {
        val imageUris = intent.getParcelableArrayListExtra(Intent.EXTRA_STREAM, Uri::class.java)
        if (imageUris == null || imageUris.isEmpty()) return
        uris.addAll(imageUris)
    }

    private fun extraImageFileFromUri(uri: Uri): AttachmentFile? {
        val folder = getFolderImage(this)
        val md5file = saveUriToFile(this, uri, folder) ?: return null
        val imgName = getFileNameFromUri(this, uri)
        // FIXME: need to fix this with correct type
        return AttachmentFile(md5file, imgName!!, Type.IMAGE)
    }

    private fun onCategoryTaskFinish(task: Task) {
        if (task.result < 0) return
        when (task.job) {
            Task.Job.CREATE, Task.Job.REMOVE, Task.Job.UPDATE -> categorySelectorFragment.notifyItemListChanged(
                jobToNotify(task.job), task.result, task.taskObj as Category
            )
        }
    }

    override fun onTaskFinish(task: Task) {
        if (task.type == Task.Type.Category) onCategoryTaskFinish(task)
    }
}