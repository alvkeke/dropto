package cn.alvkeke.dropto.ui.fragment

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ValueAnimator
import android.content.DialogInterface
import android.os.Bundle
import android.util.Log
import android.view.HapticFeedbackConstants
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
import cn.alvkeke.dropto.ui.comonent.AttachmentCard
import cn.alvkeke.dropto.ui.intf.NoteDBAttemptListener
import cn.alvkeke.dropto.ui.intf.NoteUIAttemptListener
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.bottomsheet.BottomSheetDialogFragment

class NoteDetailFragment : BottomSheetDialogFragment(), AttachmentCard.CardListener{
    private lateinit var dbListener: NoteDBAttemptListener
    private lateinit var uiListener: NoteUIAttemptListener
    private lateinit var etNoteItemText: EditText
    private lateinit var scrollContainer: LinearLayout

    private var note: NoteItem? = null
    private val removedAttachments = ArrayList<AttachmentFile>()
    private var isImageChanged = false

    fun setNoteItem(item: NoteItem) {
        this.note = item
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

        if (note != null)
            loadItemDataToUI(note!!)

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
        if (note == null) {
            note = NoteItem(text)
            dbListener.onAttempt(NoteDBAttemptListener.Attempt.CREATE, note!!)
            return
        }
        if (text.isEmpty() && isImageChanged && note!!.attachments.isEmpty()) {
            Toast.makeText(
                requireContext(),
                "Got empty item after modifying, remove it", Toast.LENGTH_SHORT
            ).show()
            Log.i(TAG, "Got empty item after modifying, remove it, note-${note!!.id}")
            dbListener.onAttempt(NoteDBAttemptListener.Attempt.REMOVE, note!!)
            return
        }
        note!!.setText(text, true)
        if (isImageChanged) {
            note!!.attachments.removeAll(removedAttachments)
        }
        dbListener.onAttempt(NoteDBAttemptListener.Attempt.UPDATE, note!!)
    }

    private inner class NoteDetailMenuListener : Toolbar.OnMenuItemClickListener {
        override fun onMenuItemClick(menuItem: MenuItem): Boolean {
            val menuId = menuItem.itemId
            if (R.id.note_detail_menu_item_ok == menuId) {
                handleOk()
            } else if (R.id.note_detail_menu_item_delete == menuId) {
                Log.d(this.toString(), "remove item")
                dbListener.onAttempt(NoteDBAttemptListener.Attempt.REMOVE, note!!)
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
        item.attachments.iterator().forEach { attachment ->
            val card = AttachmentCard(context, attachment, this)
            scrollContainer.addView(card)
        }
    }

    override fun onRemove(card: AttachmentCard, attachment: AttachmentFile) {
        val hei = card.height
        val animator = ValueAnimator.ofInt(hei, 0)
        animator.addUpdateListener { valueAnimator: ValueAnimator ->
            val params = card.layoutParams
            params.height = valueAnimator.animatedValue as Int
            card.layoutParams = params
        }
        animator.addListener(object : AnimatorListenerAdapter() {
            override fun onAnimationEnd(animation: Animator) {
                // not really remove here, we can cancel the operation before saving
                removedAttachments.add(attachment)
                isImageChanged = true
                scrollContainer.removeView(card)
            }
        })
        animator.start()
    }

    override fun onClick(card: AttachmentCard, attachment: AttachmentFile) {
        val attempt = when (attachment.type) {
            AttachmentFile.Type.IMAGE -> {
                NoteUIAttemptListener.Attempt.SHOW_IMAGE
            }
            AttachmentFile.Type.FILE -> {
                NoteUIAttemptListener.Attempt.OPEN_FILE
            }
        }
        val index = when (attachment.type) {
            AttachmentFile.Type.IMAGE -> {
                note!!.images.indexOf(attachment)
            }
            AttachmentFile.Type.FILE -> {
                note!!.files.indexOf(attachment)
            }
        }

        uiListener.onAttempt(attempt, note!!, index)
    }

    override fun onLongClick(card: AttachmentCard, attachment: AttachmentFile) {
        card.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
    }

    companion object {
        private const val TAG = "NoteDetailFragment"
    }

}