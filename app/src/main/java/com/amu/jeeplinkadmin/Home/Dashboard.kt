package com.amu.jeeplinkadmin.Home

import android.animation.ValueAnimator
import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.Typeface
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.animation.AnimationUtils
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.cardview.widget.CardView
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import com.amu.jeeplinkadmin.R
import com.github.mikephil.charting.charts.LineChart
import com.github.mikephil.charting.components.XAxis
import com.github.mikephil.charting.data.Entry
import com.github.mikephil.charting.data.LineData
import com.github.mikephil.charting.data.LineDataSet
import com.github.mikephil.charting.formatter.IndexAxisValueFormatter
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration

class Dashboard : Fragment() {

    private val listeners = mutableListOf<ListenerRegistration>()

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_dashboard, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val fabAdd           = view.findViewById<CardView>(R.id.fabAdd)
        val navHome          = view.findViewById<LinearLayout>(R.id.navHome)
        val navRoutes        = view.findViewById<LinearLayout>(R.id.navRoutes)
        val navFeedback      = view.findViewById<LinearLayout>(R.id.navFeedback)
        val navSettings      = view.findViewById<LinearLayout>(R.id.navSettings)
        val btnProfile       = view.findViewById<CardView>(R.id.btnProfile)
        val tvProfileInitial = view.findViewById<TextView>(R.id.tvProfileInitial)
        val btnNotification  = view.findViewById<FrameLayout>(R.id.btnNotification)
        val tvNotifBadge     = view.findViewById<TextView>(R.id.tvNotifBadge)
        val ivNotifBell      = view.findViewById<ImageView>(R.id.ivNotifBell)

        val ivNavHome     = view.findViewById<ImageView>(R.id.ivNavHome)
        val ivNavRoutes   = view.findViewById<ImageView>(R.id.ivNavRoutes)
        val ivNavFeedback = view.findViewById<ImageView>(R.id.ivNavFeedback)
        val ivNavSettings = view.findViewById<ImageView>(R.id.ivNavSettings)

        val tvNavHome     = view.findViewById<TextView>(R.id.tvNavHome)
        val tvNavRoutes   = view.findViewById<TextView>(R.id.tvNavRoutes)
        val tvNavFeedback = view.findViewById<TextView>(R.id.tvNavFeedback)
        val tvNavSettings = view.findViewById<TextView>(R.id.tvNavSettings)

        val tvTotalRoutes    = view.findViewById<TextView>(R.id.tvTotalRoutes)
        val tvPendingReports = view.findViewById<TextView>(R.id.tvPendingReports)
        val tvPremiumCount   = view.findViewById<TextView>(R.id.tvPremiumCount)

        val allLayouts = listOf(navHome, navRoutes, navFeedback, navSettings)
        val allIcons   = listOf(ivNavHome, ivNavRoutes, ivNavFeedback, ivNavSettings)
        val allLabels  = listOf(tvNavHome, tvNavRoutes, tvNavFeedback, tvNavSettings)

        setNavActive(navHome, ivNavHome, tvNavHome, allLayouts, allIcons, allLabels)
        animateCards(view)

        // ── Load profile initial ──────────────────────────────────────
        val currentUser = FirebaseAuth.getInstance().currentUser
        val displayName = currentUser?.displayName?.takeIf { it.isNotBlank() }
        if (displayName != null) {
            tvProfileInitial.text = displayName.firstOrNull()?.uppercaseChar()?.toString() ?: "A"
        } else {
            val uid = currentUser?.uid
            if (uid != null) {
                FirebaseFirestore.getInstance()
                    .collection("users").document(uid).get()
                    .addOnSuccessListener { doc ->
                        if (!isAdded) return@addOnSuccessListener
                        val username = doc.getString("username") ?: "A"
                        tvProfileInitial.text = username.firstOrNull()?.uppercaseChar()?.toString() ?: "A"
                    }
            }
        }

        // ── Notification bell ─────────────────────────────────────────
        val prefs                = requireContext().getSharedPreferences("admin_prefs", Context.MODE_PRIVATE)
        val notificationsEnabled = prefs.getBoolean("notifications_enabled", true)

        setupNotificationBell(tvNotifBadge, ivNotifBell, notificationsEnabled)

