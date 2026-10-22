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
import cn.alvkeke.dropto.data.User
import cn.alvkeke.dropto.storage.DataManager
import cn.alvkeke.dropto.ui.activity.LoginActivity
import cn.alvkeke.dropto.ui.component.PasswordInputBox
import kotlinx.coroutines.launch

class LoginFragment : Fragment() {

    private lateinit var userIdInput: EditText
    private lateinit var passwordInput: PasswordInputBox
    private lateinit var errorText: TextView
    private lateinit var loginButton: View

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        return inflater.inflate(R.layout.fragment_login, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        userIdInput = view.findViewById(R.id.login_user_id)
        passwordInput = view.findViewById(R.id.login_password)
        errorText = view.findViewById(R.id.login_error)
        loginButton = view.findViewById(R.id.login_button)

        if (savedInstanceState == null) userIdInput.setText(User.DEFAULT_LOCAL_USER_ID)

        loginButton.setOnClickListener { login() }
        view.findViewById<View>(R.id.login_create_user).setOnClickListener {
            (activity as? LoginActivity)?.showRegister()
        }
        passwordInput.setOnEditorActionListener { _, actionId, _ ->
            if (actionId != EditorInfo.IME_ACTION_DONE) return@setOnEditorActionListener false
            login()
            true
        }
    }

    private fun login() {
        val id = userIdInput.text.toString().trim()
        val password = passwordInput.text
        if (!User.isValidId(id)) {
            showError(R.string.string_login_id_invalid)
            return
        }

        passwordInput.hideSoftKeyboard()
        errorText.isVisible = false
        loginButton.isEnabled = false

        viewLifecycleOwner.lifecycleScope.launch {
            val user = User(User.LOCAL_DOMAIN, id)
            if (!DataManager.verifyUser(user, password)) {
                fail(R.string.string_login_wrong_password)
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
        loginButton.isEnabled = true
        showError(message)
    }

    private fun showError(message: Int) {
        errorText.setText(message)
        errorText.isVisible = true
    }

}
