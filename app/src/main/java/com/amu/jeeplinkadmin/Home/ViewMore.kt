package com.amu.jeeplinkadmin.Home

import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.Typeface
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.cardview.widget.CardView
import androidx.fragment.app.Fragment
import com.amu.jeeplinkadmin.R
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query

class ViewMore : Fragment() {

    companion object {
        private const val ARG_DOC_ID     = "docId"
        private const val ARG_USERNAME   = "userName"
        private const val ARG_FEEDBACK   = "feedbackText"
        private const val ARG_RATING     = "rating"
        private const val ARG_STATUS     = "status"
        private const val ARG_CATEGORIES = "categories"
        private const val ARG_TIME       = "timeAgo"

        fun newInstance(
            docId: String,
            userName: String,
            feedbackText: String,
            rating: Int,
            status: String,
            categories: String,
            timeAgo: String
        ): ViewMore {
            return ViewMore().apply {
                arguments = Bundle().apply {
                    putString(ARG_DOC_ID,     docId)
                    putString(ARG_USERNAME,   userName)
                    putString(ARG_FEEDBACK,   feedbackText)
                    putInt(ARG_RATING,        rating)
                    putString(ARG_STATUS,     status)
                    putString(ARG_CATEGORIES, categories)
                    putString(ARG_TIME,       timeAgo)
                }
            }
        }
    }

    private val db = FirebaseFirestore.getInstance()

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_view_more, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val docId      = arguments?.getString(ARG_DOC_ID)     ?: return
        val userName   = arguments?.getString(ARG_USERNAME)   ?: "Anonymous"
        val feedbackTx = arguments?.getString(ARG_FEEDBACK)   ?: ""
        val rating     = arguments?.getInt(ARG_RATING)        ?: 0
        val status     = arguments?.getString(ARG_STATUS)     ?: ""
        val categories = arguments?.getString(ARG_CATEGORIES) ?: ""
        val timeAgo    = arguments?.getString(ARG_TIME)       ?: ""

        val btnBack             = view.findViewById<ImageView>(R.id.btnBack)
        val cvAvatar            = view.findViewById<CardView>(R.id.cvAvatar)
        val tvAvatar            = view.findViewById<TextView>(R.id.tvAvatar)
        val tvUserName          = view.findViewById<TextView>(R.id.tvUserName)
        val tvTimeAgo           = view.findViewById<TextView>(R.id.tvTimeAgo)
        val tvStars             = view.findViewById<TextView>(R.id.tvStars)
        val tvRatingLabel       = view.findViewById<TextView>(R.id.tvRatingLabel)
        val tvFeedbackText      = view.findViewById<TextView>(R.id.tvFeedbackText)
        val tvStatusBadge       = view.findViewById<TextView>(R.id.tvStatusBadge)
        val categoriesContainer = view.findViewById<LinearLayout>(R.id.categoriesContainer)
        val btnReplyToReview    = view.findViewById<CardView>(R.id.btnReplyToReview)
        val btnPending          = view.findViewById<CardView>(R.id.btnPending)
        val btnArchive          = view.findViewById<CardView>(R.id.btnArchive)
        val tvAdminReplySection = view.findViewById<TextView>(R.id.tvAdminReply)

        // ─── Avatar ───────────────────────────────────────────────────
        val initial = userName.firstOrNull()?.uppercaseChar()?.toString() ?: "?"
        cvAvatar.setCardBackgroundColor(Color.parseColor("#1246C2"))
        tvAvatar.text = initial
        tvAvatar.setTextColor(Color.WHITE)

        tvUserName.text    = userName
        tvTimeAgo.text     = timeAgo
        tvStars.text       = "★".repeat(rating) + "☆".repeat(5 - rating)
        tvRatingLabel.text = String.format("%.1f Rating", rating.toFloat())

        tvFeedbackText.apply {
            text       = feedbackTx
            visibility = if (feedbackTx.isNotEmpty()) View.VISIBLE else View.GONE
        }

