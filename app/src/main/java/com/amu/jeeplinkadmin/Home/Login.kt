package com.amu.jeeplinkadmin.Home

import android.os.Bundle
import android.text.method.HideReturnsTransformationMethod
import android.text.method.PasswordTransformationMethod
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.Fragment
import com.amu.jeeplinkadmin.R
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore

class Login : Fragment() {

    private var isPasswordVisible = false

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_login, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val etEmail          = view.findViewById<EditText>(R.id.etUsername)   // reuse same ID or rename
        val etPassword       = view.findViewById<EditText>(R.id.etPassword)
        val btnLogin         = view.findViewById<TextView>(R.id.btnLogin)
        val ivTogglePassword = view.findViewById<ImageView>(R.id.ivTogglePassword)

        ivTogglePassword.setOnClickListener {
            isPasswordVisible = !isPasswordVisible
            if (isPasswordVisible) {
                etPassword.transformationMethod = HideReturnsTransformationMethod.getInstance()
                ivTogglePassword.setImageResource(R.drawable.ic_eye_on)
            } else {
                etPassword.transformationMethod = PasswordTransformationMethod.getInstance()
                ivTogglePassword.setImageResource(R.drawable.ic_eye_off)
            }
            etPassword.setSelection(etPassword.text.length)
        }

        btnLogin.setOnClickListener {
            val email    = etEmail.text.toString().trim()
            val password = etPassword.text.toString().trim()

            when {
                email.isBlank() -> {
                    etEmail.error = "Please enter your email"
                    return@setOnClickListener
                }
                !android.util.Patterns.EMAIL_ADDRESS.matcher(email).matches() -> {
                    etEmail.error = "Please enter a valid email"
                    return@setOnClickListener
                }
                password.isBlank() -> {
                    etPassword.error = "Please enter your password"
                    return@setOnClickListener
                }
            }

            btnLogin.text = "Logging in..."
            btnLogin.isEnabled = false

            FirebaseAuth.getInstance()
                .signInWithEmailAndPassword(email, password)
                .addOnSuccessListener { authResult ->
                    if (!isAdded) return@addOnSuccessListener

                    val uid  = authResult.user?.uid ?: run {
                        handleLoginFailure(btnLogin, "Authentication error. Try again.")
                        return@addOnSuccessListener
                    }

                    FirebaseFirestore.getInstance()
                        .collection("users")
                        .document(uid)
                        .get()
                        .addOnSuccessListener { doc ->
                            if (!isAdded) return@addOnSuccessListener

                            val role = doc.getString("role")

                            if (role == "admin") {
                                val username = doc.getString("username")
                                    ?.takeIf { it.isNotBlank() }
                                    ?: "Admin"

                                Toast.makeText(
                                    requireContext(),
                                    "Welcome, $username!",
                                    Toast.LENGTH_SHORT
                                ).show()

                                parentFragmentManager.beginTransaction()
                                    .replace(R.id.fragmentContainer, Dashboard())
                                    .commit()

                            } else {
                                FirebaseAuth.getInstance().signOut()
                                handleLoginFailure(btnLogin, "Access denied. Admins only.")
                            }
                        }
                        .addOnFailureListener { e ->
                            if (!isAdded) return@addOnFailureListener
                            FirebaseAuth.getInstance().signOut()
                            handleLoginFailure(btnLogin, "Failed to verify role: ${e.message}")
                        }
                }
                .addOnFailureListener { e ->
                    if (!isAdded) return@addOnFailureListener
                    handleLoginFailure(btnLogin, "Login failed: ${e.message}")
                }
        }
    }

    private fun handleLoginFailure(btnLogin: TextView, message: String) {
        btnLogin.text = "Login"
        btnLogin.isEnabled = true
        Toast.makeText(requireContext(), message, Toast.LENGTH_LONG).show()
    }
}