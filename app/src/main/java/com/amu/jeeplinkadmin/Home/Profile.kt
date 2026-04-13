package com.amu.jeeplinkadmin.Home

import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.Typeface
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.Switch
import android.widget.TextView
import android.widget.Toast
import androidx.cardview.widget.CardView
import androidx.core.content.FileProvider
import androidx.fragment.app.Fragment
import com.amu.jeeplinkadmin.R
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import java.io.File
import java.io.FileWriter

class Profile : Fragment() {

    private val db   = FirebaseFirestore.getInstance()
    private val auth = FirebaseAuth.getInstance()

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_profile, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val tvAvatar          = view.findViewById<TextView>(R.id.tvAvatar)
        val tvUsername        = view.findViewById<TextView>(R.id.tvUsername)
        val tvUsernameVal     = view.findViewById<TextView>(R.id.tvUsernameValue)
        val tvEmailVal        = view.findViewById<TextView>(R.id.tvEmailValue)
        val rowPassword       = view.findViewById<LinearLayout>(R.id.rowPassword)
        val rowNotifications  = view.findViewById<LinearLayout>(R.id.rowNotifications)
        val rowExportRoutes   = view.findViewById<LinearLayout>(R.id.rowExportRoutes)
        val btnSignOut        = view.findViewById<CardView>(R.id.btnSignOut)

        val currentUser = auth.currentUser
        val uid         = currentUser?.uid

        tvEmailVal.text = currentUser?.email ?: "—"

        val displayName = currentUser?.displayName?.takeIf { it.isNotBlank() }

        if (displayName != null) {
            applyUsername(displayName, tvAvatar, tvUsername, tvUsernameVal)
        } else if (uid != null) {
            db.collection("users").document(uid).get()
                .addOnSuccessListener { doc ->
                    if (!isAdded) return@addOnSuccessListener
                    val username = doc.getString("username") ?: "Admin"
                    applyUsername(username, tvAvatar, tvUsername, tvUsernameVal)
                }
                .addOnFailureListener {
                    applyUsername("Admin", tvAvatar, tvUsername, tvUsernameVal)
                }
        }

        rowPassword.setOnClickListener {
            parentFragmentManager.beginTransaction()
                .replace(R.id.fragmentContainer, ChangePassword())
                .addToBackStack(null)
                .commit()
        }

        rowNotifications.setOnClickListener {
            showNotificationsDialog()
        }

        rowExportRoutes.setOnClickListener {
            exportRoutesToCsv()
        }

        btnSignOut.setOnClickListener {
            val dialogView = LayoutInflater.from(requireContext())
                .inflate(R.layout.dialog_sign_out, null)

            val dialog = AlertDialog.Builder(requireContext())
                .setView(dialogView)
                .setCancelable(true)
                .create()
            dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)

            dialogView.findViewById<com.google.android.material.card.MaterialCardView>(R.id.btnCancel)
                .setOnClickListener { dialog.dismiss() }

            dialogView.findViewById<com.google.android.material.card.MaterialCardView>(R.id.btnConfirmSignOut)
                .setOnClickListener {
                    auth.signOut()
                    Toast.makeText(requireContext(), "Signed out successfully", Toast.LENGTH_SHORT).show()
                    dialog.dismiss()
                    parentFragmentManager.beginTransaction()
                        .replace(R.id.fragmentContainer, Login())
                        .commit()
                }

