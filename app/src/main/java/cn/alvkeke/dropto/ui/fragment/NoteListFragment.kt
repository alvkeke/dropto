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
import androidx.activity.result.contract.ActivityResultContracts.PickMultipleVisualMedia
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.widget.PopupMenu
import androidx.appcompat.widget.Toolbar
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsAnimationCompat
import androidx.core.view.WindowInsetsCompat
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import cn.alvkeke.dropto.R
import cn.alvkeke.dropto.data.Category
import cn.alvkeke.dropto.data.AttachmentFile
import cn.alvkeke.dropto.data.NoteItem
import cn.alvkeke.dropto.mgmt.Global
import cn.alvkeke.dropto.storage.FileHelper
import cn.alvkeke.dropto.ui.adapter.NoteListAdapter
import cn.alvkeke.dropto.ui.adapter.SelectableListAdapter.SelectListener
import cn.alvkeke.dropto.ui.comonent.CountableImageButton
import cn.alvkeke.dropto.ui.comonent.MyPopupMenu
import cn.alvkeke.dropto.ui.intf.ErrorMessageHandler
import cn.alvkeke.dropto.ui.intf.FragmentOnBackListener
import cn.alvkeke.dropto.ui.intf.ListNotification
import cn.alvkeke.dropto.ui.intf.ListNotification.Notify
import cn.alvkeke.dropto.ui.intf.NoteDBAttemptListener
import cn.alvkeke.dropto.ui.listener.OnRecyclerViewTouchListener
import com.google.android.material.appbar.MaterialToolbar
import androidx.core.view.get
import androidx.core.view.size
import cn.alvkeke.dropto.ui.comonent.NoteItemView
import cn.alvkeke.dropto.ui.intf.NoteUIAttemptListener



class NoteListFragment : Fragment(), ListNotification<NoteItem>, FragmentOnBackListener {
    private lateinit var context: Context
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
    private lateinit var rlNoteList: RecyclerView

    private var category: Category? = null
    private var noteItemAdapter: NoteListAdapter = NoteListAdapter()

    fun setCategory(category: Category) {
        this.category = category
        noteItemAdapter.setList(category.noteItems)
    }

    @Suppress("unused")
    fun getCategory(): Category? {
        return this.category
    }

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
        dbListener = context as NoteDBAttemptListener
        uiListener = context as NoteUIAttemptListener
        checkNotNull(category)

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

        toolbar.setTitle(category!!.title)
        toolbar.setNavigationIcon(R.drawable.icon_common_back)
        toolbar.setNavigationOnClickListener(OnNavigationIconClick())
        toolbar.setOnMenuItemClickListener(NoteListMenuListener())
        toolbar.inflateMenu(R.menu.fragment_note_list_toolbar)
        setToolbarMenuVisible(false)

