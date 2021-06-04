package com.damn.n4splayer.ui

import android.widget.SeekBar

// hm, no ktx helper yet?
fun SeekBar.onProgressChanged(c: (progress: Int, fromUser: Boolean) -> Unit) {
    setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
        override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) =
            c(progress, fromUser)

        override fun onStartTrackingTouch(seekBar: SeekBar?) = Unit

        override fun onStopTrackingTouch(seekBar: SeekBar?) = Unit
    })
}