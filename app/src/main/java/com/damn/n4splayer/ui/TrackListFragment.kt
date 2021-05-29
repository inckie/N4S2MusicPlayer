package com.damn.n4splayer.ui

import android.content.Context
import android.graphics.drawable.Drawable
import android.net.Uri
import android.os.Bundle
import android.util.LruCache
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.lifecycle.LiveData
import com.damn.n4splayer.PlayerService
import com.damn.n4splayer.Track
import com.damn.n4splayer.databinding.FragmentItemListBinding

class TrackListFragment : Fragment() {

    private lateinit var mTracks: LiveData<List<Track>>

    override fun onAttach(context: Context) {
        super.onAttach(context)
        mTracks = (context as MainActivity).getTracks()
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        val binding = FragmentItemListBinding.inflate(inflater, container, false)
        val iconCache = object : LruCache<Uri, Drawable>(16) { // only 8 is really needed
            override fun create(key: Uri): Drawable =
                inflater.context.contentResolver.openInputStream(key).use {
                    return Drawable.createFromStream(it, key.toString())
                }
        }
        binding.list.adapter = TrackRecyclerViewAdapter(
            this@TrackListFragment,
            mTracks,
            iconCache,
            { play(it) })
        return binding.root
    }

    private fun play(track: Track) = PlayerService.play(requireActivity(), track)

    companion object {
        @JvmStatic
        fun newInstance() = TrackListFragment()
    }
}