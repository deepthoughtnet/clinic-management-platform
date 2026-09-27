package com.deepthoughtnet.clinic.api.platform.communicationtest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.ByteArrayOutputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import org.junit.jupiter.api.Test;

class ReminderAudioNormalizerTest {
    @Test
    void normalizesElevenLabsPcmToCanonicalAudio() {
        var normalized = ReminderAudioNormalizer.normalize(new byte[320], "audio/pcm", 16_000);
        assertThat(normalized.inputFormat()).isEqualTo("PCM16");
        assertThat(normalized.sampleRate()).isEqualTo(16_000);
        assertThat(normalized.channels()).isEqualTo(1);
        assertThat(normalized.bytes()).hasSize(320);
    }

    @Test
    void normalizesExistingPcmWav() {
        var normalized = ReminderAudioNormalizer.normalize(pcmWav(16_000, 1, 160), "audio/wav", 0);
        assertThat(normalized.inputFormat()).isEqualTo("WAV/PCM16");
        assertThat(normalized.sampleRate()).isEqualTo(16_000);
        assertThat(normalized.bytes()).hasSize(320);
    }

    @Test
    void rejectsMp3InsteadOfPassingItToTheWavConverter() {
        assertThatThrownBy(() -> ReminderAudioNormalizer.normalize(new byte[]{1, 2}, "audio/mpeg", 0))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Unsupported");
    }

    private byte[] pcmWav(int sampleRate, int channels, int samples) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        int dataSize = samples * channels * 2;
        writeAscii(out, "RIFF"); writeInt(out, 36 + dataSize); writeAscii(out, "WAVEfmt "); writeInt(out, 16);
        writeShort(out, 1); writeShort(out, channels); writeInt(out, sampleRate); writeInt(out, sampleRate * channels * 2);
        writeShort(out, channels * 2); writeShort(out, 16); writeAscii(out, "data"); writeInt(out, dataSize);
        for (int i = 0; i < samples * channels; i++) writeShort(out, (i % 20) * 500);
        return out.toByteArray();
    }

    private void writeAscii(ByteArrayOutputStream out, String value) { out.writeBytes(value.getBytes(java.nio.charset.StandardCharsets.US_ASCII)); }
    private void writeInt(ByteArrayOutputStream out, int value) { out.writeBytes(ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN).putInt(value).array()); }
    private void writeShort(ByteArrayOutputStream out, int value) { out.writeBytes(ByteBuffer.allocate(2).order(ByteOrder.LITTLE_ENDIAN).putShort((short) value).array()); }
}
