package cn.alvkeke.dropto.ui.activity

import android.content.ClipData
import android.content.ClipboardManager
import android.content.DialogInterface
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.view.WindowManager
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.FileProvider
import androidx.fragment.app.Fragment
import cn.alvkeke.dropto.DroptoApplication
import cn.alvkeke.dropto.R
import cn.alvkeke.dropto.data.Category
import cn.alvkeke.dropto.data.AttachmentFile
import cn.alvkeke.dropto.data.AttachmentFile.Companion.from
import cn.alvkeke.dropto.data.AttachmentFile.Type
import cn.alvkeke.dropto.data.NoteItem
import cn.alvkeke.dropto.debug.DebugFunction.tryExtractResImages
import cn.alvkeke.dropto.mgmt.Global.getFolderImage
import cn.alvkeke.dropto.mgmt.Global.getFolderImageShare
import cn.alvkeke.dropto.service.Task
import cn.alvkeke.dropto.service.Task.Companion.createCategory
import cn.alvkeke.dropto.service.Task.Companion.createNote
import cn.alvkeke.dropto.service.Task.Companion.jobToNotify
import cn.alvkeke.dropto.service.Task.Companion.removeCategory
import cn.alvkeke.dropto.service.Task.Companion.removeNote
import cn.alvkeke.dropto.service.Task.Companion.updateCategory
import cn.alvkeke.dropto.service.Task.Companion.updateNote
import cn.alvkeke.dropto.service.Task.ResultListener
import cn.alvkeke.dropto.storage.DataLoader.categories
import cn.alvkeke.dropto.storage.DataLoader.findCategory
import cn.alvkeke.dropto.storage.DataLoader.loadCategories
import cn.alvkeke.dropto.storage.DataLoader.loadCategoryNotes
import cn.alvkeke.dropto.storage.ImageLoader
import cn.alvkeke.dropto.ui.fragment.CategoryDetailFragment
import cn.alvkeke.dropto.ui.fragment.CategoryListFragment
import cn.alvkeke.dropto.ui.fragment.CategorySelectorFragment
import cn.alvkeke.dropto.ui.fragment.ImageViewerFragment
import cn.alvkeke.dropto.ui.fragment.NoteDetailFragment
import cn.alvkeke.dropto.ui.fragment.NoteListFragment
import cn.alvkeke.dropto.ui.intf.CategoryDBAttemptListener
import cn.alvkeke.dropto.ui.intf.CategoryUIAttemptListener
import cn.alvkeke.dropto.ui.intf.ErrorMessageHandler
import cn.alvkeke.dropto.ui.intf.FragmentOnBackListener
import cn.alvkeke.dropto.ui.intf.NoteDBAttemptListener
import cn.alvkeke.dropto.ui.intf.NoteUIAttemptListener
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException
import java.util.LinkedList
import java.util.Random
import java.util.function.Consumer