        btnNotification.setOnClickListener {
            if (!notificationsEnabled) {
                android.widget.Toast.makeText(
                    requireContext(),
                    "Notifications are disabled. Enable them in Profile settings.",
                    android.widget.Toast.LENGTH_SHORT
                ).show()
                return@setOnClickListener
            }

            val now = System.currentTimeMillis()
            prefs.edit()
                .putLong("last_seen_feedback_time", now)
                .putLong("last_seen_premium_time", now)
                .apply()

            tvNotifBadge.visibility   = View.GONE
            ivNotifBell.imageTintList = ColorStateList.valueOf(Color.parseColor("#64748B"))

            parentFragmentManager.beginTransaction()
                .replace(R.id.fragmentContainer, Notifications())
                .addToBackStack(null)
                .commit()
        }

        btnProfile.setOnClickListener {
            parentFragmentManager.beginTransaction()
                .replace(R.id.fragmentContainer, Profile())
                .addToBackStack(null)
                .commit()
        }

        fabAdd.setOnClickListener {
            parentFragmentManager.beginTransaction()
                .replace(R.id.fragmentContainer, NewRoute())
                .addToBackStack(null)
                .commit()
        }

        navRoutes.setOnClickListener {
            setNavActive(navRoutes, ivNavRoutes, tvNavRoutes, allLayouts, allIcons, allLabels)
            parentFragmentManager.beginTransaction()
                .replace(R.id.fragmentContainer, Routes())
                .addToBackStack(null)
                .commit()
        }

        navFeedback.setOnClickListener {
            setNavActive(navFeedback, ivNavFeedback, tvNavFeedback, allLayouts, allIcons, allLabels)
            parentFragmentManager.beginTransaction()
                .replace(R.id.fragmentContainer, Feedback())
                .addToBackStack(null)
                .commit()
        }

        navSettings.setOnClickListener {
            setNavActive(navSettings, ivNavSettings, tvNavSettings, allLayouts, allIcons, allLabels)
            parentFragmentManager.beginTransaction()
                .replace(R.id.fragmentContainer, Profile())
                .addToBackStack(null)
                .commit()
        }

