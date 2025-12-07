package cn.alvkeke.dropto.ui.activity

import android.content.ClipData
import android.content.ClipboardManager
import android.content.ComponentName
import android.content.DialogInterface
import android.content.Intent
import android.net.Uri
import android.os.Build
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
import cn.alvkeke.dropto.R
import cn.alvkeke.dropto.data.Category
import cn.alvkeke.dropto.data.ImageFile
import cn.alvkeke.dropto.data.ImageFile.Companion.from
import cn.alvkeke.dropto.data.NoteItem
import cn.alvkeke.dropto.debug.DebugFunction.tryExtractResImages
import cn.alvkeke.dropto.mgmt.Global.getFolderImage
import cn.alvkeke.dropto.mgmt.Global.getFolderImageShare
import cn.alvkeke.dropto.service.CoreService
import cn.alvkeke.dropto.service.CoreServiceConnection
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
import cn.alvkeke.dropto.ui.fragment.CategoryDetailFragment
import cn.alvkeke.dropto.ui.fragment.CategoryListFragment
import cn.alvkeke.dropto.ui.fragment.CategorySelectorFragment
import cn.alvkeke.dropto.ui.fragment.ImageViewerFragment
import cn.alvkeke.dropto.ui.fragment.NoteDetailFragment
import cn.alvkeke.dropto.ui.fragment.NoteListFragment
import cn.alvkeke.dropto.ui.intf.CategoryAttemptListener
import cn.alvkeke.dropto.ui.intf.ErrorMessageHandler
import cn.alvkeke.dropto.ui.intf.FragmentOnBackListener
import cn.alvkeke.dropto.ui.intf.NoteAttemptListener
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException
import java.util.LinkedList
import java.util.Random
import java.util.function.Consumer

