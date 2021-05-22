package com.damn.n4splayer.ui

import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.lifecycle.LiveData
import androidx.recyclerview.widget.LinearLayoutManager
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
        binding.list.apply {
            layoutManager = LinearLayoutManager(context)
            adapter = TrackRecyclerViewAdapter(
                this@TrackListFragment,
                mTracks,
                { track -> play(track) })
        }
        return binding.root
    }

    private fun play(track: Track) = PlayerService.play(requireActivity(), track)

    companion object {
        @JvmStatic
        fun newInstance() = TrackListFragment()
    }
}