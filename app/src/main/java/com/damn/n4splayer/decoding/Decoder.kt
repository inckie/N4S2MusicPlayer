package com.damn.n4splayer.decoding

import loggersoft.kotlin.streams.ByteOrder
import loggersoft.kotlin.streams.StreamInput
import loggersoft.kotlin.streams.StringEncoding
import loggersoft.kotlin.streams.toBinaryBufferedStream
import java.io.ByteArrayInputStream
import java.io.Closeable
import java.io.EOFException
import java.io.InputStream

object Decoder {

    const val SampleRate = 22050

    @ExperimentalUnsignedTypes
    private class ASFBlockHeader(ifs: StreamInput) {
        val blockId = ifs.readString(StringEncoding.ASCII, 4)
        val size = ifs.readInt(ByteOrder.LittleEndian)
    }

    @ExperimentalUnsignedTypes
    private class PTHeader(ifs: StreamInput) {
        init {
            val magic = ifs.readByteArray(4)
            while (true) {
                when (ifs.readByteUnsigned()) {
                    0xFF -> break
                    0xFE -> {} // skip
                    0xFC -> {} // skip
                    0xFD -> {
                        while (true) {
                            when (ifs.readByteUnsigned()) {
                                0x82 -> dwChannels = readInt(ifs)
                                0x83 -> dwCompression = readInt(ifs)
                                0x84 -> dwSampleRate = readInt(ifs)
                                0x85 -> dwNumSamples = readInt(ifs)
                                0x86 -> dwLoopOffset = readInt(ifs)
                                0x87 -> dwLoopLength = readInt(ifs)
                                0x88 -> dwDataStart = readInt(ifs)
                                0x92 -> dwBytesPerSample = readInt(ifs)
                                0x80 -> bSplit = readByte(ifs)
                                0xA0 -> bSplitCompression = readByte(ifs)
                                0xFF -> {}
                                0x8A -> break
                                else -> ifs.skip(ifs.readByte().toLong())
                            }
                        }
                    }
                    else -> {
                        val readByte = ifs.readByteUnsigned()
                        if (0xFF != readByte)
                            ifs.skip(readByte.toLong())
                        else {
                            ifs.skip(3) // must be 4 bytes align, but I don't have a position
                            break
                        }
                    }
                }
            }
        }

        private fun readByte(ifs: StreamInput) =
            ifs.readInt(ifs.readByteUnsigned()).toByte()

        private fun readInt(ifs: StreamInput) =
            ifs.readInt(ifs.readByteUnsigned(), byteOrder = ByteOrder.BigEndian).toInt()

        var dwSampleRate: Int = 0
        var dwChannels: Int = 0
        var dwCompression: Int = 0
        var dwNumSamples: Int = 0
        var dwDataStart: Int = 0
        var dwLoopOffset: Int = 0
        var dwLoopLength: Int = 0
        var dwBytesPerSample: Int = 0
        var bSplit: Byte = 0
        var bSplitCompression: Byte = 0
    }

    @ExperimentalUnsignedTypes
    fun StreamInput.readByteArray(count: Int) = ByteArray(count).apply {
        readBytes(this)
    }

    @ExperimentalUnsignedTypes
    private fun <T> parseSCHlBlock(ifs: StreamInput, func: (ByteArray) -> T): List<T> {
        val result = mutableListOf<T>()
        try {
            val hdrFile = ASFBlockHeader(ifs)
            // alignment workaround hacks (we don't have position for that stream)
            val pt = ByteArray(hdrFile.size - 8) // 8 is size of ASFBlockHeader
            ifs.readBytes(pt)
            val ptHeader = PTHeader(ByteArrayInputStream(pt).toBinaryBufferedStream(byteOrder = ByteOrder.BigEndian))
            val hdrSCCl = ASFBlockHeader(ifs)
            val nBlocks = ifs.readInt(ByteOrder.LittleEndian)
            // we only need to calculate these, since the rest are multiple of 4
            var pos = hdrFile.size
            for (i in 0 until nBlocks) {
                pos += parseSCDlBlock(ifs) { result += func(it) }
            }
            val end = ASFBlockHeader(ifs)
            // there we need 4 byte align
            ifs.skip((4 - pos % 4) % 4L)
        } catch (e: EOFException) {
            // ignored
        }
        return result
    }

    @ExperimentalUnsignedTypes
    private fun parseSCDlBlock(
        ifs: StreamInput,
        result: (ByteArray) -> Unit
    ): Int {
        val hdrFile = ASFBlockHeader(ifs)
        // 8 is size of ASFBlockHeader
        when (hdrFile.blockId) {
            "SCDl" -> result(ifs.readByteArray(hdrFile.size - 8))
            else -> ifs.skip(hdrFile.size - 8L)
        }
        return hdrFile.size
    }

    @ExperimentalUnsignedTypes
    class SCHlBlock(ifs: StreamInput) {
        val blocks: List<ByteArray> = parseSCHlBlock(ifs) { it }
    }

    @ExperimentalUnsignedTypes
    class CloseableIterator(stream: InputStream) : Iterator<List<ShortArray>>, Closeable {

        private val _stream = stream.toBinaryBufferedStream(byteOrder = ByteOrder.BigEndian)

        private var next: List<ShortArray> = listOf()

        override fun hasNext(): Boolean {
            if (_stream.isClosed || _stream.isEof)
                return false
            next = parseSCHlBlock(_stream) { ADPCMDecoder.decode(it) }
            return next.isNotEmpty()
        }

        override fun next(): List<ShortArray> = next

        override fun close() = _stream.close()

    }

}