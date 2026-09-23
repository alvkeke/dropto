package cn.alvkeke.dropto.ui.fragment

import android.widget.FrameLayout
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.bottomsheet.BottomSheetDialogFragment


open class MgmtBottomSheetFragment : BottomSheetDialogFragment() {

    override fun onStart() {
        super.onStart()
        val dialog = dialog as? BottomSheetDialog ?: return
        val sheet = dialog.findViewById<FrameLayout>(
            com.google.android.material.R.id.design_bottom_sheet
        ) ?: return

        sheet.layoutParams = sheet.layoutParams.apply {
            height = (resources.displayMetrics.heightPixels * SHEET_HEIGHT_RATIO).toInt()
        }
        // the pages paint their own background, clip it so the rounded corners
        // of the sheet stay visible
        sheet.clipToOutline = true
        BottomSheetBehavior.from(sheet).apply {
            skipCollapsed = true
            state = BottomSheetBehavior.STATE_EXPANDED
        }
    }

    open fun finish() {
        dismiss()
    }

    companion object {
        // leave a gap above the sheet so it reads as a popup window
        private const val SHEET_HEIGHT_RATIO = 0.9f
    }
}
