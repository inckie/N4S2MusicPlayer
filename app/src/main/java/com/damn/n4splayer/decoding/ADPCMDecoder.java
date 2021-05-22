package com.damn.n4splayer.decoding;

public class ADPCMDecoder {
    public static native short[] decode(byte[] block);
    static {
        System.loadLibrary("native-lib");
    }
}
