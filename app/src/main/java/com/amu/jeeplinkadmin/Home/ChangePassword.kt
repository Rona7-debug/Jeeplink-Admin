package com.amu.jeeplinkadmin.Home

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.Fragment
import com.amu.jeeplinkadmin.R
import com.google.firebase.auth.EmailAuthProvider
import com.google.firebase.auth.FirebaseAuth

class ChangePassword : Fragment() {

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_change_password, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        super.onViewCreated(view, savedInstanceState)

        val etCurrentPassword = view.findViewById<EditText>(R.id.etCurrentPassword)
        val etNewPassword     = view.findViewById<EditText>(R.id.etNewPassword)
        val etConfirmPassword = view.findViewById<EditText>(R.id.etConfirmPassword)
        val btnUpdatePassword = view.findViewById<TextView>(R.id.btnUpdatePassword)

        setupPasswordToggle(view, R.id.etCurrentPassword)
        setupPasswordToggle(view, R.id.etNewPassword)
        setupPasswordToggle(view, R.id.etConfirmPassword)

        btnUpdatePassword.setOnClickListener {
            val current = etCurrentPassword.text.toString().trim()
            val newPass = etNewPassword.text.toString().trim()
            val confirm = etConfirmPassword.text.toString().trim()

            when {
                current.isEmpty() || newPass.isEmpty() || confirm.isEmpty() -> {
                    Toast.makeText(requireContext(), "Please fill in all fields", Toast.LENGTH_SHORT).show()
                    return@setOnClickListener
                }
                newPass.length < 8 -> {
                    Toast.makeText(requireContext(), "Password must be at least 8 characters", Toast.LENGTH_SHORT).show()
                    return@setOnClickListener
                }
                newPass != confirm -> {
                    Toast.makeText(requireContext(), "New passwords do not match", Toast.LENGTH_SHORT).show()
                    return@setOnClickListener
                }
                current == newPass -> {
                    Toast.makeText(requireContext(), "New password must be different from current password", Toast.LENGTH_SHORT).show()
                    return@setOnClickListener
                }
            }

            reauthAndChangePassword(current, newPass, btnUpdatePassword)
        }

        view.findViewById<ImageView>(R.id.btnBack).setOnClickListener {
            requireActivity().onBackPressedDispatcher.onBackPressed()
        }
    }

    private fun reauthAndChangePassword(
        currentPassword: String,
        newPassword: String,
        btnUpdatePassword: TextView
    ) {
        val user  = FirebaseAuth.getInstance().currentUser ?: return
        val email = user.email ?: return

        btnUpdatePassword.text      = "Updating..."
        btnUpdatePassword.isEnabled = false

        val credential = EmailAuthProvider.getCredential(email, currentPassword)

        user.reauthenticate(credential)
            .addOnSuccessListener {
                user.updatePassword(newPassword)
                    .addOnSuccessListener {
                        if (!isAdded) return@addOnSuccessListener
                        Toast.makeText(
                            requireContext(),
                            "Password updated successfully!",
                            Toast.LENGTH_SHORT
                        ).show()
                        requireActivity().onBackPressedDispatcher.onBackPressed()
                    }
                    .addOnFailureListener { e ->
                        if (!isAdded) return@addOnFailureListener
                        resetButton(btnUpdatePassword)
                        Toast.makeText(
                            requireContext(),
                            "Failed to update password: ${e.message}",
                            Toast.LENGTH_LONG
                        ).show()
                    }
            }
            .addOnFailureListener {
                if (!isAdded) return@addOnFailureListener
                resetButton(btnUpdatePassword)
                Toast.makeText(
                    requireContext(),
                    "Current password is incorrect",
                    Toast.LENGTH_SHORT
                ).show()
            }
    }

    private fun resetButton(btn: TextView) {
        btn.text      = "Update Password"
        btn.isEnabled = true
    }

    private fun setupPasswordToggle(view: View, editTextId: Int) {
        val editText = view.findViewById<EditText>(editTextId)
        var isVisible = false

        val initialDrawable = androidx.core.content.ContextCompat.getDrawable(
            requireContext(), R.drawable.ic_eye_off
        )?.mutate()
        initialDrawable?.setTint(android.graphics.Color.parseColor("#AAAAAA"))
        editText.setCompoundDrawablesWithIntrinsicBounds(null, null, initialDrawable, null)

        editText.setOnTouchListener { _, event ->
            if (event.action == android.view.MotionEvent.ACTION_UP) {
                val drawableEnd = editText.compoundDrawables[2]
                if (drawableEnd != null &&
                    event.rawX >= editText.right - editText.paddingEnd - drawableEnd.bounds.width()
                ) {
                    isVisible = !isVisible

                    val drawableRes = if (isVisible) R.drawable.ic_eye_on else R.drawable.ic_eye_off
                    val drawable = androidx.core.content.ContextCompat.getDrawable(
                        requireContext(), drawableRes
                    )?.mutate()
                    drawable?.setTint(android.graphics.Color.parseColor("#AAAAAA"))

                    editText.inputType = if (isVisible) {
                        android.text.InputType.TYPE_CLASS_TEXT or
                                android.text.InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD
                    } else {
                        android.text.InputType.TYPE_CLASS_TEXT or
                                android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD
                    }

                    editText.setCompoundDrawablesWithIntrinsicBounds(null, null, drawable, null)
                    editText.setSelection(editText.text.length)
                    true
                } else false
            } else false
        }
    }
}