        val layoutManager = LinearLayoutManager(context)
        layoutManager.setReverseLayout(true)
        noteItemAdapter.setSelectListener(NoteListSelectListener())
        noteItemAdapter.setList(category!!.noteItems)

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
        if (noteItemAdapter.isSelectMode) {
            noteItemAdapter.clearSelectItems()
        } else {
            finish()
        }
        return true
    }

    private inner class OnNavigationIconClick : OnClickListener {
        override fun onClick(view: View) {
            onBackPressed()
        }
    }

    private fun removeSelectedNotes() {
        val items: ArrayList<NoteItem> = noteItemAdapter.selectedItems
        noteItemAdapter.clearSelectItems()
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
        val items: ArrayList<NoteItem> = noteItemAdapter.selectedItems
        noteItemAdapter.clearSelectItems()
        uiListener.onAttemptBatch(NoteUIAttemptListener.Attempt.COPY, items)
    }

    private fun handleMenuShare() {
        val items: ArrayList<NoteItem> = noteItemAdapter.selectedItems
        noteItemAdapter.clearSelectItems()
        uiListener.onAttemptBatch(NoteUIAttemptListener.Attempt.SHOW_SHARE, items)
    }

    private fun handleMenuForward() {
        val items: ArrayList<NoteItem> = noteItemAdapter.selectedItems
        noteItemAdapter.clearSelectItems()
        uiListener.onAttemptBatch(NoteUIAttemptListener.Attempt.SHOW_FORWARD, items)
    }

    private inner class NoteListMenuListener : Toolbar.OnMenuItemClickListener {
        override fun onMenuItemClick(item: MenuItem): Boolean {
            val menuId = item.itemId
            if (R.id.note_list_menu_delete == menuId) {
                handleMenuDelete()
            } else if (R.id.note_list_menu_copy == menuId) {
                handleMenuCopy()
            } else if (R.id.note_list_menu_share == menuId) {
                handleMenuShare()
            } else if (R.id.note_list_menu_forward == menuId) {
                handleMenuForward()
            }
            return false
        }
    }

    private fun setToolbarMenuVisible(visible: Boolean) {
        val menu = toolbar.getMenu()
        for (i in 0..<menu.size) {
            val menuItem = menu[i]
            menuItem.isVisible = visible
        }
    }

    private inner class NoteListSelectListener : SelectListener {
        override fun onSelectEnter() {
            setToolbarMenuVisible(true)
            toolbar.setNavigationIcon(R.drawable.icon_common_cross)
        }

        override fun onSelectExit() {
            setToolbarMenuVisible(false)
            toolbar.setNavigationIcon(R.drawable.icon_common_back)
            toolbar.title = category!!.title
        }

        override fun onSelect(index: Int) {
            toolbar.title = "${noteItemAdapter.selectedCount}"
        }
        override fun onUnSelect(index: Int) {
            toolbar.title = "${noteItemAdapter.selectedCount}"
        }
    }

    private inner class NoteListTouchListener : OnRecyclerViewTouchListener() {
        override fun onItemClickAt(v: View, index: Int, event: MotionEvent): Boolean {
            if (noteItemAdapter.isSelectMode) {
                noteItemAdapter.toggleSelectItems(index)
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

                    NoteItemView.ClickedContent.Type.IMAGE -> {
                        showImageView(index, content.index)
                    }

                    NoteItemView.ClickedContent.Type.FILE -> {
                        showFileView(index, content.index)
                    }
                }
            }
            return true
        }

        private var firstHoldItem = -1
        private var lastHoldItem = -1
        override fun onItemLongClick(v: View, index: Int): Boolean {
            v.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
            firstHoldItem = index
            lastHoldItem = -1
            noteItemAdapter.toggleSelectItems(index)
            return true
        }

        override fun onItemLongClickSlideOn(v: View, index: Int): Boolean {
            v.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
            if (lastHoldItem != -1 && lastHoldItem !in firstHoldItem..index) {
                noteItemAdapter.unselectItem(lastHoldItem)
            } else {
                noteItemAdapter.selectItem(index)
            }
            lastHoldItem = index
            return true
        }

        override fun onSlideEnd(
            v: View,
            e: MotionEvent,
            deltaX: Float,
            deltaY: Float
        ): Boolean {
            val width = fragmentView.width
            val thresholdExit = width / 3
            if (deltaX > thresholdExit) {
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

    private fun setMaskTransparent(targetX: Float) {
        val width = fragmentView.width
        val alpha = (width - targetX) / width
        fragmentParent.background.alpha = (alpha * 255 / 2).toInt()
    }

    private fun moveFragmentView(targetX: Float) {
        setMaskTransparent(targetX)
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
                noteItemAdapter.clearSelectItems()
                attachments.clear()
                getParentFragmentManager().beginTransaction()
                    .remove(this@NoteListFragment).commit()
            }
        })
        animator.addUpdateListener { valueAnimator: ValueAnimator ->
            val deltaX = valueAnimator.animatedValue as Float
            setMaskTransparent(deltaX)
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
            btnAttachImage -> AttachmentFile.Type.IMAGE
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
        PickMultipleVisualMedia(9), this )
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
            item.categoryId = category!!.id
            if (attachments.isEmpty()) {
                if (content.isEmpty()) return
            } else {
                for (a in attachments) {
                    val folder = Global.getFolderImage(context)
                    val md5file = FileHelper.saveUriToFile(context, a.uri, folder)
                    val imgName = FileHelper.getFileNameFromUri(context, a.uri)
                    val imageFile = AttachmentFile.from(md5file!!, imgName!!, a.type)
                    item.attachments.add(imageFile)
                }
            }
            setPendingItem(item)
            dbListener.onAttempt(NoteDBAttemptListener.Attempt.CREATE, item)
        }
    }

    private fun showImageView(index: Int, imageIndex: Int) {
        val noteItem = category!!.getNoteItem(index)
        uiListener.onAttempt(
            NoteUIAttemptListener.Attempt.SHOW_IMAGE,
            noteItem,
            imageIndex
        )
    }

    private fun showFileView(index: Int, fileIndex: Int) {
        val noteItem = category!!.getNoteItem(index)
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

    private var myPopupMenu: MyPopupMenu? = null
    private fun showItemPopMenu(index: Int, v: View, x: Int, y: Int) {
        val noteItem = category!!.getNoteItem(index)
        if (myPopupMenu == null) {
            val menu = PopupMenu(context, v).menu
            requireActivity().menuInflater.inflate(R.menu.item_pop_menu, menu)
            myPopupMenu = MyPopupMenu(context).setMenu(menu)
                .setListener { menuItem, extraData ->
                    val note = extraData as NoteItem
                    when (val itemId = menuItem.itemId) {
                        R.id.item_pop_m_delete -> {
                            AlertDialog.Builder(context)
                                .setTitle(R.string.dialog_note_delete_pop_remove_title)
                                .setMessage(R.string.dialog_note_delete_pop_remove_message)
                                .setNegativeButton(R.string.string_cancel, null)
                                .setPositiveButton(
                                    R.string.string_ok
                                ) { _: DialogInterface, _: Int ->
                                    dbListener.onAttempt(
                                        NoteDBAttemptListener.Attempt.REMOVE,
                                        note
                                    )
                                }
                                .create().show()
                        }

                        R.id.item_pop_m_pin -> {
                            throwErrorMessage("try to Pin item at $index")
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
        myPopupMenu!!.setData(noteItem).show(v, x, y)
    }

    private var pendingNoteItem: NoteItem? = null
    private fun setPendingItem(item: NoteItem) {
        pendingNoteItem = item
    }

    private fun clearPendingItem() {
        pendingNoteItem = null
    }

    override fun notifyItemListChanged(notify: Notify, index: Int, itemObj: NoteItem) {
        if (itemObj.categoryId != category!!.id) {
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
            Notify.REMOVED -> noteItemAdapter.remove(itemObj)
            Notify.CLEARED -> noteItemAdapter.clear()
        }
    }

    companion object {
        const val TAG: String = "NoteListFragment"
        private const val PROP_NAME = "translationX"
    }
}