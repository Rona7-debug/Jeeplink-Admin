package com.amu.jeeplinkadmin.Home

import android.app.Activity
import android.content.Intent
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.Fragment
import com.amu.jeeplinkadmin.R
import com.amu.jeeplinkadmin.adapter.AdminRoute
import com.google.firebase.firestore.FirebaseFirestore
import org.osmdroid.config.Configuration
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Polyline

class NewRoute : Fragment() {

    private val majorStops       = mutableListOf<Map<String, String>>()
    private val pointsAlongRoute = mutableMapOf<String, MutableList<String>>()
    private val waypoints        = mutableListOf<GeoPoint>()
    private val farePoints       = mutableListOf<GeoPoint>()

    private var isTraditionalSelected = true
    private var editRouteId: String?  = null
    private var mapView: MapView?     = null

    private var tvWaypointCount: TextView?     = null
    private var etNormalFare: TextView?        = null
    private var etDiscountedFare: TextView?    = null
    private var majorStopsList: LinearLayout?  = null
    private var pointsAlongList: LinearLayout? = null

    private var storedDistanceKm: Double = -1.0

    private val pointCategories = arrayOf("church", "government", "health", "hotel", "mall_grocery", "road", "school", "services", "terminal")
    private val categoryLabels  = mapOf("church" to "Church", "government" to "Government", "health" to "Health", "hotel" to "Hotel", "mall_grocery" to "Mall / Grocery", "road" to "Road", "school" to "School", "services" to "Services", "terminal" to "Terminal")

