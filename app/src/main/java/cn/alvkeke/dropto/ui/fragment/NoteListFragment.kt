package cn.alvkeke.dropto.ui.fragment

import android.animation.ObjectAnimator
import android.animation.ValueAnimator
import android.annotation.SuppressLint
import android.app.AlertDialog.*
import android.content.Context
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.view.Gravity
import android.view.HapticFeedbackConstants
import android.view.LayoutInflater
import android.view.MenuItem
import android.view.MotionEvent
import android.view.View
import android.view.View.OnClickListener
import android.view.View.OnLongClickListener
import android.view.ViewGroup
import android.view.ViewGroup.MarginLayoutParams
import android.widget.EditText
import android.widget.ImageButton
import android.widget.Toast
import androidx.activity.result.ActivityResultCallback
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.result.contract.ActivityResultContracts.PickMultipleVisualMedia
import androidx.appcompat.view.menu.MenuBuilder
import androidx.appcompat.widget.Toolbar
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsAnimationCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.get
import androidx.core.view.size
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import cn.alvkeke.dropto.DroptoApplication
import cn.alvkeke.dropto.R
import cn.alvkeke.dropto.data.AttachmentFile
import cn.alvkeke.dropto.data.Category
import cn.alvkeke.dropto.data.NoteItem
import cn.alvkeke.dropto.service.CoreServiceListener
import cn.alvkeke.dropto.storage.DataBaseHelper
import cn.alvkeke.dropto.storage.DataLoader.categories
import cn.alvkeke.dropto.storage.FileHelper
import cn.alvkeke.dropto.storage.getReactionList
import cn.alvkeke.dropto.ui.UserInterfaceHelper
import cn.alvkeke.dropto.ui.UserInterfaceHelper.animateRemoveFromParent
import cn.alvkeke.dropto.ui.UserInterfaceHelper.copyText
import cn.alvkeke.dropto.ui.UserInterfaceHelper.openFileWithExternalApp
import cn.alvkeke.dropto.ui.UserInterfaceHelper.shareFileToExternal
import cn.alvkeke.dropto.ui.UserInterfaceHelper.showMediaFragment
import cn.alvkeke.dropto.ui.UserInterfaceHelper.showNoteDetailFragment
import cn.alvkeke.dropto.ui.activity.MainViewModel
import cn.alvkeke.dropto.ui.adapter.NoteListAdapter
import cn.alvkeke.dropto.ui.comonent.CountableImageButton
import cn.alvkeke.dropto.ui.comonent.NoteItemView
import cn.alvkeke.dropto.ui.comonent.PopupMenu
import cn.alvkeke.dropto.ui.comonent.ReactionDialog
import cn.alvkeke.dropto.ui.comonent.SelectableRecyclerView
import cn.alvkeke.dropto.ui.comonent.SelectableRecyclerView.SelectListener
import cn.alvkeke.dropto.ui.intf.FragmentOnBackListener
import cn.alvkeke.dropto.ui.listener.OnRecyclerViewTouchListener
import com.google.android.material.appbar.MaterialToolbar
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlin.math.abs


class NoteListFragment : Fragment(), FragmentOnBackListener, CoreServiceListener {
    private val app: DroptoApplication
        get() = requireActivity().application as DroptoApplication

    private lateinit var context: Context
    private lateinit var viewModel: MainViewModel
    private lateinit var fragmentParent: View
    private lateinit var fragmentView: View
    private lateinit var etInputText: EditText
    private lateinit var btnAttachImage: CountableImageButton
    private lateinit var btnAttachFile: CountableImageButton
    private val btnAttachList: ArrayList<CountableImageButton> = ArrayList()
    private lateinit var contentContainer: ConstraintLayout
    private lateinit var naviBar: View
    private lateinit var toolbar: MaterialToolbar
    private lateinit var rlNoteList: SelectableRecyclerView