class MainActivity : AppCompatActivity(), ErrorMessageHandler, ResultListener, NoteAttemptListener,
    CategoryAttemptListener, CategorySelectorFragment.CategorySelectListener {
    private var myService : CoreService? = null
    private val serviceConn: CoreServiceConnection = object : CoreServiceConnection(this) {
        override fun execOnServiceConnected(
            componentName: ComponentName,
            bundleAfterConnected: Bundle?
        ) {
            myService = service
        }

        override fun execOnServiceDisconnected() {
            myService = null
        }
    }

    private fun setupCoreService(savedInstanceState: Bundle?) {
        val serviceIntent = Intent(this, CoreService::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(serviceIntent)
        } else {
            startService(serviceIntent)
        }
        serviceConn.setBundleAfterConnected(savedInstanceState)
        bindService(serviceIntent, serviceConn, BIND_AUTO_CREATE)
    }

    private fun clearCoreService() {
        if (myService == null) return
        unbindService(serviceConn)
    }

    override fun onDestroy() {
        super.onDestroy()
        clearCoreService()
    }

    private var categoryListFragment: CategoryListFragment? = null
    private var noteListFragment: NoteListFragment? = null
    private var imageViewerFragment: ImageViewerFragment? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        this.enableEdgeToEdge()
        setContentView(R.layout.activity_main)

        window.setFlags(
            WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS
        )

        onBackPressedDispatcher.addCallback(this, OnFragmentBackPressed(true))

        setupCoreService(savedInstanceState)

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
        myService!!.queueTask(createCategory(Category("Local(Debug)", Category.Type.LOCAL_CATEGORY)))
        myService!!.queueTask(createCategory(Category("REMOTE USERS", Category.Type.REMOTE_USERS)))
        myService!!.queueTask(
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
                        val imageFile = from(imgFile, imgFile.name)
                        e.addImageFile(imageFile)
                    }
                }
                myService!!.queueTask(createNote(e))
            }
        }).start()
    }

    override fun onAttempt(attempt: CategoryAttemptListener.Attempt, category: Category?) {
        when (attempt) {
            CategoryAttemptListener.Attempt.CREATE -> myService!!.queueTask(createCategory(category!!))
            CategoryAttemptListener.Attempt.REMOVE -> myService!!.queueTask(removeCategory(category!!))
            CategoryAttemptListener.Attempt.UPDATE -> myService!!.queueTask(updateCategory(category!!))
            CategoryAttemptListener.Attempt.SHOW_DETAIL -> handleCategoryDetailShow(category!!)
            CategoryAttemptListener.Attempt.SHOW_CREATE -> showCategoryCreatingDialog()
            CategoryAttemptListener.Attempt.SHOW_EXPAND -> handleCategoryExpand(category!!)
            CategoryAttemptListener.Attempt.DEBUG_ADD_DATA -> AlertDialog.Builder(this)
                .setTitle("Debug function")
                .setMessage("Create Debug data?")
                .setNegativeButton(R.string.string_cancel, null)
                .setPositiveButton(
                    R.string.string_ok
                ) { _: DialogInterface, _: Int -> addDebugData() }
                .create().show()
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

    private fun generateShareFile(imageFile: ImageFile): File? {
        shareFolder = getFolderImageShare(this)

        try {
            val imageName = imageFile.getName()
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
        note.iterateImages().forEachRemaining(Consumer { f: ImageFile ->
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
            getSystemService(CLIPBOARD_SERVICE) as ClipboardManager?
        if (clipboardManager == null) {
            Log.e(this.toString(), "Failed to get ClipboardManager")
            return
        }
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

    private fun handleNoteImageShow(item: NoteItem, imageIndex: Int) {
        if (imageViewerFragment == null) {
            imageViewerFragment = ImageViewerFragment()
        }
        val imageFile = item.getImageAt(imageIndex)
        if (imageFile == null) {
            Log.e(this.toString(), "Failed to get image at index: $imageIndex")
            return
        }
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

    override fun onAttempt(attempt: NoteAttemptListener.Attempt, e: NoteItem) {
        onAttempt(attempt, e, null)
    }

    override fun onAttempt(attempt: NoteAttemptListener.Attempt, e: NoteItem, ext: Any?) {
        when (attempt) {
            NoteAttemptListener.Attempt.REMOVE -> myService!!.queueTask(removeNote(e))
            NoteAttemptListener.Attempt.CREATE -> myService!!.queueTask(createNote(e))
            NoteAttemptListener.Attempt.SHOW_DETAIL -> handleNoteDetailShow(e)
            NoteAttemptListener.Attempt.COPY -> handleNoteCopy(e)
            NoteAttemptListener.Attempt.SHOW_SHARE -> handleNoteShare(e)
            NoteAttemptListener.Attempt.UPDATE -> myService!!.queueTask(updateNote(e))
            NoteAttemptListener.Attempt.SHOW_IMAGE -> {
                val imageIndex = ext as Int
                handleNoteImageShow(e, imageIndex)
            }

            NoteAttemptListener.Attempt.SHOW_FORWARD -> handleNoteForward(e)
        }
    }

    override fun onAttemptBatch(
        attempt: NoteAttemptListener.Attempt,
        noteItems: ArrayList<NoteItem>
    ) {
        when (attempt) {
            NoteAttemptListener.Attempt.REMOVE, NoteAttemptListener.Attempt.CREATE, NoteAttemptListener.Attempt.UPDATE -> {
                val job: Task.Job? = convertAttemptToJob(attempt)
                for (e in noteItems) {
                    myService!!.queueTask(Task.onNoteStorage(job!!, e))
                }
            }

            NoteAttemptListener.Attempt.COPY -> {
                val sb = StringBuilder()
                val listOne: NoteItem = noteItems[noteItems.size - 1]
                for (e in noteItems) {
                    sb.append(e.text)
                    if (e == listOne) continue
                    sb.append("\n")
                }
                handleTextCopy(sb.toString())
            }

            NoteAttemptListener.Attempt.SHOW_SHARE -> if (noteItems.size == 1) handleNoteShare(
                noteItems[0]
            )
            else handleNoteShareMultiple(noteItems)

            else -> Log.e(this.toString(), "This operation is not support batch: $attempt")
        }
    }

    override fun onSelected(index: Int, category: Category) {
        if (pendingForwardNote == null) return
        val item = pendingForwardNote!!.clone()
        item.categoryId = category.id
        myService!!.queueTask(createNote(item))
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

        private fun convertAttemptToJob(attempt: NoteAttemptListener.Attempt): Task.Job? {
            return when (attempt) {
                NoteAttemptListener.Attempt.REMOVE -> Task.Job.REMOVE
                NoteAttemptListener.Attempt.UPDATE -> Task.Job.UPDATE
                NoteAttemptListener.Attempt.CREATE -> Task.Job.CREATE
                else -> null
            }
        }
    }
}