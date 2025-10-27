package com.genzopia.Instagame.LoginActivities

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import androidx.recyclerview.widget.RecyclerView

class AvatarAdapter(
    private val items: List<Int>,
    private val onClick: (resId: Int) -> Unit
) : RecyclerView.Adapter<AvatarAdapter.ViewHolder>() {

    inner class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val img: ImageView = view.findViewById(com.genzopia.Instagame.R.id.imgAvatar)
        val overlay: View = view.findViewById(com.genzopia.Instagame.R.id.selectionOverlay)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val v = LayoutInflater.from(parent.context).inflate(com.genzopia.Instagame.R.layout.item_avatar_card, parent, false)
        return ViewHolder(v)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val resId = items[position]
        holder.img.setImageResource(resId)
        holder.overlay.visibility = View.GONE
        holder.itemView.setOnClickListener {
            onClick(resId)
        }
    }

    override fun getItemCount(): Int = items.size
}

