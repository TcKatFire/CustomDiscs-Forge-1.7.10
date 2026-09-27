package me.navoei.customdiscs.client;

import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioInputStream;
import java.io.IOException;
import java.io.InputStream;

final class Pcm16Downscaler extends InputStream {
    private final AudioInputStream source;
    private final int sourceBytesPerSample;
    private final int channels;
    private final byte[] sourceBuffer;

    Pcm16Downscaler(AudioInputStream source) {
        this.source = source;
        AudioFormat format = source.getFormat();
        sourceBytesPerSample = format.getSampleSizeInBits() / 8;
        channels = format.getChannels();
        sourceBuffer = new byte[8192 * channels * sourceBytesPerSample];
    }

    @Override
    public int read() throws IOException {
        byte[] value = new byte[1];
        return read(value, 0, 1) == -1 ? -1 : value[0] & 0xff;
    }

    @Override
    public int read(byte[] output, int offset, int length) throws IOException {
        int frames = length / (2 * channels);
        if (frames == 0) {
            return 0;
        }
        int maxBytes = Math.min(sourceBuffer.length, frames * sourceBytesPerSample * channels);
        int read = source.read(sourceBuffer, 0, maxBytes);
        if (read <= 0) {
            return -1;
        }
        int input = 0;
        int outputStart = offset;
        int frameSize = sourceBytesPerSample * channels;
        int frameCount = read / frameSize;
        for (int frame = 0; frame < frameCount; frame++) {
            for (int channel = 0; channel < channels; channel++) {
                int sample = 0;
                for (int byteIndex = 0; byteIndex < sourceBytesPerSample; byteIndex++) {
                    sample |= (sourceBuffer[input++] & 0xff) << (byteIndex * 8);
                }
                short narrowed = (short) (sample >> ((sourceBytesPerSample - 2) * 8));
                output[offset++] = (byte) narrowed;
                output[offset++] = (byte) (narrowed >> 8);
            }
        }
        return offset - outputStart;
    }
}
