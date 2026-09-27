package me.navoei.customdiscs.client;

import net.minecraftforge.common.config.Configuration;
import me.navoei.customdiscs.AudioCategory;

import java.io.File;
import java.util.Locale;

public final class ClientAudioSettings {
    private static final String CONFIG_CATEGORY = "volume";
    private static Configuration configuration;
    private static final float[] VOLUMES = {1.0F, 1.0F, 1.0F};

    private ClientAudioSettings() {
    }

    public static void load(File configDirectory) {
        configuration = new Configuration(new File(configDirectory, "customdiscs-client.cfg"));
        configuration.load();
        for (AudioCategory category : AudioCategory.values()) {
            VOLUMES[category.getId()] = configuration.getFloat(
                    CONFIG_CATEGORY, key(category), 1.0F, 0.0F, 1.0F,
                    "Local " + key(category) + " volume.");
        }
        if (configuration.hasChanged()) {
            configuration.save();
        }
    }

    public static synchronized float get(AudioCategory category) {
        return VOLUMES[category.getId()];
    }

    public static synchronized void adjust(AudioCategory category, float amount) {
        int index = category.getId();
        set(category, clamp(VOLUMES[index] + amount));
    }

    public static synchronized void setPercent(AudioCategory category, int percent) {
        if (percent < 0 || percent > 100) {
            throw new IllegalArgumentException("Volume percentage must be between 0 and 100.");
        }
        set(category, percent / 100.0F);
    }

    static AudioCategory parseCategory(String value) {
        String category = value.toLowerCase(Locale.ROOT);
        if ("disc".equals(category)) return AudioCategory.MUSIC_DISC;
        if ("head".equals(category)) return AudioCategory.PLAYER_HEAD;
        if ("horn".equals(category)) return AudioCategory.HORN;
        throw new IllegalArgumentException("Unknown volume category: " + value);
    }

    static int parsePercent(String value) {
        try {
            int percent = Integer.parseInt(value);
            if (percent >= 0 && percent <= 100) {
                return percent;
            }
        } catch (NumberFormatException ignored) {
        }
        throw new IllegalArgumentException("Volume percentage must be an integer from 0 to 100.");
    }

    private static void set(AudioCategory category, float volume) {
        VOLUMES[category.getId()] = volume;
        configuration.get(CONFIG_CATEGORY, key(category), 1.0F).set(volume);
        configuration.save();
    }

    static float clamp(float volume) {
        return Math.max(0.0F, Math.min(1.0F, volume));
    }

    static String key(AudioCategory category) {
        if (category == AudioCategory.MUSIC_DISC) return "music-disc";
        if (category == AudioCategory.HORN) return "horn";
        return "player-head";
    }

    static String configCategory() {
        return CONFIG_CATEGORY;
    }
}
