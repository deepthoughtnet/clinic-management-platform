package com.deepthoughtnet.clinic.api.platform.communicationtest;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Locale;
import org.springframework.util.StringUtils;

/** Converts supported reminder TTS output into a provider-neutral PCM16 intermediate. */
final class ReminderAudioNormalizer {
    private ReminderAudioNormalizer() {}

    static CanonicalPcm normalize(byte[] audio, String contentType, int knownSampleRate) {
        if (audio == null || audio.length == 0) throw new IllegalArgumentException("Empty audio");
        String type = StringUtils.hasText(contentType) ? contentType.toLowerCase(Locale.ROOT) : "";
        if (type.contains("wav") || type.contains("wave")) return fromWav(audio);
        if (type.contains("pcm") || type.contains("l16")) {
            if (knownSampleRate <= 0 || audio.length % 2 != 0) throw new IllegalArgumentException("Invalid PCM16 audio");
            return new CanonicalPcm(audio.clone(), knownSampleRate, 1, "PCM16");
        }
        throw new IllegalArgumentException("Unsupported TTS audio content type");
    }

    private static CanonicalPcm fromWav(byte[] wav) {
        if (wav.length < 12 || wav[0] != 'R' || wav[1] != 'I' || wav[2] != 'F' || wav[3] != 'F'
                || wav[8] != 'W' || wav[9] != 'A' || wav[10] != 'V' || wav[11] != 'E') {
            throw new IllegalArgumentException("Invalid WAV audio");
        }
        int fmt = -1, fmtLength = 0, data = -1, dataLength = 0;
        for (int offset = 12; offset + 8 <= wav.length;) {
            int length = littleInt(wav, offset + 4);
            if (length < 0 || offset + 8L + length > wav.length) throw new IllegalArgumentException("Invalid WAV chunk");
            if (wav[offset] == 'f' && wav[offset + 1] == 'm' && wav[offset + 2] == 't' && wav[offset + 3] == ' ') {
                fmt = offset + 8; fmtLength = length;
            } else if (wav[offset] == 'd' && wav[offset + 1] == 'a' && wav[offset + 2] == 't' && wav[offset + 3] == 'a') {
                data = offset + 8; dataLength = length;
            }
            offset += 8 + length + (length & 1);
        }
        if (fmt < 0 || fmtLength < 16 || data < 0 || dataLength <= 0 || data + dataLength > wav.length) {
            throw new IllegalArgumentException("WAV fmt/data chunk missing");
        }
        int format = littleShort(wav, fmt);
        int channels = littleShort(wav, fmt + 2);
        int rate = littleInt(wav, fmt + 4);
        int bits = littleShort(wav, fmt + 14);
        if (format != 1 || channels <= 0 || rate <= 0 || bits != 16 || dataLength % (channels * 2) != 0) {
            throw new IllegalArgumentException("Only PCM16 WAV audio is supported");
        }
        return new CanonicalPcm(java.util.Arrays.copyOfRange(wav, data, data + dataLength), rate, channels, "WAV/PCM16");
    }

    private static int littleInt(byte[] bytes, int offset) {
        return ByteBuffer.wrap(bytes, offset, 4).order(ByteOrder.LITTLE_ENDIAN).getInt();
    }

    private static int littleShort(byte[] bytes, int offset) {
        return ByteBuffer.wrap(bytes, offset, 2).order(ByteOrder.LITTLE_ENDIAN).getShort() & 0xffff;
    }

    record CanonicalPcm(byte[] bytes, int sampleRate, int channels, String inputFormat) {}
}
