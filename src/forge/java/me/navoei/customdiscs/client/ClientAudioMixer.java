package me.navoei.customdiscs.client;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.DataLine;
import javax.sound.sampled.LineUnavailableException;
import javax.sound.sampled.SourceDataLine;
import java.io.File;
import java.io.IOException;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;
import java.util.logging.Logger;

@SideOnly(Side.CLIENT)
final class ClientAudioMixer {
    static final ClientAudioMixer INSTANCE = new ClientAudioMixer();
    private static final Logger LOGGER = Logger.getLogger("CustomDiscs");
    private static final int SAMPLES_PER_FRAME = 960;
    private static final int MONO_BYTES = SAMPLES_PER_FRAME * 2;
    private static final javax.sound.sampled.AudioFormat OUTPUT_FORMAT =
            new javax.sound.sampled.AudioFormat(48000.0F, 16, 2, true, false);
    private final Map<UUID, Sound> sounds = new ConcurrentHashMap<UUID, Sound>();
    private volatile double listenerX;
    private volatile double listenerY;
    private volatile double listenerZ;
    private volatile float listenerYaw;
    private volatile boolean running;
    private volatile SourceDataLine line;
    private Thread thread;

    private ClientAudioMixer() {
    }

    boolean play(UUID id, File file, double x, double y, double z, float range, float gain) {
        if (sounds.size() >= 32 && !sounds.containsKey(id)) {
            return false;
        }
        Sound sound = new Sound(id, file);
        update(sound, x, y, z, range, gain);
        sounds.put(id, sound);
        if (!running) {
            running = true;
            thread = new Thread(new Runnable() {
                @Override
                public void run() {
                    mixLoop();
                }
            }, "CustomDiscs-AudioMixer");
            thread.setDaemon(true);
            thread.start();
        }
        return true;
    }

    void update(UUID id, double x, double y, double z, float range, float gain) {
        Sound sound = sounds.get(id);
        if (sound != null) {
            update(sound, x, y, z, range, gain);
        }
    }

    void stop(UUID id) {
        Sound sound = sounds.remove(id);
        if (sound != null) {
            sound.stopped = true;
        }
    }

    void setListener(double x, double y, double z, float yaw) {
        listenerX = x;
        listenerY = y;
        listenerZ = z;
        listenerYaw = yaw;
    }

    private void mixLoop() {
        try {
            ensureLine();
            byte[] output = new byte[SAMPLES_PER_FRAME * 4];
            byte[] mono = new byte[MONO_BYTES];
            int[] mix = new int[SAMPLES_PER_FRAME * 2];
            while (running) {
                if (sounds.isEmpty()) {
                    closeLine();
                    running = false;
                    return;
                }
                java.util.Arrays.fill(mix, 0);
                for (Sound sound : sounds.values()) {
                    if (sound.stopped) {
                        dispose(sound);
                        sounds.remove(sound.id);
                        continue;
                    }
                    if (sound.input == null) {
                        try {
                            sound.input = AudioDecoder.open(sound.file);
                        } catch (Exception e) {
                            LOGGER.log(Level.WARNING, "Could not decode custom audio " + sound.file.getName(), e);
                            finish(sound, false);
                            continue;
                        }
                    }
                    int bytesRead;
                    try {
                        bytesRead = readFrame(sound.input, mono);
                    } catch (IOException e) {
                        LOGGER.log(Level.WARNING, "Could not read custom audio " + sound.file.getName(), e);
                        finish(sound, false);
                        continue;
                    }
                    if (bytesRead < 0) {
                        finish(sound, true);
                        continue;
                    }
                    spatialMix(sound, mono, bytesRead, mix);
                }
                for (int i = 0; i < mix.length; i++) {
                    int sample = Math.max(Short.MIN_VALUE, Math.min(Short.MAX_VALUE, mix[i]));
                    output[i * 2] = (byte) sample;
                    output[i * 2 + 1] = (byte) (sample >> 8);
                }
                line.write(output, 0, output.length);
            }
        } catch (LineUnavailableException e) {
            LOGGER.log(Level.WARNING, "CustomDiscs cannot open the client audio output.", e);
            for (Sound sound : sounds.values()) {
                finish(sound, false);
            }
        } finally {
            closeLine();
            running = false;
        }
    }

