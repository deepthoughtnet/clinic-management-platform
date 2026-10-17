package com.deepthoughtnet.clinic.carepilot.ai_call.provider;

import java.io.ByteArrayOutputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.List;

/** Deterministic PCM16/WAV to mono 8kHz G.711 μ-law frame preparation. */
public final class G711MuLawAudioConverter {
    private static final int TARGET_RATE = 8_000;
    private static final int FRAME_BYTES = 160;

    private G711MuLawAudioConverter() {}

    public static List<byte[]> toFrames(byte[] wav) {
        int format = findFmtChunk(wav);
        int audioFormat = littleEndianShort(wav, format);
        int channels = littleEndianShort(wav, format + 2);
        int sampleRate = littleEndianInt(wav, format + 4);
        int bitsPerSample = littleEndianShort(wav, format + 14);
        if (audioFormat != 1 || bitsPerSample != 16) throw new IllegalArgumentException("Only PCM16 WAV is supported");
        return toFrames(wav, sampleRate, channels);
    }

    public static List<byte[]> toFrames(byte[] wav, int sourceRate, int channels) {
        if (wav == null || wav.length < 44 || sourceRate <= 0 || channels <= 0) throw new IllegalArgumentException("Unsupported audio input");
        int dataOffset = findDataChunk(wav);
        int dataLength = littleEndianInt(wav, dataOffset - 4);
        int available = Math.min(dataLength, wav.length - dataOffset);
        return toFramesPcm(java.util.Arrays.copyOfRange(wav, dataOffset, dataOffset + available), sourceRate, channels);
    }

    /** Converts little-endian signed PCM16 to paced 8kHz mono μ-law frames. */
    public static List<byte[]> toFramesPcm(byte[] pcm, int sourceRate, int channels) {
        if (pcm == null || pcm.length == 0 || sourceRate <= 0 || channels <= 0 || pcm.length % (2 * channels) != 0) {
            throw new IllegalArgumentException("Empty or invalid PCM audio");
        }
        int available = pcm.length;
        if (available <= 0 || available % 2 != 0) throw new IllegalArgumentException("Empty or invalid PCM audio");
        short[] source = new short[available / 2 / channels];
        for (int i = 0; i < source.length; i++) {
            long sum = 0;
            for (int channel = 0; channel < channels; channel++) {
                int offset = ((i * channels + channel) * 2);
                sum += (short) ((pcm[offset] & 0xff) | (pcm[offset + 1] << 8));
            }
            source[i] = (short) (sum / channels);
        }
        int targetSamples = Math.max(1, (int) Math.round((double) source.length * TARGET_RATE / sourceRate));
        ByteArrayOutputStream encoded = new ByteArrayOutputStream(targetSamples);
        for (int i = 0; i < targetSamples; i++) {
            double position = (double) i * (source.length - 1) / Math.max(1, targetSamples - 1);
            int left = (int) Math.floor(position);
            int right = Math.min(source.length - 1, left + 1);
            int sample = (int) Math.round(source[left] + (source[right] - source[left]) * (position - left));
            encoded.write(muLaw(sample));
        }
        byte[] bytes = encoded.toByteArray();
        List<byte[]> frames = new ArrayList<>((bytes.length + FRAME_BYTES - 1) / FRAME_BYTES);
        for (int start = 0; start < bytes.length; start += FRAME_BYTES) {
            byte[] frame = new byte[FRAME_BYTES];
            int length = Math.min(FRAME_BYTES, bytes.length - start);
            System.arraycopy(bytes, start, frame, 0, length);
            if (length < FRAME_BYTES) java.util.Arrays.fill(frame, length, FRAME_BYTES, (byte) 0xff);
            frames.add(frame);
        }
        return List.copyOf(frames);
    }

    private static int findDataChunk(byte[] wav) {
        for (int offset = 12; offset + 8 <= wav.length;) {
            int length = littleEndianInt(wav, offset + 4);
            if (wav[offset] == 'd' && wav[offset + 1] == 'a' && wav[offset + 2] == 't' && wav[offset + 3] == 'a') return offset + 8;
            offset += 8 + length + (length & 1);
        }
        throw new IllegalArgumentException("WAV data chunk missing");
    }