    private lateinit var category: Category
    private var noteItemAdapter: NoteListAdapter = NoteListAdapter()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        fragmentParent = inflater.inflate(R.layout.fragment_note_list, container, false)
        return fragmentParent
    }

    override fun onStart() {
        super.onStart()
        app.addTaskListener(this)
    }

    override fun onStop() {
        app.delTaskListener(this)
        super.onStop()
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        context = requireContext()
        viewModel = ViewModelProvider(requireActivity())[MainViewModel::class.java]

        fragmentView = view.findViewById(R.id.note_list_fragment_container)
        rlNoteList = view.findViewById(R.id.note_list_listview)
        val btnAddNote = view.findViewById<ImageButton>(R.id.note_list_input_button)
        btnAttachImage = view.findViewById(R.id.note_list_input_attach_image)
        btnAttachFile = view.findViewById(R.id.note_list_input_attach_file)
        etInputText = view.findViewById(R.id.note_list_input_box)
        contentContainer = view.findViewById(R.id.note_list_content_container)
        val statusBar = view.findViewById<View>(R.id.note_list_status_bar)
        naviBar = view.findViewById(R.id.note_list_navigation_bar)
        toolbar = view.findViewById(R.id.note_list_toolbar)

        UserInterfaceHelper.setSystemBarHeight(view, statusBar, naviBar)
        setIMEViewChange(view)

        toolbar.setNavigationIcon(R.drawable.icon_common_back)
        toolbar.setNavigationOnClickListener(OnNavigationIconClick())
        toolbar.setOnMenuItemClickListener(NoteListMenuListener())
        toolbar.inflateMenu(R.menu.fragment_note_list_toolbar)
        setToolbarMenuBySelectedItems(0)

        val layoutManager = LinearLayoutManager(context)
        layoutManager.setReverseLayout(true)
        rlNoteList.setSelectListener(NoteListSelectListener())

        viewModel.category.observe(viewLifecycleOwner) { category ->
            this.category = category
            toolbar.setTitle(category.title)
            noteItemAdapter.setList(category.noteItems)
        }

        rlNoteList.setAdapter(noteItemAdapter)
        rlNoteList.setLayoutManager(layoutManager)

        btnAddNote.setOnClickListener(OnItemAddClick())
        btnAttachList.add(btnAttachFile)
        btnAttachList.add(btnAttachImage)
        val btnAttachImageCallback = CountableButtonCallback(btnAttachImage)
        val btnAttachFileCallback = CountableButtonCallback(btnAttachFile)
        btnAttachImage.setOnClickListener(btnAttachImageCallback)
        btnAttachImage.setOnLongClickListener(btnAttachImageCallback)
        btnAttachFile.setOnClickListener(btnAttachFileCallback)
        btnAttachFile.setOnLongClickListener(btnAttachFileCallback)
        rlNoteList.setOnTouchListener(NoteListTouchListener())
    }

    override fun onBackPressed(): Boolean {
        if (rlNoteList.isSelectMode) {
            rlNoteList.unSelectAllItems()
        } else {
            finish()
        }
        return true
    }

    private inner class OnNavigationIconClick : OnClickListener {
        override fun onClick(view: View) {
            onBackPressed()
            // TODO: pop the fragment from stack instead of finish directly
        }
    }

    private fun getSelectedNoteItems(): ArrayList<NoteItem> {
        val items = ArrayList<NoteItem>()
        for (i in rlNoteList.selectedIndexes) {
            items.add(noteItemAdapter.get(i))
        }
        return items
    }

    private fun handleMenuDelete() {
        val items: ArrayList<NoteItem> = getSelectedNoteItems()
        rlNoteList.unSelectAllItems()
        requestRemoveNotes(items)
    }

    private fun handleMenuCopy() {
        val items: ArrayList<NoteItem> = getSelectedNoteItems()
        rlNoteList.unSelectAllItems()
        handleCopyMultipleNotes(items)
    }

    private fun handleMenuShare() {
        val items: ArrayList<NoteItem> = getSelectedNoteItems()
        rlNoteList.unSelectAllItems()
        handleShareMultipleNotes(items)
    }

    private fun handleMenuForward() {
        val items: ArrayList<NoteItem> = getSelectedNoteItems()
        rlNoteList.unSelectAllItems()
        handleForwardMultipleNotes(items)
    }

    private inner class NoteListMenuListener : Toolbar.OnMenuItemClickListener {
        override fun onMenuItemClick(item: MenuItem): Boolean {
            when (item.itemId) {
                R.id.note_list_menu_delete -> handleMenuDelete()
                R.id.note_list_menu_copy -> handleMenuCopy()
                R.id.note_list_menu_share -> handleMenuShare()
                R.id.note_list_menu_forward -> handleMenuForward()
                else -> {
                    Log.e(this.toString(), "Unknown menu id: ${item.itemId}")
                    return false
                }
            }
            return false
        }
    }

    private fun setToolbarMenuBySelectedItems(count: Int) {
        val menu = toolbar.getMenu()

        when (count) {
            0 -> {
                for (i in 0 until menu.size) {
                    val menuItem = menu[i]
                    menuItem.isVisible = false
                }
            }
            else -> {
                for (i in 0 until menu.size) {
                    val menuItem = menu[i]
                    menuItem.isVisible = true
                }
            }
        }
    }

    private inner class NoteListSelectListener : SelectListener {
        override fun onSelectEnter() {
            setToolbarMenuBySelectedItems(rlNoteList.selectedCount)
            toolbar.setNavigationIcon(R.drawable.icon_common_cross)
        }

        override fun onSelectExit() {
            setToolbarMenuBySelectedItems(0)
            toolbar.setNavigationIcon(R.drawable.icon_common_back)
            toolbar.title = category.title
        }

        override fun onSelect(index: Int) {
            toolbar.title = "${rlNoteList.selectedCount}"
        }
        override fun onUnSelect(index: Int) {
            toolbar.title = "${rlNoteList.selectedCount}"
        }
    }

    private inner class NoteListTouchListener : OnRecyclerViewTouchListener(context) {
        override fun onItemClickAt(v: View, index: Int, event: MotionEvent): Boolean {
            if (rlNoteList.isSelectMode) {
                rlNoteList.toggleSelectItems(index)
            } else {
                val x = event.rawX.toInt()
                val y = event.rawY.toInt()

                val itemView = v as NoteItemView
                Log.v(TAG, "clicked item-$index, view index: ${itemView.index}")

                // Translate RecyclerView coordinates to itemView coordinates
                val localX = event.x - itemView.left
                val localY = event.y - itemView.top
                Log.v(TAG, "localX: $localX, localY: $localY")

                val content = itemView.checkClickedContent(localX, localY)

                when (content.type) {
                    NoteItemView.ClickedContent.Type.BACKGROUND -> {
                        showItemPopMenu(index, v, x, y)
                    }
                    NoteItemView.ClickedContent.Type.SENDER_ICON -> {
                        Builder(context)
                            .setTitle("Sender Package")
                            .setMessage("Sender: ${itemView.sender}")
                            .setPositiveButton(android.R.string.ok, null)
                            .show()
                    }

                    NoteItemView.ClickedContent.Type.MEDIA -> {
                        if (itemView.medias.size > NoteItemView.MAX_IMAGE_COUNT &&
                            content.index >= NoteItemView.MAX_IMAGE_COUNT - 1
                        ) {
                            showNoteDetail(index)
                        } else {
                            showMediaView(index, content.index)
                        }
                    }

                    NoteItemView.ClickedContent.Type.FILE -> {
                        if (itemView.files.size > NoteItemView.MAX_FILE_COUNT &&
                            content.index >= itemView.medias.size + NoteItemView.MAX_FILE_COUNT - 1
                        ) {
                            showNoteDetail(index)
                        } else {
                            tryOpenFile(index, content.index)
                        }
                    }

                    NoteItemView.ClickedContent.Type.REACTION -> {
                        v.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
                        removeReaction(index, content.index)
                    }
                }
            }
            return true
        }

        private var moveToSelect = false
        private var firstHoldItem = -1
        private var lastHoldItem = -1
        override fun onItemLongClick(v: View, index: Int): Boolean {
            v.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
            firstHoldItem = index
            lastHoldItem = index
            if (rlNoteList.isItemSelected(index)) {
                moveToSelect = false
                rlNoteList.unselectItem(index)
            } else {
                moveToSelect = true
                rlNoteList.selectItem(index)
            }
            return true
        }

        private fun opFun(index: Int) {
            if (moveToSelect)
                rlNoteList.selectItem(index)
            else
                rlNoteList.unselectItem(index)
        }
        private fun opFunNeg(index: Int) {
            if (moveToSelect)
                rlNoteList.unselectItem(index)
            else
                rlNoteList.selectItem(index)
        }
        override fun onItemLongClickSlideOn(v: View, index: Int): Boolean {
            v.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)

            val lastRange = abs(lastHoldItem - firstHoldItem)
            val curRange = abs(index - firstHoldItem)

            if (curRange > lastRange) {
                opFun(index)
            } else if (curRange < lastRange) {
                opFunNeg(lastHoldItem)
            }

            lastHoldItem = index
            return true
        }

        override fun onDragHorizontalEnd(
            v: View,
            e: MotionEvent,
            delta: Float,
            speed: Float,
        ): Boolean {
            val width = fragmentView.width
            val thresholdExit = width / 3
            if (speed > 2 || delta > thresholdExit) {
                finish()
            } else {
                resetPosition()
            }
            return true
        }

        override fun onDraggingHorizontal(
            v: View,
            e: MotionEvent,
            delta: Float,
        ): Boolean {
            if (delta > 0) {
                moveFragmentView(delta)
            } else {
                moveFragmentView(0f)
            }
            return true
        }
    }

    private fun setMaskTransparent(offsetRatio: Float) {
        fragmentParent.background.alpha = (offsetRatio * 255 / 2).toInt()
    }

    private fun moveUpperFragmentView(offsetRatio: Float) {

        val upperView = parentFragmentManager
                .fragments[parentFragmentManager.fragments.size - 2].view ?: return
        val parWidth = upperView.width.toFloat()
        val totalOffset = (parWidth * 2f / 3f)
        upperView.translationX = -totalOffset * offsetRatio
    }

    private fun moveFragmentView(targetX: Float) {
        val width = fragmentView.width.toFloat()
        val ratio = (width - targetX) / width

        setMaskTransparent(ratio)
        moveUpperFragmentView(ratio)
        fragmentView.translationX = targetX
    }

    @JvmOverloads
    fun finish(duration: Long = 200) {
        animateRemoveFromParent(fragmentView, duration, closeToRight = true) { valueAnimator: ValueAnimator ->
            val width = fragmentView.width.toFloat()
            val deltaX = valueAnimator.animatedValue as Float
            val ratio = (width - deltaX) / width
            setMaskTransparent(ratio)
            moveUpperFragmentView(ratio)
        }
    }

    fun resetPosition() {
        val startX = fragmentView.translationX
        if (startX == 0f) return
        ObjectAnimator.ofFloat(
            fragmentView,
            PROP_NAME, startX, 0f
        ).setDuration(100).start()
    }

    private fun setIMEViewChange(view: View) {
        ViewCompat.setWindowInsetsAnimationCallback(
            view,
            object : WindowInsetsAnimationCompat.Callback(
                DISPATCH_MODE_STOP
            ) {
                private var naviHei = 0
                override fun onPrepare(animation: WindowInsetsAnimationCompat) {
                    super.onPrepare(animation)
                    naviHei = naviBar.height
                }

                override fun onProgress(
                    insets: WindowInsetsCompat,
                    runningAnimations: MutableList<WindowInsetsAnimationCompat>
                ): WindowInsetsCompat {
                    val imeHei = insets.getInsets(WindowInsetsCompat.Type.ime()).bottom
                    val params =
                        contentContainer.layoutParams as MarginLayoutParams
                    if (imeHei > naviHei) {
                        params.bottomMargin = imeHei - naviHei
                        contentContainer.layoutParams = params
                    } else {
                        params.bottomMargin = 0
                        contentContainer.layoutParams = params
                    }
                    return insets
                }
            })
    }

    private class TmpAttachment(val uri: Uri, val type: AttachmentFile.Type)
    private val attachments = ArrayList<TmpAttachment>()
    private fun ArrayList<TmpAttachment>.contains(uri: Uri): Boolean {
        for (att in this) {
            if (att.uri == uri) return true
        }
        return false
    }

    private fun addAttachments(btn: CountableImageButton, uris: List<Uri>) {
        val type = when(btn) {
            btnAttachImage -> AttachmentFile.Type.MEDIA
            btnAttachFile -> AttachmentFile.Type.FILE
            else -> error("Unknown attachment button clicked, not allowed")
        }

        var increasedCount = 0
        for (uri in uris) {
            if (attachments.contains(uri))
                continue
            attachments.add(TmpAttachment(uri, type))
            increasedCount++
        }
        btn.count += increasedCount
    }

    private fun clearAttachments() {
        attachments.clear()
        for (btn in btnAttachList) {
            btn.count = 0
        }
    }

    private inner class CountableButtonCallback(private val btn: CountableImageButton) :
        ActivityResultCallback<List<Uri>>, OnClickListener, OnLongClickListener {

        private var imagePicker = registerForActivityResult(
            // not going to limit the number of selected images/videos
        PickMultipleVisualMedia(99), this )
        private var filePicker = registerForActivityResult(
        ActivityResultContracts.OpenMultipleDocuments(), this)

        override fun onActivityResult(result: List<Uri>) {
            if (result.isEmpty()) {
                Log.d(TAG, "No attachment is selected")
                return
            }
            // only try to update UI if the list is not empty
            addAttachments(btn, result)
        }

        override fun onClick(view: View) {
            when (btn) {
                btnAttachImage -> imagePicker.launch(PickVisualMediaRequest.Builder()
                            .setMediaType(ActivityResultContracts.PickVisualMedia.ImageAndVideo)
                            .build())
                btnAttachFile -> filePicker.launch(arrayOf("*/*"))
                else -> error("Unknown attachment button clicked, not allowed")
            }
        }

        override fun onLongClick(view: View): Boolean {
            clearAttachments()
            return true
        }
    }

    private inner class OnItemAddClick : OnClickListener {
        override fun onClick(v: View) {
            val content = etInputText.text.toString().trim { it <= ' ' }
            val item = NoteItem(content)
            item.categoryId = category.id
            viewLifecycleOwner.lifecycleScope.launch(Dispatchers.IO) {
                if (attachments.isEmpty()) {
                    if (content.isEmpty()) return@launch
                } else {
                    for (a in attachments) {
                        val folder = FileHelper.attachmentStorage
                        val md5file = FileHelper.saveUriToFile(context, a.uri, folder)
                        val imgName = FileHelper.getFileNameFromUri(context, a.uri)
                        val imageFile = AttachmentFile.from(md5file!!, imgName, a.type)
                        item.attachments.add(imageFile)
                    }
                }
                requestCreateNote(item)
            }
        }
    }


    private fun throwErrorMessage(msg: String) {
        Log.e(TAG, msg)
        Toast.makeText(
            context,
            msg,
            Toast.LENGTH_SHORT
        ).show()
    }

    private lateinit var noteItemForMenu: NoteItem
    private val menuListener: PopupMenu.MenuListener = object : PopupMenu.MenuListener {
        override fun onMenuItemClick(menuItem: MenuItem) {
            val note = noteItemForMenu
            when (val itemId = menuItem.itemId) {
                R.id.item_pop_m_delete -> {
                    requestRemoveNote(note)
                }

                R.id.item_pop_m_restore -> {
                    requestRestoreNote(note)
                }

                R.id.item_pop_m_pin -> {
                    throwErrorMessage("try to Pin item")
                }

                R.id.item_pop_m_edit -> {
                    showNoteDetailFragment(note)
                }

                R.id.item_pop_m_copy_text -> {
                    handleNoteCopy(note)
                }

                R.id.item_pop_m_share -> {
                    handleNoteShare(note)
                }

                R.id.item_pop_m_forward -> {
                    handleNoteForward(note)
                }

                else -> {
                    throwErrorMessage(
                        "Unknown menu id: " +
                                resources.getResourceEntryName(itemId)
                    )
                }
            }
        }
    }

    private lateinit var popupMenu: PopupMenu
    @SuppressLint("RestrictedApi")
    private fun showItemPopMenu(index: Int, v: View, x: Int, y: Int) {
        val noteItem = noteItemAdapter.get(index)
        if (!::popupMenu.isInitialized) {
            popupMenu = PopupMenu(context)
            val menu = MenuBuilder(context)
            requireActivity().menuInflater.inflate(R.menu.item_pop_menu, menu)
            popupMenu.setMenu(menu)
            popupMenu.setMenuListener(menuListener)
            popupMenu.animationStyle = R.style.PopupMenuAnimation
            popupMenu.setOnDismissListener {
                rlNoteList.clearHighLight()
            }
        }
        Log.v(TAG, "show popup menu for item-$index at x:$x, y:$y")
        noteItemForMenu = noteItem
        val isDeleted = noteItem.isDeleted
        popupMenu.setMenuItemVisible(R.id.item_pop_m_restore, isDeleted)
        popupMenu.setMenuItemVisible(R.id.item_pop_m_delete, !isDeleted)

        val targetTop = y - popupMenu.height / 3
        val targetBottom = targetTop + popupMenu.height
        val xShow = v.width / 5
        var yShow = targetTop

        val location = IntArray(2)
        rlNoteList.getLocationOnScreen(location)
        val topLimit = location[1]
        val bottomLimit = topLimit + rlNoteList.bottom

        if (targetTop < topLimit) {
            yShow = topLimit
        } else if (targetBottom > bottomLimit) {
            yShow = bottomLimit - popupMenu.height
        }

        rlNoteList.highLight(index)
        popupMenu.showAtLocation(rlNoteList, Gravity.NO_GRAVITY, xShow, yShow)
        DataBaseHelper(context).writableDatabase.use {
            reactionView.setReactions(it.getReactionList())
        }
        reactionView.onReactionClick = { reaction ->
            if (noteItem.reactions.contains(reaction)) {
                noteItem.reactions.remove(reaction)
            } else {
                noteItem.reactions.add(reaction)
            }
            v.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
            app.service?.updateNote(noteItem)
            popupMenu.dismiss()
        }
        reactionView.showAtLocation(
            rlNoteList,
            Gravity.NO_GRAVITY,
            xShow,
            yShow - reactionView.height
        )
    }

    private var _reactionView: ReactionDialog? = null
    private val reactionView: ReactionDialog
        get() {
            if (_reactionView == null) {
                _reactionView = ReactionDialog(context).apply {
                    height = 64 * (resources.displayMetrics.density).toInt()
                    setMaxWidth(resources.displayMetrics.widthPixels  / 4)
                    DataBaseHelper(context).readableDatabase.use {
                        reactionList.addAll(it.getReactionList())
                    }
                }
            }
            return _reactionView!!
        }

    private fun showNoteDetail(index: Int) {
        val noteItem = noteItemAdapter.get(index)
        showNoteDetailFragment(noteItem)
    }

    private fun showMediaView(index: Int, attachmentIndex: Int) {
        val noteItem = noteItemAdapter.get(index)
        this.showMediaFragment(noteItem.attachments[attachmentIndex])
    }

    private fun tryOpenFile(index: Int, fileIndex: Int) {
        val noteItem = noteItemAdapter.get(index)
        context.openFileWithExternalApp(noteItem.attachments[fileIndex])
    }

    private fun removeReaction(index: Int, reactionIndex: Int) {
        val noteItem = noteItemAdapter.get(index)
        noteItem.reactions.removeAt(reactionIndex)
        app.service?.updateNote(noteItem)
    }

    private fun handleShareMultipleNotes(items: ArrayList<NoteItem>) {
        FileHelper.emptyShareFolder()
        val uris = ArrayList<Uri>()
        val sb = StringBuilder()
        var typeMain: String? = null
        for (e in items) {
            FileHelper.generateShareFiles(context,e, uris)
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
        context.shareFileToExternal(sb.toString(), typeMain, uris)
    }

    private fun handleNoteShare(item: NoteItem, index: Int = -1) {
        if (index >= 0) {
            val attachment = item.attachments[index]
            val uri = FileHelper.generateShareFile(context, attachment)
            val text = attachment.name
            val mimeType = attachment.mimeType
            context.shareFileToExternal(text, mimeType, arrayListOf(uri))

            return
        }

        FileHelper.emptyShareFolder()
        val uris = ArrayList<Uri>()
        FileHelper.generateShareFiles(context, item, uris)
        val text = item.text
        val mimeType = item.getAttachmentMimeType()
        context.shareFileToExternal(text, mimeType, uris)
    }

    private fun handleNoteCopy(e: NoteItem) {
        context.copyText(e.text)
    }

    private fun handleCopyMultipleNotes(items: ArrayList<NoteItem>) {
        val sb = StringBuilder()
        for (e in items) {
            if (e.text.isNotEmpty()) {
                sb.append(e.text)
                sb.append('\n')
            }
        }
        context.copyText(sb.toString())
    }

    private val forwardFragment by lazy {
        CategorySelectorFragment()
    }
    private fun handleNoteForward(note: NoteItem) {
        forwardFragment.prepare(categories, object : CategorySelectorFragment.CategorySelectListener {
            override fun onSelected(index: Int, category: Category) {
                Log.d(TAG, "Category-$index[${category.title}] selected for forwarding")
                val item = note.clone()
                item.categoryId = category.id
                app.service?.createNote(item)
            }

            override fun onCancel() { }
        })
        forwardFragment.show(parentFragmentManager, null)
    }
    private fun handleForwardMultipleNotes(notes: ArrayList<NoteItem>) {
        forwardFragment.prepare(categories, object : CategorySelectorFragment.CategorySelectListener{
            override fun onSelected(index: Int, category: Category) {
                Log.d(
                    TAG,
                    "Category-$index[${category.title}] selected for forwarding, ${notes.size} notes"
                )
                if (notes.isEmpty()) return
                for (note in notes) {
                    val item = note.clone()
                    item.categoryId = category.id
                    app.service?.createNote(item)
                }
            }
            override fun onCancel() { }
        })
        forwardFragment.show(parentFragmentManager, null)
    }

    private var pendingNoteItem: NoteItem? = null
    private fun setPendingItem(item: NoteItem) {
        pendingNoteItem = item
    }

    private fun clearPendingItem() {
        pendingNoteItem = null
    }

    private fun requestCreateNote(item: NoteItem) {
        setPendingItem(item)
        app.service?.createNote(item)
    }

    private fun requestRemoveNote(item: NoteItem) {
        app.service?.removeNote(item)
    }

    private fun requestRemoveNotes(items: ArrayList<NoteItem>) {
        for (item in items) {
            requestRemoveNote(item)
        }
    }

    private fun requestRestoreNote(item: NoteItem) {
        app.service?.restoreNote(item)
    }

    override fun onNoteCreated(result: Int, noteItem: NoteItem) {
        noteItemAdapter.add(result, noteItem)
        rlNoteList.smoothScrollToPosition(0)
        if (noteItem == pendingNoteItem) {
            clearPendingItem()
            etInputText.text.clear()
            clearAttachments()
        }
    }

    override fun onNoteUpdated(result: Int, noteItem: NoteItem) {
        noteItemAdapter.update(noteItem)
    }

    override fun onNoteRemoved(result: Int, noteItem: NoteItem) {
        if (noteItemAdapter.showDeleted) {
            noteItemAdapter.update(noteItem)
        } else {
            noteItemAdapter.remove(noteItem)
        }
    }

    override fun onNoteRestored(result: Int, noteItem: NoteItem) {
        noteItemAdapter.update(noteItem)
    }

    companion object {
        const val TAG: String = "NoteListFragment"
        private const val PROP_NAME = "translationX"
    }
}