    private void spatialMix(Sound sound, byte[] mono, int bytesRead, int[] output) {
        double dx = sound.x - listenerX;
        double dy = sound.y - listenerY;
        double dz = sound.z - listenerZ;
        double distance = Math.sqrt(dx * dx + dy * dy + dz * dz);
        double radians = Math.toRadians(listenerYaw);
        double pan = distance < 0.001D ? 0.0D
                : (-dx * Math.cos(radians) - dz * Math.sin(radians)) / distance;
        pan = Math.max(-1.0D, Math.min(1.0D, pan));
        double left = Math.sqrt((1.0D - pan) * 0.5D);
        double right = Math.sqrt((1.0D + pan) * 0.5D);
        double gain = gainAtDistance(distance, sound.range, sound.gain);
        int sampleCount = Math.min(SAMPLES_PER_FRAME, bytesRead / 2);
        for (int i = 0; i < sampleCount; i++) {
            int sample = (short) ((mono[i * 2] & 0xff) | (mono[i * 2 + 1] << 8));
            output[i * 2] += (int) (sample * gain * left);
            output[i * 2 + 1] += (int) (sample * gain * right);
        }
    }

    static double attenuation(double distance, float serverRange) {
        if (serverRange <= 0.0F || Float.isNaN(serverRange)
                || Float.isInfinite(serverRange)) {
            return 0.0D;
        }
        return Math.max(0.0D, 1.0D - Math.max(0.0D, distance) / serverRange);
    }

    static double gainAtDistance(double distance, float serverRange, float localVolume) {
        float volume = Math.max(0.0F, Math.min(1.0F, localVolume));
        return attenuation(distance, serverRange) * volume;
    }

    private void finish(Sound sound, boolean natural) {
        sounds.remove(sound.id);
        dispose(sound);
        ClientPlaybackManager.INSTANCE.audioFinishedFromMixer(sound.id);
    }

    private int readFrame(AudioInputStream input, byte[] buffer) throws IOException {
        int offset = 0;
        while (offset < buffer.length) {
            int read = input.read(buffer, offset, buffer.length - offset);
            if (read < 0) {
                return offset == 0 ? -1 : offset;
            }
            offset += read;
        }
        return offset;
    }

    private void ensureLine() throws LineUnavailableException {
        DataLine.Info info = new DataLine.Info(SourceDataLine.class, OUTPUT_FORMAT);
        line = (SourceDataLine) AudioSystem.getLine(info);
        line.open(OUTPUT_FORMAT, MONO_BYTES * 4);
        line.start();
    }

    private void dispose(Sound sound) {
        if (sound.input != null) {
            try {
                sound.input.close();
            } catch (IOException e) {
                LOGGER.log(Level.FINE, "Could not close custom audio stream.", e);
            }
            sound.input = null;
        }
    }

    private void closeLine() {
        if (line != null) {
            line.stop();
            line.close();
            line = null;
        }
    }

    private void update(Sound sound, double x, double y, double z, float range, float gain) {
        sound.x = x;
        sound.y = y;
        sound.z = z;
        sound.range = range;
        sound.gain = Math.max(0.0F, Math.min(1.0F, gain));
    }

    private static final class Sound {
        private final UUID id;
        private final File file;
        private volatile double x;
        private volatile double y;
        private volatile double z;
        private volatile float range;
        private volatile float gain;
        private volatile boolean stopped;
        private AudioInputStream input;

        private Sound(UUID id, File file) {
            this.id = id;
            this.file = file;
        }
    }
}