        db.collection("feedback").document(docId).get()
            .addOnSuccessListener { doc ->
                if (!isAdded || doc == null) return@addOnSuccessListener

                val realStatus   = doc.getString("status")?.lowercase()?.trim() ?: ""
                val hasReply     = doc.getBoolean("hasReply") ?: false

                val bucket = when {
                    realStatus == "resolved"                              -> "resolved"
                    realStatus == "archived"                             -> "archived"
                    hasReply && realStatus != "resolved"
                            && realStatus != "archived"                 -> "pending"
                    realStatus == "pending"                              -> "pending"
                    else                                                 -> "new"
                }

                tvStatusBadge?.apply {
                    when (bucket) {
                        "resolved" -> { text = "RESOLVED"; setTextColor(Color.parseColor("#047857")); setBackgroundResource(R.drawable.badge_resolved) }
                        "archived" -> { text = "ARCHIVED"; setTextColor(Color.parseColor("#475569")); setBackgroundResource(R.drawable.badge_archived) }
                        "pending"  -> { text = "PENDING";  setTextColor(Color.parseColor("#D97706")); setBackgroundResource(R.drawable.badge_archived) }
                        else       -> { text = "NEW";      setTextColor(Color.parseColor("#C2410C")); setBackgroundResource(R.drawable.badge_new)      }
                    }
                    visibility = View.VISIBLE
                }

                btnPending?.visibility = if (bucket == "new") View.VISIBLE else View.GONE
                // Don't show "Archive" if already archived
                btnArchive?.visibility = if (bucket != "archived") View.VISIBLE else View.GONE
            }

        if (categories.isNotEmpty()) {
            categories.split(" • ").forEach { cat ->
                val chip = TextView(requireContext()).apply {
                    text = cat.trim()
                    setTextColor(Color.parseColor("#1246C2"))
                    textSize = 13f
                    typeface = Typeface.DEFAULT
                    setPadding(28, 14, 28, 14)
                    setBackgroundResource(R.drawable.chip_outline_blue)
                }
                val params = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply { marginEnd = 8 }
                categoriesContainer.addView(chip, params)
            }
        }

        db.collection("feedback").document(docId)
            .collection("replies")
            .orderBy("repliedAt", Query.Direction.DESCENDING)
            .limit(1)
            .get()
            .addOnSuccessListener { replySnapshot ->
                if (!isAdded) return@addOnSuccessListener
                val latestReplyDoc  = replySnapshot.documents.firstOrNull()
                val latestReplyText = latestReplyDoc?.getString("replyText") ?: ""
                val latestReplyId   = latestReplyDoc?.id ?: ""

                tvAdminReplySection?.apply {
                    if (latestReplyText.isNotEmpty()) {
                        text       = "💬 Admin: $latestReplyText"
                        visibility = View.VISIBLE
                    } else {
                        visibility = View.GONE
                    }
                }

                btnReplyToReview?.setOnClickListener {
                    parentFragmentManager.beginTransaction()
                        .replace(R.id.fragmentContainer, ReplyFeedback.newInstance(
                            docId              = docId,
                            userName           = userName,
                            feedbackText       = feedbackTx,
                            rating             = rating,
                            status             = status,
                            categories         = categories,
                            timeAgo            = timeAgo,
                            existingReply      = latestReplyText,
                            existingReplyDocId = latestReplyId
                        ))
                        .addToBackStack(null)
                        .commit()
                }
            }
            .addOnFailureListener {
                btnReplyToReview?.setOnClickListener {
                    parentFragmentManager.beginTransaction()
                        .replace(R.id.fragmentContainer, ReplyFeedback.newInstance(
                            docId              = docId,
                            userName           = userName,
                            feedbackText       = feedbackTx,
                            rating             = rating,
                            status             = status,
                            categories         = categories,
                            timeAgo            = timeAgo,
                            existingReply      = "",
                            existingReplyDocId = ""
                        ))
                        .addToBackStack(null)
                        .commit()
                }
            }

        btnBack.setOnClickListener { parentFragmentManager.popBackStack() }

        btnPending?.setOnClickListener {
            db.collection("feedback").document(docId)
                .collection("replies").limit(1).get()
                .addOnSuccessListener { snap ->
                    if (!isAdded) return@addOnSuccessListener
                    if (snap.isEmpty) {
                        Toast.makeText(requireContext(),
                            "Cannot mark as pending without a reply first.",
                            Toast.LENGTH_SHORT).show()
                    } else {
                        updateStatus(docId, "pending", hasReply = true)
                    }
                }
        }

        btnArchive?.setOnClickListener {
            updateStatus(docId, "archived", hasReply = false)
        }
    }

    private fun updateStatus(docId: String, newStatus: String, hasReply: Boolean) {
        val updates = mutableMapOf<String, Any>(
            "status" to newStatus
        )
        if (newStatus == "pending") updates["hasReply"] = true

        db.collection("feedback").document(docId)
            .update(updates)
            .addOnSuccessListener {
                if (!isAdded) return@addOnSuccessListener
                Toast.makeText(
                    requireContext(),
                    "Marked as ${newStatus.replaceFirstChar { it.uppercase() }}",
                    Toast.LENGTH_SHORT
                ).show()
                parentFragmentManager.popBackStack()
            }
            .addOnFailureListener { e ->
                Toast.makeText(requireContext(), "Error: ${e.message}", Toast.LENGTH_SHORT).show()
            }
    }
}