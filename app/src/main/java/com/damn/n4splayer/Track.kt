package com.damn.n4splayer

import kotlinx.parcelize.Parcelize
import android.net.Uri
import android.os.Parcelable

@Parcelize
data class Track(
    val name: String,
    val track: Uri,
    val map: Uri?
) : Parcelable