class MainActivity : AppCompatActivity(), ErrorMessageHandler, ResultListener,
    NoteDBAttemptListener, NoteUIAttemptListener,
    CategoryDBAttemptListener, CategoryUIAttemptListener,
    CategorySelectorFragment.CategorySelectListener {

    private val app: DroptoApplication
        get() = application as DroptoApplication

    private var categoryListFragment: CategoryListFragment? = null
    private var noteListFragment: NoteListFragment? = null
    private var imageViewerFragment: ImageViewerFragment? = null

    override fun onStart() {
        super.onStart()
        Log.d(this.toString(), "MainActivity onStart")
        app.addTaskListener(this)
    }

    override fun onStop() {
        super.onStop()
        Log.d(this.toString(), "MainActivity onStop")
        app.delTaskListener(this)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Log.d(this.toString(), "MainActivity onCreate")
        this.enableEdgeToEdge()
        setContentView(R.layout.activity_main)

        window.setFlags(
            WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS
        )

        onBackPressedDispatcher.addCallback(this, OnFragmentBackPressed(true))


        val categories: ArrayList<Category> = loadCategories(this)
        if (savedInstanceState != null) {
            val fragments = supportFragmentManager.fragments
            for (f in fragments) {
                when (f) {
                    is CategoryListFragment -> {
                        categoryListFragment = f
                    }

                    is NoteListFragment -> {
                        noteListFragment = f
                        recoverNoteListFragment(savedInstanceState)
                    }

                    is ImageViewerFragment -> {
                        imageViewerFragment = f
                        recoverImageViewFragment(savedInstanceState)
                    }

                    is CategorySelectorFragment -> {
                        f.setCategories(categories)
                    }

                    is NoteDetailFragment -> {
                        recoverNoteDetailFragment(f, savedInstanceState)
                    }

                    is CategoryDetailFragment -> {
                        recoverCategoryDetailFragment(f, savedInstanceState)
                    }

                    else -> {
                        Log.e(this.toString(), "unknown fragment: $f")
                    }
                }
            }
        }

        if (categoryListFragment == null) {
            categoryListFragment = CategoryListFragment()
        }
        categoryListFragment!!.setCategories(categories)
        if (!categoryListFragment!!.isAdded) {
            startFragment(categoryListFragment!!)
        }
    }

    private var savedNoteListCategoryId: Long = SAVED_NOTE_LIST_CATEGORY_ID_NONE
    private fun recoverNoteListFragment(state: Bundle) {
        savedNoteListCategoryId =
            state.getLong(SAVED_NOTE_LIST_CATEGORY_ID, SAVED_NOTE_LIST_CATEGORY_ID_NONE)
        if (savedNoteListCategoryId == SAVED_NOTE_LIST_CATEGORY_ID_NONE) return
        val category = findCategory(savedNoteListCategoryId)
        noteListFragment!!.setCategory(category!!)
    }

    private var savedNoteInfoNoteId: Long = SAVED_NOTE_INFO_NOTE_ID_NONE
    private fun recoverNoteDetailFragment(fragment: NoteDetailFragment, state: Bundle) {
        if (savedNoteListCategoryId == SAVED_NOTE_LIST_CATEGORY_ID_NONE) return
        savedNoteInfoNoteId = state.getLong(SAVED_NOTE_INFO_NOTE_ID, SAVED_NOTE_INFO_NOTE_ID_NONE)
        val category = findCategory(savedNoteListCategoryId)
        if (category == null) {
            fragment.dismiss()
            return
        }
        val item = category.findNoteItem(savedNoteInfoNoteId)
        if (item == null) {
            fragment.dismiss()
            return
        }
        fragment.setNoteItem(item)
    }

    private var savedCategoryDetailId: Long = SAVED_CATEGORY_DETAIL_ID_NONE
    private fun recoverCategoryDetailFragment(fragment: CategoryDetailFragment, state: Bundle) {
        savedCategoryDetailId =
            state.getLong(SAVED_CATEGORY_DETAIL_ID, SAVED_CATEGORY_DETAIL_ID_NONE)
        if (savedCategoryDetailId == SAVED_CATEGORY_DETAIL_ID_NONE) return
        val category = findCategory(savedCategoryDetailId)
        fragment.setCategory(category!!)
    }

    private var savedImageViewFile: String? = null
    private fun recoverImageViewFragment(state: Bundle) {
        savedImageViewFile = state.getString(SAVED_IMAGE_VIEW_FILE)
        if (savedImageViewFile == null) return
        imageViewerFragment!!.setImgFile(File(savedImageViewFile!!))
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putLong(SAVED_NOTE_LIST_CATEGORY_ID, savedNoteListCategoryId)
        outState.putLong(SAVED_NOTE_INFO_NOTE_ID, savedNoteInfoNoteId)
        outState.putLong(SAVED_CATEGORY_DETAIL_ID, savedCategoryDetailId)
        outState.putString(SAVED_IMAGE_VIEW_FILE, savedImageViewFile)
    }

    private val currentFragments = LinkedList<Fragment>()
    private fun startFragment(fragment: Fragment) {
        currentFragments.push(fragment)
        supportFragmentManager.beginTransaction()
            .add(R.id.main_container, fragment, null)
            .addToBackStack(null)
            .commit()
    }

    private fun startFragmentAnime(fragment: Fragment) {
        currentFragments.push(fragment)
        supportFragmentManager.beginTransaction()
            .setCustomAnimations(R.anim.slide_in, R.anim.slide_out)
            .add(R.id.main_container, fragment, null)
            .addToBackStack(null)
            .commit()
    }

    private fun popFragment(): Fragment? {
        var fragment: Fragment?
        while ((currentFragments.pop().also { fragment = it }) != null) {
            if (!fragment!!.isVisible) continue
            return fragment
        }
        return null
    }

    internal inner class OnFragmentBackPressed(enabled: Boolean) : OnBackPressedCallback(enabled) {
        override fun handleOnBackPressed() {
            val fragment = popFragment()
            if (fragment is NoteListFragment) {
                savedNoteListCategoryId = SAVED_NOTE_LIST_CATEGORY_ID_NONE
            }
            var ret = false
            if (fragment is FragmentOnBackListener) {
                ret = (fragment as FragmentOnBackListener).onBackPressed()
            }
            if (!ret) {
                this@MainActivity.finish()
            }
        }
    }

    fun handleCategoryExpand(category: Category) {
        if (noteListFragment == null) {
            noteListFragment = NoteListFragment()
        }
        val ret = loadCategoryNotes(this, category)
        if (!ret) {
            Log.e(this.toString(), "Failed to get noteList from database")
        }
        noteListFragment!!.setCategory(category)
        savedNoteListCategoryId = category.id
        startFragmentAnime(noteListFragment!!)
    }

    fun showCategoryCreatingDialog() {
        savedCategoryDetailId = SAVED_CATEGORY_DETAIL_ID_NONE
        supportFragmentManager.beginTransaction()
            .add(CategoryDetailFragment(null), null)
            .commit()
    }

    private fun handleCategoryDetailShow(c: Category) {
        savedCategoryDetailId = c.id
        val fragment = CategoryDetailFragment(c)
        supportFragmentManager.beginTransaction()
            .add(fragment, null)
            .commit()
    }

    private fun addDebugData() {
        app.service?.queueTask(createCategory(Category("Local(Debug)", Category.Type.LOCAL_CATEGORY)))
        app.service?.queueTask(createCategory(Category("REMOTE USERS", Category.Type.REMOTE_USERS)))
        app.service?.queueTask(
            createCategory(
                Category(
                    "REMOTE SELF DEVICE",
                    Category.Type.REMOTE_SELF_DEV
                )
            )
        )

        Thread(Runnable {
            try {
                Thread.sleep(1000)
            } catch (ex: InterruptedException) {
                Log.e(this.toString(), "failed to sleep: $ex")
            }
            val imgFolder = getFolderImage(this)
            val imgFiles = tryExtractResImages(this, imgFolder) ?: return@Runnable
            val categories: ArrayList<Category> = categories
            if (categories.isEmpty()) return@Runnable

            val r = Random()
            val cateId = categories[r.nextInt(categories.size)].id
            var idx = 0
            for (i in 0..14) {
                val e = NoteItem("ITEM$i$i", System.currentTimeMillis())
                e.categoryId = cateId
                if (r.nextBoolean()) {
                    e.setText(e.text, true)
                }
                if (idx < imgFiles.size && r.nextBoolean()) {
                    val imgFile = imgFiles[idx]
                    idx++
                    if (imgFile.exists()) {
                        val imageFile = from(imgFile, imgFile.name, Type.IMAGE)
                        e.attachments.add(imageFile)
                    }
                }
                app.service?.queueTask(createNote(e))
            }
        }).start()
    }

    override fun onAttempt(attempt: CategoryDBAttemptListener.Attempt, category: Category) {
        when (attempt) {
            CategoryDBAttemptListener.Attempt.CREATE ->
                app.service?.queueTask(createCategory(category))
            CategoryDBAttemptListener.Attempt.REMOVE ->
                app.service?.queueTask(removeCategory(category))
            CategoryDBAttemptListener.Attempt.UPDATE ->
                app.service?.queueTask(updateCategory(category))
        }
    }

    override fun onAttempt(attempt: CategoryUIAttemptListener.Attempt) {
        when (attempt) {
            CategoryUIAttemptListener.Attempt.SHOW_CREATE -> showCategoryCreatingDialog()
            CategoryUIAttemptListener.Attempt.DEBUG_ADD_DATA -> AlertDialog.Builder(this)
                .setTitle("Debug function")
                .setMessage("Create Debug data?")
                .setNegativeButton(R.string.string_cancel, null)
                .setPositiveButton(
                    R.string.string_ok
                ) { _: DialogInterface, _: Int -> addDebugData() }
                .create().show()
            else -> error("Unsupported UI attempt without category: $attempt")
        }
    }

    override fun onAttempt(attempt: CategoryUIAttemptListener.Attempt, category: Category) {
        when (attempt) {
            CategoryUIAttemptListener.Attempt.SHOW_DETAIL -> handleCategoryDetailShow(category)
            CategoryUIAttemptListener.Attempt.SHOW_EXPAND -> handleCategoryExpand(category)
            else -> error("Unsupported UI attempt with category: $attempt")
        }
    }

    private fun getUriForFile(file: File): Uri {
        return FileProvider.getUriForFile(
            this,
            "$packageName.fileprovider", file
        )
    }

    private var shareFolder: File? = null
    private fun emptyShareFolder() {
        if (shareFolder == null) return
        val files = shareFolder!!.listFiles() ?: return
        for (file in files) {
            if (!file.isDirectory) {
                val ret = file.delete()
                Log.d("emptyFolder", "file delete result: $ret")
            }
        }
    }

    private fun generateShareFile(imageFile: AttachmentFile): File? {
        shareFolder = getFolderImageShare(this)

        try {
            val imageName = imageFile.name
            val fileToShare: File?
            if (imageName.isEmpty()) {
                fileToShare = imageFile.md5file
            } else {
                fileToShare = File(shareFolder, imageName)
                copyFile(imageFile.md5file, fileToShare)
            }
            return fileToShare
        } catch (e: IOException) {
            Log.e(this.toString(), "Failed to copy file: $e")
            return null
        }
    }

    private fun generateShareFileAndUriForNote(note: NoteItem, uris: ArrayList<Uri>) {
        note.attachments.iterator().forEachRemaining(Consumer { f: AttachmentFile ->
            val ff = generateShareFile(f)
            val uri = getUriForFile(ff!!)
            uris.add(uri)
        })
    }

    private fun triggerShare(text: String, uris: ArrayList<Uri>) {
        val sendIntent = Intent(Intent.ACTION_SEND)
        val count = uris.size
        if (count == 0) {
            sendIntent.type = "text/plain"
        } else {
            sendIntent.type = "image/*"
            sendIntent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }

        if (count == 1) {
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

    private fun handleNoteShareMultiple(items: ArrayList<NoteItem>) {
        emptyShareFolder()
        val uris = ArrayList<Uri>()
        val sb = StringBuilder()
        for (e in items) {
            generateShareFileAndUriForNote(e, uris)
            val text = e.text
            if (!text.isEmpty()) {
                sb.append(text)
                sb.append('\n')
            }
        }
        triggerShare(sb.toString(), uris)
    }

    private fun handleNoteShare(item: NoteItem) {
        emptyShareFolder()
        val uris = ArrayList<Uri>()
        generateShareFileAndUriForNote(item, uris)
        triggerShare(item.text, uris)
    }

    private fun handleTextCopy(text: String) {
        val clipboardManager =
            getSystemService(CLIPBOARD_SERVICE) as ClipboardManager
        val data = ClipData.newPlainText("text", text)
        clipboardManager.setPrimaryClip(data)
    }

    private fun handleNoteCopy(e: NoteItem) {
        handleTextCopy(e.text)
    }

    private fun handleNoteDetailShow(item: NoteItem) {
        savedNoteInfoNoteId = item.id
        val fragment = NoteDetailFragment()
        fragment.setNoteItem(item)
        supportFragmentManager.beginTransaction()
            .add(fragment, null)
            .commit()
    }

    private fun mimeTypeFromFileName(name: String): String {
        val extension = name.substringAfterLast('.', "")
        return android.webkit.MimeTypeMap.getSingleton()
            .getMimeTypeFromExtension(extension.lowercase()) ?: "*/*"
    }

    private fun handleNoteFileOpen(item: NoteItem, fileIndex: Int) {
        val file = item.files[fileIndex]
        val uri = getUriForFile(file.md5file)
        val mimeType = mimeTypeFromFileName(file.name)
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

    private fun handleNoteImageShow(item: NoteItem, imageIndex: Int) {
        if (imageViewerFragment == null) {
            imageViewerFragment = ImageViewerFragment()
        }
        val imageFile = item.images[imageIndex]
        savedImageViewFile = imageFile.md5file.absolutePath
        imageViewerFragment!!.setImgFile(imageFile.md5file)
        imageViewerFragment!!.show(supportFragmentManager, null)
    }

    private var pendingForwardNote: NoteItem? = null
    private fun handleNoteForward(note: NoteItem) {
        pendingForwardNote = note
        val forwardFragment = CategorySelectorFragment()
        forwardFragment.setCategories(categories)
        forwardFragment.show(supportFragmentManager, null)
    }

    override fun onAttempt(attempt: NoteDBAttemptListener.Attempt, e: NoteItem) {
        when (attempt) {
            NoteDBAttemptListener.Attempt.REMOVE -> app.service?.queueTask(removeNote(e))
            NoteDBAttemptListener.Attempt.CREATE -> app.service?.queueTask(createNote(e))
            NoteDBAttemptListener.Attempt.UPDATE -> app.service?.queueTask(updateNote(e))
        }
    }

    override fun onAttemptBatch(
        attempt: NoteDBAttemptListener.Attempt,
        noteItems: ArrayList<NoteItem>
    ) {
        when (attempt) {
            NoteDBAttemptListener.Attempt.REMOVE,
            NoteDBAttemptListener.Attempt.CREATE,
            NoteDBAttemptListener.Attempt.UPDATE -> {
                val job: Task.Job = convertAttemptToJob(attempt)
                for (e in noteItems) {
                    app.service?.queueTask(Task.onNoteStorage(job, e))
                }
            }
        }
    }

    override fun onAttempt(attempt: NoteUIAttemptListener.Attempt, e: NoteItem) {
        // pass in index -1 to make sure this method is not invoked for UI_SHOW_IMAGE
        onAttempt(attempt, e, -1)
    }

    override fun onAttempt(attempt: NoteUIAttemptListener.Attempt, e: NoteItem, index: Int) {
        when (attempt) {
            NoteUIAttemptListener.Attempt.COPY -> handleNoteCopy(e)
            NoteUIAttemptListener.Attempt.SHOW_DETAIL -> handleNoteDetailShow(e)
            NoteUIAttemptListener.Attempt.SHOW_SHARE -> handleNoteShare(e)
            NoteUIAttemptListener.Attempt.SHOW_FORWARD -> handleNoteForward(e)
            NoteUIAttemptListener.Attempt.SHOW_IMAGE -> handleNoteImageShow(e, index)
            NoteUIAttemptListener.Attempt.OPEN_FILE -> handleNoteFileOpen(e, index)
        }
    }

    override fun onAttemptBatch(
        attempt: NoteUIAttemptListener.Attempt,
        noteItems: ArrayList<NoteItem>
    ) {
        when (attempt) {
            NoteUIAttemptListener.Attempt.COPY -> {
                val sb = StringBuilder()
                val listOne: NoteItem = noteItems[noteItems.size - 1]
                for (e in noteItems) {
                    sb.append(e.text)
                    if (e == listOne) continue
                    sb.append("\n")
                }
                handleTextCopy(sb.toString())
            }

            NoteUIAttemptListener.Attempt.SHOW_SHARE -> {
                if (noteItems.size == 1)
                    handleNoteShare(noteItems[0])
                else
                    handleNoteShareMultiple(noteItems)
            }

            else -> Log.e(this.toString(), "unsupported batch UI attempt: $attempt")
        }
    }

    override fun onSelected(index: Int, category: Category) {
        if (pendingForwardNote == null) return
        val item = pendingForwardNote!!.clone()
        item.categoryId = category.id
        app.service?.queueTask(createNote(item))
    }

    override fun onError(error: String) {
        Toast.makeText(this, error, Toast.LENGTH_SHORT).show()
    }

    private fun onCategoryTaskFinish(task: Task) {
        if (task.result < 0) return
        if (categoryListFragment == null) return
        when (task.job) {
            Task.Job.CREATE, Task.Job.REMOVE, Task.Job.UPDATE -> categoryListFragment!!.notifyItemListChanged(
                jobToNotify(task.job), task.result, task.taskObj as Category
            )
        }
    }

    private fun onNoteTaskFinish(task: Task) {
        val n = task.taskObj as NoteItem
        val index = task.result
        if (n == pendingForwardNote) pendingForwardNote = null
        if (index < 0) return
        if (noteListFragment == null) return
        noteListFragment!!.notifyItemListChanged(jobToNotify(task.job), index, n)
    }

    override fun onTaskFinish(task: Task) {
        when (task.type) {
            Task.Type.Category -> {
                onCategoryTaskFinish(task)
                return
            }

            Task.Type.NoteItem -> onNoteTaskFinish(task)
        }
    }

    companion object {

        const val TAG: String = "MainActivity"

        private const val SAVED_NOTE_LIST_CATEGORY_ID_NONE: Long = -1
        private const val SAVED_NOTE_LIST_CATEGORY_ID = "SAVED_NOTE_LIST_CATEGORY_ID"
        private const val SAVED_NOTE_INFO_NOTE_ID_NONE: Long = -1
        private const val SAVED_NOTE_INFO_NOTE_ID = "SAVED_NOTE_INFO_NOTE_ID"
        private const val SAVED_CATEGORY_DETAIL_ID_NONE: Long = -1
        private const val SAVED_CATEGORY_DETAIL_ID = "SAVED_CATEGORY_DETAIL_ID"
        private const val SAVED_IMAGE_VIEW_FILE = "SAVED_IMAGE_VIEW_FILE"

        @Throws(IOException::class)
        private fun copyFile(src: File, dst: File) {
            val fi = FileInputStream(src)
            val fo = FileOutputStream(dst)
            val buffer = ByteArray(1024)
            var length: Int
            while ((fi.read(buffer).also { length = it }) > 0) {
                fo.write(buffer, 0, length)
            }
            fi.close()
            fo.close()
        }

        private fun convertAttemptToJob(attempt: NoteDBAttemptListener.Attempt): Task.Job {
            return when (attempt) {
                NoteDBAttemptListener.Attempt.REMOVE -> Task.Job.REMOVE
                NoteDBAttemptListener.Attempt.UPDATE -> Task.Job.UPDATE
                NoteDBAttemptListener.Attempt.CREATE -> Task.Job.CREATE
            }
        }
    }
}