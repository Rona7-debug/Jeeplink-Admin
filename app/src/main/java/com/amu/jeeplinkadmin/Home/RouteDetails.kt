package com.amu.jeeplinkadmin.Home

import android.graphics.Color
import android.graphics.Paint
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.core.content.res.ResourcesCompat
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.amu.jeeplinkadmin.R
import com.amu.jeeplinkadmin.adapter.AdminRoute
import com.amu.jeeplinkadmin.adapter.StopsAdapter
import com.google.android.material.card.MaterialCardView
import com.google.firebase.firestore.FirebaseFirestore
import org.osmdroid.config.Configuration
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Polyline

class RouteDetails : Fragment() {

    companion object {
        private const val ARG_ROUTE = "arg_route"
        fun newInstance(route: AdminRoute): RouteDetails {
            val fragment = RouteDetails()
            val bundle = Bundle()
            bundle.putSerializable(ARG_ROUTE, route)
            fragment.arguments = bundle
            return fragment
        }
    }

    private var tradNormalFare     = 0
    private var tradDiscountedFare = 0
    private var modNormalFare      = 0
    private var modDiscountedFare  = 0
    private var isTraditionalSelected = true

    private var tvNormalFare: TextView?     = null
    private var tvDiscountedFare: TextView? = null
    private var mapView: MapView?           = null
    private var loadedGeoPoints: List<GeoPoint> = emptyList()
    private var currentRoute: AdminRoute?   = null

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        Configuration.getInstance().apply {
            load(requireContext(), requireContext().getSharedPreferences("osmdroid", 0))
            userAgentValue = requireContext().packageName
        }
        return inflater.inflate(R.layout.fragment_route_details, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val route = arguments?.getSerializable(ARG_ROUTE) as? AdminRoute
        currentRoute = route

        val btnBack           = view.findViewById<View>(R.id.btnBack)
        val tvTitle           = view.findViewById<TextView>(R.id.tvTitle)
        val tvRouteName       = view.findViewById<TextView>(R.id.tvRouteName)
        val btnTraditional    = view.findViewById<TextView>(R.id.btnTraditional)
        val btnModern         = view.findViewById<TextView>(R.id.btnModern)
        val vehicleTypeToggle = view.findViewById<LinearLayout>(R.id.vehicleTypeToggle)
        val btnEditRoute      = view.findViewById<MaterialCardView>(R.id.btnEditRoute)
        val btnDeleteRoute    = view.findViewById<MaterialCardView>(R.id.btnDeleteRoute)
        val rvStops           = view.findViewById<RecyclerView>(R.id.rvStops)
        val pointsContainer   = view.findViewById<LinearLayout>(R.id.pointsContainer)
        val mapClickCatcher   = view.findViewById<View>(R.id.mapClickCatcher)

        tvNormalFare     = view.findViewById(R.id.tvNormalFare)
        tvDiscountedFare = view.findViewById(R.id.tvDiscountedFare)
        tvNormalFare?.text     = "₱—"
        tvDiscountedFare?.text = "₱—"

        route?.let {
            tvTitle.text     = "Route ${it.routeCode} Details"
            tvRouteName.text = it.landmarks
        }

        mapView = view.findViewById(R.id.mapView)
        mapView?.setTileSource(TileSourceFactory.MAPNIK)
        mapView?.setUseDataConnection(true)
        mapView?.setMultiTouchControls(true)
        mapView?.controller?.setZoom(14.0)
        mapView?.controller?.setCenter(GeoPoint(10.3157, 123.8854))
        mapView?.isHorizontalMapRepetitionEnabled = false
        mapView?.isVerticalMapRepetitionEnabled   = false
        mapView?.minZoomLevel = 10.0
        mapView?.maxZoomLevel = 19.0

        route?.let { loadRouteCoordinates(it.routeId) }

        mapClickCatcher.setOnClickListener {
            val coordsList = ArrayList(loadedGeoPoints.map { point ->
                hashMapOf("lat" to point.latitude, "lng" to point.longitude)
            })
            val fullscreenFragment = RouteMapFullscreen.newInstance(
                coordsList,
                currentRoute?.routeCode ?: ""
            )
            parentFragmentManager.beginTransaction()
                .replace(R.id.fragmentContainer, fullscreenFragment)
                .addToBackStack(null)
                .commit()
        }

        setTraditionalSelected(btnTraditional, btnModern, vehicleTypeToggle)
        btnTraditional.setOnClickListener {
            if (!isTraditionalSelected) { isTraditionalSelected = true; setTraditionalSelected(btnTraditional, btnModern, vehicleTypeToggle); updateFareDisplay() }
        }
        btnModern.setOnClickListener {
            if (isTraditionalSelected) { isTraditionalSelected = false; setModernSelected(btnTraditional, btnModern, vehicleTypeToggle); updateFareDisplay() }
        }

        route?.let { loadRouteDetails(it.routeId, tvRouteName, rvStops, pointsContainer) }

        btnBack.setOnClickListener {
            parentFragmentManager.beginTransaction()
                .replace(R.id.fragmentContainer, Routes())
                .addToBackStack(null)
                .commit()
        }

        btnEditRoute.setOnClickListener {
            val editFragment = NewRoute.newInstanceForEdit(route!!)
            parentFragmentManager.beginTransaction()
                .replace(R.id.fragmentContainer, editFragment)
                .addToBackStack(null)
                .commit()
        }

        btnDeleteRoute.setOnClickListener {
            val dialogView = layoutInflater.inflate(R.layout.dialog_delete_confirm, null)
            val tvMessage  = dialogView.findViewById<TextView>(R.id.tvDeleteMessage)
            tvMessage.text = "Are you sure you want to delete route ${route?.routeCode}? This action cannot be undone."
            val dialog = AlertDialog.Builder(requireContext()).setView(dialogView).setCancelable(true).create()
            dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)
            dialogView.findViewById<MaterialCardView>(R.id.btnCancel).setOnClickListener { dialog.dismiss() }
            dialogView.findViewById<MaterialCardView>(R.id.btnConfirmDelete).setOnClickListener {
                dialog.dismiss()
                route?.let { deleteRoute(it.routeId, it.routeCode) }
            }
            dialog.show()
            dialog.window?.setLayout(
                (resources.displayMetrics.widthPixels * 0.90).toInt(),
                android.view.ViewGroup.LayoutParams.WRAP_CONTENT
            )
        }
    }

    // ── Load coordinates and draw directly — no OSRM needed ──────────
    // Coordinates saved are already the real road path from KML LineString
    private fun loadRouteCoordinates(routeId: String) {
        val db = FirebaseFirestore.getInstance()
        db.collection("jeepney_routes").document(routeId).get()
            .addOnSuccessListener { doc ->
                if (!isAdded) return@addOnSuccessListener
                val rawCoords = doc.get("coordinates") as? List<*>
                if (rawCoords != null && rawCoords.isNotEmpty()) {
                    val geoPoints = rawCoords.mapNotNull { item ->
                        val map = item as? Map<*, *>
                        val lat = (map?.get("lat") as? Number)?.toDouble()
                        val lng = (map?.get("lng") as? Number)?.toDouble()
                        if (lat != null && lng != null) GeoPoint(lat, lng) else null
                    }
                    if (geoPoints.isNotEmpty()) drawRouteOnMap(geoPoints)
                } else {
                    mapView?.controller?.setCenter(GeoPoint(10.3157, 123.8854))
                }
            }
            .addOnFailureListener { mapView?.controller?.setCenter(GeoPoint(10.3157, 123.8854)) }
    }

    // ── Draw polyline — clean line, no markers, no dots ──────────────
    private fun drawRouteOnMap(points: List<GeoPoint>) {
        if (!isAdded) return
        loadedGeoPoints = points
        mapView?.overlays?.clear()
        if (points.size < 2) { mapView?.invalidate(); return }

        val polyline = Polyline()
        polyline.setPoints(ArrayList(points))
        polyline.outlinePaint.apply {
            color       = Color.parseColor("#1246C2")
            strokeWidth = 10f
            isAntiAlias = true
            style       = Paint.Style.STROKE  // prevents dots at every point
            strokeJoin  = Paint.Join.ROUND
            strokeCap   = Paint.Cap.ROUND
        }
        polyline.infoWindow = null
        mapView?.overlays?.add(polyline)
        mapView?.controller?.setCenter(points[points.size / 2])
        mapView?.controller?.setZoom(14.0)
        mapView?.invalidate()
    }

    override fun onResume() { super.onResume(); mapView?.onResume() }
    override fun onPause()  { super.onPause();  mapView?.onPause()  }

    private fun loadRouteDetails(
        routeId: String,
        tvRouteName: TextView,
        rvStops: RecyclerView,
        pointsContainer: LinearLayout
    ) {
        val db = FirebaseFirestore.getInstance()
        val darkBlue = Color.parseColor("#09215C")
        db.collection("jeepney_routes").document(routeId).get()
            .addOnSuccessListener { doc ->
                if (!isAdded) return@addOnSuccessListener

                val landmarks        = doc.getString("landmarks") ?: ""
                val areasCoveredList = doc.get("areasCovered") as? List<*>
                val areasCovered     = areasCoveredList?.filterIsInstance<String>()?.joinToString(" • ") ?: ""
                tvRouteName.text     = if (areasCovered.isNotBlank()) areasCovered else landmarks

                // ── Read saved fare from Firestore ────────────────────
                val fareMin = doc.getLong("fareMin")?.toInt()
                val fareMax = doc.getLong("fareMax")?.toInt()
                if (fareMin != null) {
                    tradNormalFare     = fareMin
                    tradDiscountedFare = (fareMin * 0.80).toInt()
                    modNormalFare      = fareMax ?: fareMin
                    modDiscountedFare  = ((fareMax ?: fareMin) * 0.80).toInt()
                    updateFareDisplay()
                }

                val majorStopsRaw = doc.get("majorStops")
                val majorStops = when {
                    majorStopsRaw is List<*> && majorStopsRaw.firstOrNull() is Map<*, *> ->
                        majorStopsRaw.mapNotNull { (it as? Map<*, *>)?.get("name") as? String }
                    majorStopsRaw is List<*> -> majorStopsRaw.filterIsInstance<String>()
                    else -> landmarks.split("•").map { it.trim() }.filter { it.isNotEmpty() }
                }
                rvStops.layoutManager = LinearLayoutManager(requireContext())
                rvStops.adapter = StopsAdapter(majorStops)

                pointsContainer.removeAllViews()
                val rawMap = doc.get("pointsAlongRoute") as? Map<String, *> ?: emptyMap<String, Any>()
                if (rawMap.isEmpty()) {
                    pointsContainer.addView(TextView(requireContext()).apply {
                        text = "No points listed."; textSize = 13f; setTextColor(Color.parseColor("#94A3B8"))
                    })
                    return@addOnSuccessListener
                }

                val orderedCategories = listOf("church", "government", "health", "hotel", "mall_grocery", "road", "school", "services", "terminal")
                for (categoryKey in orderedCategories) {
                    val items      = rawMap[categoryKey] as? List<*> ?: continue
                    val cleanItems = items.filterIsInstance<String>()
                    if (cleanItems.isEmpty()) continue
                    val categoryTitle = when (categoryKey) {
                        "mall_grocery" -> "Mall / Grocery"; "road" -> "Road"; "school" -> "School"
                        "government"   -> "Government";     "health" -> "Health"; "hotel" -> "Hotel"
                        "church"       -> "Church";         "services" -> "Services"; "terminal" -> "Terminal"
                        else           -> categoryKey.replaceFirstChar { it.uppercase() }
                    }
                    pointsContainer.addView(TextView(requireContext()).apply {
                        text = categoryTitle; textSize = 15f; setTextColor(darkBlue)
                        typeface = ResourcesCompat.getFont(requireContext(), R.font.lato_bold)
                        setPadding(0, 24, 0, 4)
                    })
                    pointsContainer.addView(TextView(requireContext()).apply {
                        text = cleanItems.joinToString("\n• ", prefix = "• ")
                        textSize = 15f; setTextColor(darkBlue)
                        typeface = ResourcesCompat.getFont(requireContext(), R.font.lato_regular)
                        setLineSpacing(0f, 1.3f)
                    })
                }
            }
            .addOnFailureListener { android.util.Log.e("RouteDetails", "Failed: ${it.message}") }
    }

    private fun updateFareDisplay() {
        if (isTraditionalSelected) {
            tvNormalFare?.text = "₱$tradNormalFare.00"
            tvDiscountedFare?.text = "₱$tradDiscountedFare.00"
        } else {
            tvNormalFare?.text = "₱$modNormalFare.00"
            tvDiscountedFare?.text = "₱$modDiscountedFare.00"
        }
    }

    private fun setTraditionalSelected(btnTraditional: TextView, btnModern: TextView, toggle: LinearLayout) {
        toggle.setBackgroundResource(R.drawable.toggle_bg)
        btnTraditional.setBackgroundResource(R.drawable.toggle_selected)
        btnTraditional.setTextColor(Color.WHITE)
        btnModern.setBackgroundColor(Color.TRANSPARENT)
        btnModern.setTextColor(Color.parseColor("#64748B"))
    }

    private fun setModernSelected(btnTraditional: TextView, btnModern: TextView, toggle: LinearLayout) {
        toggle.setBackgroundResource(R.drawable.toggle_bg_yellow)
        btnTraditional.setBackgroundColor(Color.parseColor("#EFD8AB"))
        btnTraditional.setTextColor(Color.parseColor("#64748B"))
        btnModern.setBackgroundResource(R.drawable.toggle_selected_yellow)
        btnModern.setTextColor(Color.parseColor("#09215C"))
    }

    private fun deleteRoute(routeId: String, routeCode: String) {
        val db = FirebaseFirestore.getInstance()
        db.collection("jeepney_routes").document(routeId).delete()
            .addOnSuccessListener {
                Toast.makeText(requireContext(), "Route $routeCode deleted.", Toast.LENGTH_SHORT).show()
                parentFragmentManager.popBackStack()
            }
            .addOnFailureListener { e ->
                Toast.makeText(requireContext(), "Error: ${e.message}", Toast.LENGTH_SHORT).show()
            }
    }

    private fun dpToPx(dp: Int): Int = (dp * resources.displayMetrics.density).toInt()
}