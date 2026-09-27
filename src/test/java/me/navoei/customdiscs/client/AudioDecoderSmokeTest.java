package me.navoei.customdiscs.client;

import me.navoei.customdiscs.AudioCategory;
import me.navoei.customdiscs.ModConfig;
import me.navoei.customdiscs.AudioFileFormat;

import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioInputStream;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.lang.reflect.Modifier;
import java.util.Arrays;

public final class AudioDecoderSmokeTest {
    public static void main(String[] args) throws Exception {
        checkHashNamedCacheExtension();
        checkServerRangeAttenuation();
        checkLocalVolumeCategories();
        checkClientVolumeCommand();
        checkVolumeControls();
        check(22050, 2);
        check(44100, 2);
        check(48000, 1);
    }

    private static void checkServerRangeAttenuation() {
        if (ModConfig.musicDiscRange != 63.0F || ModConfig.hornRange != 63.0F
                || ModConfig.headRange != 63.0F
                || ClientAudioMixer.attenuation(63.0D, 63.0F) != 0.0D
                || ClientAudioMixer.attenuation(0.0D, 63.0F) != 1.0D
                || Math.abs(ClientAudioMixer.gainAtDistance(31.5D, 63.0F, 0.5F) - 0.25D) > 0.0001D) {
            throw new AssertionError("Server range must control attenuation independently of local volume.");
        }
    }

    private static void checkLocalVolumeCategories() {
        if (!"volume".equals(ClientAudioSettings.configCategory())) {
            throw new AssertionError("Client volume values must persist under the local volume category.");
        }
        String[] keys = new String[AudioCategory.values().length];
        for (AudioCategory category : AudioCategory.values()) {
            if (ClientAudioSettings.get(category) != 1.0F) {
                throw new AssertionError("Local category volume must default to 100%.");
            }
            String key = ClientAudioSettings.key(category);
            for (String previous : keys) {
                if (key.equals(previous)) {
                    throw new AssertionError("Each audio category needs its own persisted local volume.");
                }
            }
            keys[category.getId()] = key;
        }
    }

    private static void checkVolumeControls() {
        if (ClientAudioSettings.clamp(-0.1F) != 0.0F
                || ClientAudioSettings.clamp(1.1F) != 1.0F
                || ClientAudioSettings.clamp(0.65F) != 0.65F) {
            throw new AssertionError("Client category volumes must remain within 0-100%.");
        }
        if (ClientAudioMixer.gainAtDistance(31.5D, 63.0F, 0.25F)
                != ClientAudioMixer.attenuation(31.5D, 63.0F) * 0.25D
                || ClientAudioMixer.gainAtDistance(31.5D, 63.0F, 1.0F)
                != ClientAudioMixer.attenuation(31.5D, 63.0F)) {
            throw new AssertionError("Local volume must scale gain without changing server attenuation.");
        }
    }

    private static void checkClientVolumeCommand() {
        ClientDiscsCommand command = new ClientDiscsCommand();
        if (!Modifier.isPublic(ClientDiscsCommand.class.getModifiers())
                || !Modifier.isPublic(methodModifiers("routeCommand"))) {
            throw new AssertionError("The ASM-dispatched command event handler must be publicly accessible.");
        }
        if (ClientDiscsCommand.parseCategory("disc") != AudioCategory.MUSIC_DISC
                || ClientDiscsCommand.parseCategory("head") != AudioCategory.PLAYER_HEAD
                || ClientDiscsCommand.parseCategory("horn") != AudioCategory.HORN
                || ClientDiscsCommand.parseCategory("HoRn") != AudioCategory.HORN
                || ClientDiscsCommand.parsePercent("0") != 0
                || ClientDiscsCommand.parsePercent("100") != 100
                || ClientDiscsCommand.parsePercent("45") != 45) {
            throw new AssertionError("Client volume command must parse supported category/percent values.");
        }
        if (ClientDiscsCommand.shouldForwardToServer(new String[]{"volume", "disc", "35"})
                || ClientDiscsCommand.shouldForwardToServer(new String[]{"VOLUME", "horn", "0"})
                || !ClientDiscsCommand.shouldForwardToServer(new String[]{"create", "track.mp3"})
                || !ClientDiscsCommand.shouldForwardToServer(new String[0])) {
            throw new AssertionError("Only volume is local; all other root commands must reach server.");
        }
        if (!command.addTabCompletionOptions(null, new String[]{"v"}).contains("volume")
                || !command.addTabCompletionOptions(null, new String[]{"volume", "h"})
                .containsAll(Arrays.asList("head", "horn"))
                || !command.addTabCompletionOptions(null, new String[]{"volume", "disc", "1"})
                .contains("100")
                || !command.addTabCompletionOptions(null, new String[]{"",}).contains("create")) {
            throw new AssertionError("Client completion must expose volume without hiding server subcommands.");
        }
        expectInvalidVolumeInput(new Runnable() {
            @Override
            public void run() {
                ClientDiscsCommand.parseCategory("jukebox");
            }
        });
        expectInvalidVolumeInput(new Runnable() {
            @Override
            public void run() {
                ClientDiscsCommand.parsePercent("-1");
            }
        });
        expectInvalidVolumeInput(new Runnable() {
            @Override
            public void run() {
                ClientDiscsCommand.parsePercent("101");
            }
        });
        expectInvalidVolumeInput(new Runnable() {
            @Override
            public void run() {
                ClientDiscsCommand.parsePercent("50.5");
            }
        });
    }

