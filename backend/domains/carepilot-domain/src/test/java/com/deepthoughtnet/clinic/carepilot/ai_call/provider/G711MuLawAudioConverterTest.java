package com.deepthoughtnet.clinic.carepilot.ai_call.provider;

import java.io.ByteArrayOutputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

class G711MuLawAudioConverterTest {
    @Test
    void convertsPcmWavToEightKhzMonoTwentyMillisecondFrames() {
        byte[] wav = pcmWav(16_000, 1, 640);

        var frames = G711MuLawAudioConverter.toFrames(wav);

        assertEquals(2, frames.size());
        assertEquals(160, frames.get(0).length);
        assertEquals(160, frames.get(1).length);
        assertFalse(java.util.Arrays.equals(frames.get(0), frames.get(1)));
    }

    @Test
    void rejectsNonPcmAudio() {
        byte[] wav = pcmWav(8_000, 1, 160);
        wav[20] = 3;

        org.junit.jupiter.api.Assertions.assertThrows(IllegalArgumentException.class,
                () -> G711MuLawAudioConverter.toFrames(wav));
    }

    @Test
    void convertsCanonicalPcmToPacedFullFrames() {
        byte[] pcm = new byte[16_000 * 2 / 2]; // 500 ms at 16 kHz, mono PCM16
        var frames = G711MuLawAudioConverter.toFramesPcm(pcm, 16_000, 1);
        assertEquals(25, frames.size());
        frames.forEach(frame -> assertEquals(160, frame.length));
    }

    private byte[] pcmWav(int sampleRate, int channels, int samples) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        int dataSize = samples * channels * 2;
        writeAscii(out, "RIFF");
        writeInt(out, 36 + dataSize);
        writeAscii(out, "WAVEfmt ");
        writeInt(out, 16);
        writeShort(out, 1);
        writeShort(out, channels);
        writeInt(out, sampleRate);
        writeInt(out, sampleRate * channels * 2);
        writeShort(out, channels * 2);
        writeShort(out, 16);
        writeAscii(out, "data");
        writeInt(out, dataSize);
        for (int i = 0; i < samples; i++) {
            short value = (short) ((i % 80) * 300);
            for (int c = 0; c < channels; c++) writeShort(out, value);
        }
        return out.toByteArray();
    }

    private void writeAscii(ByteArrayOutputStream out, String value) {
        out.writeBytes(value.getBytes(java.nio.charset.StandardCharsets.US_ASCII));
    }

    private void writeInt(ByteArrayOutputStream out, int value) {
        out.writeBytes(ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN).putInt(value).array());
    }

    private void writeShort(ByteArrayOutputStream out, int value) {
        out.writeBytes(ByteBuffer.allocate(2).order(ByteOrder.LITTLE_ENDIAN).putShort((short) value).array());
    }
}
