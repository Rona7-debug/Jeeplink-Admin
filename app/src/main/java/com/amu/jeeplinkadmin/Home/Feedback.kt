package com.amu.jeeplinkadmin.Home

import android.app.AlertDialog
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.Typeface
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import androidx.cardview.widget.CardView
import androidx.fragment.app.Fragment
import com.amu.jeeplinkadmin.R
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query

class Feedback : Fragment() {

    private val db = FirebaseFirestore.getInstance()

    private var currentTab    = "all"
    private var currentDays   = 7
    private var currentRating = 0
    private var searchQuery   = ""

    private var allDocs: List<com.google.firebase.firestore.DocumentSnapshot> = emptyList()
    private val bucketCache = mutableMapOf<String, String>()

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_feedback, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val fabAdd      = view.findViewById<CardView>(R.id.fabAdd)
        val navHome     = view.findViewById<LinearLayout>(R.id.navHome)
        val navRoutes   = view.findViewById<LinearLayout>(R.id.navRoutes)
        val navFeedback = view.findViewById<LinearLayout>(R.id.navFeedback)
        val navSettings = view.findViewById<LinearLayout>(R.id.navSettings)
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

        setNavActive(navFeedback, ivNavFeedback, tvNavFeedback, allLayouts, allIcons, allLabels)

        navHome.setOnClickListener {
            setNavActive(navHome, ivNavHome, tvNavHome, allLayouts, allIcons, allLabels)
            parentFragmentManager.beginTransaction()
                .replace(R.id.fragmentContainer, Dashboard()).commit()
        }
        navRoutes.setOnClickListener {
            setNavActive(navRoutes, ivNavRoutes, tvNavRoutes, allLayouts, allIcons, allLabels)
            parentFragmentManager.beginTransaction()
                .replace(R.id.fragmentContainer, Routes()).addToBackStack(null).commit()
        }
        fabAdd.setOnClickListener {
            parentFragmentManager.beginTransaction()
                .replace(R.id.fragmentContainer, NewRoute()).addToBackStack(null).commit()
        }
        navSettings.setOnClickListener {
            setNavActive(navSettings, ivNavSettings, tvNavSettings, allLayouts, allIcons, allLabels)
            parentFragmentManager.beginTransaction()
                .replace(R.id.fragmentContainer, Profile()).addToBackStack(null).commit()
        }

        val btnSearch          = view.findViewById<ImageView>(R.id.btnSearch)
        val searchBarContainer = view.findViewById<LinearLayout>(R.id.searchBarContainer)
        val etFeedbackSearch   = view.findViewById<EditText>(R.id.etFeedbackSearch)
        val btnClearSearch     = view.findViewById<ImageView>(R.id.btnClearSearch)

        btnSearch.setOnClickListener {
            if (searchBarContainer.visibility == View.GONE) {
                searchBarContainer.visibility = View.VISIBLE
                etFeedbackSearch.requestFocus()
                val imm = requireContext().getSystemService(android.content.Context.INPUT_METHOD_SERVICE)
                        as android.view.inputmethod.InputMethodManager
                imm.showSoftInput(etFeedbackSearch, android.view.inputmethod.InputMethodManager.SHOW_IMPLICIT)
            } else {
                searchBarContainer.visibility = View.GONE
                etFeedbackSearch.setText("")
                searchQuery = ""
                applyFilters(view)
            }
        }

        btnClearSearch.setOnClickListener {
            etFeedbackSearch.setText("")
            searchQuery = ""
            btnClearSearch.visibility = View.GONE
            applyFilters(view)
        }