    private static int methodModifiers(String name) {
        try {
            return ClientDiscsCommand.class.getMethod(name, net.minecraftforge.event.CommandEvent.class)
                    .getModifiers();
        } catch (NoSuchMethodException e) {
            throw new AssertionError("Missing public command event handler.", e);
        }
    }

    private static void expectInvalidVolumeInput(Runnable parse) {
        try {
            parse.run();
            throw new AssertionError("Invalid client volume input must be rejected.");
        } catch (net.minecraft.command.WrongUsageException expected) {
        }
    }

    private static void checkHashNamedCacheExtension() throws Exception {
        String hash = "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef";
        File cachedMp3 = new File(AudioFileFormat.cacheName(hash, "mp3"));
        if (!cachedMp3.getName().equals(hash + ".mp3")
                || !"mp3".equals(AudioDecoder.decoderExtension(cachedMp3))) {
            throw new AssertionError("Hash-named MP3 cache files must retain their decoder extension.");
        }
        if (AudioFileFormat.isSupported("exe")) {
            throw new AssertionError("Only supported audio extensions may enter the cache.");
        }
    }

    private static void check(int sampleRate, int channels) throws Exception {
        int frames = sampleRate / 5;
        byte[] data = new byte[frames * channels * 2];
        for (int frame = 0; frame < frames; frame++) {
            short sample = (short) (Math.sin(frame * 0.1D) * 12000);
            for (int channel = 0; channel < channels; channel++) {
                int offset = (frame * channels + channel) * 2;
                data[offset] = (byte) sample;
                data[offset + 1] = (byte) (sample >> 8);
            }
        }
        AudioFormat format = new AudioFormat(AudioFormat.Encoding.PCM_SIGNED,
                sampleRate, 16, channels, channels * 2, sampleRate, false);
        int bytes = 0;
        int peak = 0;
        try (AudioInputStream input = new AudioInputStream(
                new ByteArrayInputStream(data), format, frames);
             AudioInputStream decoded = AudioDecoder.convert(input)) {
            if (!AudioDecoder.FORMAT.matches(decoded.getFormat())) {
                throw new AssertionError("Unexpected output format: " + decoded.getFormat());
            }
            byte[] output = new byte[1024];
            int count;
            while ((count = decoded.read(output)) != -1) {
                bytes += count;
                for (int offset = 0; offset + 1 < count; offset += 2) {
                    int sample = (short) ((output[offset] & 0xff)
                            | (output[offset + 1] << 8));
                    peak = Math.max(peak, Math.abs(sample));
                }
            }
        }
        int expected = 48000 / 5 * 2;
        if (Math.abs(bytes - expected) > 4) {
            throw new AssertionError("Expected " + expected + " bytes, got " + bytes);
        }
        if (peak < 1000) {
            throw new AssertionError("Decoded audio is unexpectedly silent.");
        }
    }
}