            dialog.show()
        }

        val navHome     = view.findViewById<LinearLayout>(R.id.navHome)
        val navRoutes   = view.findViewById<LinearLayout>(R.id.navRoutes)
        val navFeedback = view.findViewById<LinearLayout>(R.id.navFeedback)
        val navSettings = view.findViewById<LinearLayout>(R.id.navSettings)
        val fabAdd      = view.findViewById<CardView>(R.id.fabAdd)

        val ivNavHome     = view.findViewById<ImageView>(R.id.ivNavHome)
        val ivNavRoutes   = view.findViewById<ImageView>(R.id.ivNavRoutes)
        val ivNavFeedback = view.findViewById<ImageView>(R.id.ivNavFeedback)
        val ivNavSettings = view.findViewById<ImageView>(R.id.ivNavSettings)

        val tvNavHome     = view.findViewById<TextView>(R.id.tvNavHome)
        val tvNavRoutes   = view.findViewById<TextView>(R.id.tvNavRoutes)
        val tvNavFeedback = view.findViewById<TextView>(R.id.tvNavFeedback)
        val tvNavSettings = view.findViewById<TextView>(R.id.tvNavSettings)

        val allLayouts = listOf(navHome, navRoutes, navFeedback, navSettings)
        val allIcons   = listOf(ivNavHome, ivNavRoutes, ivNavFeedback, ivNavSettings)
        val allLabels  = listOf(tvNavHome, tvNavRoutes, tvNavFeedback, tvNavSettings)

        setNavActive(navSettings, ivNavSettings, tvNavSettings, allLayouts, allIcons, allLabels)

        navHome.setOnClickListener {
            setNavActive(navHome, ivNavHome, tvNavHome, allLayouts, allIcons, allLabels)
            parentFragmentManager.beginTransaction()
                .replace(R.id.fragmentContainer, Dashboard()).commit()
        }
        navRoutes.setOnClickListener {
            setNavActive(navRoutes, ivNavRoutes, tvNavRoutes, allLayouts, allIcons, allLabels)
            parentFragmentManager.beginTransaction()
                .replace(R.id.fragmentContainer, Routes())
                .addToBackStack(null).commit()
        }
        navFeedback.setOnClickListener {
            setNavActive(navFeedback, ivNavFeedback, tvNavFeedback, allLayouts, allIcons, allLabels)
            parentFragmentManager.beginTransaction()
                .replace(R.id.fragmentContainer, Feedback()).commit()
        }
        fabAdd.setOnClickListener {
            parentFragmentManager.beginTransaction()
                .replace(R.id.fragmentContainer, NewRoute())
                .addToBackStack(null).commit()
        }
    }

    private fun showNotificationsDialog() {
        val prefs     = requireContext().getSharedPreferences("admin_prefs", Context.MODE_PRIVATE)
        val isEnabled = prefs.getBoolean("notifications_enabled", true)

        val dialogView = LayoutInflater.from(requireContext())
            .inflate(R.layout.dialog_notifications, null)

        val dialog = AlertDialog.Builder(requireContext())
            .setView(dialogView)
            .setCancelable(true)
            .create()
        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)

        val switchNotif = dialogView.findViewById<Switch>(R.id.switchNotifications)
        val btnDone     = dialogView.findViewById<com.google.android.material.card.MaterialCardView>(R.id.btnDone)

        switchNotif.isChecked = isEnabled

        btnDone.setOnClickListener {
            prefs.edit()
                .putBoolean("notifications_enabled", switchNotif.isChecked)
                .apply()
            val msg = if (switchNotif.isChecked) "Notifications enabled" else "Notifications disabled"
            Toast.makeText(requireContext(), msg, Toast.LENGTH_SHORT).show()
            dialog.dismiss()
        }

        dialog.show()
    }

    private fun exportRoutesToCsv() {
        Toast.makeText(requireContext(), "Preparing export...", Toast.LENGTH_SHORT).show()

        db.collection("jeepney_routes")
            .whereEqualTo("isActive", true)
            .get()
            .addOnSuccessListener { snapshot ->
                if (!isAdded) return@addOnSuccessListener

                if (snapshot.isEmpty) {
                    Toast.makeText(requireContext(), "No routes to export", Toast.LENGTH_SHORT).show()
                    return@addOnSuccessListener
                }

                try {
                    val fileName = "jeeplink_routes_${System.currentTimeMillis()}.csv"

                    val contentValues = android.content.ContentValues().apply {
                        put(android.provider.MediaStore.Downloads.DISPLAY_NAME, fileName)
                        put(android.provider.MediaStore.Downloads.MIME_TYPE, "text/csv")
                        put(android.provider.MediaStore.Downloads.IS_PENDING, 1)
                    }

                    val resolver  = requireContext().contentResolver
                    val collection = android.provider.MediaStore.Downloads
                        .getContentUri(android.provider.MediaStore.VOLUME_EXTERNAL_PRIMARY)
                    val itemUri = resolver.insert(collection, contentValues)

                    if (itemUri == null) {
                        Toast.makeText(requireContext(), "Failed to create file", Toast.LENGTH_SHORT).show()
                        return@addOnSuccessListener
                    }

                    resolver.openOutputStream(itemUri)?.use { outputStream ->
                        val writer = outputStream.bufferedWriter()

                        writer.write("Route Code,Route Name,PUJ Type,Fare Min,Fare Max,Landmarks\n")

                        snapshot.documents.forEach { doc ->
                            val code      = doc.getString("routeCode") ?: ""
                            val name      = doc.getString("routeName") ?: ""
                            val pujType   = doc.getString("pujType")   ?: ""
                            val fareMin   = doc.getLong("fareMin")     ?: 0
                            val fareMax   = doc.getLong("fareMax")     ?: 0
                            val landmarks = doc.getString("landmarks") ?.replace(",", ";") ?: ""
                            writer.write("$code,\"$name\",$pujType,$fareMin,$fareMax,\"$landmarks\"\n")
                        }

                        writer.flush()
                    }

                    contentValues.clear()
                    contentValues.put(android.provider.MediaStore.Downloads.IS_PENDING, 0)
                    resolver.update(itemUri, contentValues, null, null)

                    Toast.makeText(
                        requireContext(),
                        "Exported to Downloads: $fileName",
                        Toast.LENGTH_LONG
                    ).show()

                } catch (e: Exception) {
                    Toast.makeText(requireContext(), "Export failed: ${e.message}", Toast.LENGTH_LONG).show()
                }
            }
            .addOnFailureListener { e ->
                if (!isAdded) return@addOnFailureListener
                Toast.makeText(requireContext(), "Failed to fetch routes: ${e.message}", Toast.LENGTH_LONG).show()
            }
    }

    private fun applyUsername(
        name: String,
        tvAvatar: TextView,
        tvUsername: TextView,
        tvUsernameVal: TextView
    ) {
        tvAvatar.text      = name.firstOrNull()?.uppercaseChar()?.toString() ?: "A"
        tvUsername.text    = name
        tvUsernameVal.text = name
    }

    private fun setNavActive(
        activeLayout: LinearLayout, activeIcon: ImageView, activeLabel: TextView,
        allLayouts: List<LinearLayout>, allIcons: List<ImageView>, allLabels: List<TextView>
    ) {
        val blue = Color.parseColor("#1246C2")
        val gray = Color.parseColor("#94A3B8")
        allLayouts.forEach { it.setBackgroundColor(Color.TRANSPARENT) }
        allIcons.forEach   { it.imageTintList = ColorStateList.valueOf(gray) }
        allLabels.forEach  { it.setTextColor(gray); it.typeface = Typeface.DEFAULT }
        activeLayout.setBackgroundResource(R.drawable.nav_item_active_bg)
        activeIcon.imageTintList = ColorStateList.valueOf(blue)
        activeLabel.setTextColor(blue)
        activeLabel.typeface = Typeface.DEFAULT_BOLD
    }
}