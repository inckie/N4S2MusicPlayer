package com.damn.n4splayer

import android.content.ContentResolver
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import com.damn.n4splayer.decoding.Decoder
import com.damn.n4splayer.decoding.MapDecoder
import loggersoft.kotlin.streams.toBinaryBufferedStream
import org.json.JSONObject
import java.io.EOFException
import java.util.*

private fun collectFiles(childDocuments: List<DocumentFile>, ext: String): Map<String, Uri> =
    childDocuments
        .filter { it.name!!.contains(ext, true) }
        .map { it.name!!.substringBefore(".") to it.uri }.toMap()

@OptIn(ExperimentalUnsignedTypes::class)
fun parseTrack(
    contentResolver: ContentResolver,
    track: Track
): Pair<MapDecoder.MapFile, MutableList<List<ByteArray>>> {
    val map = contentResolver.openInputStream(track.map!!)!!
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

fun loadTracks(
    contentResolver: ContentResolver,
    childDocuments: List<DocumentFile>
): MutableList<Track> {
    val muss = collectFiles(childDocuments, "mus")
    val maps = collectFiles(childDocuments, "map")
    val tracks = mutableListOf<Track>()
    val infos = parse(contentResolver, childDocuments)
    tracks.addAll(muss.map {
        (Track(it.key, it.value, maps[it.key], infos[it.key.lowercase(Locale.ENGLISH)]))
    })
    tracks.sortBy { it.name }
    return tracks
}

private fun getFile(jsonObject: JSONObject, key: String, files: Map<String, Uri>): Uri? {
    val fileName = jsonObject.optString(key)
    if (fileName.isEmpty())
        return null
    return files[fileName.lowercase(Locale.ENGLISH)]
}

fun parse(
    contentResolver: ContentResolver,
    childDocuments: List<DocumentFile>
): Map<String, TrackInfo> {
    val json = childDocuments.firstOrNull { it.name!!.endsWith(".json", true) } ?: return mapOf()
    val mapping = contentResolver.openInputStream(json.uri)!!.use {
        val readText = it.bufferedReader().readText()
        JSONObject(readText)
    }

    val allFiles = childDocuments.map { it.name!!.lowercase(Locale.ENGLISH) to it.uri }.toMap()
    val res = mutableMapOf<String, TrackInfo>()
    mapping.keys().forEach { track: String ->
        val jsonObject = mapping.getJSONObject(track)
        res[track.lowercase(Locale.ENGLISH) ] = TrackInfo(
            getFile(jsonObject, "banner", allFiles),
            getFile(jsonObject, "icon", allFiles)
        )
    }
    return res
}
