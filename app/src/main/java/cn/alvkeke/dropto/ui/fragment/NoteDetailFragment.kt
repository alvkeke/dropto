package cn.alvkeke.dropto.ui.fragment

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ValueAnimator
import android.content.DialogInterface
import android.content.Intent
import android.net.Uri
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
import cn.alvkeke.dropto.DroptoApplication
import cn.alvkeke.dropto.R
import cn.alvkeke.dropto.data.AttachmentFile
import cn.alvkeke.dropto.data.NoteItem
import cn.alvkeke.dropto.storage.FileHelper
import cn.alvkeke.dropto.ui.UserInterfaceHelper.openFileWithExternalApp
import cn.alvkeke.dropto.ui.UserInterfaceHelper.showMediaFragment
import cn.alvkeke.dropto.ui.comonent.AttachmentCard
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.bottomsheet.BottomSheetDialogFragment

class NoteDetailFragment : BottomSheetDialogFragment(), AttachmentCard.CardListener{
    private val app: DroptoApplication
        get() = requireActivity().application as DroptoApplication

    private lateinit var etNoteItemText: EditText
    private lateinit var scrollContainer: LinearLayout

    private var note: NoteItem? = null
    fun setNoteItem(note: NoteItem?) {
        this.note = note
    }
    private val removedAttachments = ArrayList<AttachmentFile>()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        return inflater.inflate(R.layout.fragment_note_detail, container, false)
    }

    override fun onStart() {
        super.onStart()
        if (note != null) {
            refreshUIData(note!!)
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val toolbar = view.findViewById<MaterialToolbar>(R.id.note_detail_toolbar)
        etNoteItemText = view.findViewById(R.id.note_detail_text)
        val scrollView = view.findViewById<ScrollView>(R.id.note_detail_scroll)
        scrollContainer = view.findViewById(R.id.note_detail_scroll_container)

        initEssentialVars()
        setPeekHeight(view)

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
            app.service?.createNote(note!!)
            return
        }
        if (text.isEmpty() && removedAttachments.containsAll(note!!.attachments)) {
            Toast.makeText(
                requireContext(),
                "Got empty item after modifying, remove it", Toast.LENGTH_SHORT
            ).show()
            Log.i(TAG, "Got empty item after modifying, remove it, note-${note!!.id}")
            app.service?.removeNote(note!!)
            return
        }
        note!!.text = text
        note!!.isEdited = true
        if (removedAttachments.isNotEmpty()) {
            note!!.attachments.removeAll(removedAttachments)
            removedAttachments.clear()
        }
        app.service?.updateNote(note!!)
    }

    private inner class NoteDetailMenuListener : Toolbar.OnMenuItemClickListener {
        override fun onMenuItemClick(menuItem: MenuItem): Boolean {
            val menuId = menuItem.itemId
            if (R.id.note_detail_menu_item_ok == menuId) {
                handleOk()
            } else if (R.id.note_detail_menu_item_delete == menuId) {
                Log.d(this.toString(), "remove item")
                app.service?.removeNote(note!!)
            } else {
                Log.e(this.toString(), "got unknown menu id: $menuId")
                return false
            }
            finish()
            return true
        }
    }

    private fun checkIfAttachmentCardExist(attachment: AttachmentFile): Boolean {
        for (i in 0 until scrollContainer.childCount) {
            val child = scrollContainer.getChildAt(i)
            if (child is AttachmentCard) {
                if (child.attachment == attachment) {
                    return true
                }
            }
        }
        return false
    }

    private fun refreshUIData(item: NoteItem) {
        etNoteItemText.setText(item.text)
        if (item.attachments.isEmpty())
            return
        val context = requireContext()
        item.attachments.forEach {
            if (checkIfAttachmentCardExist(it)) {
                return@forEach
            }
            val card = AttachmentCard(context, it, this)
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
                scrollContainer.removeView(card)
            }
        })
        animator.start()
    }

    private fun triggerShare(text: String, type: String, uri: Uri) {
        val sendIntent = Intent(Intent.ACTION_SEND)

        sendIntent.type = type

        sendIntent.putExtra(Intent.EXTRA_STREAM, uri)

        sendIntent.putExtra(Intent.EXTRA_TEXT, text)
        Log.d(this.toString(), "no image, share text: $text")

        val shareIntent = Intent.createChooser(sendIntent, "Share to")
        try {
            this.startActivity(shareIntent)
        } catch (e: Exception) {
            Log.e(this.toString(), "Failed to create share Intent: $e")
        }
    }


    override fun onShare(
        card: AttachmentCard,
        attachment: AttachmentFile
    ) {
        val uri = FileHelper.generateShareFile(requireContext(), attachment)
        val text = attachment.name
        val mimeType = attachment.mimeType

        triggerShare(text, mimeType, uri)
    }

    override fun onClick(card: AttachmentCard, attachment: AttachmentFile) {
        when (attachment.type) {
            AttachmentFile.Type.MEDIA -> {
                this.showMediaFragment(attachment)
            }
            AttachmentFile.Type.FILE -> {
                requireContext().openFileWithExternalApp(attachment)
            }
        }
    }

    override fun onOpen(card: AttachmentCard, attachment: AttachmentFile) {
        requireContext().openFileWithExternalApp(attachment)
    }

    companion object {
        private const val TAG = "NoteDetailFragment"
    }

}