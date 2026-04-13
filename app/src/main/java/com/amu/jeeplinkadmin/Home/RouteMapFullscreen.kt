package com.amu.jeeplinkadmin.Home

import android.graphics.Color
import android.graphics.Paint
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ListView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.cardview.widget.CardView
import androidx.fragment.app.Fragment
import com.amu.jeeplinkadmin.R
import org.osmdroid.config.Configuration
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Polyline

class RouteMapFullscreen : Fragment() {

    companion object {
        private const val ARG_COORDS     = "arg_coords"
        private const val ARG_TITLE      = "arg_title"
        private const val ARG_STOP_NAMES = "arg_stop_names"

        fun newInstance(
            coordsString: String,
            title: String,
            stopNames: ArrayList<String> = arrayListOf()
        ): RouteMapFullscreen {
            val fragment = RouteMapFullscreen()
            fragment.arguments = Bundle().apply {
                putString(ARG_COORDS, coordsString)
                putString(ARG_TITLE, title)
                putStringArrayList(ARG_STOP_NAMES, stopNames)
            }
            return fragment
        }

        fun newInstance(
            coords: ArrayList<HashMap<String, Double>>,
            title: String,
            stopNames: ArrayList<String> = arrayListOf()
        ): RouteMapFullscreen {
            val coordsString = coords.mapNotNull { map ->
                val lat = map["lat"]; val lng = map["lng"]
                if (lat != null && lng != null) "$lat,$lng" else null
            }.joinToString("|")
            return newInstance(coordsString, title, stopNames)
        }
    }

    private var mapView: MapView? = null
    private val waypoints = mutableListOf<GeoPoint>()

