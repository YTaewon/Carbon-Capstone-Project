package com.example.myapplication12345.ui.ranking

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.example.myapplication12345.R

class RankingAdapter(private val profileList: ArrayList<Profiles>) : RecyclerView.Adapter<RankingAdapter.CustomViewHolder>() {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): CustomViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_ranking, parent, false)
        return CustomViewHolder(view)
    }

    override fun getItemCount(): Int = profileList.size

    override fun onBindViewHolder(holder: CustomViewHolder, position: Int) {
        val profile = profileList[position]
        holder.profile.setImageResource(profile.profile)
        holder.name.text = profile.name
        profile.score.toString().also { holder.score.text = it }
        (position + 4).toString().also { holder.ranking.text = it }
    }

    class CustomViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val profile = itemView.findViewById<ImageView>(R.id.iv_profile)
        val name = itemView.findViewById<TextView>(R.id.tv_name)
        val score = itemView.findViewById<TextView>(R.id.tv_score)
        val ranking = itemView.findViewById<TextView>(R.id.tv_ranking)
    }

    fun updateData(newProfiles: ArrayList<Profiles>) {
        profileList.clear()
        profileList.addAll(newProfiles)
        profileList.sortByDescending { it.score }
        notifyDataSetChanged()
    }
}