    private static int findFmtChunk(byte[] wav) {
        for (int offset = 12; offset + 8 <= wav.length;) {
            int length = littleEndianInt(wav, offset + 4);
            if (wav[offset] == 'f' && wav[offset + 1] == 'm' && wav[offset + 2] == 't' && wav[offset + 3] == ' ') return offset + 8;
            offset += 8 + length + (length & 1);
        }
        throw new IllegalArgumentException("WAV fmt chunk missing");
    }

    private static int littleEndianInt(byte[] data, int offset) {
        return ByteBuffer.wrap(data, offset, 4).order(ByteOrder.LITTLE_ENDIAN).getInt();
    }

    private static int littleEndianShort(byte[] data, int offset) {
        return ByteBuffer.wrap(data, offset, 2).order(ByteOrder.LITTLE_ENDIAN).getShort() & 0xffff;
    }

    private static int muLaw(int sample) {
        int sign = (sample >> 8) & 0x80;
        if (sign != 0) sample = -sample;
        sample = Math.min(sample, 32635) + 132;
        int exponent = 7;
        for (int mask = 0x4000; (sample & mask) == 0 && exponent > 0; mask >>= 1) exponent--;
        int mantissa = (sample >> (exponent + 3)) & 0x0f;
        return (~(sign | (exponent << 4) | mantissa)) & 0xff;
    }

    /** Wraps gateway G.711 μ-law bytes as a bounded 8 kHz mono PCM16 WAV for existing STT. */
    public static byte[] muLawToPcmWav(byte[] muLaw) {
        if (muLaw == null || muLaw.length == 0) throw new IllegalArgumentException("Empty μ-law audio");
        byte[] pcm = new byte[muLaw.length * 2];
        for (int i = 0; i < muLaw.length; i++) {
            short sample = decodeMuLaw(muLaw[i]);
            pcm[i * 2] = (byte) (sample & 0xff);
            pcm[i * 2 + 1] = (byte) ((sample >>> 8) & 0xff);
        }
        ByteBuffer wav = ByteBuffer.allocate(44 + pcm.length).order(ByteOrder.LITTLE_ENDIAN);
        wav.put("RIFF".getBytes(java.nio.charset.StandardCharsets.US_ASCII)).putInt(36 + pcm.length)
                .put("WAVEfmt ".getBytes(java.nio.charset.StandardCharsets.US_ASCII)).putInt(16)
                .putShort((short) 1).putShort((short) 1).putInt(TARGET_RATE).putInt(TARGET_RATE * 2)
                .putShort((short) 2).putShort((short) 16)
                .put("data".getBytes(java.nio.charset.StandardCharsets.US_ASCII)).putInt(pcm.length).put(pcm);
        return wav.array();
    }

    /** Validates the exact PCM16 mono 8 kHz WAV contract produced for STT. */
    public static boolean isPcm16Mono8kWav(byte[] wav) {
        try {
            if (wav == null || wav.length < 44
                    || wav[0] != 'R' || wav[1] != 'I' || wav[2] != 'F' || wav[3] != 'F'
                    || wav[8] != 'W' || wav[9] != 'A' || wav[10] != 'V' || wav[11] != 'E') return false;
            int format = findFmtChunk(wav);
            int dataOffset = findDataChunk(wav);
            int dataLength = littleEndianInt(wav, dataOffset - 4);
            return littleEndianShort(wav, format) == 1
                    && littleEndianShort(wav, format + 2) == 1
                    && littleEndianInt(wav, format + 4) == TARGET_RATE
                    && littleEndianShort(wav, format + 14) == 16
                    && dataLength >= 0
                    && dataOffset + dataLength <= wav.length
                    && (dataLength % 2 == 0);
        } catch (RuntimeException ex) {
            return false;
        }
    }

    private static short decodeMuLaw(byte value) {
        int encoded = (~value) & 0xff;
        int sample = ((encoded & 0x0f) << 3) + 132;
        sample <<= (encoded & 0x70) >> 4;
        return (short) ((encoded & 0x80) != 0 ? 132 - sample : sample - 132);
    }
}
