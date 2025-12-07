package cn.alvkeke.dropto.ui.comonent

import android.content.Context
import android.graphics.Bitmap
import android.view.LayoutInflater
import android.widget.ImageView
import android.widget.TextView
import androidx.constraintlayout.widget.ConstraintLayout
import cn.alvkeke.dropto.R

class ImageCard(context: Context) : ConstraintLayout(context) {
    private val imageView: ImageView
    private val btnRemove: ImageView
    private val tvImageName: TextView
    private val tvImageMd5: TextView

    init {
        val inflater = LayoutInflater.from(context)
        val view = inflater.inflate(R.layout.component_image_card, this, false)

        imageView = view.findViewById(R.id.note_detail_image_view)
        btnRemove = view.findViewById(R.id.note_detail_image_remove)
        tvImageMd5 = view.findViewById(R.id.note_detail_image_md5)
        tvImageName = view.findViewById(R.id.note_detail_image_name)

        this.addView(view)
    }

    fun setImageName(name: String?) {
        tvImageName.text = name
    }

    fun setImageMd5(md5: String?) {
        tvImageMd5.text = md5
    }

    fun setImage(bitmap: Bitmap) {
        imageView.setImageBitmap(bitmap)
    }

    fun setImageResource(resId: Int) {
        imageView.setImageResource(resId)
    }

    fun setRemoveButtonClickListener(listener: OnClickListener?) {
        btnRemove.setOnClickListener(listener)
    }

    fun setImageClickListener(listener: OnClickListener?) {
        imageView.setOnClickListener(listener)
    }
}
