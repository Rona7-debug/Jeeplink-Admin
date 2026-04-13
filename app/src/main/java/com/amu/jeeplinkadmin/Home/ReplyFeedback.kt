package com.amu.jeeplinkadmin.Home

import android.content.res.ColorStateList
import android.graphics.Color
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.widget.SwitchCompat
import androidx.cardview.widget.CardView
import androidx.fragment.app.Fragment
import com.amu.jeeplinkadmin.R
import com.google.firebase.Timestamp
import com.google.firebase.firestore.FirebaseFirestore

class ReplyFeedback : Fragment() {

    companion object {
        private const val ARG_DOC_ID             = "docId"
        private const val ARG_USERNAME           = "userName"
        private const val ARG_FEEDBACK           = "feedbackText"
        private const val ARG_RATING             = "rating"
        private const val ARG_STATUS             = "status"
        private const val ARG_CATEGORIES         = "categories"
        private const val ARG_TIME               = "timeAgo"
        private const val ARG_EXISTING_REPLY     = "existingReply"
        private const val ARG_EXISTING_REPLY_ID  = "existingReplyDocId"

        fun newInstance(
            docId: String,
            userName: String,
            feedbackText: String,
            rating: Int,
            status: String,
            categories: String,
            timeAgo: String,
            existingReply: String = "",
            existingReplyDocId: String = ""
        ): ReplyFeedback {
            return ReplyFeedback().apply {
                arguments = Bundle().apply {
                    putString(ARG_DOC_ID,            docId)
                    putString(ARG_USERNAME,          userName)
                    putString(ARG_FEEDBACK,          feedbackText)
                    putInt(ARG_RATING,               rating)
                    putString(ARG_STATUS,            status)
                    putString(ARG_CATEGORIES,        categories)
                    putString(ARG_TIME,              timeAgo)
                    putString(ARG_EXISTING_REPLY,    existingReply)
                    putString(ARG_EXISTING_REPLY_ID, existingReplyDocId)
                }
            }
        }
    }

    private val db = FirebaseFirestore.getInstance()

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_reply_feedback, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val docId              = arguments?.getString(ARG_DOC_ID)            ?: return
        val userName           = arguments?.getString(ARG_USERNAME)           ?: "Anonymous"
        val feedbackTx         = arguments?.getString(ARG_FEEDBACK)           ?: ""
        val rating             = arguments?.getInt(ARG_RATING)                ?: 0
        val status             = arguments?.getString(ARG_STATUS)             ?: ""
        val categories         = arguments?.getString(ARG_CATEGORIES)         ?: ""
        val timeAgo            = arguments?.getString(ARG_TIME)               ?: ""
        val existingReply      = arguments?.getString(ARG_EXISTING_REPLY)     ?: ""
        val existingReplyDocId = arguments?.getString(ARG_EXISTING_REPLY_ID)  ?: ""

        val isEditing = existingReply.isNotEmpty()

        val btnBack        = view.findViewById<ImageView>(R.id.btnBack)
        val tvAvatar       = view.findViewById<TextView>(R.id.tvAvatar)
        val cvAvatar       = view.findViewById<CardView>(R.id.cvAvatar)
        val tvUserName     = view.findViewById<TextView>(R.id.tvUserName)
        val tvTimeAgo      = view.findViewById<TextView>(R.id.tvTimeAgo)
        val tvStatusBadge  = view.findViewById<TextView>(R.id.tvStatusBadge)
        val tvStars        = view.findViewById<TextView>(R.id.tvStars)
        val tvCategoriesTv = view.findViewById<TextView>(R.id.tvCategories)
        val tvFeedbackText = view.findViewById<TextView>(R.id.tvFeedbackText)
        val etReply        = view.findViewById<EditText>(R.id.etReply)
        val tvCharCount    = view.findViewById<TextView>(R.id.tvCharCount)
        val switchResolved = view.findViewById<SwitchCompat>(R.id.switchResolved)
        val btnCancel      = view.findViewById<CardView>(R.id.btnCancel)
        val btnSendReply   = view.findViewById<CardView>(R.id.btnSendReply)
        val qaThankYou     = view.findViewById<TextView>(R.id.qaThankYou)
        val qaIssueFixed   = view.findViewById<TextView>(R.id.qaIssueFixed)
        val qaNeedMoreInfo = view.findViewById<TextView>(R.id.qaNeedMoreInfo)
        val qaUnderReview  = view.findViewById<TextView>(R.id.qaUnderReview)

        if (isEditing) {
            etReply.setText(existingReply)
            etReply.setSelection(existingReply.length)
            tvCharCount.text = "${existingReply.length} / 500"
        }

        val initial = userName.firstOrNull()?.uppercaseChar()?.toString() ?: "?"
        cvAvatar.setCardBackgroundColor(Color.parseColor("#1246C2"))
        tvAvatar.text = initial
        tvAvatar.setTextColor(Color.WHITE)
        tvUserName.text = userName
        tvTimeAgo.text  = timeAgo
        tvStars.text    = "★".repeat(rating) + "☆".repeat(5 - rating)

        tvCategoriesTv.apply {
            text       = categories
            visibility = if (categories.isNotEmpty()) View.VISIBLE else View.GONE
        }
        tvFeedbackText.apply {
            text       = feedbackTx
            visibility = if (feedbackTx.isNotEmpty()) View.VISIBLE else View.GONE
        }

