package me.navoei.customdiscs.client;

import javazoom.spi.mpeg.sampled.convert.MpegFormatConversionProvider;
import javazoom.spi.mpeg.sampled.file.MpegAudioFileReader;
import me.navoei.customdiscs.AudioFileFormat;
import org.jflac.sound.spi.Flac2PcmAudioInputStream;
import org.jflac.sound.spi.FlacAudioFileReader;

import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.UnsupportedAudioFileException;
import java.io.File;
import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;

final class AudioDecoder {
    static final AudioFormat FORMAT = new AudioFormat(
            AudioFormat.Encoding.PCM_SIGNED, 48000.0F, 16, 1, 2, 48000.0F, false);

    private AudioDecoder() {
    }

    static AudioInputStream open(File file) throws IOException, UnsupportedAudioFileException {
        String extension = decoderExtension(file);
        AudioInputStream input;
        if ("mp3".equals(extension)) {
            input = mp3(file);
        } else if ("flac".equals(extension)) {
            input = flac(file);
        } else if ("wav".equals(extension)) {
            input = AudioSystem.getAudioInputStream(file);
        } else {
            throw new UnsupportedAudioFileException("Unsupported audio extension.");
        }
        return convert(input);
    }

    static String decoderExtension(File file) {
        return AudioFileFormat.extension(file.getName());
    }

    static AudioInputStream convert(AudioInputStream input)
            throws IOException, UnsupportedAudioFileException {
        int channels = input.getFormat().getChannels();
        if (channels < 1 || channels > 8) {
            input.close();
            throw new UnsupportedAudioFileException("Audio must contain between one and eight channels.");
        }
        AudioFormat source = input.getFormat();
        if (source.getSampleRate() <= 0.0F || Float.isNaN(source.getSampleRate())
                || Float.isInfinite(source.getSampleRate())) {
            input.close();
            throw new UnsupportedAudioFileException("Audio has no valid sample rate.");
        }
        AudioFormat pcmFormat = new AudioFormat(AudioFormat.Encoding.PCM_SIGNED,
                source.getSampleRate(), 16, channels, channels * 2,
                source.getSampleRate(), false);
        AudioInputStream pcm;
        try {
            pcm = AudioSystem.getAudioInputStream(pcmFormat, input);
        } catch (IllegalArgumentException e) {
            input.close();
            throw new UnsupportedAudioFileException("Cannot convert audio to 16-bit PCM.");
        }
        if (pcmFormat.getSampleRate() <= 0.0F || pcmFormat.getSampleRate() > 192000.0F) {
            pcm.close();
            input.close();
            throw new UnsupportedAudioFileException("Unsupported audio sample rate.");
        }
        if (channels == 1 && pcmFormat.getSampleRate() == FORMAT.getSampleRate()) {
            return new AudioInputStream(new OwnedInputStream(pcm, input), FORMAT,
                    pcm.getFrameLength());
        }
        return new AudioInputStream(new OwnedInputStream(new MonoResampleInputStream(pcm), input), FORMAT,
                AudioSystem.NOT_SPECIFIED);
    }

    private static AudioInputStream mp3(File file) throws IOException, UnsupportedAudioFileException {
        AudioInputStream input = new MpegAudioFileReader().getAudioInputStream(file);
        AudioFormat source = input.getFormat();
        AudioFormat decoded = new AudioFormat(AudioFormat.Encoding.PCM_SIGNED,
                source.getSampleRate(), 16, source.getChannels(),
                source.getChannels() * 2, source.getFrameRate(), false);
        return new MpegFormatConversionProvider().getAudioInputStream(decoded, input);
    }

    private static AudioInputStream flac(File file) throws IOException, UnsupportedAudioFileException {
        AudioInputStream input = new FlacAudioFileReader().getAudioInputStream(file);
        AudioFormat source = input.getFormat();
        int sampleSize = source.getSampleSizeInBits();
        AudioFormat decoded = new AudioFormat(AudioFormat.Encoding.PCM_SIGNED,
                source.getSampleRate(), sampleSize, source.getChannels(),
                (sampleSize / 8) * source.getChannels(), source.getFrameRate(), false);
        AudioInputStream pcm = new Flac2PcmAudioInputStream(input, decoded, input.getFrameLength());
        if (sampleSize <= 16) {
            return pcm;
        }
        AudioFormat pcm16 = new AudioFormat(AudioFormat.Encoding.PCM_SIGNED,
                source.getSampleRate(), 16, source.getChannels(),
                source.getChannels() * 2, source.getFrameRate(), false);
        return new AudioInputStream(new Pcm16Downscaler(pcm), pcm16, pcm.getFrameLength());
    }

