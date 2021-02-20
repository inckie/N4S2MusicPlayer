import com.damn.n4splayer.ADPCMDecoder
import loggersoft.kotlin.streams.ByteOrder
import loggersoft.kotlin.streams.StreamInput
import loggersoft.kotlin.streams.StringEncoding
import loggersoft.kotlin.streams.toBinaryBufferedStream
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
                    0xFE -> {
                    } // skip
                    0xFC -> {
                    } // skip
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
                                0xFF -> {
                                }
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
                            ifs.skip(3)
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
    private fun decodeSCDlBlock(
        ifs: StreamInput,
        result: MutableList<ShortArray>
    ) {
        val hdrFile = ASFBlockHeader(ifs)
        if ("SCDl" == hdrFile.blockId) {
            val adpcm = ifs.readByteArray(hdrFile.size - 8)
            val decodeBlock = ADPCMDecoder.decode(adpcm)
            result.add(decodeBlock)
        } else {
            ifs.skip(hdrFile.size - 8L) // 8 is size of ASFBlockHeader
        }
    }

    @ExperimentalUnsignedTypes
    private fun StreamInput.readByteArray(count: Int): ByteArray {
        val res = ByteArray(count)
        this.readBytes(res)
        return res
    }

    @ExperimentalUnsignedTypes
    private fun decodeSCHlBlock(ifs: StreamInput): List<ShortArray> {
        val result = mutableListOf<ShortArray>()
        try {
            val hdrFile = ASFBlockHeader(ifs)
            val phHeader = PTHeader(ifs)
            val hdrSCCl = ASFBlockHeader(ifs)
            val nBlocks = ifs.readInt(ByteOrder.LittleEndian)
            for (i in 0 until nBlocks) {
                decodeSCDlBlock(ifs, result)
            }
            val end = ASFBlockHeader(ifs)
        } catch (e: EOFException) {
            // ignored
        }
        return result
    }

    @ExperimentalUnsignedTypes
    private fun parseSCDlBlock(
        ifs: StreamInput,
        result: MutableList<ByteArray>
    ) {
        val hdrFile = ASFBlockHeader(ifs)
        if ("SCDl" == hdrFile.blockId) {
            val adpcm = ifs.readByteArray(hdrFile.size - 8)
            result.add(adpcm)
        } else {
            ifs.skip(hdrFile.size - 8L) // 8 is size of ASFBlockHeader
        }
    }

    @ExperimentalUnsignedTypes
    class SCHlBlock(ifs: StreamInput) {
        private val blocks: List<ByteArray>
        init {
            val hdrFile = ASFBlockHeader(ifs)
            val phHeader = PTHeader(ifs)
            val hdrSCCl = ASFBlockHeader(ifs)
            val nBlocks = ifs.readInt(ByteOrder.LittleEndian)
            val result = mutableListOf<ByteArray>()
            for (i in 0 until nBlocks) {
                parseSCDlBlock(ifs, result)
            }
            val end = ASFBlockHeader(ifs)
            blocks = result
        }

        fun decode(): List<ShortArray> = blocks.map {ADPCMDecoder.decode(it) }
    }

    @ExperimentalUnsignedTypes
    class CloseableIterator(stream: InputStream)
        : Iterator<List<ShortArray>>, Closeable {

        private val _stream = stream.toBinaryBufferedStream(byteOrder = ByteOrder.BigEndian)

        private var next: List<ShortArray> = listOf()

        override fun hasNext(): Boolean {
            if(_stream.isClosed || _stream.isEof)
                return false
            next = decodeSCHlBlock(_stream)
            return next.isNotEmpty()
        }

        override fun next(): List<ShortArray> = next

        override fun close() = _stream.close()

    }

}