package cn.alvkeke.dropto.ui.fragment

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ValueAnimator
import android.content.DialogInterface
import android.graphics.Bitmap
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.Toast
import androidx.appcompat.widget.Toolbar
import cn.alvkeke.dropto.R
import cn.alvkeke.dropto.data.AttachmentFile
import cn.alvkeke.dropto.data.NoteItem
import cn.alvkeke.dropto.mgmt.Global
import cn.alvkeke.dropto.storage.ImageLoader
import cn.alvkeke.dropto.storage.ImageLoader.loadImageAsync
import cn.alvkeke.dropto.ui.comonent.ImageCard
import cn.alvkeke.dropto.ui.intf.NoteDBAttemptListener
import cn.alvkeke.dropto.ui.intf.NoteUIAttemptListener
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.bottomsheet.BottomSheetDialogFragment

class NoteDetailFragment : BottomSheetDialogFragment() {
    private lateinit var dbListener: NoteDBAttemptListener
    private lateinit var uiListener: NoteUIAttemptListener
    private lateinit var etNoteItemText: EditText
    private lateinit var scrollContainer: LinearLayout

    private var item: NoteItem? = null
    private var isImageChanged = false
    private val attachments = ArrayList<AttachmentFile>()

    fun setNoteItem(item: NoteItem) {
        this.item = item
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        return inflater.inflate(R.layout.fragment_note_detail, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        dbListener = requireContext() as NoteDBAttemptListener
        uiListener = requireContext() as NoteUIAttemptListener

        val toolbar = view.findViewById<MaterialToolbar>(R.id.note_detail_toolbar)
        etNoteItemText = view.findViewById(R.id.note_detail_text)
        val scrollView = view.findViewById<ScrollView>(R.id.note_detail_scroll)
        scrollContainer = view.findViewById(R.id.note_detail_scroll_container)

        initEssentialVars()
        setPeekHeight(view)

        if (item != null)
            loadItemDataToUI(item!!)

        toolbar.inflateMenu(R.menu.note_detail_toolbar)
        toolbar.setOnMenuItemClickListener(NoteDetailMenuListener())
        toolbar.setNavigationIcon(R.drawable.icon_common_cross)
        toolbar.setNavigationOnClickListener(BackNavigationClick())
        scrollView.setOnScrollChangeListener(ScrollViewListener())
    }

    private var isDraggable = true

    private inner class ScrollViewListener : View.OnScrollChangeListener {
        override fun onScrollChange(view: View, x: Int, y: Int, i2: Int, i3: Int) {
            if (y <= 0) {
                if (!isDraggable) {
                    isDraggable = true
                    behavior.isDraggable = true
                }
            } else {
                if (isDraggable) {
                    isDraggable = false
                    behavior.isDraggable = false
                }
            }
        }
    }

    private lateinit var behavior: BottomSheetBehavior<FrameLayout>
    private fun initEssentialVars() {
        val dialog = requireDialog() as BottomSheetDialog
        behavior = dialog.behavior
    }

    private fun setPeekHeight(view: View) {
        val displayHei = requireActivity().resources.displayMetrics.heightPixels
        val peekHei = displayHei * 35 / 100
        behavior.peekHeight = peekHei
        val layoutParams = view.layoutParams
        layoutParams.height = displayHei
    }

    private fun finish() {
        this.dismiss()
    }

    override fun onDismiss(dialog: DialogInterface) {
        super.onDismiss(dialog)
    }

    private inner class BackNavigationClick : View.OnClickListener {
        override fun onClick(view: View) {
            finish()
        }
    }

    private fun handleOk() {
        val text = etNoteItemText.text.toString()
        if (item == null) {
            item = NoteItem(text)
            dbListener.onAttempt(NoteDBAttemptListener.Attempt.CREATE, item!!)
            return
        }
        if (text.isEmpty() && isImageChanged && attachments.isEmpty()) {
            Toast.makeText(
                requireContext(),
                "Got empty item after modifying, remove it", Toast.LENGTH_SHORT
            ).show()
            dbListener.onAttempt(NoteDBAttemptListener.Attempt.REMOVE, item!!)
            return
        }
        item!!.setText(text, true)
        if (isImageChanged) {
            item!!.attachments.clear()
            item!!.attachments.addAll(attachments)
        }
        dbListener.onAttempt(NoteDBAttemptListener.Attempt.UPDATE, item!!)
    }

    private inner class NoteDetailMenuListener : Toolbar.OnMenuItemClickListener {
        override fun onMenuItemClick(menuItem: MenuItem): Boolean {
            val menuId = menuItem.itemId
            if (R.id.note_detail_menu_item_ok == menuId) {
                handleOk()
            } else if (R.id.note_detail_menu_item_delete == menuId) {
                Log.d(this.toString(), "remove item")
                dbListener.onAttempt(NoteDBAttemptListener.Attempt.REMOVE, item!!)
            } else {
                Log.e(this.toString(), "got unknown menu id: $menuId")
                return false
            }
            finish()
            return true
        }
    }

    /**
     * load item info to View, input item cannot be null,
     * there is no valid-check for the item.
     */
    private fun loadItemDataToUI(item: NoteItem) {
        etNoteItemText.setText(item.text)

        if (item.attachments.isEmpty()) return

        val context = requireContext()
        item.images.iterator().forEach { attachment: AttachmentFile ->
            val card = ImageCard(context)
            card.setImageMd5(attachment.md5)
            card.setImageName(attachment.name)

            // all attachments in images should have correct type, not checking here
            val type = Global.mimeTypeFromFileName(attachment.name)

            if (type.startsWith("image/")) {
                loadImageAsync(attachment.md5file) { bitmap: Bitmap? ->
                    if (bitmap == null) {
                        val errMsg = "Failed to get image file, skip this item"
                        Log.e(this.toString(), errMsg)
                        Toast.makeText(context, errMsg, Toast.LENGTH_SHORT).show()
                        card.setImageResource(R.drawable.img_load_error)
                    } else {
                        card.setImage(bitmap)
                    }
                }
            } else if (type.startsWith("video/")) {
                ImageLoader.loadVideoThumbnailAsync(attachment.md5file) { bitmap: Bitmap? ->
                    if (bitmap == null) {
                        val errMsg = "Failed to get video file, skip this item"
                        Log.e(this.toString(), errMsg)
                        Toast.makeText(context, errMsg, Toast.LENGTH_SHORT).show()
                        card.setImageResource(R.drawable.img_load_error)
                    } else {
                        card.setImage(bitmap)
                        // TODO: add a play icon overlay
                    }
                }
            } else {
                Log.e(TAG, "Unsupported attachment type in images: $type")
            }

            card.setRemoveButtonClickListener(OnRemoveClickListener(item, attachment))
            val imageIndex = item.attachments.indexOf(attachment)
            card.setImageClickListener(OnImageClickListener( item, imageIndex ))
            scrollContainer.addView(card)
        }
    }

    private inner class OnRemoveClickListener(
        val note: NoteItem,
        val attachment: AttachmentFile) : View.OnClickListener {
        override fun onClick(card: View) {
            val hei = card.height
            val animator = ValueAnimator.ofInt(hei, 0)
            animator.addUpdateListener { valueAnimator: ValueAnimator ->
                val params = card.layoutParams
                params.height = valueAnimator.animatedValue as Int
                card.layoutParams = params
            }
            animator.addListener(object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: Animator) {
                    note.attachments.remove(attachment)
                    isImageChanged = true
                    scrollContainer.removeView(card)
                }
            })
            animator.start()
        }
    }

    private inner class OnImageClickListener(
        val note: NoteItem,
        val imageIndex: Int
    ) : View.OnClickListener {
        override fun onClick(card: View) {
            uiListener.onAttempt(
                NoteUIAttemptListener.Attempt.SHOW_IMAGE,
                note,
                imageIndex
            )
        }
    }

    companion object {
        private const val TAG = "NoteDetailFragment"
    }

}