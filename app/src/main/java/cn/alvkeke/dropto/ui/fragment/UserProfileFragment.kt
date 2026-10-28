package cn.alvkeke.dropto.ui.fragment

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import androidx.core.widget.doAfterTextChanged
import androidx.lifecycle.lifecycleScope
import cn.alvkeke.dropto.R
import cn.alvkeke.dropto.runtime.DataManager
import cn.alvkeke.dropto.ui.component.CapsuleToolbar
import cn.alvkeke.dropto.ui.intf.FragmentOnBackListener
import kotlinx.coroutines.launch

/** Edits the profile of the account that is logged in, opened by tapping its avatar. */
class UserProfileFragment : MgmtBottomSheetFragment(), FragmentOnBackListener {

    companion object {
        /** Posted when the profile changed, so the page that opened this one reloads it. */
        const val RESULT_PROFILE_UPDATED = "user_profile_updated"
    }

    private lateinit var toolbar: CapsuleToolbar
    private lateinit var nameInput: EditText
    private lateinit var userIdText: TextView
    private lateinit var saveButton: Button

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        return inflater.inflate(R.layout.fragment_user_profile, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        toolbar = view.findViewById(R.id.user_profile_toolbar)
        nameInput = view.findViewById(R.id.user_profile_name)
        userIdText = view.findViewById(R.id.user_profile_user_id)
        saveButton = view.findViewById(R.id.user_profile_save)

        toolbar.setTitle(getString(R.string.string_user_profile_title))
        toolbar.setNavigationOnClickListener { finish() }

        val user = DataManager.currentUser
        if (user == null) {
            view.post { finish() }
            return
        }

        nameInput.setText(user.name)
        nameInput.doAfterTextChanged { nameInput.error = null }
        userIdText.text = "${user.id}@${user.domain}"

        saveButton.setOnClickListener {
            val name = nameInput.text.toString().trim()
            if (name.isNullOrEmpty()) {
                nameInput.error = getString(R.string.string_user_profile_name_empty)
                return@setOnClickListener
            }

            lifecycleScope.launch {
                if (!DataManager.updateActiveUserName(name)) return@launch
                parentFragmentManager.setFragmentResult(RESULT_PROFILE_UPDATED, Bundle())
                finish()
            }
        }
    }

    override fun onBackPressed(): Boolean {
        finish()
        return true
    }
}
