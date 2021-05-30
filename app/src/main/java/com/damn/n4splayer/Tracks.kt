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

// cache since DocumentFile makes a query for every property check
class DocFile(df: DocumentFile) {
    val uri = df.uri
    val name = df.name!!
}

private fun collectFiles(files: List<DocFile>, ext: String): Map<String, Uri> =
    files
        .filter { it.name.contains(ext, true) }
        .map { it.name.substringBefore(".") to it.uri }.toMap()

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
    files: List<DocFile>
): MutableList<Track> {
    val muss = collectFiles(files, "mus")
    val maps = collectFiles(files, "map")
    val asfs = collectFiles(files, "asf")
    val infos = parseJson(contentResolver, files)
    val tracks = mutableListOf<Track>()
    tracks.addAll(muss.map {
        Track(it.key, it.value, maps[it.key], infos[it.key.lowercase(Locale.ENGLISH)])
    })
    tracks.addAll(asfs.map {
        Track(it.key, it.value, null, infos[it.key.lowercase(Locale.ENGLISH)])
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

private fun parseJson(
    contentResolver: ContentResolver,
    childDocuments: List<DocFile>
): Map<String, TrackInfo> {
    val json = childDocuments.firstOrNull { it.name.endsWith(".json", true) } ?: return mapOf()
    val mapping = contentResolver.openInputStream(json.uri)!!.use {
        val readText = it.bufferedReader().readText()
        JSONObject(readText)
    }

    val allFiles = childDocuments.map { it.name.lowercase(Locale.ENGLISH) to it.uri }.toMap()
    val res = mutableMapOf<String, TrackInfo>()
    mapping.keys().forEach { track: String ->
        val jsonObject = mapping.getJSONObject(track)
        res[track.lowercase(Locale.ENGLISH)] = TrackInfo(
            getFile(jsonObject, "banner", allFiles),
            getFile(jsonObject, "icon", allFiles)
        )
    }
    return res
}