        setupLiveCounts(tvTotalRoutes, tvPendingReports, tvPremiumCount)
        setupFeedbackChart(view)
    }

    // ── Notification bell ─────────────────────────────────────────────
    private fun setupNotificationBell(
        tvNotifBadge: TextView,
        ivNotifBell: ImageView,
        notificationsEnabled: Boolean
    ) {
        if (!notificationsEnabled) {
            tvNotifBadge.visibility   = View.GONE
            ivNotifBell.imageTintList = ColorStateList.valueOf(Color.parseColor("#CBD5E1"))
            return
        }

        val prefs                = requireContext().getSharedPreferences("admin_prefs", Context.MODE_PRIVATE)
        val lastSeenFeedbackTime = prefs.getLong("last_seen_feedback_time", 0L)
        val lastSeenPremiumTime  = prefs.getLong("last_seen_premium_time", 0L)
        val db                   = FirebaseFirestore.getInstance()

        var newFeedbackCount = 0
        var newPremiumCount  = 0

        fun updateBadge() {
            val total = newFeedbackCount + newPremiumCount
            if (!isAdded) return
            if (total > 0) {
                tvNotifBadge.visibility   = View.VISIBLE
                tvNotifBadge.text         = if (total > 99) "99+" else total.toString()
                ivNotifBell.imageTintList = ColorStateList.valueOf(Color.parseColor("#1246C2"))
            } else {
                tvNotifBadge.visibility   = View.GONE
                ivNotifBell.imageTintList = ColorStateList.valueOf(Color.parseColor("#64748B"))
            }
        }

        listeners += db.collection("feedback")
            .addSnapshotListener { snapshot, _ ->
                if (!isAdded || snapshot == null) return@addSnapshotListener
                var count = 0
                snapshot.documents.forEach { doc ->
                    val timestamp = doc.getTimestamp("submittedAt")?.toDate()?.time ?: 0L
                    if (timestamp > lastSeenFeedbackTime) count++
                }
                newFeedbackCount = count
                updateBadge()
            }

        listeners += db.collection("users")
            .whereEqualTo("isPremium", true)
            .addSnapshotListener { snapshot, _ ->
                if (!isAdded || snapshot == null) return@addSnapshotListener
                var count = 0
                snapshot.documents.forEach { doc ->
                    val timestamp = doc.getTimestamp("premiumSince")?.toDate()?.time ?: 0L
                    if (timestamp > lastSeenPremiumTime) count++
                }
                newPremiumCount = count
                updateBadge()
            }
    }

    private fun animateCards(view: View) {
        val cards = listOf(
            view.findViewById<View>(R.id.cardTotalRoutes),
            view.findViewById<View>(R.id.cardPendingReports),
            view.findViewById<View>(R.id.cardPremium),
            view.findViewById<View>(R.id.cardFeedbackTrends)
        )
        val handler = Handler(Looper.getMainLooper())
        cards.forEachIndexed { index, card ->
            card?.let {
                it.alpha = 0f
                handler.postDelayed({
                    it.alpha = 1f
                    it.startAnimation(AnimationUtils.loadAnimation(requireContext(), R.anim.card_bounce))
                }, index * 80L)
            }
        }
    }

    private fun animateCountUp(textView: TextView, targetValue: Int, durationMs: Long = 800) {
        val animator = ValueAnimator.ofInt(0, targetValue)
        animator.duration = durationMs
        animator.interpolator = android.view.animation.DecelerateInterpolator()
        animator.addUpdateListener { animation ->
            if (isAdded) textView.text = animation.animatedValue.toString()
        }
        animator.start()
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

    private fun setupFeedbackChart(view: View) {
        val chart = view.findViewById<LineChart>(R.id.lineChartFeedback)
        val db    = FirebaseFirestore.getInstance()
        val days  = listOf("MON", "TUE", "WED", "THU", "FRI", "SAT", "SUN")

        db.collection("feedback").get()
            .addOnSuccessListener { snapshot ->
                if (!isAdded) return@addOnSuccessListener

                val counts = IntArray(7) { 0 }
                snapshot.documents.forEach { doc ->
                    val timestamp = doc.getTimestamp("submittedAt")
                    timestamp?.let {
                        val cal = java.util.Calendar.getInstance()
                        cal.time = it.toDate()
                        val dayIndex = (cal.get(java.util.Calendar.DAY_OF_WEEK) + 5) % 7
                        counts[dayIndex]++
                    }
                }

                val entries = counts.mapIndexed { i, count -> Entry(i.toFloat(), count.toFloat()) }

                val dataSet = LineDataSet(entries, "").apply {
                    color = Color.parseColor("#1246C2")
                    lineWidth = 2.5f
                    setCircleColor(Color.parseColor("#1246C2"))
                    circleRadius = 4f
                    circleHoleRadius = 2f
                    circleHoleColor = Color.WHITE
                    setDrawValues(false)
                    mode = LineDataSet.Mode.CUBIC_BEZIER
                    cubicIntensity = 0.2f
                    setDrawFilled(true)
                    fillDrawable = ContextCompat.getDrawable(requireContext(), R.drawable.chart_gradient_fill)
                }

                chart.apply {
                    data = LineData(dataSet)
                    description.isEnabled = false
                    legend.isEnabled = false
                    setTouchEnabled(false)
                    setDrawGridBackground(false)
                    setDrawBorders(false)
                    xAxis.apply {
                        valueFormatter = IndexAxisValueFormatter(days)
                        position = XAxis.XAxisPosition.BOTTOM
                        setDrawGridLines(false)
                        setDrawAxisLine(false)
                        textColor = Color.parseColor("#94A3B8")
                        textSize = 11f
                        granularity = 1f
                        labelCount = 7
                    }
                    axisLeft.isEnabled = false
                    axisRight.isEnabled = false
                    animateXY(1000, 600)
                    invalidate()
                }
            }
    }

    private fun setupLiveCounts(
        tvTotalRoutes    : TextView,
        tvPendingReports : TextView,
        tvPremiumCount   : TextView
    ) {
        val db = FirebaseFirestore.getInstance()

        listeners += db.collection("jeepney_routes")
            .whereEqualTo("isActive", true)
            .addSnapshotListener { snapshot, _ ->
                if (!isAdded || snapshot == null) return@addSnapshotListener
                animateCountUp(tvTotalRoutes, snapshot.size())
            }
        listeners += db.collection("feedback")
            .whereEqualTo("status", "pending")
            .addSnapshotListener { snapshot, error ->
                if (!isAdded) return@addSnapshotListener
                if (error != null || snapshot == null) {
                    db.collection("feedback").get()
                        .addOnSuccessListener { allSnapshot ->
                            if (!isAdded) return@addOnSuccessListener
                            animateCountUp(tvPendingReports, allSnapshot.size())
                        }
                    return@addSnapshotListener
                }
                animateCountUp(tvPendingReports, snapshot.size())
            }

        listeners += db.collection("users")
            .whereEqualTo("isPremium", true)
            .addSnapshotListener { snapshot, _ ->
                if (!isAdded || snapshot == null) return@addSnapshotListener
                animateCountUp(tvPremiumCount, snapshot.size())
            }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        listeners.forEach { it.remove() }
        listeners.clear()
    }
}