        val effectiveStatus = when {
            status == "resolved" -> "resolved"
            status == "archived" -> "archived"
            isEditing            -> "pending"
            status == "pending"  -> "pending"
            else                 -> "new"
        }

        when (effectiveStatus) {
            "resolved" -> {
                tvStatusBadge.text = "RESOLVED"
                tvStatusBadge.setTextColor(Color.parseColor("#047857"))
                tvStatusBadge.setBackgroundResource(R.drawable.badge_resolved)
                switchResolved.isChecked = true
            }
            "archived" -> {
                tvStatusBadge.text = "ARCHIVED"
                tvStatusBadge.setTextColor(Color.parseColor("#475569"))
                tvStatusBadge.setBackgroundResource(R.drawable.badge_archived)
            }
            "pending"  -> {
                tvStatusBadge.text = "PENDING"
                tvStatusBadge.setTextColor(Color.parseColor("#D97706"))
                tvStatusBadge.setBackgroundResource(R.drawable.badge_archived)
            }
            else       -> {
                tvStatusBadge.text = "NEW"
                tvStatusBadge.setTextColor(Color.parseColor("#C2410C"))
                tvStatusBadge.setBackgroundResource(R.drawable.badge_new)
            }
        }

        val quickActions = mapOf(
            qaThankYou     to "Thank you for reporting this! We appreciate your feedback.",
            qaIssueFixed   to "Great news! The issue you reported has been fixed.",
            qaNeedMoreInfo to "Could you provide more details so we can better assist you?",
            qaUnderReview  to "Your feedback is currently under review by our team."
        )

        quickActions.forEach { (chip, text) ->
            chip.setOnClickListener {
                etReply.setText(text)
                etReply.setSelection(text.length)
                quickActions.keys.forEach { c ->
                    c.setBackgroundResource(R.drawable.chip_outline_blue)
                    c.setTextColor(Color.parseColor("#1246C2"))
                }
                chip.setBackgroundResource(R.drawable.chip_filled_blue)
                chip.setTextColor(Color.WHITE)
            }
        }

        etReply.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                val len = s?.length ?: 0
                tvCharCount.text = "$len / 500"
                tvCharCount.setTextColor(
                    Color.parseColor(if (len >= 500) "#E03C3C" else "#9EA1A8")
                )
                if (len > 500) s?.delete(500, s.length)
            }
        })

        btnBack.setOnClickListener   { parentFragmentManager.popBackStack() }
        btnCancel.setOnClickListener { parentFragmentManager.popBackStack() }
        btnSendReply.setOnClickListener {
            val replyText = etReply.text.toString().trim()
            if (replyText.isEmpty()) {
                Toast.makeText(requireContext(), "Please type a reply.", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            val markResolved = switchResolved.isChecked
            val newStatus = when {
                markResolved         -> "resolved"
                status == "resolved" -> "resolved"
                status == "archived" -> "archived"
                else                 -> "pending"
            }

            btnSendReply.isEnabled = false
            val docRef = db.collection("feedback").document(docId)

            if (isEditing && existingReplyDocId.isNotEmpty()) {
                docRef.collection("replies").document(existingReplyDocId)
                    .update(
                        "replyText", replyText,
                        "repliedAt", Timestamp.now()
                    )
                    .addOnSuccessListener {
                        docRef.update(mapOf(
                            "status"    to newStatus,
                            "hasReply"  to true,
                            "repliedAt" to Timestamp.now()
                        ))
                            .addOnSuccessListener {
                                if (!isAdded) return@addOnSuccessListener
                                btnSendReply.isEnabled = true
                                Toast.makeText(requireContext(), "Reply updated!", Toast.LENGTH_SHORT).show()
                                parentFragmentManager.popBackStack()
                            }
                            .addOnFailureListener { e ->
                                btnSendReply.isEnabled = true
                                Toast.makeText(requireContext(), "Error: ${e.message}", Toast.LENGTH_SHORT).show()
                            }
                    }
                    .addOnFailureListener { e ->
                        btnSendReply.isEnabled = true
                        Toast.makeText(requireContext(), "Failed to update: ${e.message}", Toast.LENGTH_SHORT).show()
                    }
            } else {
                docRef.collection("replies")
                    .add(hashMapOf(
                        "replyText" to replyText,
                        "repliedAt" to Timestamp.now(),
                        "repliedBy" to "admin",
                        "status"    to newStatus
                    ))
                    .addOnSuccessListener {
                        docRef.update(mapOf(
                            "status"    to newStatus,
                            "hasReply"  to true,
                            "repliedAt" to Timestamp.now()
                        ))
                            .addOnSuccessListener {
                                if (!isAdded) return@addOnSuccessListener
                                btnSendReply.isEnabled = true
                                Toast.makeText(requireContext(), "Reply sent!", Toast.LENGTH_SHORT).show()
                                parentFragmentManager.popBackStack()
                            }
                            .addOnFailureListener { e ->
                                btnSendReply.isEnabled = true
                                Toast.makeText(requireContext(), "Error: ${e.message}", Toast.LENGTH_SHORT).show()
                            }
                    }
                    .addOnFailureListener { e ->
                        btnSendReply.isEnabled = true
                        Toast.makeText(requireContext(), "Failed to send: ${e.message}", Toast.LENGTH_SHORT).show()
                    }
            }
        }
    }
}