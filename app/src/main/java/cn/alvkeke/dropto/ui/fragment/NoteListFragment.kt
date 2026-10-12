package cn.alvkeke.dropto.ui.fragment

import android.annotation.SuppressLint
import android.app.AlertDialog.Builder
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
import android.widget.Toast
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
import androidx.core.view.doOnPreDraw
import androidx.core.view.size
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.SimpleItemAnimator
import cn.alvkeke.dropto.DroptoApplication
import cn.alvkeke.dropto.R
import cn.alvkeke.dropto.data.AttachmentFile
import cn.alvkeke.dropto.data.Category
import cn.alvkeke.dropto.data.NoteItem
import cn.alvkeke.dropto.service.CoreServiceListener
import cn.alvkeke.dropto.storage.DataBaseHelper
import cn.alvkeke.dropto.storage.DataLoader.categories
import cn.alvkeke.dropto.storage.FileHelper
import cn.alvkeke.dropto.storage.ImageLoader
import cn.alvkeke.dropto.storage.getReactionList
import cn.alvkeke.dropto.ui.UserInterfaceHelper.copyText
import cn.alvkeke.dropto.ui.UserInterfaceHelper.observeSystemBarInsets
import cn.alvkeke.dropto.ui.UserInterfaceHelper.openFileWithExternalApp
import cn.alvkeke.dropto.ui.UserInterfaceHelper.applyPadding
import cn.alvkeke.dropto.ui.UserInterfaceHelper.setBottomMargin
import cn.alvkeke.dropto.ui.UserInterfaceHelper.setTopMargin
import cn.alvkeke.dropto.ui.UserInterfaceHelper.shareAttachmentFileToExternal
import cn.alvkeke.dropto.ui.UserInterfaceHelper.shareFilesToExternal
import cn.alvkeke.dropto.ui.UserInterfaceHelper.showMediaFragment
import cn.alvkeke.dropto.ui.activity.MainViewModel
import cn.alvkeke.dropto.ui.adapter.NoteListAdapter
import cn.alvkeke.dropto.ui.component.CapsuleInputBox
import cn.alvkeke.dropto.ui.component.NoteItemView
import cn.alvkeke.dropto.ui.component.PopupAttachmentCard
import cn.alvkeke.dropto.ui.component.PopupMenu
import cn.alvkeke.dropto.ui.component.ReactionDialog
import cn.alvkeke.dropto.ui.component.SelectableRecyclerView
import cn.alvkeke.dropto.ui.component.SelectableRecyclerView.SelectListener
import cn.alvkeke.dropto.ui.intf.FragmentOnBackListener
import cn.alvkeke.dropto.ui.intf.HorizontalDragListener
import cn.alvkeke.dropto.ui.listener.OnRecyclerViewTouchListener
import com.google.android.material.appbar.MaterialToolbar
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlin.coroutines.resume


