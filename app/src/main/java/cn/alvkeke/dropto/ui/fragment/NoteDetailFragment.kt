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
import cn.alvkeke.dropto.data.ImageFile
import cn.alvkeke.dropto.data.NoteItem
import cn.alvkeke.dropto.storage.ImageLoader.loadImageAsync
import cn.alvkeke.dropto.ui.comonent.ImageCard
import cn.alvkeke.dropto.ui.intf.NoteAttemptListener
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import java.util.function.Consumer

class NoteDetailFragment : BottomSheetDialogFragment() {
    private lateinit var listener: NoteAttemptListener
    private lateinit var etNoteItemText: EditText
    private lateinit var scrollContainer: LinearLayout

    private var item: NoteItem? = null
    private var isImageChanged = false
    private val imageList = ArrayList<ImageFile>()

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
        listener = requireContext() as NoteAttemptListener

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
            listener.onAttempt(NoteAttemptListener.Attempt.CREATE, item!!)
            return
        }
        if (text.isEmpty() && isImageChanged && imageList.isEmpty()) {
            Toast.makeText(
                requireContext(),
                "Got empty item after modifying, remove it", Toast.LENGTH_SHORT
            ).show()
            listener.onAttempt(NoteAttemptListener.Attempt.REMOVE, item!!)
            return
        }
        item!!.setText(text, true)
        if (isImageChanged) {
            item!!.useImageFiles(imageList)
        }
        listener.onAttempt(NoteAttemptListener.Attempt.UPDATE, item!!)
    }

    private inner class NoteDetailMenuListener : Toolbar.OnMenuItemClickListener {
        override fun onMenuItemClick(menuItem: MenuItem): Boolean {
            val menuId = menuItem.itemId
            if (R.id.note_detail_menu_item_ok == menuId) {
                handleOk()
            } else if (R.id.note_detail_menu_item_delete == menuId) {
                Log.d(this.toString(), "remove item")
                listener.onAttempt(NoteAttemptListener.Attempt.REMOVE, item!!)
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

        if (item.isNoImage) return

        val context = requireContext()
        item.iterateImages().forEachRemaining(Consumer { imageFile: ImageFile ->
            imageList.add(imageFile)
            val card = ImageCard(context)
            card.setImageMd5(imageFile.md5)
            card.setImageName(imageFile.getName())

            loadImageAsync(imageFile.md5file) { bitmap: Bitmap? ->
                if (bitmap == null) {
                    val errMsg = "Failed to get image file, skip this item"
                    Log.e(this.toString(), errMsg)
                    Toast.makeText(context, errMsg, Toast.LENGTH_SHORT).show()
                    card.setImageResource(R.drawable.img_load_error)
                } else {
                    card.setImage(bitmap)
                }
            }
            card.setRemoveButtonClickListener { _: View ->
                val hei = card.height
                val animator = ValueAnimator.ofInt(hei, 0)
                animator.addUpdateListener { valueAnimator: ValueAnimator ->
                    val params = card.layoutParams
                    params.height = valueAnimator.animatedValue as Int
                    card.layoutParams = params
                }
                animator.addListener(object : AnimatorListenerAdapter() {
                    override fun onAnimationEnd(animation: Animator) {
                        imageList.remove(imageFile)
                        isImageChanged = true
                        scrollContainer.removeView(card)
                    }
                })
                animator.start()
            }
            val imageIndex = item.indexOf(imageFile)
            card.setImageClickListener { _: View ->
                listener.onAttempt(
                    NoteAttemptListener.Attempt.SHOW_IMAGE,
                    item,
                    imageIndex
                )
            }
            scrollContainer.addView(card)
        })
    }
}