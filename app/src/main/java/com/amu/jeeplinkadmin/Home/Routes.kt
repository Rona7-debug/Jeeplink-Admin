package com.amu.jeeplinkadmin.Home

import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.cardview.widget.CardView
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.amu.jeeplinkadmin.R
import com.amu.jeeplinkadmin.adapter.AdminRoute
import com.amu.jeeplinkadmin.adapter.AdminRouteAdapter
import com.google.firebase.firestore.FirebaseFirestore

class Routes : Fragment() {

    private lateinit var routeAdapter: AdminRouteAdapter
    private val allRoutes = mutableListOf<AdminRoute>()
    private var searchQuery = ""

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_routes, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val etSearch     = view.findViewById<EditText>(R.id.etSearch)
        val rvRoutes     = view.findViewById<RecyclerView>(R.id.rvRoutes)
        val progressBar  = view.findViewById<ProgressBar>(R.id.progressBar)
        val emptyState   = view.findViewById<LinearLayout>(R.id.emptyState)
        val tvRouteCount = view.findViewById<TextView>(R.id.tvRouteCount)
        val fabAdd       = view.findViewById<CardView>(R.id.fabAdd)
        val navHome      = view.findViewById<LinearLayout>(R.id.navHome)
        val navFeedback  = view.findViewById<LinearLayout>(R.id.navFeedback)
        val navSettings  = view.findViewById<LinearLayout>(R.id.navSettings)

        navHome.setOnClickListener {
            parentFragmentManager.beginTransaction()
                .replace(R.id.fragmentContainer, Dashboard())
                .commit()
        }

        fabAdd.setOnClickListener {
            parentFragmentManager.beginTransaction()
                .replace(R.id.fragmentContainer, NewRoute())
                .addToBackStack(null)
                .commit()
        }

        navFeedback.setOnClickListener {
            parentFragmentManager.beginTransaction()
                .replace(R.id.fragmentContainer, Feedback())
                .addToBackStack(null)
                .commit()
        }

        navSettings.setOnClickListener {
            parentFragmentManager.beginTransaction()
                .replace(R.id.fragmentContainer, Profile())
                .addToBackStack(null)
                .commit()
        }

        routeAdapter = AdminRouteAdapter(emptyList()) { route ->
            parentFragmentManager.beginTransaction()
                .replace(R.id.fragmentContainer, RouteDetails.newInstance(route))
                .addToBackStack(null)
                .commit()
        }

        rvRoutes.layoutManager = LinearLayoutManager(requireContext())
        rvRoutes.adapter = routeAdapter

        etSearch.addTextChangedListener(object : TextWatcher {
            override fun afterTextChanged(s: Editable?) {
                searchQuery = s.toString().trim()
                filterRoutes(emptyState, tvRouteCount)
            }
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
        })

        loadRoutes(progressBar, emptyState, tvRouteCount)
    }

    private fun loadRoutes(
        progressBar: ProgressBar,
        emptyState: LinearLayout,
        tvRouteCount: TextView
    ) {
        val db = FirebaseFirestore.getInstance()
        progressBar.visibility = View.VISIBLE

        db.collection("jeepney_routes")
            .whereEqualTo("isActive", true)
            .get()
            .addOnSuccessListener { routeSnapshot ->
                if (routeSnapshot.isEmpty) {
                    progressBar.visibility = View.GONE
                    emptyState.visibility  = View.VISIBLE
                    tvRouteCount.text      = "0 routes"
                    return@addOnSuccessListener
                }

                allRoutes.clear()
                routeSnapshot.documents.forEach { doc ->

                    val rawStops = doc.get("majorStops") as? List<*>
                    val majorStops = rawStops?.mapNotNull { item ->
                        val map = item as? Map<*, *> ?: return@mapNotNull null
                        val name = map["name"] as? String ?: return@mapNotNull null
                        val type = map["type"] as? String ?: "Intermediate Stop"
                        mapOf("name" to name, "type" to type)
                    } ?: emptyList()

                    allRoutes.add(AdminRoute(
                        routeId     = doc.id,
                        routeCode   = doc.getString("routeCode")  ?: "",
                        routeName   = doc.getString("routeName")  ?: "",
                        landmarks   = doc.getString("landmarks")  ?: "",
                        pujType     = doc.getString("pujType")    ?: "",
                        isActive    = doc.getBoolean("isActive")  ?: true,
                        regularFare = 0,
                        majorStops  = majorStops,
                        fareMin     = doc.getLong("fareMin")?.toInt() ?: 0,
                        fareMax     = doc.getLong("fareMax")?.toInt() ?: 0
                    ))
                }

                allRoutes.sortBy { it.routeCode }
                progressBar.visibility = View.GONE
                filterRoutes(emptyState, tvRouteCount)
            }
            .addOnFailureListener { e ->
                progressBar.visibility = View.GONE
                Toast.makeText(requireContext(), "Error: ${e.message}", Toast.LENGTH_SHORT).show()
            }
    }

    private fun filterRoutes(emptyState: LinearLayout, tvRouteCount: TextView) {
        val filtered = if (searchQuery.isBlank()) {
            allRoutes
        } else {
            allRoutes.filter { route ->
                route.routeCode.contains(searchQuery, ignoreCase = true) ||
                        route.routeName.contains(searchQuery, ignoreCase = true) ||
                        route.landmarks.contains(searchQuery, ignoreCase = true) ||
                        route.majorStops.any { stop ->
                            stop["name"]?.contains(searchQuery, ignoreCase = true) == true
                        }
            }
        }

        routeAdapter.updateRoutes(filtered)

        val count = filtered.size
        tvRouteCount.text = if (searchQuery.isBlank())
            "$count routes"
        else
            "$count result${if (count != 1) "s" else ""} for \"$searchQuery\""

        emptyState.visibility = if (filtered.isEmpty()) View.VISIBLE else View.GONE
    }
}