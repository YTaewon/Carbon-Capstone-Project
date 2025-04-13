package com.example.myapplication12345.ui.ranking

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.example.myapplication12345.R
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener
import timber.log.Timber

class RankingAdapter(private val profileList: ArrayList<Profiles>) : RecyclerView.Adapter<RankingAdapter.CustomViewHolder>() {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): CustomViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_ranking, parent, false)
        return CustomViewHolder(view)
    }

    override fun getItemCount(): Int = profileList.size

    override fun onBindViewHolder(holder: CustomViewHolder, position: Int) {
        val profile = profileList[position]
        holder.name.text = profile.name
        profile.score.toString().also { holder.score.text = it }
        (position + 4).toString().also { holder.ranking.text = it }

        // Firebase에서 프로필 이미지 로드
        FirebaseDatabase.getInstance().reference.child("users").child(profile.userId)
            .child("profileImageUrl").addListenerForSingleValueEvent(object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    val imageUrl = snapshot.getValue(String::class.java)
                    if (imageUrl != null) {Glide.with(holder.itemView.context)
                        .load(imageUrl)
                        .placeholder(R.drawable.user)
                        .error(R.drawable.user)
                        .into(holder.profile)
                    }else{
                        holder.profile.setImageResource(R.drawable.user)
                    }
                }

                override fun onCancelled(error: DatabaseError) {
                    Timber.w(error.toException(), "loadProfileImage:onCancelled")
                    holder.profile.setImageResource(R.drawable.user)
                }
            })
    }

    class CustomViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val profile = itemView.findViewById<ImageView>(R.id.iv_profile)!!
        val name = itemView.findViewById<TextView>(R.id.tv_name)!!
        val score = itemView.findViewById<TextView>(R.id.tv_score)!!
        val ranking = itemView.findViewById<TextView>(R.id.tv_ranking)!!
    }

    fun updateData(newProfiles: ArrayList<Profiles>) {
        profileList.clear()
        profileList.addAll(newProfiles)
        profileList.sortByDescending { it.score }
        notifyDataSetChanged()
    }
}