package cn.alvkeke.dropto.ui.fragment

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.widget.EditText
import android.widget.TextView
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import cn.alvkeke.dropto.R
import cn.alvkeke.dropto.runtime.model.User
import cn.alvkeke.dropto.runtime.DataManager
import cn.alvkeke.dropto.ui.activity.LoginActivity
import cn.alvkeke.dropto.ui.component.PasswordInputBox
import kotlinx.coroutines.launch

class RegisterFragment : Fragment() {

    private lateinit var userIdInput: EditText
    private lateinit var passwordInput: PasswordInputBox
    private lateinit var passwordRepeatInput: PasswordInputBox
    private lateinit var errorText: TextView
    private lateinit var registerButton: View

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        return inflater.inflate(R.layout.fragment_register, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        userIdInput = view.findViewById(R.id.register_user_id)
        passwordInput = view.findViewById(R.id.register_password)
        passwordRepeatInput = view.findViewById(R.id.register_password_repeat)
        errorText = view.findViewById(R.id.register_error)
        registerButton = view.findViewById(R.id.register_button)

        registerButton.setOnClickListener { register() }
        // long press registers without a password, the account can set one later
        registerButton.setOnLongClickListener {
            register(allowEmptyPassword = true)
            true
        }
        view.findViewById<View>(R.id.register_back).setOnClickListener {
            (activity as? LoginActivity)?.showLogin()
        }
        passwordRepeatInput.setOnEditorActionListener { _, actionId, _ ->
            if (actionId != EditorInfo.IME_ACTION_DONE) return@setOnEditorActionListener false
            register()
            true
        }
    }

    private fun register(allowEmptyPassword: Boolean = false) {
        val id = userIdInput.text.toString().trim()
        val password = passwordInput.text
        val repeat = passwordRepeatInput.text
        if (!User.isValidId(id)) {
            showError(R.string.string_login_id_invalid)
            return
        }
        if (password.isEmpty() && !allowEmptyPassword) {
            showError(R.string.string_login_password_required)
            return
        }
        if (password != repeat) {
            showError(R.string.string_register_password_mismatch)
            return
        }

        passwordRepeatInput.hideSoftKeyboard()
        errorText.isVisible = false
        registerButton.isEnabled = false

        viewLifecycleOwner.lifecycleScope.launch {
            val user = User(User.LOCAL_DOMAIN, id)
            if (!DataManager.registerUser(user, password)) {
                fail(R.string.string_register_failed)
                return@launch
            }
            if (!DataManager.activateUser(user)) {
                fail(R.string.string_login_failed)
                return@launch
            }

            (activity as? LoginActivity)?.onLoginSucceeded()
        }
    }

    private fun fail(message: Int) {
        registerButton.isEnabled = true
        showError(message)
    }

    private fun showError(message: Int) {
        errorText.setText(message)
        errorText.isVisible = true
    }

}
