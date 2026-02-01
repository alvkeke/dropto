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
import androidx.lifecycle.ViewModelProvider
import cn.alvkeke.dropto.DroptoApplication
import cn.alvkeke.dropto.R
import cn.alvkeke.dropto.data.AttachmentFile
import cn.alvkeke.dropto.data.AttachmentFile.Companion.from
import cn.alvkeke.dropto.data.AttachmentFile.Type
import cn.alvkeke.dropto.data.Category
import cn.alvkeke.dropto.data.NoteItem
import cn.alvkeke.dropto.debug.DebugFunction.tryExtractResImages
import cn.alvkeke.dropto.mgmt.Global
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
import cn.alvkeke.dropto.storage.DataLoader
import cn.alvkeke.dropto.storage.DataLoader.categories
import cn.alvkeke.dropto.storage.DataLoader.loadCategories
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

    private var _categoryListFragment: CategoryListFragment? = null
    private var categoryListFragment: CategoryListFragment
        get() {
            if (_categoryListFragment == null) {
                Log.e(TAG, "Creating new CategoryListFragment instance")
                _categoryListFragment = CategoryListFragment()
            }
            return _categoryListFragment!!
        }
        set(value) {
            _categoryListFragment = value
        }
    private var _noteListFragment: NoteListFragment? = null
    private var noteListFragment: NoteListFragment
        get() {
            if (_noteListFragment == null) {
                _noteListFragment = NoteListFragment()
            }
            return _noteListFragment!!
        }
        set(value) {
            _noteListFragment = value
        }
    private var _noteDetailFragment: NoteDetailFragment? = null
    private var noteDetailFragment: NoteDetailFragment
        get() {
            if (_noteDetailFragment == null) {
                _noteDetailFragment = NoteDetailFragment()
            }
            return _noteDetailFragment!!
        }
        set(value) {
            _noteDetailFragment = value
        }
    private var _imageViewerFragment: ImageViewerFragment? = null
    private var imageViewerFragment: ImageViewerFragment
        get() {
            if (_imageViewerFragment == null) {
                _imageViewerFragment = ImageViewerFragment()
            }
            return _imageViewerFragment!!
        }
        set(value) {
            _imageViewerFragment = value
        }

    // 修正 ViewModel 获取方式，保证与 Fragment 共享同一个 ViewModel
    private val viewModel: MainViewModel by lazy {
        ViewModelProvider(this)[MainViewModel::class.java]
    }

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
        setContentView(R.layout.activity_main)

        window.setFlags(
            WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS
        )

        onBackPressedDispatcher.addCallback(this, OnFragmentBackPressed(true))

        for (f in supportFragmentManager.fragments) {
            when (f) {
                is CategoryListFragment -> categoryListFragment = f
                is NoteListFragment -> noteListFragment = f
                is NoteDetailFragment -> noteDetailFragment = f
                is ImageViewerFragment -> imageViewerFragment = f
            }
        }

        if (!categoryListFragment.isAdded) {
            startFragment(categoryListFragment)
        }

        // FIXME: categoryListFragment doesn't display correctly after changing the dark/light mode
        if (savedInstanceState == null) {
            Log.e(TAG, "onCreate: fresh start")
            val categories: ArrayList<Category> = loadCategories(this)
            viewModel.setCategoriesList(categories)
        } else {
            Log.e(TAG, "onCreate: restore from savedInstanceState")
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
    }

    private val currentFragments = LinkedList<Fragment>()
    private fun startFragment(fragment: Fragment) {
        currentFragments.push(fragment)
        supportFragmentManager.beginTransaction()
            .add(R.id.main_container, fragment, null)
            .addToBackStack(fragment.javaClass.simpleName)
            .commit()
    }

    private fun startFragmentAnime(fragment: Fragment) {
        currentFragments.push(fragment)
        supportFragmentManager.beginTransaction()
            .setCustomAnimations(R.anim.slide_in, R.anim.slide_out)
            .add(R.id.main_container, fragment, null)
            .addToBackStack(fragment.javaClass.simpleName)
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
        val ret = DataLoader.loadCategoryNotes(this, category)
        if (!ret) {
            Log.e(this.toString(), "Failed to get noteList from database")
        }
        viewModel.setCategory(category)
        startFragmentAnime(noteListFragment)
    }

    fun showCategoryCreatingDialog() {
        supportFragmentManager.beginTransaction()
            .add(CategoryDetailFragment(null), null)
            .commit()
    }

    private fun handleCategoryDetailShow(c: Category) {
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
                        val imageFile = from(imgFile, imgFile.name, Type.MEDIA)
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

    private fun triggerShare(text: String, type: String, uris: ArrayList<Uri>) {
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

    private fun handleNoteShareMultiple(items: ArrayList<NoteItem>) {
        emptyShareFolder()
        val uris = ArrayList<Uri>()
        val sb = StringBuilder()
        var typeMain: String? = null
        for (e in items) {
            generateShareFileAndUriForNote(e, uris)
            if (e.text.isNotEmpty()) {
                sb.append(e.text)
                sb.append('\n')
            }
            val type = e.getAttachmentMimeType().substringBefore('/')
            if (typeMain == null) {
                typeMain = type
                continue
            }

            if (typeMain != type) {
                typeMain = "*"
            }
        }
        typeMain += "/*"
        triggerShare(sb.toString(), typeMain, uris)
    }

    private fun handleNoteShare(item: NoteItem) {
        emptyShareFolder()
        val uris = ArrayList<Uri>()
        generateShareFileAndUriForNote(item, uris)
        val text = item.text
        val mimeType = item.getAttachmentMimeType()
        triggerShare(text, mimeType, uris)
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
        viewModel.setNoteItem(item)
        supportFragmentManager.beginTransaction()
            .add(noteDetailFragment, null)
            .commit()
    }

    private fun handleNoteFileOpen(file: AttachmentFile) {
        val uri = getUriForFile(file.md5file)
        val mimeType = Global.mimeTypeFromFileName(file.name)
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

    private fun handleNoteMediaShow(mediaFile: AttachmentFile) {
        if (mediaFile.isVideo) {
            // FIXME: here is a workaround for video playback, open it with external player now
            // need to implement a unified media viewer later
            handleNoteFileOpen(mediaFile)
        } else {
            viewModel.setImageFile(mediaFile.md5file)
            imageViewerFragment.show(supportFragmentManager, null)
        }
    }

    private val pendingForwardNotes: ArrayList<NoteItem> = ArrayList()
    private fun handleNoteForward(note: NoteItem) {
        pendingForwardNotes.add(note)
        val forwardFragment = CategorySelectorFragment()
        forwardFragment.setCategories(categories)
        forwardFragment.show(supportFragmentManager, null)
    }
    private fun handleMultipleNoteForward(notes: ArrayList<NoteItem>) {
        pendingForwardNotes.addAll(notes)
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
            NoteUIAttemptListener.Attempt.SHOW_MEDIA -> {
                val attachment = e.attachments[index]
                if (attachment.type != Type.MEDIA) {
                    Log.e(TAG, "Try SHOW_MEDIA on a non-media attachment")
                    Toast.makeText(this,
                        "Failed to open media, wrong type",
                        Toast.LENGTH_SHORT).show()
                    return
                }
                handleNoteMediaShow(attachment)
            }
            NoteUIAttemptListener.Attempt.OPEN_FILE ->
                handleNoteFileOpen(e.attachments[index])
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

            NoteUIAttemptListener.Attempt.SHOW_FORWARD -> {
                handleMultipleNoteForward(noteItems)
            }

            else -> Log.e(TAG, "unsupported batch UI attempt: $attempt")
        }
    }

    override fun onSelected(index: Int, category: Category) {
        Log.d(TAG, "Category-$index selected for forwarding: ${category.title}, pending notes: ${pendingForwardNotes.size}")
        if (pendingForwardNotes.isEmpty()) return
        for (note in pendingForwardNotes) {
            val item = note.clone()
            item.categoryId = category.id
            app.service?.queueTask(createNote(item))
        }
        pendingForwardNotes.clear()
    }

    override fun onError(error: String) {
        Toast.makeText(this, error, Toast.LENGTH_SHORT).show()
    }

    private fun onCategoryTaskFinish(task: Task) {
        if (task.result < 0) return
        when (task.job) {
            Task.Job.CREATE, Task.Job.REMOVE, Task.Job.UPDATE -> {
                categoryListFragment.notifyItemListChanged(
                    jobToNotify(task.job),
                    task.result,
                    task.taskObj as Category
                )
            }
        }
    }

    private fun onNoteTaskFinish(task: Task) {
        val n = task.taskObj as NoteItem
        Log.d(TAG, "Note task finished: ${task.job} for note id ${n.id}")
        val index = task.result
        if (index < 0) return
        noteListFragment.notifyItemListChanged(jobToNotify(task.job), index, n)
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