    private static final class OwnedInputStream extends FilterInputStream {
        private final InputStream owner;

        private OwnedInputStream(InputStream stream, InputStream owner) {
            super(stream);
            this.owner = owner;
        }

        @Override
        public void close() throws IOException {
            IOException failure = null;
            try {
                super.close();
            } catch (IOException e) {
                failure = e;
            }
            try {
                owner.close();
            } catch (IOException e) {
                if (failure == null) {
                    failure = e;
                }
            }
            if (failure != null) {
                throw failure;
            }
        }
    }

    private static final class MonoResampleInputStream extends InputStream {
        private final AudioInputStream source;
        private final int frameSize;
        private final int channels;
        private final int sourceRate;
        private final byte[] input;
        private final byte[] samples;
        private final byte[] singleByte = new byte[1];
        private int inputOffset;
        private int inputLength;
        private int sampleOffset;
        private int sampleLength;
        private int currentSample;
        private int nextSample;
        private long phase;
        private boolean initialized;
        private boolean ended;

        private MonoResampleInputStream(AudioInputStream source) {
            this.source = source;
            channels = source.getFormat().getChannels();
            frameSize = channels * 2;
            sourceRate = Math.round(source.getFormat().getSampleRate());
            input = new byte[frameSize * 1024];
            samples = new byte[2048];
        }

        @Override
        public int read() throws IOException {
            return read(singleByte, 0, 1) < 0 ? -1 : singleByte[0] & 0xff;
        }

        @Override
        public int read(byte[] output, int offset, int length) throws IOException {
            if (offset < 0 || length < 0 || length > output.length - offset) {
                throw new IndexOutOfBoundsException();
            }
            if (length == 0) {
                return 0;
            }
            int written = 0;
            while (written < length) {
                if (sampleOffset == sampleLength && !fillSamples()) {
                    return written == 0 ? -1 : written;
                }
                int count = Math.min(length - written, sampleLength - sampleOffset);
                System.arraycopy(samples, sampleOffset, output, offset + written, count);
                sampleOffset += count;
                written += count;
            }
            return written;
        }

        @Override
        public void close() throws IOException {
            source.close();
        }

        private boolean fillSamples() throws IOException {
            if (ended) {
                return false;
            }
            if (!initialized) {
                currentSample = readSourceSample();
                nextSample = readSourceSample();
                initialized = true;
            }
            sampleLength = 0;
            sampleOffset = 0;
            for (int i = 0; i < samples.length / 2 && currentSample != Integer.MIN_VALUE; i++) {
                double fraction = nextSample == Integer.MIN_VALUE ? 0.0D
                        : (double) phase / FORMAT.getSampleRate();
                short mono = (short) Math.round(currentSample
                        + (nextSample - currentSample) * fraction);
                samples[sampleLength++] = (byte) mono;
                samples[sampleLength++] = (byte) (mono >> 8);
                if (nextSample == Integer.MIN_VALUE) {
                    ended = true;
                    break;
                }
                phase += sourceRate;
                while (phase >= FORMAT.getSampleRate()) {
                    currentSample = nextSample;
                    nextSample = readSourceSample();
                    phase -= (long) FORMAT.getSampleRate();
                    if (nextSample == Integer.MIN_VALUE) {
                        break;
                    }
                }
            }
            return sampleLength > 0;
        }

        private int readSourceSample() throws IOException {
            if (inputLength - inputOffset < frameSize) {
                inputLength = source.read(input, 0, input.length);
                inputOffset = 0;
                if (inputLength < frameSize) {
                    return Integer.MIN_VALUE;
                }
            }
            int sum = 0;
            for (int channel = 0; channel < channels; channel++) {
                int offset = inputOffset + channel * 2;
                sum += (short) ((input[offset] & 0xff) | (input[offset + 1] << 8));
            }
            inputOffset += frameSize;
            return sum / channels;
        }
    }
}