    private val searchHandler = Handler(Looper.getMainLooper())
    private var searchRunnable: Runnable? = null
    private val suggestionResults = mutableListOf<Pair<String, GeoPoint>>()

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        Configuration.getInstance().apply {
            load(requireContext(), requireContext().getSharedPreferences("osmdroid", 0))
            userAgentValue = requireContext().packageName
            osmdroidTileCache = java.io.File(requireContext().cacheDir, "osmdroid")
            tileDownloadThreads = 4
            tileFileSystemCacheMaxBytes = 100L * 1024 * 1024
        }
        return inflater.inflate(R.layout.fragment_route_map_fullscreen, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val btnBack         = view.findViewById<View>(R.id.btnBack)
        val tvMapTitle      = view.findViewById<TextView>(R.id.tvMapTitle)
        val tvWaypointCount = view.findViewById<TextView>(R.id.tvWaypointCountFullscreen)
        val etMapSearch     = view.findViewById<EditText>(R.id.etMapSearch)
        val btnMapSearch    = view.findViewById<TextView>(R.id.btnMapSearch)
        val btnUndo         = view.findViewById<TextView>(R.id.btnUndoWaypoint)
        val btnClear        = view.findViewById<TextView>(R.id.btnClearWaypoints)
        val btnDone         = view.findViewById<TextView>(R.id.btnDoneFullscreen)
        val suggestionCard  = view.findViewById<CardView>(R.id.suggestionCard)
        val lvSuggestions   = view.findViewById<ListView>(R.id.lvSuggestions)

        val title     = arguments?.getString(ARG_TITLE) ?: "Route Map"
        val stopNames = arguments?.getStringArrayList(ARG_STOP_NAMES) ?: arrayListOf()
        tvMapTitle.text = if (title.isNotBlank()) "Route $title" else "Route Map"

        mapView = view.findViewById(R.id.mapViewFullscreen)
        mapView?.setTileSource(TileSourceFactory.MAPNIK)
        mapView?.setUseDataConnection(true)
        mapView?.setMultiTouchControls(true)
        mapView?.controller?.setZoom(14.0)
        mapView?.controller?.setCenter(GeoPoint(10.3157, 123.8854))
        mapView?.isHorizontalMapRepetitionEnabled = false
        mapView?.isVerticalMapRepetitionEnabled   = false
        mapView?.minZoomLevel = 10.0
        mapView?.maxZoomLevel = 19.0

        // ── Load existing waypoints from compact string ───────────────
        val coordsString = arguments?.getString(ARG_COORDS) ?: ""
        if (coordsString.isNotBlank()) {
            coordsString.split("|").forEach { pair ->
                val parts = pair.split(",")
                if (parts.size == 2) {
                    try {
                        val lat = parts[0].toDouble()
                        val lng = parts[1].toDouble()
                        if (lat != 0.0 || lng != 0.0) waypoints.add(GeoPoint(lat, lng))
                    } catch (e: NumberFormatException) { }
                }
            }
        }

        refreshMapOverlays()
        updateWaypointCount(tvWaypointCount)
        if (waypoints.isNotEmpty()) {
            mapView?.controller?.setCenter(waypoints[waypoints.size / 2])
            mapView?.controller?.setZoom(13.0)
        }

        // ── Autocomplete search ───────────────────────────────────────
        etMapSearch.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                val query = s.toString().trim()
                searchRunnable?.let { searchHandler.removeCallbacks(it) }
                if (query.length < 2) { suggestionCard.visibility = View.GONE; return }
                searchRunnable = Runnable { fetchSuggestions(query, suggestionCard, lvSuggestions, tvWaypointCount) }
                searchHandler.postDelayed(searchRunnable!!, 500)
            }
        })

        btnMapSearch.setOnClickListener {
            val query = etMapSearch.text.toString().trim()
            if (query.isNotBlank()) {
                searchRunnable?.let { searchHandler.removeCallbacks(it) }
                fetchSuggestions(query, suggestionCard, lvSuggestions, tvWaypointCount)
            } else Toast.makeText(requireContext(), "Enter a location to search", Toast.LENGTH_SHORT).show()
        }

        etMapSearch.setOnEditorActionListener { _, _, _ ->
            val query = etMapSearch.text.toString().trim()
            if (query.isNotBlank()) fetchSuggestions(query, suggestionCard, lvSuggestions, tvWaypointCount)
            true
        }

        lvSuggestions.setOnItemClickListener { _, _, position, _ ->
            val selected = suggestionResults.getOrNull(position) ?: return@setOnItemClickListener
            waypoints.add(selected.second)
            refreshMapOverlays()
            updateWaypointCount(tvWaypointCount)
            mapView?.controller?.animateTo(selected.second)
            mapView?.controller?.setZoom(16.0)
            etMapSearch.setText("")
            suggestionCard.visibility = View.GONE
            suggestionResults.clear()
            Toast.makeText(requireContext(), "Added: ${selected.first}", Toast.LENGTH_SHORT).show()
        }

        // ── Undo ──────────────────────────────────────────────────────
        btnUndo.setOnClickListener {
            if (waypoints.isNotEmpty()) {
                waypoints.removeAt(waypoints.size - 1)
                refreshMapOverlays()
                updateWaypointCount(tvWaypointCount)
            }
        }

        // ── Clear ─────────────────────────────────────────────────────
        btnClear.setOnClickListener {
            if (waypoints.isNotEmpty()) {
                AlertDialog.Builder(requireContext())
                    .setTitle("Clear Waypoints")
                    .setMessage("Remove all ${waypoints.size} waypoints?")
                    .setPositiveButton("Clear") { _, _ ->
                        waypoints.clear()
                        refreshMapOverlays()
                        updateWaypointCount(tvWaypointCount)
                    }
                    .setNegativeButton("Cancel", null).show()
            }
        }

        // ── Done — pass waypoints back to NewRoute ────────────────────
        btnDone.setOnClickListener { sendResultAndPop() }
        btnBack.setOnClickListener { sendResultAndPop() }
    }

    private fun sendResultAndPop() {
        val coordsOut = waypoints.joinToString("|") { "${it.latitude},${it.longitude}" }
        val result = Bundle().apply { putString("updated_waypoints", coordsOut) }
        parentFragmentManager.setFragmentResult("map_waypoints", result)
        parentFragmentManager.popBackStack()
    }

    // ── Fetch autocomplete suggestions ────────────────────────────────
    private fun fetchSuggestions(
        query: String,
        suggestionCard: CardView,
        lvSuggestions: ListView,
        tvCount: TextView
    ) {
        val searchQueries = listOf(
            "$query, Cebu City, Philippines",
            "$query, Cebu, Philippines",
            "$query, Philippines"
        )
        Thread {
            for (searchQuery in searchQueries) {
                try {
                    val encodedQuery = java.net.URLEncoder.encode(searchQuery, "UTF-8")
                    val url = "https://nominatim.openstreetmap.org/search?q=$encodedQuery&format=json&limit=5&countrycodes=ph"
                    val client  = okhttp3.OkHttpClient()
                    val request = okhttp3.Request.Builder().url(url).header("User-Agent", "JeeplinkAdmin/1.0").build()
                    val response = client.newCall(request).execute()
                    val body     = response.body?.string() ?: continue
                    val arr      = org.json.JSONArray(body)
                    if (arr.length() > 0) {
                        val results = mutableListOf<Pair<String, GeoPoint>>()
                        for (i in 0 until arr.length()) {
                            val item      = arr.getJSONObject(i)
                            val name      = item.optString("display_name")
                            val lat       = item.getDouble("lat")
                            val lng       = item.getDouble("lon")
                            val shortName = name.split(",").take(2).joinToString(",").trim()
                            results.add(Pair(shortName, GeoPoint(lat, lng)))
                        }
                        requireActivity().runOnUiThread {
                            if (!isAdded) return@runOnUiThread
                            suggestionResults.clear()
                            suggestionResults.addAll(results)
                            lvSuggestions.adapter = ArrayAdapter(requireContext(), android.R.layout.simple_list_item_1, results.map { it.first })
                            suggestionCard.visibility = View.VISIBLE
                        }
                        return@Thread
                    }
                } catch (e: Exception) { android.util.Log.e("Suggest", "Error: ${e.message}") }
            }
            requireActivity().runOnUiThread {
                if (isAdded) {
                    suggestionResults.clear()
                    suggestionCard.visibility = View.GONE
                    Toast.makeText(requireContext(), "No results for: $query", Toast.LENGTH_SHORT).show()
                }
            }
        }.start()
    }

    // ── Clean polyline only — NO markers, NO icons ────────────────────
    private fun refreshMapOverlays() {
        mapView?.overlays?.clear()
        if (waypoints.isEmpty()) { mapView?.invalidate(); return }

        if (waypoints.size >= 2) {
            val polyline = Polyline()
            polyline.setPoints(ArrayList(waypoints))
            polyline.outlinePaint.apply {
                color       = Color.parseColor("#1246C2")
                strokeWidth = 10f
                isAntiAlias = true
                style       = Paint.Style.STROKE
                strokeJoin  = Paint.Join.ROUND
                strokeCap   = Paint.Cap.ROUND
            }
            polyline.infoWindow = null
            mapView?.overlays?.add(polyline)
        }

        mapView?.invalidate()
    }

    private fun updateWaypointCount(tv: TextView) {
        tv.text = "${waypoints.size} pt${if (waypoints.size != 1) "s" else ""}"
    }

    override fun onResume() { super.onResume(); mapView?.onResume() }
    override fun onPause()  { super.onPause();  mapView?.onPause()  }
}