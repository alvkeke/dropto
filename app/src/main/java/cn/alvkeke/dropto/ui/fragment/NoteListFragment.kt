package cn.alvkeke.dropto.ui.fragment

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ObjectAnimator
import android.animation.ValueAnimator
import android.annotation.SuppressLint
import android.content.Context
import android.content.DialogInterface
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
import androidx.activity.result.ActivityResultCallback
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.result.contract.ActivityResultContracts.PickMultipleVisualMedia
import androidx.appcompat.app.AlertDialog
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
import cn.alvkeke.dropto.R
import cn.alvkeke.dropto.data.AttachmentFile
import cn.alvkeke.dropto.data.Category
import cn.alvkeke.dropto.data.NoteItem
import cn.alvkeke.dropto.mgmt.Global
import cn.alvkeke.dropto.storage.FileHelper
import cn.alvkeke.dropto.ui.activity.MainViewModel
import cn.alvkeke.dropto.ui.adapter.NoteListAdapter
import cn.alvkeke.dropto.ui.comonent.CountableImageButton
import cn.alvkeke.dropto.ui.comonent.NoteItemView
import cn.alvkeke.dropto.ui.comonent.PopupMenu
import cn.alvkeke.dropto.ui.comonent.SelectableRecyclerView
import cn.alvkeke.dropto.ui.comonent.SelectableRecyclerView.SelectListener
import cn.alvkeke.dropto.ui.intf.ErrorMessageHandler
import cn.alvkeke.dropto.ui.intf.FragmentOnBackListener
import cn.alvkeke.dropto.ui.intf.ListNotification
import cn.alvkeke.dropto.ui.intf.ListNotification.Notify
import cn.alvkeke.dropto.ui.intf.NoteDBAttemptListener
import cn.alvkeke.dropto.ui.intf.NoteUIAttemptListener
import cn.alvkeke.dropto.ui.listener.OnRecyclerViewTouchListener
import com.google.android.material.appbar.MaterialToolbar
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlin.math.abs


class NoteListFragment : Fragment(), ListNotification<NoteItem>, FragmentOnBackListener {
    private lateinit var context: Context
    private lateinit var viewModel: MainViewModel
    private lateinit var dbListener: NoteDBAttemptListener
    private lateinit var uiListener: NoteUIAttemptListener
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

    @SuppressLint("ClickableViewAccessibility")
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        context = requireContext()
        viewModel = ViewModelProvider(requireActivity())[MainViewModel::class.java]
        dbListener = context as NoteDBAttemptListener
        uiListener = context as NoteUIAttemptListener

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

        setSystemBarHeight(view, statusBar, naviBar)
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
            rlNoteList.clearSelectItems()
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

    private fun removeSelectedNotes() {
        val items: ArrayList<NoteItem> = getSelectedNoteItems()
        rlNoteList.clearSelectItems()
        dbListener.onAttemptBatch(NoteDBAttemptListener.Attempt.REMOVE, items)
    }

    private fun handleMenuDelete() {
        AlertDialog.Builder(context)
            .setTitle(R.string.dialog_note_delete_selected_title)
            .setMessage(R.string.dialog_note_delete_selected_message)
            .setNegativeButton(R.string.string_cancel, null)
            .setPositiveButton(
                R.string.string_ok
            ) { _: DialogInterface, _: Int -> removeSelectedNotes() }
            .create().show()
    }

    private fun handleMenuCopy() {
        val items: ArrayList<NoteItem> = getSelectedNoteItems()
        rlNoteList.clearSelectItems()
        uiListener.onAttemptBatch(NoteUIAttemptListener.Attempt.COPY, items)
    }

    private fun handleMenuShare() {
        val items: ArrayList<NoteItem> = getSelectedNoteItems()
        rlNoteList.clearSelectItems()
        uiListener.onAttemptBatch(NoteUIAttemptListener.Attempt.SHOW_SHARE, items)
    }

    private fun handleMenuForward() {
        val items: ArrayList<NoteItem> = getSelectedNoteItems()
        rlNoteList.clearSelectItems()
        uiListener.onAttemptBatch(NoteUIAttemptListener.Attempt.SHOW_FORWARD, items)
    }

