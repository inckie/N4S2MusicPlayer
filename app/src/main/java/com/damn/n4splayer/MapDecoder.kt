package com.damn.n4splayer

import Decoder.readByteArray
import loggersoft.kotlin.streams.ByteOrder
import loggersoft.kotlin.streams.StreamInput
import loggersoft.kotlin.streams.StringEncoding

object MapDecoder {

    @ExperimentalUnsignedTypes
    class MAPHeader(ifs: StreamInput) {
        var bUnknown1: Byte
        var bFirstSection: Int
        var bNumSections: Int
        var bRecordSize: Int
        var Unknown2: ByteArray
        var bNumRecords: Int

        init {
            val magic = ifs.readString(StringEncoding.ASCII,4)
            bUnknown1 = ifs.readByte()
            bFirstSection = ifs.readByteUnsigned()
            bNumSections = ifs.readByteUnsigned()
            bRecordSize = ifs.readByteUnsigned()
            Unknown2 = ifs.readByteArray(3)
            bNumRecords = ifs.readByteUnsigned()
        }
    }

    @ExperimentalUnsignedTypes
    class MAPSectionDefRecord(ifs: StreamInput) {
        val bMin = ifs.readByteUnsigned()
        val bMax = ifs.readByteUnsigned()
        val bNextSection = ifs.readByteUnsigned()
    }

    @ExperimentalUnsignedTypes
    class MAPSectionDef(ifs: StreamInput) {
        var bIndex: Int = ifs.readByteUnsigned()
        var bNumRecords: Int = ifs.readByteUnsigned()
        var szId: Int = ifs.readInt(2).toInt()
        var msdRecords: List<MAPSectionDefRecord> = IntRange(0, 7).map {
            MAPSectionDefRecord(ifs)
        }.toList().take(bNumRecords) // I read some data I don't need there
    }

    data class MapFile(val startSection: Int,
                       val sections: List<Section>)

    @ExperimentalUnsignedTypes
    class Section(val section: MAPSectionDef,
                  val offset: Long)

    @ExperimentalUnsignedTypes
    @JvmStatic
    fun load(ifs: StreamInput): MapFile {
        val header = MAPHeader(ifs)
        // super ineffective
        val map = IntRange(0, header.bNumSections - 1).map { MAPSectionDef(ifs) }
        ifs.skip(header.bRecordSize.toLong() * header.bNumRecords)
        val offsets = IntRange(0, header.bNumSections - 1).map { ifs.readInt(4, false, ByteOrder.BigEndian) }
        return MapFile(header.bFirstSection, map.zip(offsets).map { Section(it.first, it.second) })
    }

}