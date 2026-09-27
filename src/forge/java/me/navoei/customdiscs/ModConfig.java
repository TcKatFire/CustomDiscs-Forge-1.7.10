package me.navoei.customdiscs;

import cpw.mods.fml.common.event.FMLPreInitializationEvent;
import net.minecraftforge.common.config.Configuration;

import java.io.File;

public final class ModConfig {
    private static File configFile;
    public static boolean musicDiscEnabled = true;
    public static boolean customHornEnabled = true;
    public static boolean customHeadEnabled = true;
    public static boolean modelSelectionEnabled;
    public static boolean musicDiscPlayingEnabled = true;
    public static boolean customHornPlayingEnabled = true;
    public static boolean customHeadPlayingEnabled = true;
    public static int maximumDownloadSizeMb = 50;
    public static int maximumFilenameLength = 100;
    public static float musicDiscVolume = 1.0F;
    public static float musicDiscRange = 63.0F;
    public static float musicDiscMaximumRange = 256.0F;
    public static float hornRange = 63.0F;
    public static float hornMaximumRange = 256.0F;
    public static int hornCooldownTicks = 140;
    public static int hornMaximumCooldownTicks = 6000;
    public static float headRange = 63.0F;
    public static float headMaximumRange = 256.0F;
    public static String subdirectoryDepth = "none";
    public static String defaultLanguage = "en";

    private ModConfig() {
    }

    public static void load(FMLPreInitializationEvent event) {
        configFile = event.getSuggestedConfigurationFile();
        load(configFile);
    }

    public static void reload() {
        if (configFile != null) {
            load(configFile);
        }
    }

    static void load(File file) {
        Configuration configuration = new Configuration(file);
        configuration.load();

        String general = Configuration.CATEGORY_GENERAL;
        int rangeConfigVersion = configuration.getInt("range-config-version", general,
                0, 0, 1, "Internal migration marker for default playback range.");
        musicDiscEnabled = configuration.getBoolean("enabled", general, true, "Enable custom music discs.");
        customHornEnabled = configuration.getBoolean("enabled", "horn", true, "Enable the custom horn item.");
        customHeadEnabled = configuration.getBoolean("enabled", "head", true, "Enable custom player-head playback.");
        modelSelectionEnabled = configuration.getBoolean("enabled", "models", false, "Enable the model selector.");
        musicDiscPlayingEnabled = configuration.getBoolean("playing-message", "music-disc", true,
                "Show a message when custom discs start playing.");
        customHornPlayingEnabled = configuration.getBoolean("playing-message", "horn", true,
                "Show a message when custom horns start playing.");
        customHeadPlayingEnabled = configuration.getBoolean("playing-message", "head", true,
                "Show a message when custom heads start playing.");
        maximumDownloadSizeMb = configuration.getInt("max-download-size", general, 50, 1, 512,
                "Maximum downloaded audio size in megabytes.");
        maximumFilenameLength = configuration.getInt("filename-maximum-length", general, 100, 1, 240,
                "Maximum audio filename length.");
        musicDiscVolume = configuration.getFloat("music-disc-volume", general, 1.0F, 0.0F, 1.0F,
                "Server-side master gain for custom discs.");
        subdirectoryDepth = configuration.getString("subdirectory-depth", general, "none",
                "Allowed musicdata subdirectory depth: none, single, or unrestricted.");
        defaultLanguage = configuration.getString("default-lang", general, "en",
                "Fallback language code for server messages.");
        musicDiscRange = configuration.getFloat("distance", "music-disc", 63.0F, 1.0F, 256.0F,
                "Default custom-disc hearing range.");
        musicDiscMaximumRange = configuration.getFloat("max-distance", "music-disc", 256.0F, 1.0F, 256.0F,
                "Maximum custom-disc hearing range.");
        hornRange = configuration.getFloat("distance", "horn", 63.0F, 1.0F, 256.0F,
                "Default custom-horn hearing range.");
        hornMaximumRange = configuration.getFloat("max-distance", "horn", 256.0F, 1.0F, 256.0F,
                "Maximum custom-horn hearing range.");
        hornCooldownTicks = configuration.getInt("cooldown", "horn", 140, 1, 12000,
                "Default custom-horn cooldown in ticks.");
        hornMaximumCooldownTicks = configuration.getInt("max-cooldown", "horn", 6000, 1, 12000,
                "Maximum custom-horn cooldown in ticks.");
        headRange = configuration.getFloat("distance", "head", 63.0F, 1.0F, 256.0F,
                "Default player-head hearing range.");
        headMaximumRange = configuration.getFloat("max-distance", "head", 256.0F, 1.0F, 256.0F,
                "Maximum player-head hearing range.");

        if (rangeConfigVersion < 1) {
            musicDiscRange = migratedRange(musicDiscRange, rangeConfigVersion);
            if (musicDiscRange != 16.0F) {
                configuration.get("distance", "music-disc", 63.0F).set(musicDiscRange);
            }
            hornRange = migratedRange(hornRange, rangeConfigVersion);
            if (hornRange != 16.0F) {
                configuration.get("distance", "horn", 63.0F).set(hornRange);
            }
            headRange = migratedRange(headRange, rangeConfigVersion);
            if (headRange != 16.0F) {
                configuration.get("distance", "head", 63.0F).set(headRange);
            }
            configuration.get(general, "range-config-version", 0).set(1);
        }

        if (!"single".equals(subdirectoryDepth) && !"unrestricted".equals(subdirectoryDepth)) {
            subdirectoryDepth = "none";
        }
        if (configuration.hasChanged()) {
            configuration.save();
        }
    }

    static float migratedRange(float range, int configVersion) {
        return configVersion < 1 && range == 16.0F ? 63.0F : range;
    }
}
