package com.damn.n4splayer.ui

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LiveData
import androidx.lifecycle.Observer
import androidx.recyclerview.widget.RecyclerView
import com.damn.n4splayer.Track
import com.damn.n4splayer.databinding.ViewTrackListItemBinding

class TrackRecyclerViewAdapter(
    owner: LifecycleOwner,
    items: LiveData<List<Track>>,
    private val selectListener: (Track) -> Unit
) : RecyclerView.Adapter<TrackRecyclerViewAdapter.ViewHolder>(), Observer<List<Track>> {

    init {
        items.observe(owner, this)
    }

    private var values: List<Track> = ArrayList()

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder = ViewHolder(
        ViewTrackListItemBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
    )

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val item = values[position]
        holder.bind(item)
    }

    override fun getItemCount(): Int = values.size

    inner class ViewHolder(private val binding: ViewTrackListItemBinding) :
        RecyclerView.ViewHolder(binding.root) {

        override fun toString() = super.toString() + " '" + binding.content.text + "'"

        fun bind(item: Track) {
            binding.content.text = item.name
            binding.root.setOnClickListener { selectListener(item) }
        }
    }

    override fun onChanged(t: List<Track>?) {
        values = t!!
        notifyDataSetChanged()
    }

}