    private val kmlFilePicker = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            result.data?.data?.let { uri -> importKmlFromUri(uri) }
        }
    }

    companion object {
        private const val ARG_ROUTE = "arg_route"
        fun newInstance() = NewRoute()
        fun newInstanceForEdit(route: AdminRoute): NewRoute {
            val fragment = NewRoute()
            fragment.arguments = Bundle().apply { putSerializable(ARG_ROUTE, route) }
            return fragment
        }
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        Configuration.getInstance().apply {
            load(requireContext(), requireContext().getSharedPreferences("osmdroid", 0))
            userAgentValue = requireContext().packageName
        }

        parentFragmentManager.setFragmentResultListener("map_waypoints", viewLifecycleOwner) { _, bundle ->
            val coordsBack = bundle.getString("updated_waypoints") ?: return@setFragmentResultListener
            waypoints.clear()
            coordsBack.split("|").forEach { pair ->
                val parts = pair.split(",")
                if (parts.size == 2) {
                    try {
                        val lat = parts[0].toDouble()
                        val lng = parts[1].toDouble()
                        if (lat != 0.0 || lng != 0.0) waypoints.add(GeoPoint(lat, lng))
                    } catch (e: NumberFormatException) { }
                }
            }
            refreshMapOverlays()
            updateFareDisplay()
        }

        return inflater.inflate(R.layout.fragment_new_route, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val btnBack        = view.findViewById<ImageView>(R.id.btnBack)
        val btnSave        = view.findViewById<TextView>(R.id.btnSave)
        val btnTraditional = view.findViewById<TextView>(R.id.btnTraditional)
        val btnModern      = view.findViewById<TextView>(R.id.btnModern)
        val btnAddStop     = view.findViewById<LinearLayout>(R.id.btnAddStop)
        val btnAddRoute    = view.findViewById<LinearLayout>(R.id.btnAddRoute)
        val etRouteCode    = view.findViewById<EditText>(R.id.etRouteCode)
        val btnUndo        = view.findViewById<TextView>(R.id.btnUndoWaypoint)
        val btnClear       = view.findViewById<TextView>(R.id.btnClearWaypoints)
        val tvTitle        = view.findViewById<TextView>(R.id.tvTitle)
        val btnImportKml   = view.findViewById<TextView>(R.id.btnImportKml)

        majorStopsList   = view.findViewById(R.id.majorStopsList)
        pointsAlongList  = view.findViewById(R.id.pointsAlongList)
        etNormalFare     = view.findViewById(R.id.etNormalFare)
        etDiscountedFare = view.findViewById(R.id.etDiscountedFare)
        etNormalFare?.isEnabled     = false
        etDiscountedFare?.isEnabled = false
        tvWaypointCount  = view.findViewById(R.id.tvWaypointCount)

        updateFareDisplay()

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

        setupMapTapListener()

        btnImportKml.setOnClickListener {
            val intent = Intent(Intent.ACTION_GET_CONTENT).apply {
                type = "*/*"
                addCategory(Intent.CATEGORY_OPENABLE)
                putExtra(Intent.EXTRA_MIME_TYPES, arrayOf(
                    "application/vnd.google-earth.kml+xml",
                    "application/octet-stream",
                    "text/xml", "text/plain", "*/*"
                ))
            }
            kmlFilePicker.launch(Intent.createChooser(intent, "Select KML File"))
        }

        val existingRoute = arguments?.getSerializable(ARG_ROUTE) as? AdminRoute
        if (existingRoute != null) {
            editRouteId = existingRoute.routeId
            etRouteCode.setText(existingRoute.routeCode)
            tvTitle.text = "Edit Route"
            btnSave.text = "Update"
            loadExistingRouteData(existingRoute.routeId)
        } else {
            tvTitle.text = "New Route"
        }

        btnUndo.setOnClickListener {
            if (waypoints.isNotEmpty()) {
                waypoints.removeAt(waypoints.size - 1)
                refreshMapOverlays()
                if (storedDistanceKm < 0) fetchAndStoreFare()
            }
        }

        btnClear.setOnClickListener {
            if (waypoints.isNotEmpty()) {
                AlertDialog.Builder(requireContext()).setTitle("Clear Waypoints").setMessage("Remove all waypoints?")
                    .setPositiveButton("Clear") { _, _ ->
                        waypoints.clear()
                        farePoints.clear()
                        storedDistanceKm = -1.0
                        refreshMapOverlays()
                        etNormalFare?.text     = "Auto-computed"
                        etDiscountedFare?.text = "Auto-computed"
                        tvWaypointCount?.text  = "0 road points loaded"
                    }
                    .setNegativeButton("Cancel", null).show()
            }
        }

        btnBack.setOnClickListener { parentFragmentManager.popBackStack() }

        setTraditionalSelected(btnTraditional, btnModern, view)
        btnTraditional.setOnClickListener {
            if (!isTraditionalSelected) {
                isTraditionalSelected = true
                setTraditionalSelected(btnTraditional, btnModern, view)
                updateFareDisplay()
            }
        }
        btnModern.setOnClickListener {
            if (isTraditionalSelected) {
                isTraditionalSelected = false
                setModernSelected(btnTraditional, btnModern, view)
                updateFareDisplay()
            }
        }

        btnAddStop.setOnClickListener { showAddStopDialog { name, type ->
            majorStops.add(mapOf("name" to name, "type" to type))
            majorStopsList?.let { refreshMajorStopsList(it) }
        }
        }
        btnAddRoute.setOnClickListener {
            showAddPointDialog { category, name ->
                pointsAlongRoute.getOrPut(category) { mutableListOf() }.add(name)
                pointsAlongList?.let { refreshPointsAlongList(it) }
            }
        }

        btnSave.setOnClickListener {
            val routeCode = etRouteCode.text.toString().trim().uppercase()
            when {
                routeCode.isBlank() -> { Toast.makeText(requireContext(), "Please enter a route code.", Toast.LENGTH_SHORT).show(); return@setOnClickListener }
                majorStops.size < 3 -> { Toast.makeText(requireContext(), "Please add Origin, Intermediate, and Destination stops.", Toast.LENGTH_SHORT).show(); return@setOnClickListener }
                waypoints.size < 2  -> { Toast.makeText(requireContext(), "Please import KML or drop at least 2 waypoints.", Toast.LENGTH_SHORT).show(); return@setOnClickListener }
            }
            saveToFirestore(routeCode)
        }

        view.findViewById<LinearLayout>(R.id.btnFullscreenMap).setOnClickListener {
            val validWaypoints = waypoints.filter { it.latitude != 0.0 || it.longitude != 0.0 }
            val coordsString = validWaypoints.joinToString("|") { "${it.latitude},${it.longitude}" }
            val fullscreenFragment = RouteMapFullscreen.newInstance(
                coordsString,
                view.findViewById<EditText>(R.id.etRouteCode).text.toString().ifBlank { "New Route" }
            )
            parentFragmentManager.beginTransaction()
                .replace(R.id.fragmentContainer, fullscreenFragment)
                .addToBackStack(null)
                .commit()
        }

        majorStopsList?.let { refreshMajorStopsList(it) }
        pointsAlongList?.let { refreshPointsAlongList(it) }
        if (waypoints.isNotEmpty()) refreshMapOverlays()
    }

    private fun updateFareDisplay() {
        if (storedDistanceKm < 0) {
            if (etNormalFare?.text.isNullOrBlank() || etNormalFare?.text == "Auto-computed") {
                etNormalFare?.text     = "Auto-computed"
                etDiscountedFare?.text = "Auto-computed"
            }
            return
        }
        val normal     = calculateFare(storedDistanceKm, isTraditionalSelected, false)
        val discounted = calculateFare(storedDistanceKm, isTraditionalSelected, true)
        etNormalFare?.setText("₱$normal.00")
        etDiscountedFare?.setText("₱$discounted.00")
        tvWaypointCount?.text = "${waypoints.size} road points • ${"%.2f".format(storedDistanceKm)} km (one-way)"
    }

    private fun importKmlFromUri(uri: Uri) {
        try {
            val inputStream = requireContext().contentResolver.openInputStream(uri)
                ?: run { Toast.makeText(requireContext(), "Could not open file.", Toast.LENGTH_SHORT).show(); return }
            val kmlContent = inputStream.bufferedReader().use { it.readText() }
            inputStream.close()

            val result      = parseKml(kmlContent)
            val linePoints  = result.first
            val namedPoints = result.second

            if (linePoints.isEmpty() && namedPoints.isEmpty()) {
                Toast.makeText(requireContext(), "No route data found in KML.", Toast.LENGTH_LONG).show()
                return
            }

            AlertDialog.Builder(requireContext())
                .setTitle("Import KML")
                .setMessage("Road points: ${linePoints.size}\nNamed stops: ${namedPoints.size}\n\nImport this route?")
                .setPositiveButton("Import") { _, _ ->
                    val originPoint = namedPoints.firstOrNull()?.second
                    val destinationPoint = namedPoints.maxByOrNull { point ->
                        if (originPoint == null) 0.0
                        else {
                            val dLat = point.second.latitude  - originPoint.latitude
                            val dLng = point.second.longitude - originPoint.longitude
                            dLat * dLat + dLng * dLng
                        }
                    }?.second

                    farePoints.clear()
                    if (originPoint != null && destinationPoint != null) {
                        farePoints.add(originPoint)
                        farePoints.add(destinationPoint)
                    } else if (linePoints.size >= 2) {
                        farePoints.add(linePoints.first())
                        farePoints.add(linePoints[linePoints.size / 2])
                    }

                    waypoints.clear()
                    waypoints.addAll(linePoints)

                    if (namedPoints.isNotEmpty()) {
                        majorStops.clear()

                        namedPoints.take(3).forEachIndexed { index, pair ->
                            val type = when (index) {
                                0 -> "Origin Point"
                                1 -> "Intermediate Stop"
                                else -> "Destination Point"
                            }
                            majorStops.add(mapOf("name" to pair.first, "type" to type))
                        }

                        majorStopsList?.let { refreshMajorStopsList(it) }

                        if (namedPoints.size > 3) {
                            val extras = namedPoints.drop(3).map { it.first }
                            pointsAlongRoute.getOrPut("road") { mutableListOf() }.addAll(extras)
                            pointsAlongList?.let { refreshPointsAlongList(it) }
                        }
                    }

                    refreshMapOverlays()
                    if (waypoints.isNotEmpty()) {
                        mapView?.controller?.setCenter(waypoints[waypoints.size / 2])
                        mapView?.controller?.setZoom(13.0)
                    }

                    Toast.makeText(requireContext(), "Import successful! Computing fare...", Toast.LENGTH_SHORT).show()
                    fetchAndStoreFare()
                }
                .setNegativeButton("Cancel", null)
                .show()

        } catch (e: Exception) {
            android.util.Log.e("KML", "Import error: ${e.message}")
            Toast.makeText(requireContext(), "Failed to read KML: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    private fun fetchAndStoreFare() {
        val points = if (farePoints.size >= 2) farePoints
        else waypoints.filter { it.latitude != 0.0 || it.longitude != 0.0 }
            .let { if (it.size >= 2) listOf(it.first(), it.last()) else it }

        if (points.size < 2) return

        val coordsString = "${points.first().longitude},${points.first().latitude};${points.last().longitude},${points.last().latitude}"

        Thread {
            try {
                val client   = okhttp3.OkHttpClient()
                val request  = okhttp3.Request.Builder()
                    .url("https://router.project-osrm.org/route/v1/driving/$coordsString?overview=false")
                    .build()
                val response = client.newCall(request).execute()
                val json     = org.json.JSONObject(response.body?.string() ?: return@Thread)
                val routes   = json.getJSONArray("routes")
                if (routes.length() == 0) return@Thread

                storedDistanceKm = routes.getJSONObject(0).getDouble("distance") / 1000.0

                requireActivity().runOnUiThread {
                    if (!isAdded) return@runOnUiThread
                    updateFareDisplay() // display from stored distance
                }
            } catch (e: Exception) {
                android.util.Log.e("OSRM", "Fare: ${e.message}")
            }
        }.start()
    }

    private fun parseKml(kmlContent: String): Pair<List<GeoPoint>, List<Pair<String, GeoPoint>>> {
        val linePoints  = mutableListOf<GeoPoint>()
        val namedPoints = mutableListOf<Pair<String, GeoPoint>>()
        try {
            val factory = javax.xml.parsers.DocumentBuilderFactory.newInstance()
            val builder = factory.newDocumentBuilder()
            val doc     = builder.parse(kmlContent.byteInputStream())
            doc.documentElement.normalize()

            val lineStrings = doc.getElementsByTagName("LineString")
            for (i in 0 until lineStrings.length) {
                val coordNodes = (lineStrings.item(i) as org.w3c.dom.Element).getElementsByTagName("coordinates")
                for (j in 0 until coordNodes.length) parseCoordText(coordNodes.item(j).textContent.trim(), linePoints)
            }

            val placemarks = doc.getElementsByTagName("Placemark")
            for (i in 0 until placemarks.length) {
                val placemark  = placemarks.item(i) as org.w3c.dom.Element
                val pointNodes = placemark.getElementsByTagName("Point")
                if (pointNodes.length == 0) continue
                val name      = placemark.getElementsByTagName("name").item(0)?.textContent?.trim() ?: "Stop ${i + 1}"
                val coordNode = (pointNodes.item(0) as org.w3c.dom.Element).getElementsByTagName("coordinates").item(0) ?: continue
                val parts = coordNode.textContent.trim().split(",")
                if (parts.size < 2) continue
                try {
                    val lng = parts[0].toDouble(); val lat = parts[1].toDouble()
                    if (lat in 4.0..21.0 && lng in 116.0..127.0) namedPoints.add(Pair(name, GeoPoint(lat, lng)))
                } catch (e: NumberFormatException) { }
            }
        } catch (e: Exception) { android.util.Log.e("KML", "Parse error: ${e.message}") }
        return Pair(linePoints, namedPoints)
    }

    private fun parseCoordText(coordText: String, points: MutableList<GeoPoint>) {
        coordText.split(Regex("\\s+")).forEach { coord ->
            val parts = coord.trim().split(",")
            if (parts.size >= 2) {
                try {
                    val lng = parts[0].toDouble(); val lat = parts[1].toDouble()
                    if (lat in 4.0..21.0 && lng in 116.0..127.0) points.add(GeoPoint(lat, lng))
                } catch (e: NumberFormatException) { }
            }
        }
    }

    private fun geocodeAndAddWaypoint(name: String, index: Int) {
        val encodedQuery = java.net.URLEncoder.encode("$name, Cebu City, Philippines", "UTF-8")
        val url = "https://nominatim.openstreetmap.org/search?q=$encodedQuery&format=json&limit=5&countrycodes=ph&bounded=1&viewbox=123.7,10.5,124.1,10.1"
        Thread {
            try {
                val client   = okhttp3.OkHttpClient()
                val request  = okhttp3.Request.Builder().url(url).header("User-Agent", "JeeplinkAdmin/1.0").build()
                val response = client.newCall(request).execute()
                val arr      = org.json.JSONArray(response.body?.string() ?: return@Thread)
                if (arr.length() == 0) { retryGeocode(name, index); return@Thread }
                val geoPoint = GeoPoint(arr.getJSONObject(0).getDouble("lat"), arr.getJSONObject(0).getDouble("lon"))
                requireActivity().runOnUiThread { if (!isAdded) return@runOnUiThread; placeWaypointAtIndex(geoPoint, index) }
            } catch (e: Exception) { android.util.Log.e("Geocode", "Error: ${e.message}") }
        }.start()
    }

    private fun retryGeocode(name: String, index: Int) {
        val encodedQuery = java.net.URLEncoder.encode("$name, Philippines", "UTF-8")
        val url = "https://nominatim.openstreetmap.org/search?q=$encodedQuery&format=json&limit=1&countrycodes=ph"
        Thread {
            try {
                val client   = okhttp3.OkHttpClient()
                val request  = okhttp3.Request.Builder().url(url).header("User-Agent", "JeeplinkAdmin/1.0").build()
                val response = client.newCall(request).execute()
                val arr      = org.json.JSONArray(response.body?.string() ?: return@Thread)
                requireActivity().runOnUiThread {
                    if (!isAdded) return@runOnUiThread
                    if (arr.length() == 0) { Toast.makeText(requireContext(), "Could not find: $name. Drop waypoint manually.", Toast.LENGTH_LONG).show(); return@runOnUiThread }
                    placeWaypointAtIndex(GeoPoint(arr.getJSONObject(0).getDouble("lat"), arr.getJSONObject(0).getDouble("lon")), index)
                }
            } catch (e: Exception) { android.util.Log.e("Geocode", "Retry: ${e.message}") }
        }.start()
    }

    private fun placeWaypointAtIndex(geoPoint: GeoPoint, index: Int) {
        if (index < waypoints.size) waypoints[index] = geoPoint
        else { while (waypoints.size <= index) waypoints.add(GeoPoint(0.0, 0.0)); waypoints[index] = geoPoint }
        if (farePoints.isEmpty()) farePoints.add(geoPoint)
        else if (farePoints.size == 1) farePoints.add(geoPoint)
        else farePoints[farePoints.size - 1] = geoPoint
        refreshMapOverlays()
        if (storedDistanceKm < 0) fetchAndStoreFare()
        mapView?.controller?.animateTo(geoPoint)
    }

    private fun setupMapTapListener() {
        mapView?.setOnTouchListener { _, event ->
            if (event.action == android.view.MotionEvent.ACTION_UP) {
                val geoPoint = mapView?.projection?.fromPixels(event.x.toInt(), event.y.toInt())
                if (geoPoint is GeoPoint) {
                    waypoints.add(geoPoint)
                    if (farePoints.isEmpty()) farePoints.add(geoPoint)
                    else if (farePoints.size == 1) farePoints.add(geoPoint)
                    else farePoints[farePoints.size - 1] = geoPoint
                    refreshMapOverlays()
                    if (storedDistanceKm < 0) fetchAndStoreFare()
                }
            }
            false
        }
    }

    private fun refreshMapOverlays() {
        mapView?.overlays?.clear()
        val validPoints = waypoints.filter { it.latitude != 0.0 || it.longitude != 0.0 }
        if (validPoints.size >= 2) {
            val polyline = Polyline()
            polyline.setPoints(ArrayList(validPoints))
            polyline.outlinePaint.apply {
                color       = Color.parseColor("#1246C2")
                strokeWidth = 12f
                isAntiAlias = true
                style       = android.graphics.Paint.Style.STROKE
                strokeJoin  = android.graphics.Paint.Join.ROUND
                strokeCap   = android.graphics.Paint.Cap.ROUND
            }
            polyline.infoWindow = null
            mapView?.overlays?.add(polyline)
        }
        mapView?.invalidate()
        tvWaypointCount?.text = if (storedDistanceKm >= 0)
            "${waypoints.size} road points • ${"%.2f".format(storedDistanceKm)} km (one-way)"
        else
            "${waypoints.size} road points loaded"
    }

    private fun calculateFare(distanceKm: Double, isTraditional: Boolean, isDiscounted: Boolean): Int {
        val baseFare  = if (isTraditional) 14.0 else 17.0
        val ratePerKm = if (isTraditional) 2.00 else 2.40
        val fare = if (distanceKm <= 4.0) baseFare else baseFare + ((distanceKm - 4.0) * ratePerKm)
        return if (isDiscounted) (fare * 0.80).toInt() else fare.toInt()
    }

    private fun loadExistingRouteData(routeId: String) {
        FirebaseFirestore.getInstance().collection("jeepney_routes").document(routeId).get()
            .addOnSuccessListener { doc ->
                if (!isAdded) return@addOnSuccessListener

                val fareMin = doc.getLong("fareMin")?.toInt()
                val fareMax = doc.getLong("fareMax")?.toInt()
                val savedKm = doc.getDouble("distanceKm")
                if (savedKm != null && savedKm > 0) {
                    storedDistanceKm = savedKm
                }  else if (fareMin != null) {
                    storedDistanceKm = if (fareMin <= 14) 4.0
                    else 4.0 + (fareMin - 14.0) / 2.0
                }
                updateFareDisplay()

                (doc.get("majorStops") as? List<*>)?.let { rawStops ->
                    majorStops.clear()
                    rawStops.forEach { item ->
                        val map  = item as? Map<*, *> ?: return@forEach
                        val name = map["name"] as? String ?: return@forEach
                        majorStops.add(mapOf("name" to name, "type" to (map["type"] as? String ?: "Intermediate Stop")))
                    }
                    majorStopsList?.let { refreshMajorStopsList(it) }
                }
                (doc.get("pointsAlongRoute") as? Map<String, *>)?.let { rawMap ->
                    pointsAlongRoute.clear()
                    rawMap.forEach { (key, value) ->
                        val items = (value as? List<*>)?.filterIsInstance<String>()
                        if (!items.isNullOrEmpty()) pointsAlongRoute[key] = items.toMutableList()
                    }
                    pointsAlongList?.let { refreshPointsAlongList(it) }
                }
                (doc.get("coordinates") as? List<*>)?.let { rawCoords ->
                    if (rawCoords.isNotEmpty()) {
                        waypoints.clear()
                        rawCoords.mapNotNull { item ->
                            val map = item as? Map<*, *>
                            val lat = (map?.get("lat") as? Number)?.toDouble()
                            val lng = (map?.get("lng") as? Number)?.toDouble()
                            if (lat != null && lng != null) GeoPoint(lat, lng) else null
                        }.forEach { waypoints.add(it) }
                        farePoints.clear()
                        if (waypoints.size >= 2) { farePoints.add(waypoints.first()); farePoints.add(waypoints.last()) }
                        refreshMapOverlays()
                        if (waypoints.isNotEmpty()) mapView?.controller?.setCenter(waypoints[waypoints.size / 2])
                    }
                }
            }
    }

    private fun saveToFirestore(routeCode: String) {
        val db          = FirebaseFirestore.getInstance()
        val origin      = majorStops.firstOrNull()?.get("name") ?: ""
        val destination = majorStops.lastOrNull()?.get("name")  ?: ""
        val validPoints = waypoints.filter { it.latitude != 0.0 || it.longitude != 0.0 }

        Toast.makeText(requireContext(), "Saving route...", Toast.LENGTH_SHORT).show()

        if (storedDistanceKm >= 0) {
            val fareMin = calculateFare(storedDistanceKm, true,  false)
            val fareMax = calculateFare(storedDistanceKm, false, false)
            doSave(db, routeCode, origin, destination, validPoints, fareMin, fareMax, storedDistanceKm)
        } else {
            val fp = if (farePoints.size >= 2) farePoints
            else if (validPoints.size >= 2) listOf(validPoints.first(), validPoints.last())
            else validPoints
            Thread {
                var distKm = 0.0
                try {
                    if (fp.size >= 2) {
                        val cs = "${fp.first().longitude},${fp.first().latitude};${fp.last().longitude},${fp.last().latitude}"
                        val client   = okhttp3.OkHttpClient()
                        val request  = okhttp3.Request.Builder().url("https://router.project-osrm.org/route/v1/driving/$cs?overview=false").build()
                        val response = client.newCall(request).execute()
                        val json     = org.json.JSONObject(response.body?.string() ?: "")
                        val routes   = json.getJSONArray("routes")
                        if (routes.length() > 0) distKm = routes.getJSONObject(0).getDouble("distance") / 1000.0
                    }
                } catch (e: Exception) { android.util.Log.e("OSRM", "Save fare: ${e.message}") }
                storedDistanceKm = distKm
                val fareMin = calculateFare(distKm, true,  false)
                val fareMax = calculateFare(distKm, false, false)
                requireActivity().runOnUiThread {
                    if (!isAdded) return@runOnUiThread
                    doSave(db, routeCode, origin, destination, validPoints, fareMin, fareMax, distKm)
                }
            }.start()
        }
    }

    private fun doSave(
        db: FirebaseFirestore,
        routeCode: String,
        origin: String,
        destination: String,
        validPoints: List<GeoPoint>,
        fareMin: Int,
        fareMax: Int,
        distanceKm: Double
    ) {
        val routeData = mapOf(
            "routeCode"        to routeCode,
            "routeName"        to "$origin to $destination",
            "landmarks"        to majorStops.joinToString(" • ") { it["name"] ?: "" },
            "pujType"          to if (isTraditionalSelected) "Traditional PUJ" else "Modern PUJ",
            "areasCovered"     to detectAreasCovered(validPoints),
            "isActive"         to true,
            "dataSource"       to "admin",
            "pointsAlongRoute" to pointsAlongRoute,
            "coordinates"      to validPoints.map { mapOf("lat" to it.latitude, "lng" to it.longitude) },
            "searchKeywords"   to generateSearchKeywords(),
            "majorStops"       to majorStops,
            "fareMin"          to fareMin,
            "fareMax"          to fareMax,
            "distanceKm"       to distanceKm
        )
        if (editRouteId != null) {
            db.collection("jeepney_routes").document(editRouteId!!).update(routeData)
                .addOnSuccessListener { Toast.makeText(requireContext(), "Route $routeCode updated!", Toast.LENGTH_SHORT).show(); parentFragmentManager.popBackStack() }
                .addOnFailureListener { Toast.makeText(requireContext(), "Failed to update.", Toast.LENGTH_SHORT).show() }
        } else {
            val routeRef = db.collection("jeepney_routes").document()
            routeRef.set(routeData.toMutableMap().apply { put("routeId", routeRef.id) })
                .addOnSuccessListener { Toast.makeText(requireContext(), "Route $routeCode saved!", Toast.LENGTH_SHORT).show(); parentFragmentManager.popBackStack() }
                .addOnFailureListener { Toast.makeText(requireContext(), "Failed to save.", Toast.LENGTH_SHORT).show() }
        }
    }

    private fun detectAreasCovered(points: List<GeoPoint>): List<String> {
        val areas = mutableSetOf<String>()
        points.forEach {
            if (it.latitude in 10.28..10.34 && it.longitude in 123.85..123.95) areas.add("Cebu City")
            if (it.latitude in 10.31..10.37 && it.longitude in 123.90..123.98) areas.add("Mandaue City")
            if (it.latitude in 10.27..10.35 && it.longitude in 123.95..124.05) areas.add("Lapu-Lapu City")
        }
        return areas.toList()
    }

    private fun generateSearchKeywords(): List<String> {
        val keywords = mutableSetOf<String>()
        majorStops.forEach { it["name"]?.let { n -> keywords.add(n.lowercase()) } }
        pointsAlongRoute.values.flatten().forEach { keywords.add(it.lowercase()) }
        return keywords.toList()
    }

    override fun onResume() { super.onResume(); mapView?.onResume() }
    override fun onPause()  { super.onPause();  mapView?.onPause()  }

    private fun setTraditionalSelected(btnTraditional: TextView, btnModern: TextView, view: View) {
        view.findViewById<LinearLayout>(R.id.vehicleTypeToggle)?.setBackgroundResource(R.drawable.toggle_bg)
        btnTraditional.setBackgroundResource(R.drawable.toggle_selected); btnTraditional.setTextColor(Color.WHITE)
        btnModern.setBackgroundColor(Color.TRANSPARENT); btnModern.setTextColor(Color.parseColor("#64748B"))
    }

    private fun setModernSelected(btnTraditional: TextView, btnModern: TextView, view: View) {
        view.findViewById<LinearLayout>(R.id.vehicleTypeToggle)?.setBackgroundResource(R.drawable.toggle_bg_yellow)
        btnTraditional.setBackgroundColor(Color.parseColor("#EFD8AB")); btnTraditional.setTextColor(Color.parseColor("#64748B"))
        btnModern.setBackgroundResource(R.drawable.toggle_selected_yellow); btnModern.setTextColor(Color.parseColor("#09215C"))
    }

    private fun showAddStopDialog(onAdd: (String, String) -> Unit) {
        if (majorStops.size >= 3) {
            Toast.makeText(requireContext(), "Route limit reached (3 stops only).", Toast.LENGTH_SHORT).show()
            return
        }

        val type = when (majorStops.size) {
            0 -> "Origin Point"
            1 -> "Intermediate Stop"
            2 -> "Destination Point"
            else -> return
        }

        val dialogView = LayoutInflater.from(requireContext()).inflate(R.layout.dialog_add_stop, null)
        val etName = dialogView.findViewById<EditText>(R.id.etStopName)
        val tvSubtitle = dialogView.findViewById<TextView>(R.id.dialogSubtitle)
        val btnAdd = dialogView.findViewById<View>(R.id.btnDialogAdd)
        val btnCancel = dialogView.findViewById<View>(R.id.btnDialogCancel)

        tvSubtitle.text = "Adding: $type"
        val typeColor = when (type) {
            "Origin Point" -> Color.parseColor("#22C55E")
            "Intermediate Stop" -> Color.parseColor("#1246C2")
            else -> Color.parseColor("#E03C3C")
        }
        tvSubtitle.setTextColor(typeColor)
        etName.hint = when (type) {
            "Origin Point" -> "e.g. Urgello"
            "Intermediate Stop" -> "e.g. SM City Cebu"
            else -> "e.g. Parkmall"
        }

        val dialog = AlertDialog.Builder(requireContext(), R.style.CustomDialogTheme)
            .setView(dialogView)
            .setCancelable(true)
            .create()

        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)

        dialog.show()

        val metrics = resources.displayMetrics
        val width = (metrics.widthPixels * 0.90).toInt()
        dialog.window?.setLayout(width, ViewGroup.LayoutParams.WRAP_CONTENT)

        btnAdd.setOnClickListener {
            val name = etName.text.toString().trim()
            if (name.isEmpty()) {
                etName.error = "Stop name is required"
                return@setOnClickListener
            }
            val stopIndex = majorStops.size
            onAdd(name, type)
            geocodeAndAddWaypoint(name, stopIndex)
            dialog.dismiss()
        }

        btnCancel.setOnClickListener { dialog.dismiss() }
    }

    private fun showAddPointDialog(onAdd: (String, String) -> Unit) {
        val dialogView = LayoutInflater.from(requireContext()).inflate(R.layout.dialog_add_point, null)

        val spinner = dialogView.findViewById<Spinner>(R.id.spinnerCategory)
        val etName = dialogView.findViewById<EditText>(R.id.etPointName)
        val btnAdd = dialogView.findViewById<View>(R.id.btnDialogAddPoint)
        val btnCancel = dialogView.findViewById<View>(R.id.btnDialogCancelPoint)

        val displayLabels = pointCategories.map { categoryLabels[it] ?: it }.toTypedArray()
        val adapter = ArrayAdapter(requireContext(), android.R.layout.simple_spinner_dropdown_item, displayLabels)
        spinner.adapter = adapter

        val dialog = AlertDialog.Builder(requireContext(), R.style.CustomDialogTheme)
            .setView(dialogView)
            .setCancelable(true)
            .create()

        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)
        dialog.show()

        val metrics = resources.displayMetrics
        val width = (metrics.widthPixels * 0.90).toInt()
        dialog.window?.setLayout(width, ViewGroup.LayoutParams.WRAP_CONTENT)

        btnAdd.setOnClickListener {
            val name = etName.text.toString().trim()
            if (name.isEmpty()) {
                etName.error = "Name is required"
                return@setOnClickListener
            }

            val selectedPos = spinner.selectedItemPosition
            val category = pointCategories[selectedPos]

            onAdd(category, name)
            dialog.dismiss()
        }

        btnCancel.setOnClickListener { dialog.dismiss() }
    }

    private fun refreshMajorStopsList(container: LinearLayout) {
        container.removeAllViews()
        if (majorStops.isEmpty()) { container.addView(buildEmptyLabel("Add Origin → Intermediate → Destination")); return }
        majorStops.forEachIndexed { i, stop ->
            val color = when (stop["type"]) { "Origin Point" -> Color.parseColor("#22C55E"); "Intermediate Stop" -> Color.parseColor("#1246C2"); "Destination Point" -> Color.parseColor("#E03C3C"); else -> Color.parseColor("#64748B") }
            container.addView(buildStopRow(stop["name"] ?: "", stop["type"] ?: "", color) { majorStops.removeAt(i); refreshMajorStopsList(container) })
        }
        if (majorStops.size < 3) {
            container.addView(TextView(requireContext()).apply {
                text = when (majorStops.size) { 1 -> "Add Intermediate Stop next"; 2 -> "Add Destination Point next"; else -> "" }
                textSize = 12f; setTextColor(Color.parseColor("#94A3B8")); setPadding(32, 8, 32, 16)
            })
        }
    }

    private fun refreshPointsAlongList(container: LinearLayout) {
        container.removeAllViews()
        if (pointsAlongRoute.isEmpty()) { container.addView(buildEmptyLabel("No points added yet. Tap + ADD POINT.")); return }
        pointCategories.forEach { category ->
            val items = pointsAlongRoute[category] ?: return@forEach
            if (items.isEmpty()) return@forEach
            container.addView(TextView(requireContext()).apply { text = categoryLabels[category]; textSize = 13f; setTextColor(Color.parseColor("#64748B")); typeface = android.graphics.Typeface.DEFAULT_BOLD; setPadding(32, 24, 32, 4) })
            items.forEachIndexed { i, name ->
                container.addView(buildStopRow(name, categoryLabels[category] ?: "") {
                    pointsAlongRoute[category]?.removeAt(i)
                    if (pointsAlongRoute[category].isNullOrEmpty()) pointsAlongRoute.remove(category)
                    refreshPointsAlongList(container)
                })
            }
        }
    }

    private fun buildEmptyLabel(text: String) = TextView(requireContext()).apply {
        this.text = text; setPadding(32, 48, 32, 48); gravity = android.view.Gravity.CENTER; setTextColor(Color.parseColor("#94A3B8"))
    }

    private fun buildStopRow(name: String, type: String, color: Int = Color.parseColor("#0F172A"), onDelete: () -> Unit) =
        LinearLayout(requireContext()).apply {
            orientation = LinearLayout.HORIZONTAL; setPadding(30, 12, 30, 12)
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
            addView(View(requireContext()).apply { layoutParams = LinearLayout.LayoutParams(8, LinearLayout.LayoutParams.MATCH_PARENT).apply { marginEnd = 16; minimumHeight = 80 }; setBackgroundColor(color) })
            addView(LinearLayout(requireContext()).apply {
                orientation = LinearLayout.VERTICAL; layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
                addView(TextView(requireContext()).apply { text = name; textSize = 15f; setTextColor(Color.parseColor("#0F172A")); typeface = android.graphics.Typeface.DEFAULT_BOLD })
                addView(TextView(requireContext()).apply { text = type; textSize = 12f; setTextColor(color); typeface = android.graphics.Typeface.DEFAULT_BOLD })
            })
            addView(ImageView(requireContext()).apply {
                layoutParams = LinearLayout.LayoutParams(48, 48); setImageResource(android.R.drawable.ic_menu_delete)
                imageTintList = android.content.res.ColorStateList.valueOf(Color.parseColor("#E3423F")); setOnClickListener { onDelete() }
            })
        }
}