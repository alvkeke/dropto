package cn.alvkeke.dropto.ui.component

import android.content.Context
import android.text.method.PasswordTransformationMethod
import android.util.AttributeSet
import android.view.LayoutInflater
import android.view.inputmethod.EditorInfo
import android.widget.EditText
import android.widget.ImageButton
import android.widget.TextView
import androidx.core.content.ContextCompat
import cn.alvkeke.dropto.R
import cn.alvkeke.dropto.ui.UserInterfaceHelper.hideSoftKeyboard
import androidx.core.content.withStyledAttributes

class PasswordInputBox @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0,
) : CapsuleLayout(context, attrs, defStyleAttr) {

    private val textBox: EditText
    private val toggleButton: ImageButton

    var passwordVisible = false
        private set

    val text: String
        get() = textBox.text.toString()

    init {
        LayoutInflater.from(context).inflate(R.layout.component_password_box, this, true)

        textBox = findViewById(R.id.password_box_text)
        toggleButton = findViewById(R.id.password_box_toggle)

        // the box is an EditText with a toggle, so hint and ime action still come from the XML tag
        context.withStyledAttributes(attrs, R.styleable.PasswordInputBox) {
            textBox.hint = getText(R.styleable.PasswordInputBox_android_hint)
            textBox.imeOptions = getInt(
                R.styleable.PasswordInputBox_android_imeOptions,
                EditorInfo.IME_ACTION_DONE,
            )
        }

        toggleButton.setOnClickListener { setPasswordVisible(!passwordVisible) }
        refreshToggle()
    }

    fun setText(value: CharSequence) {
        textBox.setText(value)
        textBox.setSelection(textBox.text.length)
    }

    fun setOnEditorActionListener(listener: TextView.OnEditorActionListener) {
        textBox.setOnEditorActionListener(listener)
    }

    fun hideSoftKeyboard() {
        textBox.hideSoftKeyboard()
    }

    private fun setPasswordVisible(visible: Boolean) {
        passwordVisible = visible

        val selection = textBox.selectionEnd
        // switching the transformation keeps the input type (password keyboard, no suggestions)
        textBox.transformationMethod =
            if (visible) null else PasswordTransformationMethod.getInstance()
        textBox.setSelection(selection.coerceIn(0, textBox.text.length))

        refreshToggle()
    }

    private fun refreshToggle() {
        // masked: plain eye (tap to display); shown: crossed eye (tap to hide)
        toggleButton.setImageResource(
            if (passwordVisible) R.drawable.icon_password_eye_displaying else R.drawable.icon_password_eye_hiding,
        )
        toggleButton.imageTintList = ContextCompat.getColorStateList(
            context,
            if (passwordVisible) R.color.color_text_main else R.color.color_text_sub,
        )
        toggleButton.contentDescription = context.getString(
            if (passwordVisible) R.string.description_password_hide
            else R.string.description_password_show,
        )
    }

}
