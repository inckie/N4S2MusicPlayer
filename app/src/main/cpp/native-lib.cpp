#include <jni.h>
#include <vector>

typedef uint8_t BYTE;
typedef int16_t SHORT;
typedef uint32_t DWORD;
typedef int32_t LONG;

static inline LONG Clip16BitSample(LONG sample) {
    if (sample > 32767)
        return 32767;
    else if (sample < -32768)
        return (-32768);
    else
        return sample;
}

#define HINIBBLE(byte) ((byte) >> 4)
#define LONIBBLE(byte) ((byte) & 0x0F)

static const uint32_t EATable[] = {
        0x00000000,
        0x000000F0,
        0x000001CC,
        0x00000188,
        0x00000000,
        0x00000000,
        0xFFFFFF30,
        0xFFFFFF24,
        0x00000000,
        0x00000001,
        0x00000003,
        0x00000004,
        0x00000007,
        0x00000008,
        0x0000000A,
        0x0000000B,
        0x00000000,
        0xFFFFFFFF,
        0xFFFFFFFD,
        0xFFFFFFFC
};

struct ASFChunkHeader {
    DWORD dwOutSize;
    SHORT lCurSampleLeft;
    SHORT lPrevSampleLeft;
    SHORT lCurSampleRight;
    SHORT lPrevSampleRight;
};

static std::vector<short> decode_EAADPCM(const BYTE* inputBuffer, const ASFChunkHeader& hdr) {
    SHORT lCurSampleLeft = hdr.lCurSampleLeft;
    SHORT lPrevSampleLeft = hdr.lPrevSampleLeft;
    SHORT lCurSampleRight = hdr.lCurSampleRight;
    SHORT lPrevSampleRight = hdr.lPrevSampleRight;

    BYTE bInput;
    DWORD dwOutSize = hdr.dwOutSize; // outsize value from the ASFChunkHeader
    DWORD i, bCount, sCount;
    LONG c1left, c2left, c1right, c2right, left, right;
    BYTE dleft, dright;

    DWORD dwSubOutSize = 0x1c;

    i = 0;

    std::vector<short> result = std::vector<short>();
    result.reserve(dwOutSize * 2);

    // process integral number of (dwSubOutSize) samples
    for (bCount = 0; bCount < (dwOutSize / dwSubOutSize); bCount++) {
        bInput = inputBuffer[i++];
        c1left = EATable[HINIBBLE(bInput)];   // predictor coeffs for left channel
        c2left = EATable[HINIBBLE(bInput) + 4];
        c1right = EATable[LONIBBLE(bInput)];  // predictor coeffs for right channel
        c2right = EATable[LONIBBLE(bInput) + 4];
        bInput = inputBuffer[i++];
        dleft = HINIBBLE(bInput) + 8;   // shift value for left channel
        dright = LONIBBLE(bInput) + 8;  // shift value for right channel
        for (sCount = 0; sCount < dwSubOutSize; sCount++) {
            bInput = inputBuffer[i++];
            left = HINIBBLE(bInput);  // HIGHER nibble for left channel
            right = LONIBBLE(bInput); // LOWER nibble for right channel
            left = (left << 0x1c) >> dleft;
            right = (right << 0x1c) >> dright;
            left = (left + lCurSampleLeft * c1left + lPrevSampleLeft * c2left + 0x80) >> 8;
            right = (right + lCurSampleRight * c1right + lPrevSampleRight * c2right + 0x80) >> 8;
            left = Clip16BitSample(left);
            right = Clip16BitSample(right);
            lPrevSampleLeft = lCurSampleLeft;
            lCurSampleLeft = left;
            lPrevSampleRight = lCurSampleRight;
            lCurSampleRight = right;
            // Now we've got lCurSampleLeft and lCurSampleRight which form one stereo
            // sample and all is set for the next input byte...
            result.push_back((SHORT) lCurSampleLeft);
            result.push_back((SHORT) lCurSampleRight);
        }
    }

    // process the rest (if any)
    if ((dwOutSize % dwSubOutSize) != 0) {
        bInput = inputBuffer[i++];
        c1left = EATable[HINIBBLE(bInput)];   // predictor coeffs for left channel
        c2left = EATable[HINIBBLE(bInput) + 4];
        c1right = EATable[LONIBBLE(bInput)];  // predictor coeffs for right channel
        c2right = EATable[LONIBBLE(bInput) + 4];
        bInput = inputBuffer[i++];
        dleft = HINIBBLE(bInput) + 8;   // shift value for left channel
        dright = LONIBBLE(bInput) + 8;  // shift value for right channel
        for (sCount = 0; sCount < (dwOutSize % dwSubOutSize); sCount++) {
            bInput = inputBuffer[i++];
            left = HINIBBLE(bInput);  // HIGHER nibble for left channel
            right = LONIBBLE(bInput); // LOWER nibble for right channel
            left = (left << 0x1c) >> dleft;
            right = (right << 0x1c) >> dright;
            left = (left + lCurSampleLeft * c1left + lPrevSampleLeft * c2left + 0x80) >> 8;
            right = (right + lCurSampleRight * c1right + lPrevSampleRight * c2right + 0x80) >> 8;
            left = Clip16BitSample(left);
            right = Clip16BitSample(right);
            lPrevSampleLeft = lCurSampleLeft;
            lCurSampleLeft = left;
            lPrevSampleRight = lCurSampleRight;
            lCurSampleRight = right;
            // Now we've got lCurSampleLeft and lCurSampleRight which form one stereo
            // sample and all is set for the next input byte...
            result.push_back((SHORT) lCurSampleLeft);
            result.push_back((SHORT) lCurSampleRight);
        }
    }
    return result;
}

extern "C"
JNIEXPORT jshortArray JNICALL
Java_com_damn_n4splayer_ADPCMDecoder_decode(JNIEnv *env, jclass clazz, jbyteArray block) {
    jboolean isCopy;
    auto bytes = env->GetByteArrayElements(block, &isCopy);
    const ASFChunkHeader& ch = *(ASFChunkHeader*)bytes;
    const auto result = decode_EAADPCM((const BYTE*)bytes + 12/*sizeof(ASFChunkHeader)*/, ch);
    env->ReleaseByteArrayElements(block, bytes, JNI_ABORT);
    auto array = env->NewShortArray(result.size());
    env->SetShortArrayRegion(array, 0, result.size(), &result[0]);
    return array;
}