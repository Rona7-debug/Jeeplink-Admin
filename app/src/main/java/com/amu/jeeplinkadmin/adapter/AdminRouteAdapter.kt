package com.amu.jeeplinkadmin.adapter

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.amu.jeeplinkadmin.R

data class AdminRoute(
    val routeId: String = "",
    val routeCode: String = "",
    val routeName: String = "",
    val landmarks: String = "",
    val pujType: String = "",
    val isActive: Boolean = true,
    val regularFare: Int = 0,
    val majorStops: List<Map<String, String>> = emptyList(),
    val fareMin: Int = 0,
    val fareMax: Int = 0
) : java.io.Serializable

class AdminRouteAdapter(
    private var routes: List<AdminRoute>,
    private val onEditClick: (AdminRoute) -> Unit
) : RecyclerView.Adapter<AdminRouteAdapter.RouteViewHolder>() {

    class RouteViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val tvRouteCode: TextView    = view.findViewById(R.id.tvRouteCode)
        val tvLandmarks: TextView    = view.findViewById(R.id.tvLandmarks)
        val tvFare: TextView         = view.findViewById(R.id.tvFare)
        val tvFare2: TextView        = view.findViewById(R.id.tvFare2)
        val btnViewDetails: TextView = view.findViewById(R.id.btnViewDetails)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RouteViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_route_admin, parent, false)
        return RouteViewHolder(view)
    }

    override fun onBindViewHolder(holder: RouteViewHolder, position: Int) {
        val route = routes[position]

        holder.tvRouteCode.text = if (route.routeCode.isNotBlank())
            "${route.routeCode}: ${route.routeName}"
        else route.routeName

        val stops = route.majorStops
        holder.tvLandmarks.text = if (stops.isNotEmpty()) {
            stops.joinToString(" → ") { it["name"] ?: "" }
        } else {
            route.landmarks
        }

        if (route.fareMin > 0 && route.fareMax > 0) {
            holder.tvFare.text  = "₱${route.fareMin}.00"
            holder.tvFare2.text = "— ₱${route.fareMax}.00"
        } else {
            holder.tvFare.text  = "₱--"
            holder.tvFare2.text = ""
        }

        holder.btnViewDetails.setOnClickListener { onEditClick(route) }
        holder.itemView.setOnClickListener { onEditClick(route) }
    }

    override fun getItemCount(): Int = routes.size

    fun updateRoutes(newRoutes: List<AdminRoute>) {
        routes = newRoutes
        notifyDataSetChanged()
    }
}