        etFeedbackSearch.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                searchQuery = s.toString().trim().lowercase()
                btnClearSearch.visibility = if (searchQuery.isEmpty()) View.GONE else View.VISIBLE
                applyFilters(view)
            }
        })

        val tabAll      = view.findViewById<TextView>(R.id.tabAll)
        val tabNew      = view.findViewById<TextView>(R.id.tabNew)
        val tabResolved = view.findViewById<TextView>(R.id.tabResolved)
        val tabPending  = view.findViewById<TextView>(R.id.tabPending)
        val tabArchive  = view.findViewById<TextView>(R.id.tabArchive)
        val allTabs     = listOf(tabAll, tabNew, tabResolved, tabPending, tabArchive)

        fun setActiveTab(tab: TextView, status: String) {
            allTabs.forEach {
                it.setTextColor(Color.parseColor("#64748B"))
                it.typeface = Typeface.DEFAULT
            }
            tab.setTextColor(Color.parseColor("#1246C2"))
            tab.typeface = Typeface.DEFAULT_BOLD
            currentTab = status
            applyFilters(view)
        }

        tabAll.setOnClickListener      { setActiveTab(tabAll,      "all")      }
        tabNew.setOnClickListener      { setActiveTab(tabNew,      "new")      }
        tabResolved.setOnClickListener { setActiveTab(tabResolved, "resolved") }
        tabPending.setOnClickListener  { setActiveTab(tabPending,  "pending")  }
        tabArchive.setOnClickListener  { setActiveTab(tabArchive,  "archived") }

        val chipDateFilter   = view.findViewById<LinearLayout>(R.id.chipDateFilter)
        val chipRatingFilter = view.findViewById<LinearLayout>(R.id.chipRatingFilter)
        val tvDateLabel      = chipDateFilter.getChildAt(0) as TextView
        val tvRatingLabel    = chipRatingFilter.getChildAt(0) as TextView

        chipDateFilter.setOnClickListener {
            val options = arrayOf("Last 7 days", "Last 30 days", "Last 90 days", "All time")
            AlertDialog.Builder(requireContext())
                .setTitle("Filter by Date")
                .setItems(options) { _, which ->
                    currentDays = when (which) { 0 -> 7; 1 -> 30; 2 -> 90; else -> 0 }
                    tvDateLabel.text = options[which]
                    fetchAndApply(view)
                }
                .show()
        }

        chipRatingFilter.setOnClickListener {
            val options = arrayOf("Any Rating", "5 Stars only", "4+ Stars", "3+ Stars", "Low (1-2 Stars)")
            AlertDialog.Builder(requireContext())
                .setTitle("Filter by Rating")
                .setItems(options) { _, which ->
                    currentRating = when (which) { 0 -> 0; 1 -> 5; 2 -> 4; 3 -> 3; else -> -1 }
                    tvRatingLabel.text = options[which]
                    applyFilters(view)
                }
                .show()
        }

        fetchAndApply(view)
    }

    private fun classifyDoc(doc: com.google.firebase.firestore.DocumentSnapshot): String {
        bucketCache[doc.id]?.let { return it }

        val status   = doc.getString("status")?.lowercase()?.trim() ?: ""
        val hasReply = doc.getBoolean("hasReply") ?: false

        return when {
            status == "resolved"                             -> "resolved"
            status == "archived"                             -> "archived"
            status == "pending"                              -> "pending"
            hasReply                                         -> "pending"
            else                                             -> "new"
        }
    }

    private fun fetchAndApply(view: View) {
        bucketCache.clear()

        db.collection("feedback")
            .orderBy("submittedAt", Query.Direction.DESCENDING)
            .limit(200)
            .get()
            .addOnSuccessListener { snapshot ->
                if (!isAdded || snapshot == null) return@addOnSuccessListener
                allDocs = snapshot.documents

                val needsCheck = mutableListOf<com.google.firebase.firestore.DocumentSnapshot>()

                allDocs.forEach { doc ->
                    val data         = doc.data ?: emptyMap<String, Any>()
                    val statusExists   = data.containsKey("status") && doc.getString("status")?.isNotBlank() == true
                    val hasReplyExists = data.containsKey("hasReply")

                    if (statusExists || hasReplyExists) {
                        // Fields exist — classify from fields directly and cache
                        val status   = doc.getString("status")?.lowercase()?.trim() ?: ""
                        val hasReply = doc.getBoolean("hasReply") ?: false
                        val bucket = when {
                            status == "resolved" -> "resolved"
                            status == "archived" -> "archived"
                            status == "pending"  -> "pending"
                            hasReply             -> "pending"
                            else                 -> "new"
                        }
                        bucketCache[doc.id] = bucket
                    } else {
                        needsCheck.add(doc)
                    }
                }

                if (needsCheck.isEmpty()) {
                    loadFeedbackStats(view, allDocs)
                    applyFilters(view)
                    return@addOnSuccessListener
                }

                var remaining = needsCheck.size

                needsCheck.forEach { doc ->
                    db.collection("feedback").document(doc.id)
                        .collection("replies")
                        .limit(1)
                        .get()
                        .addOnSuccessListener { replySnap ->
                            if (!isAdded) return@addOnSuccessListener

                            val hasReplies = !replySnap.isEmpty
                            val bucket     = if (hasReplies) "pending" else "new"
                            bucketCache[doc.id] = bucket

                            db.collection("feedback").document(doc.id).update(
                                mapOf(
                                    "status"   to bucket,
                                    "hasReply" to hasReplies
                                )
                            )

                            remaining--
                            if (remaining == 0) {
                                loadFeedbackStats(view, allDocs)
                                applyFilters(view)
                            }
                        }
                        .addOnFailureListener {
                            bucketCache[doc.id] = "new"
                            remaining--
                            if (remaining == 0) {
                                loadFeedbackStats(view, allDocs)
                                applyFilters(view)
                            }
                        }
                }
            }
            .addOnFailureListener { e ->
                if (!isAdded) return@addOnFailureListener
                val container = view.findViewById<LinearLayout>(R.id.feedbackContainer) ?: return@addOnFailureListener
                container.removeAllViews()
                container.addView(TextView(requireContext()).apply {
                    text = "Error loading feedback: ${e.message}"
                    setTextColor(Color.parseColor("#E03C3C"))
                    textSize = 13f
                    setPadding(0, 24, 0, 24)
                })
            }
    }

    private fun loadFeedbackStats(
        view: View,
        docs: List<com.google.firebase.firestore.DocumentSnapshot>
    ) {
        if (!isAdded) return
        val total = docs.size

        val barIds = mapOf(5 to R.id.progressBar5, 4 to R.id.progressBar4,
            3 to R.id.progressBar3, 2 to R.id.progressBar2, 1 to R.id.progressBar1)
        val pctIds = mapOf(5 to R.id.tvPct5, 4 to R.id.tvPct4,
            3 to R.id.tvPct3, 2 to R.id.tvPct2, 1 to R.id.tvPct1)

        if (total == 0) {
            view.findViewById<TextView>(R.id.tvAvgRating)?.text    = "0.0"
            view.findViewById<TextView>(R.id.tvTotalReviews)?.text = "No reviews yet"
            view.findViewById<TextView>(R.id.tvNew)?.text          = "0"
            view.findViewById<TextView>(R.id.tvPending)?.text      = "0"
            view.findViewById<TextView>(R.id.tvResolved)?.text     = "0"
            view.findViewById<TextView>(R.id.tvArchived)?.text     = "0"
            for (star in 1..5) {
                view.findViewById<ProgressBar>(barIds[star]!!)?.progress = 0
                view.findViewById<TextView>(pctIds[star]!!)?.text = "0%"
            }
            return
        }

        val ratingCounts = IntArray(6)
        var ratingSum = 0.0
        var newCount = 0; var pending = 0; var resolved = 0; var archived = 0

        docs.forEach { doc ->
            val r = (doc.getLong("rating") ?: 0L).toInt()
            if (r in 1..5) { ratingCounts[r]++; ratingSum += r }
            when (classifyDoc(doc)) {
                "new"      -> newCount++
                "pending"  -> pending++
                "resolved" -> resolved++
                "archived" -> archived++
            }
        }

        view.findViewById<TextView>(R.id.tvAvgRating)?.text    = String.format("%.1f", ratingSum / total)
        view.findViewById<TextView>(R.id.tvTotalReviews)?.text = "From ${formatCount(total)} reviews"

        for (star in 1..5) {
            val pct = ratingCounts[star] * 100 / total
            view.findViewById<ProgressBar>(barIds[star]!!)?.progress = pct
            view.findViewById<TextView>(pctIds[star]!!)?.text = "$pct%"
        }

        view.findViewById<TextView>(R.id.tvNew)?.text      = formatCount(newCount)
        view.findViewById<TextView>(R.id.tvPending)?.text  = formatCount(pending)
        view.findViewById<TextView>(R.id.tvResolved)?.text = formatCount(resolved)
        view.findViewById<TextView>(R.id.tvArchived)?.text = formatCount(archived)
    }

    private fun applyFilters(view: View) {
        val container = view.findViewById<LinearLayout>(R.id.feedbackContainer) ?: return

        var docs = allDocs

        docs = when (currentTab) {
            "new"      -> docs.filter { classifyDoc(it) == "new"      }
            "resolved" -> docs.filter { classifyDoc(it) == "resolved" }
            "pending"  -> docs.filter { classifyDoc(it) == "pending"  }
            "archived" -> docs.filter { classifyDoc(it) == "archived" }
            else       -> docs
        }

        if (currentDays > 0) {
            val cutoffMs = System.currentTimeMillis() - (currentDays * 86400L * 1000L)
            docs = docs.filter {
                (it.getTimestamp("submittedAt")?.toDate()?.time ?: 0L) >= cutoffMs
            }
        }

        docs = when {
            currentRating > 0   -> docs.filter { (it.getLong("rating") ?: 0L) >= currentRating }
            currentRating == -1 -> docs.filter { (it.getLong("rating") ?: 0L) in 1..2 }
            else -> docs
        }

        if (searchQuery.isNotEmpty()) {
            docs = docs.filter { doc ->
                val feedbackText = doc.getString("feedback")?.lowercase() ?: ""
                val categories   = (doc.get("categories") as? List<*>)?.joinToString(" ")?.lowercase() ?: ""
                val ratingStr    = (doc.getLong("rating") ?: 0L).toString()
                feedbackText.contains(searchQuery) ||
                        categories.contains(searchQuery) ||
                        ratingStr.contains(searchQuery)
            }
        }

        container.removeAllViews()

        if (docs.isEmpty()) {
            container.addView(TextView(requireContext()).apply {
                text = if (searchQuery.isNotEmpty()) "No results for \"$searchQuery\"."
                else "No feedback found."
                setTextColor(Color.parseColor("#94A3B8"))
                textSize = 14f
                setPadding(0, 24, 0, 24)
            })
            return
        }

        docs.forEach { doc ->
            val userId     = doc.getString("userId") ?: "anonymous"
            val feedbackTx = doc.getString("feedback") ?: ""
            val rating     = (doc.getLong("rating") ?: 0L).toInt()
            val status     = doc.getString("status")?.lowercase() ?: ""
            val hasReply   = doc.getBoolean("hasReply") ?: false
            val timestamp  = doc.getTimestamp("submittedAt")
            val categories = (doc.get("categories") as? List<*>)?.joinToString(" • ") ?: ""
            val timeAgo    = getTimeAgo(timestamp?.toDate()?.time ?: 0L)
            val bucket     = classifyDoc(doc)

            val cardView = layoutInflater.inflate(R.layout.item_feedback_card, container, false)
            var resolvedUserName = "Anonymous"

            db.collection("users").document(userId).get()
                .addOnSuccessListener { userDoc ->
                    if (!isAdded) return@addOnSuccessListener
                    val userName = userDoc.getString("username") ?: "Anonymous"
                    resolvedUserName = userName
                    val initial = userName.firstOrNull()?.uppercaseChar()?.toString() ?: "?"
                    cardView.findViewById<TextView>(R.id.tvAvatar)?.text = initial
                    cardView.findViewById<TextView>(R.id.tvAvatar)?.let { av ->
                        (av.parent as? CardView)?.setCardBackgroundColor(Color.parseColor("#1246C2"))
                        av.setTextColor(Color.WHITE)
                    }
                    cardView.findViewById<TextView>(R.id.tvUserName)?.text = userName
                }
                .addOnFailureListener {
                    cardView.findViewById<TextView>(R.id.tvAvatar)?.text = "?"
                    cardView.findViewById<TextView>(R.id.tvUserName)?.text = "Anonymous"
                }

            cardView.findViewById<TextView>(R.id.tvTimeAgo)?.text = timeAgo
            cardView.findViewById<TextView>(R.id.tvStars)?.text   = "★".repeat(rating) + "☆".repeat(5 - rating)

            cardView.findViewById<TextView>(R.id.tvCategories)?.apply {
                text       = categories
                visibility = if (categories.isNotEmpty()) View.VISIBLE else View.GONE
            }
            cardView.findViewById<TextView>(R.id.tvFeedbackText)?.apply {
                text       = feedbackTx
                visibility = if (feedbackTx.isNotEmpty()) View.VISIBLE else View.GONE
            }

            cardView.findViewById<TextView>(R.id.tvStatusBadge)?.apply {
                when (bucket) {
                    "resolved" -> { text = "RESOLVED"; setTextColor(Color.parseColor("#047857")); setBackgroundResource(R.drawable.badge_resolved) }
                    "archived" -> { text = "ARCHIVED"; setTextColor(Color.parseColor("#475569")); setBackgroundResource(R.drawable.badge_archived) }
                    "pending"  -> { text = "PENDING";  setTextColor(Color.parseColor("#D97706")); setBackgroundResource(R.drawable.badge_archived) }
                    else       -> { text = "NEW";      setTextColor(Color.parseColor("#C2410C")); setBackgroundResource(R.drawable.badge_new)      }
                }
            }

            val tvReplyBtn   = cardView.findViewById<TextView>(R.id.tvReplyBtn)
            val tvAdminReply = cardView.findViewById<TextView>(R.id.tvAdminReply)

            tvReplyBtn?.text = if (bucket == "pending" || hasReply) "Edit Reply" else "Reply"

            if (bucket == "pending" || hasReply) {
                db.collection("feedback").document(doc.id)
                    .collection("replies")
                    .orderBy("repliedAt", Query.Direction.DESCENDING)
                    .limit(1)
                    .get()
                    .addOnSuccessListener { replySnapshot ->
                        if (!isAdded) return@addOnSuccessListener
                        val latestReplyDoc  = replySnapshot.documents.firstOrNull()
                        val latestReplyText = latestReplyDoc?.getString("replyText") ?: ""
                        val latestReplyId   = latestReplyDoc?.id ?: ""

                        tvAdminReply?.apply {
                            if (latestReplyText.isNotEmpty()) {
                                text       = "💬 Admin: $latestReplyText"
                                visibility = View.VISIBLE
                            } else {
                                visibility = View.GONE
                            }
                        }

                        tvReplyBtn?.setOnClickListener {
                            parentFragmentManager.beginTransaction()
                                .replace(R.id.fragmentContainer, ReplyFeedback.newInstance(
                                    docId              = doc.id,
                                    userName           = resolvedUserName,
                                    feedbackText       = feedbackTx,
                                    rating             = rating,
                                    status             = status,
                                    categories         = categories,
                                    timeAgo            = timeAgo,
                                    existingReply      = latestReplyText,
                                    existingReplyDocId = latestReplyId
                                ))
                                .addToBackStack(null).commit()
                        }
                    }
                    .addOnFailureListener { tvAdminReply?.visibility = View.GONE }
            } else {
                tvAdminReply?.visibility = View.GONE
                tvReplyBtn?.setOnClickListener {
                    parentFragmentManager.beginTransaction()
                        .replace(R.id.fragmentContainer, ReplyFeedback.newInstance(
                            docId              = doc.id,
                            userName           = resolvedUserName,
                            feedbackText       = feedbackTx,
                            rating             = rating,
                            status             = status,
                            categories         = categories,
                            timeAgo            = timeAgo,
                            existingReply      = "",
                            existingReplyDocId = ""
                        ))
                        .addToBackStack(null).commit()
                }
            }

            cardView.findViewById<TextView>(R.id.tvViewMoreBtn)?.setOnClickListener {
                parentFragmentManager.beginTransaction()
                    .replace(R.id.fragmentContainer, ViewMore.newInstance(
                        docId        = doc.id,
                        userName     = resolvedUserName,
                        feedbackText = feedbackTx,
                        rating       = rating,
                        status       = status,
                        categories   = categories,
                        timeAgo      = timeAgo
                    ))
                    .addToBackStack(null).commit()
            }

            container.addView(cardView)
        }
    }

    private fun setNavActive(
        activeLayout: LinearLayout, activeIcon: ImageView, activeLabel: TextView,
        allLayouts: List<LinearLayout>, allIcons: List<ImageView>, allLabels: List<TextView>
    ) {
        val blue = Color.parseColor("#1246C2"); val gray = Color.parseColor("#94A3B8")
        allLayouts.forEach { it.setBackgroundColor(Color.TRANSPARENT) }
        allIcons.forEach   { it.imageTintList = ColorStateList.valueOf(gray) }
        allLabels.forEach  { it.setTextColor(gray); it.typeface = Typeface.DEFAULT }
        activeLayout.setBackgroundResource(R.drawable.nav_item_active_bg)
        activeIcon.imageTintList = ColorStateList.valueOf(blue)
        activeLabel.setTextColor(blue)
        activeLabel.typeface = Typeface.DEFAULT_BOLD
    }

    private fun getTimeAgo(timeMs: Long): String {
        if (timeMs == 0L) return "Just now"
        val diff = System.currentTimeMillis() - timeMs
        val mins = diff / 60000; val hours = mins / 60; val days = hours / 24
        return when {
            mins < 1   -> "Just now"
            mins < 60  -> "$mins min ago"
            hours < 24 -> "$hours hour${if (hours > 1) "s" else ""} ago"
            days == 1L -> "Yesterday"
            else       -> "$days days ago"
        }
    }

    private fun formatCount(n: Int): String =
        if (n >= 1000) String.format("%.1fK", n / 1000.0) else n.toString()
}