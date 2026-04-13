package com.amu.jeeplinkadmin.Home

import android.animation.ObjectAnimator
import android.graphics.Color
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.animation.DecelerateInterpolator
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.fragment.app.Fragment
import com.amu.jeeplinkadmin.R
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.Query
import java.text.SimpleDateFormat
import java.util.Locale

class Notifications : Fragment() {

    private val listeners = mutableListOf<ListenerRegistration>()
    private var hasAddedFeedbackHeader = false
    private var hasAddedPremiumHeader = false

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_notifications, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        view.findViewById<ImageView>(R.id.btnBack).setOnClickListener {
            parentFragmentManager.popBackStack()
        }

        view.findViewById<TextView>(R.id.btnMarkAllRead)?.setOnClickListener {
            markAllAsRead()
        }

        loadNotifications(view)
    }

    private fun loadNotifications(view: View) {
        val container = view.findViewById<LinearLayout>(R.id.notificationsContainer)
        val db = FirebaseFirestore.getInstance()

        val dateFormat = SimpleDateFormat("MMM dd, yyyy • hh:mm a", Locale.getDefault())

        val feedbackListener = db.collection("feedback")
            .orderBy("submittedAt", Query.Direction.DESCENDING)
            .limit(20)
            .addSnapshotListener { snapshot, _ ->
                if (!isAdded || snapshot == null) return@addSnapshotListener

                container.removeAllViews()
                hasAddedFeedbackHeader = false
                hasAddedPremiumHeader = false

                snapshot.documents.forEach { doc ->

                    val hasReply = doc.getBoolean("hasReply") ?: false
                    if (hasReply) return@forEach // 🔥 ONLY NEW

                    if (!hasAddedFeedbackHeader) {
                        addSectionTitle(container, "New Feedback")
                        hasAddedFeedbackHeader = true
                    }

                    val message = doc.getString("feedback") ?: return@forEach
                    val timestamp = doc.getTimestamp("submittedAt")

                    val timeStr = timestamp?.let {
                        dateFormat.format(it.toDate())
                    } ?: "—"

                    addNotificationItem(
                        container = container,
                        iconRes = R.drawable.ic_feedback_blue,
                        title = "New Feedback",
                        message = message,
                        time = timeStr,
                        accentColor = "#1246C2",
                        docId = doc.id
                    )
                }

                loadPremiumNotifications(container, dateFormat)
            }

        listeners.add(feedbackListener)
    }

    private fun loadPremiumNotifications(
        container: LinearLayout,
        dateFormat: SimpleDateFormat
    ) {
        val db = FirebaseFirestore.getInstance()

        val premiumListener = db.collection("users")
            .whereEqualTo("isPremium", true)
            .orderBy("premiumSince", Query.Direction.DESCENDING)
            .limit(20)
            .addSnapshotListener { snapshot, _ ->
                if (!isAdded || snapshot == null) return@addSnapshotListener

                snapshot.documents.forEach { doc ->

                    if (!hasAddedPremiumHeader) {
                        addSectionTitle(container, "Premium")
                        hasAddedPremiumHeader = true
                    }

                    val plan = doc.getString("premiumPlan") ?: "monthly"
                    val price = doc.getLong("premiumPrice") ?: 49L

                    val timestamp = doc.getTimestamp("premiumSince")

                    val timeStr = timestamp?.let {
                        dateFormat.format(it.toDate())
                    } ?: "—"

                    addNotificationItem(
                        container = container,
                        iconRes = R.drawable.ic_star_premium,
                        title = "New Premium Subscriber",
                        message = "User subscribed to ${
                            plan.replaceFirstChar { it.uppercase() }
                        } plan — ₱$price",
                        time = timeStr,
                        accentColor = "#F59E0B"
                    )
                }
            }

        listeners.add(premiumListener)
    }

    private fun addSectionTitle(container: LinearLayout, title: String) {
        val tv = TextView(requireContext()).apply {
            text = title
            textSize = 14f
            setTextColor(Color.parseColor("#64748B"))
            setPadding(16, 24, 16, 8)
        }
        container.addView(tv)
    }

    private fun addNotificationItem(
        container: LinearLayout,
        iconRes: Int,
        title: String,
        message: String,
        time: String,
        accentColor: String,
        docId: String? = null
    ) {
        val itemView = LayoutInflater.from(requireContext())
            .inflate(R.layout.item_notification, container, false)

        val iconView = itemView.findViewById<ImageView>(R.id.ivNotifIcon)

        iconView.setImageResource(iconRes)
        iconView.setColorFilter(Color.parseColor(accentColor))

        itemView.findViewById<TextView>(R.id.tvNotifTitle).text = title
        itemView.findViewById<TextView>(R.id.tvNotifMessage).text = message
        itemView.findViewById<TextView>(R.id.tvNotifTime).text = time
        itemView.findViewById<View>(R.id.vAccent)
            .setBackgroundColor(Color.parseColor(accentColor))

        // 🔥 CLICK → OPEN FEEDBACK
        itemView.setOnClickListener {
            if (docId != null) {
                parentFragmentManager.beginTransaction()
                    .replace(
                        R.id.fragmentContainer,
                        ReplyFeedback.newInstance(
                            docId = docId,
                            userName = "User",
                            feedbackText = message,
                            rating = 0,
                            status = "new",
                            categories = "",
                            timeAgo = time
                        )
                    )
                    .addToBackStack(null)
                    .commit()
            }
        }

        // ✨ animation
        itemView.alpha = 0f
        itemView.translationY = 40f

        container.addView(itemView)

        itemView.animate()
            .alpha(1f)
            .translationY(0f)
            .setDuration(300)
            .setInterpolator(DecelerateInterpolator())
            .start()

        ObjectAnimator.ofFloat(iconView, "scaleX", 0.8f, 1f).apply {
            duration = 250
            start()
        }
        ObjectAnimator.ofFloat(iconView, "scaleY", 0.8f, 1f).apply {
            duration = 250
            start()
        }

        val divider = View(requireContext()).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 1
            ).apply { setMargins(16, 0, 16, 0) }
            setBackgroundColor(Color.parseColor("#F0F0F0"))
        }

        container.addView(divider)
    }

    private fun markAllAsRead() {
        val db = FirebaseFirestore.getInstance()

        db.collection("feedback")
            .whereEqualTo("hasReply", false)
            .get()
            .addOnSuccessListener { snapshot ->
                snapshot.documents.forEach { doc ->
                    db.collection("feedback").document(doc.id)
                        .update("hasReply", true)
                }
            }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        listeners.forEach { it.remove() }
        listeners.clear()
    }
}