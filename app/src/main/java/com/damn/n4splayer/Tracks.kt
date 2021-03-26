package com.damn.n4splayer

import android.content.ContentResolver
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import loggersoft.kotlin.streams.toBinaryBufferedStream
import java.io.EOFException

private fun collectFiles(childDocuments: List<DocumentFile>, ext: String): Map<String, Uri> =
    childDocuments
        .filter { it.name!!.contains(ext, true) }
        .map { it.name!!.substringBefore(".") to it.uri }.toMap()

@OptIn(ExperimentalUnsignedTypes::class)
fun parseTrack(
    contentResolver: ContentResolver,
    track: Track
): Pair<MapDecoder.MapFile, MutableList<List<ByteArray>>> {
    val map = contentResolver.openInputStream(track.map)!!
        .use { MapDecoder.load(it.toBinaryBufferedStream()) }
    val sections = mutableListOf<List<ByteArray>>()
    try {
        contentResolver.openInputStream(track.track)!!.use {
            val bis = it.toBinaryBufferedStream()
            while (!bis.isEof) {
                sections.add(Decoder.SCHlBlock(bis).blocks)
            }
        }
    } catch (e: EOFException) {
        // ignored
    }
    return Pair(map, sections)
}

fun loadTracks(childDocuments: List<DocumentFile>): MutableList<Track> {
    val muss = collectFiles(childDocuments, "mus")
    val maps = collectFiles(childDocuments, "map")
    val tracks = mutableListOf<Track>()
    maps.forEach {
        muss[it.key]?.let { mus ->
            tracks.add(Track(it.key, mus, it.value))
        }
    }
    return tracks
}