class NoteListFragment : Fragment(), FragmentOnBackListener, CoreServiceListener,
    NoteItemView.EventListener {
    interface EventListener {
        fun onNoteListClose()
    }

    private val app: DroptoApplication
        get() = requireActivity().application as DroptoApplication

    private lateinit var context: Context
    private lateinit var viewModel: MainViewModel
    private lateinit var fragmentParent: View
    private lateinit var capsuleInputBox: CapsuleInputBox
    private lateinit var inputContainer: View
    private lateinit var contentContainer: ConstraintLayout
    private var topInset = 0
    private var navInset = 0
    private lateinit var toolbar: MaterialToolbar
    private lateinit var rlNoteList: SelectableRecyclerView

    private var category: Category? = null
    private var noteItemAdapter: NoteListAdapter = NoteListAdapter()
    private var fillJob: Job? = null
    private var pendingEditNoteId = 0L
    private var draftText: String? = null

    var horizontalDragListener: HorizontalDragListener? = null
    var eventListener: EventListener? = null

    // ------------------------------------------------------------- item touch events
    // the items hit test their own area and report what was touched here: every touch ends
    // up in this class, the list listener only reports the dragging of the empty space

    override fun onSenderClick(note: NoteItem) {
        Builder(context)
            .setTitle("Sender Package")
            .setMessage("Sender: ${note.sender}")
            .setPositiveButton(android.R.string.ok, null)
            .show()
    }

    override fun onBackgroundClick(note: NoteItem, anchorX: Int, anchorY: Int) {
        if (rlNoteList.isSelectMode) {
            rlNoteList.toggleSelectItems(positionOf(note))
            return
        }
        showItemPopMenu(note, anchorX, anchorY)
    }

    override fun onMediaClick(note: NoteItem, file: AttachmentFile) {
        if (rlNoteList.isSelectMode) {
            rlNoteList.toggleSelectItems(positionOf(note))
            return
        }
        this.showMediaFragment(file)
    }

    override fun onMediaLongClick(note: NoteItem, file: AttachmentFile, anchorY: Int) {
        if (rlNoteList.isSelectMode) {
            rlNoteList.toggleSelectItems(positionOf(note))
            return
        }
        showAttachmentCard(note, file, anchorY)
    }

    override fun onFileClick(note: NoteItem, file: AttachmentFile) {
        if (rlNoteList.isSelectMode) {
            rlNoteList.toggleSelectItems(positionOf(note))
            return
        }
        context.openFileWithExternalApp(file)
    }

    override fun onFileLongClick(note: NoteItem, file: AttachmentFile, anchorY: Int) {
        onMediaLongClick(note, file, anchorY)
    }

    override fun onReactionClick(note: NoteItem, reaction: String) {
        if (rlNoteList.isSelectMode) {
            rlNoteList.toggleSelectItems(positionOf(note))
            return
        }
        // only allow to remove the reaction without reaction filter
        if (isFilteringReaction) {
            return
        }
        rlNoteList.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
        removeReaction(note, reaction)
    }

    override fun onReactionLongClick(note: NoteItem, reaction: String) {
        if (rlNoteList.isSelectMode) {
            rlNoteList.toggleSelectItems(positionOf(note))
            return
        }
        if (!isFilteringReaction) {
            setReactionFilter(reaction)
        }
    }

    override fun onItemLongPress(note: NoteItem, anchorY: Int) {
        val itemIndex = positionOf(note)
        if (rlNoteList.isItemSelected(itemIndex)) {
            rlNoteList.unselectItem(itemIndex)
        } else {
            rlNoteList.selectItem(itemIndex)
        }
    }

    override fun onLongPressDrag(currentX: Int, currentY: Int) { }

    override fun onLongPressRelease() { }

    override fun onDragStart(startX: Int, startY: Int): Boolean {
        dragStartX = startX
        dragStartY = startY
        horizontalDragListener?.onDragStart()
        return true
    }

    override fun onDragging(currentX: Int, currentY: Int): Boolean {
        val deltaX = currentX - dragStartX
        horizontalDragListener?.onDragging(deltaX.toFloat())
        return true
    }

    override fun onDragEnd(currentX: Int, currentY: Int, speedX: Float, speedY: Float) {
        val deltaX = currentX - dragStartX
        horizontalDragListener?.onDragEnd(deltaX.toFloat(), speedX)
    }


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

        rlNoteList = view.findViewById(R.id.note_list_listview)
        capsuleInputBox = view.findViewById(R.id.note_list_input_box)
        inputContainer = view.findViewById(R.id.note_list_input_container)
        pendingEditNoteId = savedInstanceState?.getLong(STATE_EDITING_NOTE_ID) ?: 0L
        draftText = savedInstanceState?.getString(STATE_EDITING_DRAFT)
        contentContainer = view.findViewById(R.id.note_list_content_container)
        toolbar = view.findViewById(R.id.note_list_toolbar)

        fun padNoteList() {
            rlNoteList.applyPadding(
                topInset + toolbar.height,
                navInset + inputContainer.height)
        }
        view.observeSystemBarInsets { status, navi ->
            topInset = status
            navInset = navi
            toolbar.setTopMargin(status)
            inputContainer.setBottomMargin(navi)
            padNoteList()
        }
        setIMEViewChange(view)

        toolbar.setNavigationIcon(R.drawable.icon_common_back)
        toolbar.setNavigationOnClickListener {
            if (rlNoteList.isSelectMode) {
                rlNoteList.unSelectAllItems()
            } else if (isFilteringReaction) {
                cancelReactionFilter()
            } else {
                eventListener?.onNoteListClose()
            }
        }
        toolbar.setOnMenuItemClickListener(NoteListMenuListener())
        toolbar.inflateMenu(R.menu.fragment_note_list_toolbar)
        setToolbarMenuBySelectedItems(0)

        val layoutManager = LinearLayoutManager(context)
        layoutManager.setReverseLayout(true)
        rlNoteList.setSelectListener(NoteListSelectListener())

        viewModel.category.observe(viewLifecycleOwner) { category ->
            // a value pushed in from the outside means a list is just opened, the value that is
            // delivered again after a recreation is the one that is already there
            val opening = viewModel.openingCategory
            val sameCategory = this.category?.id == category.id
            this.category = category
            toolbar.setTitle(category.title)
            if (sameCategory) {
                fillJob?.cancel()
                noteItemAdapter.syncList(category.noteItems)
            } else {
                fillNoteList(category.noteItems, async = opening)
            }
            restoreEditingNote(category)
        }

        noteItemAdapter.showDeleted = false
        noteItemAdapter.eventListener = this
        rlNoteList.setAdapter(noteItemAdapter)
        (rlNoteList.itemAnimator as? SimpleItemAnimator)?.supportsChangeAnimations = false
        rlNoteList.setLayoutManager(layoutManager)
        rlNoteList.setOnTouchListener(NoteListTouchListener())

        capsuleInputBox.onCancelClick = View.OnClickListener {
            capsuleInputBox.clearAttachments()
            if (isEditingMode) {
                exitEditMode()
            }
        }
        capsuleInputBox.onSendClick = SendButtonClickListener()
        val attachListener = AttachButtonListener()
        capsuleInputBox.onAttachClick = attachListener
        capsuleInputBox.onAttachLongClick = attachListener
    }

    private fun fillNoteList(notes: List<NoteItem>, async: Boolean = true) {
        fillJob?.cancel()
        noteItemAdapter.clear()
        if (!async) {
            // non-async loading, just load all of them
            noteItemAdapter.append(notes)
            fillJob = viewLifecycleOwner.lifecycleScope.launch(Dispatchers.IO) {
                ImageLoader.prefetchMediaSizes(notes.flatMap { it.medias })
            }
            return
        }
        fillJob = viewLifecycleOwner.lifecycleScope.launch {
            withContext(Dispatchers.IO) {
                ImageLoader.prefetchMediaSizes(notes.flatMap { it.medias })
            }
            var index = 0
            while (index < notes.size) {
                val visible = noteItemAdapter.itemCount
                val built = rlNoteList.childCount
                noteItemAdapter.append(notes.subList(index, ++index))
                if (index >= notes.size || noteItemAdapter.itemCount == visible) continue
                awaitNextFrame()
                if (rlNoteList.childCount <= built) {
                    noteItemAdapter.append(notes.subList(index, notes.size))
                    break
                }
            }
        }
    }

    private suspend fun awaitNextFrame() {
        suspendCancellableCoroutine { fill ->
            rlNoteList.doOnPreDraw { rlNoteList.post { if (fill.isActive) fill.resume(Unit) } }
        }
    }

    override fun onBackPressed(): Boolean {
        if (rlNoteList.isSelectMode) {
            rlNoteList.unSelectAllItems()
        } else if (isFilteringReaction) {
            cancelReactionFilter()
        } else {
            return false
        }
        return true
    }

    private fun positionOf(note: NoteItem): Int {
        return noteItemAdapter.indexOf(note)
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
            if (isFilteringReaction) {
                toolbar.setNavigationIcon(R.drawable.icon_common_cross)
                toolbar.setTitle("Tag: $filteredReaction")
            } else {
                toolbar.setNavigationIcon(R.drawable.icon_common_back)
                toolbar.title = category?.title
            }
        }

        override fun onSelect(index: Int) {
            toolbar.title = if (isFilteringReaction) {
                "Tag: $filteredReaction - ${rlNoteList.selectedCount}"
            } else {
                "${rlNoteList.selectedCount}"
            }
        }
        override fun onUnSelect(index: Int) {
            toolbar.title = if (isFilteringReaction) {
                "Tag: $filteredReaction - ${rlNoteList.selectedCount}"
            } else {
                "${rlNoteList.selectedCount}"
            }
        }
    }

    private var reactionFilter : ((NoteItem) -> Boolean)? = null
    private lateinit var filteredReaction: String
    private val isFilteringReaction: Boolean
        get() = reactionFilter != null
    private fun setReactionFilter(reaction: String) {
        filteredReaction = reaction
        reactionFilter = { e -> e.reactions.contains(filteredReaction) }
        noteItemAdapter.addFilter(reactionFilter!!)
        toolbar.setNavigationIcon(R.drawable.icon_common_cross)
        toolbar.setTitle("Tag: $filteredReaction")
    }
    private fun cancelReactionFilter() {
        reactionFilter?.let {
            noteItemAdapter.removeFilter(it)
        }
        toolbar.setNavigationIcon(R.drawable.icon_common_back)
        toolbar.setTitle(category?.title)
        reactionFilter = null
    }

    private var dragStartX = 0
    private var dragStartY = 0

    private inner class NoteListTouchListener : OnRecyclerViewTouchListener(context) {
        override fun onDragHorizontalStart(v: View, e: MotionEvent) {
            onDragStart(e.rawX.toInt(), e.rawY.toInt())
        }

        override fun onDragHorizontalEnd(
            v: View,
            e: MotionEvent,
            delta: Float,
            speed: Float,
        ): Boolean {
            onDragEnd(e.rawX.toInt(), e.rawY.toInt(), speed, 0f)
            return true
        }

        override fun onDraggingHorizontal(
            v: View,
            e: MotionEvent,
            delta: Float,
        ): Boolean {
            return onDragging(e.rawX.toInt(), e.rawY.toInt())
        }
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
                    naviHei = navInset
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


    private inner class AttachButtonListener : OnClickListener, OnLongClickListener {

        private var imagePicker = registerForActivityResult(
            // not going to limit the number of selected images/videos
        PickMultipleVisualMedia(99)) {
            it.forEach { uri ->
                capsuleInputBox.addAttachment(uri, AttachmentFile.Type.MEDIA)
            }
        }
        private var filePicker = registerForActivityResult(
        ActivityResultContracts.OpenMultipleDocuments()) {
            it.forEach { uri ->
                capsuleInputBox.addAttachment(uri, AttachmentFile.Type.FILE)
            }
        }

        override fun onClick(view: View) {
            imagePicker.launch(PickVisualMediaRequest.Builder()
                .setMediaType(ActivityResultContracts.PickVisualMedia.ImageAndVideo)
                .build())
        }

        override fun onLongClick(view: View): Boolean {
            filePicker.launch(arrayOf("*/*"))
            return true
        }
    }

    private inner class SendButtonClickListener : OnClickListener {
        override fun onClick(v: View) {
            val content = capsuleInputBox.text.trim { it <= ' ' }

            if (capsuleInputBox.attachments.isEmpty() && content.isEmpty()) {
                if (isEditingMode) {
                    // warning empty item after editing
                    v.performHapticFeedback(
                        HapticFeedbackConstants.LONG_PRESS
                    )
                }
                return
            }

            val item = if (isEditingMode) {
                val e = editingItem!!
                e.text = content
                e.attachments.clear()   // clear the previous items, prevent dup attachments
                e
            } else {
                if (category == null) return
                val e = NoteItem(content)
                e.categoryId = category!!.id
                e
            }

            viewLifecycleOwner.lifecycleScope.launch(Dispatchers.IO) {
                capsuleInputBox.attachments.forEach {
                    when (it) {
                        is CapsuleInputBox.AttachmentItem.Attachment -> {
                            item.attachments.add(it.file)
                        }
                        is CapsuleInputBox.AttachmentItem.Unhandled -> {
                            val folder = FileHelper.attachmentStorage
                            val md5file = FileHelper.saveUriToFile(context, it.uri, folder)
                            val imgName = FileHelper.getFileNameFromUri(context, it.uri)
                            val imageFile = AttachmentFile.from(md5file!!, imgName, it.type)
                            item.attachments.add(imageFile)
                        }
                    }
                }

                if (isEditingMode) {
                    requestUpdateNote(item)
                } else {
                    requestCreateNote(item)
                }
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
                    setupEditMode(note)
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
    private fun showItemPopMenu(note: NoteItem, anchorX: Int, anchorY: Int) {
        if (!::popupMenu.isInitialized) {
            popupMenu = PopupMenu(context)
            val menu = MenuBuilder(context)
            requireActivity().menuInflater.inflate(R.menu.item_pop_menu, menu)
            popupMenu.setMenu(menu)
            popupMenu.setMenuListener(menuListener)
            popupMenu.animationStyle = R.style.PopupMenuAnimation
            popupMenu.setOnDismissListener {
                rlNoteList.clearHighLight()
                _reactionView?.dismiss()
            }
        }
        Log.v(TAG, "show popup menu for note-${note.id} at ($anchorX, $anchorY)")
        if (popupMenu.isShowing) popupMenu.dismiss()
        _reactionView?.dismiss()
        noteItemForMenu = note
        val isDeleted = note.isDeleted
        popupMenu.setMenuItemVisible(R.id.item_pop_m_restore, isDeleted)
        popupMenu.setMenuItemVisible(R.id.item_pop_m_delete, !isDeleted)

        val listOnScreen = IntArray(2)
        val listInWindow = IntArray(2)
        rlNoteList.getLocationOnScreen(listOnScreen)
        rlNoteList.getLocationInWindow(listInWindow)
        val windowOffsetX = listOnScreen[0] - listInWindow[0]
        val windowOffsetY = listOnScreen[1] - listInWindow[1]

        val leftLimit = listInWindow[0]
        val topLimit = listInWindow[1]
        val rightLimit = leftLimit + rlNoteList.width
        val bottomLimit = topLimit + rlNoteList.height
        val safeTop = maxOf(topLimit, topInset)
        val safeBottom = bottomLimit - navInset
        val xShow = (anchorX - windowOffsetX - popupMenu.width / 2)
            .coerceIn(leftLimit, (rightLimit - popupMenu.width).coerceAtLeast(leftLimit))
        val yShow = (anchorY - windowOffsetY - popupMenu.height / 3)
            .coerceIn(safeTop, (safeBottom - popupMenu.height).coerceAtLeast(safeTop))

        rlNoteList.highLight(positionOf(note))
        popupMenu.showAtLocation(rlNoteList, Gravity.NO_GRAVITY, xShow, yShow)
        val reactions = DataBaseHelper(context).writableDatabase.use {
            it.getReactionList()
        }
        if (reactions.isEmpty()) return
        reactionView.setReactions(reactions)
        reactionView.onReactionClick = { reaction ->
            if (note.reactions.contains(reaction)) {
                note.reactions.remove(reaction)
            } else {
                note.reactions.add(reaction)
            }
            rlNoteList.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
            app.service?.updateNote(note)
            popupMenu.dismiss()
        }
        val reactionY = (yShow - reactionView.height)
            .takeIf { it >= safeTop }
            ?: (yShow + popupMenu.height)
        reactionView.showAtLocation(rlNoteList, Gravity.NO_GRAVITY, xShow, reactionY)
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
    //TODO: implement a better look for this
    private fun showAttachmentCard(note: NoteItem, attachment: AttachmentFile, anchorY: Int) {
        val screenWidth = context.resources.displayMetrics.widthPixels
        val card = PopupAttachmentCard(attachment, context)
        card.width = screenWidth / 2
        card.height = card.width
        // TODO: set the height to wrap_content, not available now
        val displayX = ((screenWidth - card.width) / 2).coerceAtLeast(0)
        val displayY = anchorY - card.height
        rlNoteList.highLight()
        card.setOnDismissListener {
            rlNoteList.clearHighLight()
        }
        card.setActionListener(object : PopupAttachmentCard.ActionListener {
            override fun onRemove(attachment: AttachmentFile) {
                val isLastAttachment = note.text.isEmpty() &&
                        note.attachments.size == 1 &&
                        note.attachments.contains(attachment)
                if (isLastAttachment) {
                    rlNoteList.performHapticFeedback(HapticFeedbackConstants.REJECT)
                } else {
                    requestRemoveAttachment(note, attachment)
                    card.dismiss()
                }
            }

            override fun onShare(attachment: AttachmentFile) {
                context.shareAttachmentFileToExternal(attachment)
                card.dismiss()
            }

            override fun onOpen(attachment: AttachmentFile) {
                context.openFileWithExternalApp(attachment)
                card.dismiss()
            }

            override fun onSave(attachment: AttachmentFile) {
                trySaveFile(attachment)
                card.dismiss()
            }
        })
        card.showAtLocation(rlNoteList, Gravity.NO_GRAVITY, displayX, displayY)
    }

    private fun trySaveFile(attachment: AttachmentFile) {
        viewLifecycleOwner.lifecycleScope.launch(Dispatchers.IO) {
            val uri = FileHelper.saveFileToDownload(context, attachment.md5file, attachment.name)
            val resultMsg = if (uri != null) {
                "Saved to Downloads: $uri"
            } else {
                "Failed to save file"
            }
            withContext(Dispatchers.Main) {
                if (!isAdded) return@withContext
                Toast.makeText(
                    context, resultMsg, Toast.LENGTH_SHORT
                ).show()
            }
        }
    }

    private fun removeReaction(note: NoteItem, reaction: String) {
        note.reactions.remove(reaction)
        app.service?.updateNote(note)
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
        context.shareFilesToExternal(sb.toString(), typeMain, uris)
    }

    private fun handleNoteShare(item: NoteItem, index: Int = -1) {
        if (index >= 0) {
            val attachment = item.attachments[index]
            val uri = FileHelper.generateShareFile(context, attachment)
            val text = attachment.name
            val mimeType = attachment.mimeType
            context.shareFilesToExternal(text, mimeType, arrayListOf(uri))

            return
        }

        FileHelper.emptyShareFolder()
        val uris = ArrayList<Uri>()
        FileHelper.generateShareFiles(context, item, uris)
        val text = item.text
        val mimeType = item.getAttachmentMimeType()
        context.shareFilesToExternal(text, mimeType, uris)
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

    private val isEditingMode: Boolean
        get() = editingItem != null

    private var editingItem: NoteItem? = null
        set(value) {
            field = value
            capsuleInputBox.editing = value != null
        }
    private fun setupEditMode(note: NoteItem) {
        if (isEditingMode) {
            // allow to re-enter, replace the old one
            capsuleInputBox.clearAttachments()
        } else {
            draftText = capsuleInputBox.text.ifEmpty { null }
        }

        editingItem = note

        note.attachments.forEach { capsuleInputBox.addAttachment(it) }
        capsuleInputBox.setText(note.text)
    }
    private fun exitEditMode() {
        editingItem = null
        // give the text that was there before the editing back, or empty the box
        val draft = draftText
        draftText = null
        if (draft != null) capsuleInputBox.setText(draft) else capsuleInputBox.clearText()
    }

    private var reqPendingItem : NoteItem? = null

    private fun requestCreateNote(item: NoteItem) {
        reqPendingItem = item  // pending item, will be cleared after created successfully
        app.service?.createNote(item)
    }

    private fun requestUpdateNote(item: NoteItem) {
        reqPendingItem = item  // pending item, will be cleared after updated successfully
        app.service?.updateNote(item)
    }

    private fun requestRemoveAttachment(item: NoteItem, attachment: AttachmentFile) {
        item.attachments.remove(attachment)
        requestUpdateNote(item)
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

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        editingItem?.let {
            outState.putLong(STATE_EDITING_NOTE_ID, it.id)
            outState.putString(STATE_EDITING_DRAFT, draftText)
        }
    }

    private fun restoreEditingNote(category: Category) {
        val id = pendingEditNoteId
        if (id == 0L) return
        pendingEditNoteId = 0L
        val note = category.noteItems.find { it.id == id } ?: return
        // the text comes back through the view state, and the draft is kept as it was
        editingItem = note
        note.attachments.forEach { capsuleInputBox.addAttachment(it) }
    }

    override fun onNoteCreated(result: Int, noteItem: NoteItem) {
        noteItemAdapter.add(result, noteItem)
        rlNoteList.smoothScrollToPosition(0)
        if (noteItem == reqPendingItem) {
            reqPendingItem = null
            capsuleInputBox.clearText()
            capsuleInputBox.clearAttachments()
        }
    }

    override fun onNoteUpdated(result: Int, noteItem: NoteItem) {
        if (noteItem == reqPendingItem) {
            reqPendingItem = null
            if (isEditingMode) {
                exitEditMode()
            }
            capsuleInputBox.clearAttachments()
        }
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
        private const val STATE_EDITING_NOTE_ID = "editing_note_id"
        private const val STATE_EDITING_DRAFT = "editing_draft"
    }
}



