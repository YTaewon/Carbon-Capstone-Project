package com.example.myapplication12345

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView

import androidx.recyclerview.widget.RecyclerView

class RankingAdapter(private val profileList: ArrayList<Profiles>) : RecyclerView.Adapter<RankingAdapter.CustomViewHolder>() {

    init {
        // 초기화 시 score에 따라 내림차순으로 정렬
        profileList.sortByDescending { it.score }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): CustomViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_ranking, parent, false)
        return CustomViewHolder(view)
    }

    override fun getItemCount(): Int {
        return profileList.size
    }

    override fun onBindViewHolder(holder: CustomViewHolder, position: Int) {
        val profile = profileList[position]
        holder.profile.setImageResource(profile.profile)
        holder.name.text = profile.name
        holder.score.text = profile.score.toString()
        // 랭킹 표시 (position + 1)
        holder.ranking.text = (position + 1).toString()
    }

    class CustomViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val profile = itemView.findViewById<ImageView>(R.id.iv_profile)
        val name = itemView.findViewById<TextView>(R.id.tv_name)
        val score = itemView.findViewById<TextView>(R.id.tv_score)
        val ranking = itemView.findViewById<TextView>(R.id.tv_ranking) // 랭킹 표시 TextView
    }
}