    private fun handleMenuDeletedVisible() {
        noteItemAdapter.showDeleted = !noteItemAdapter.showDeleted
        val menuItem = toolbar.menu.findItem(R.id.note_list_menu_deleted_visible)
        menuItem.title = if (noteItemAdapter.showDeleted) {
            resources.getString(R.string.hide_deleted_notes)
        } else {
            resources.getString(R.string.show_deleted_notes)
        }
    }

    private inner class NoteListMenuListener : Toolbar.OnMenuItemClickListener {
        override fun onMenuItemClick(item: MenuItem): Boolean {
            when (item.itemId) {
                R.id.note_list_menu_delete -> handleMenuDelete()
                R.id.note_list_menu_copy -> handleMenuCopy()
                R.id.note_list_menu_share -> handleMenuShare()
                R.id.note_list_menu_forward -> handleMenuForward()
                R.id.note_list_menu_deleted_visible -> handleMenuDeletedVisible()
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
                    menuItem.isVisible = menuItem.itemId == R.id.note_list_menu_deleted_visible
                }
            }
            else -> {
                for (i in 0 until menu.size) {
                    val menuItem = menu[i]
                    menuItem.isVisible = menuItem.itemId != R.id.note_list_menu_deleted_visible
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

        override fun onSlideEnd(
            v: View,
            e: MotionEvent,
            deltaX: Float,
            deltaY: Float,
            speed: Float,
        ): Boolean {
            val width = fragmentView.width
            val thresholdExit = width / 3
            if (speed > 2 || deltaX > thresholdExit) {
                finish()
            } else {
                resetPosition()
            }
            return true
        }

        override fun onSlideOnGoing(
            v: View,
            e: MotionEvent,
            deltaX: Float,
            deltaY: Float
        ): Boolean {
            if (deltaX > 0) {
                moveFragmentView(deltaX)
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
        val startX = fragmentView.translationX
        val width = fragmentView.width.toFloat()
        val animator = ObjectAnimator.ofFloat(
            fragmentView,
            PROP_NAME, startX, width
        ).setDuration(duration)
        animator.addListener(object : AnimatorListenerAdapter() {
            override fun onAnimationEnd(animation: Animator) {
                super.onAnimationEnd(animation)
                rlNoteList.clearSelectItems()
                attachments.clear()
                getParentFragmentManager().beginTransaction()
                    .remove(this@NoteListFragment).commit()
            }
        })
        animator.addUpdateListener { valueAnimator: ValueAnimator ->
            val deltaX = valueAnimator.animatedValue as Float
            val ratio = (width - deltaX) / width
            setMaskTransparent(ratio)
            moveUpperFragmentView(ratio)
        }
        animator.start()
    }

    fun resetPosition() {
        val startX = fragmentView.translationX
        if (startX == 0f) return
        ObjectAnimator.ofFloat(
            fragmentView,
            PROP_NAME, startX, 0f
        ).setDuration(100).start()
    }

    private fun setSystemBarHeight(parent: View, status: View, navi: View) {
        ViewCompat.setOnApplyWindowInsetsListener(
            parent
        ) { _: View, insets: WindowInsetsCompat ->
            val statusHei: Int = insets.getInsets(WindowInsetsCompat.Type.statusBars()).top
            val naviHei: Int = insets.getInsets(WindowInsetsCompat.Type.navigationBars()).bottom
            status.layoutParams.height = statusHei
            navi.layoutParams.height = naviHei
            WindowInsetsCompat.CONSUMED
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
            viewLifecycleOwner.lifecycleScope.launch(SupervisorJob() + Dispatchers.IO) {
                if (attachments.isEmpty()) {
                    if (content.isEmpty()) return@launch
                } else {
                    for (a in attachments) {
                        val folder = Global.attachmentStorage
                        val md5file = FileHelper.saveUriToFile(context, a.uri, folder)
                        val imgName = FileHelper.getFileNameFromUri(context, a.uri)
                        val imageFile = AttachmentFile.from(md5file!!, imgName, a.type)
                        item.attachments.add(imageFile)
                    }
                }
                setPendingItem(item)
                dbListener.onAttempt(NoteDBAttemptListener.Attempt.CREATE, item)
            }
        }
    }

    private fun showNoteDetail(index: Int) {
        val noteItem = noteItemAdapter.get(index)
        uiListener.onAttempt(
            NoteUIAttemptListener.Attempt.SHOW_DETAIL,
            noteItem,
        )
    }

    private fun showMediaView(index: Int, attachmentIndex: Int) {
        val noteItem = noteItemAdapter.get(index)
        uiListener.onAttempt(
            NoteUIAttemptListener.Attempt.SHOW_MEDIA,
            noteItem,
            attachmentIndex
        )
    }

    private fun tryOpenFile(index: Int, fileIndex: Int) {
        val noteItem = noteItemAdapter.get(index)
        uiListener.onAttempt(
            NoteUIAttemptListener.Attempt.OPEN_FILE,
            noteItem,
            fileIndex
        )
    }

    private fun throwErrorMessage(msg: String) {
        if (dbListener !is ErrorMessageHandler) return
        val handler = dbListener as ErrorMessageHandler
        handler.onError(msg)
    }


    private lateinit var noteItemForMenu: NoteItem
    private val menuListener: PopupMenu.MenuListener = object : PopupMenu.MenuListener {
        override fun onMenuItemClick(menuItem: MenuItem) {
            val note = noteItemForMenu
            when (val itemId = menuItem.itemId) {
                R.id.item_pop_m_delete -> {
                    dbListener.onAttempt(
                        NoteDBAttemptListener.Attempt.REMOVE,
                        note
                    )
                }

                R.id.item_pop_m_restore -> {
                    dbListener.onAttempt(
                        NoteDBAttemptListener.Attempt.RESTORE,
                        note
                    )
                }

                R.id.item_pop_m_pin -> {
                    throwErrorMessage("try to Pin item")
                }

                R.id.item_pop_m_edit -> {
                    uiListener.onAttempt(NoteUIAttemptListener.Attempt.SHOW_DETAIL, note)
                }

                R.id.item_pop_m_copy_text -> {
                    uiListener.onAttempt(NoteUIAttemptListener.Attempt.COPY, note)
                }

                R.id.item_pop_m_share -> {
                    uiListener.onAttempt(NoteUIAttemptListener.Attempt.SHOW_SHARE, note)
                }

                R.id.item_pop_m_forward -> {
                    uiListener.onAttempt(NoteUIAttemptListener.Attempt.SHOW_FORWARD, note)
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
    }

    private var pendingNoteItem: NoteItem? = null
    private fun setPendingItem(item: NoteItem) {
        pendingNoteItem = item
    }

    private fun clearPendingItem() {
        pendingNoteItem = null
    }

    override fun notifyItemListChanged(notify: Notify, index: Int, itemObj: NoteItem) {
        if (itemObj.categoryId != category.id) {
            Log.e(this.toString(), "target NoteItem not exist in current category")
            return
        }
        when (notify) {
            Notify.INSERTED -> {
                if (!noteItemAdapter.add(index, itemObj)) return
                rlNoteList.smoothScrollToPosition(index)
                if (itemObj == pendingNoteItem) {
                    clearPendingItem()
                    // clear input box text for manually added item
                    etInputText.setText("")
                    clearAttachments()
                }
            }

            Notify.UPDATED -> noteItemAdapter.update(index)
            Notify.REMOVED -> {
                Log.e(TAG, "item removed: $index, showDeleted: ${noteItemAdapter.showDeleted}")
                if (noteItemAdapter.showDeleted) {
                    noteItemAdapter.update(index)
                } else {
                    noteItemAdapter.remove(itemObj)
                }
            }
            Notify.RESTORED -> {
                noteItemAdapter.update(index)
            }
            Notify.CLEARED -> noteItemAdapter.clear()
        }
    }

    companion object {
        const val TAG: String = "NoteListFragment"
        private const val PROP_NAME = "translationX"
    }
}