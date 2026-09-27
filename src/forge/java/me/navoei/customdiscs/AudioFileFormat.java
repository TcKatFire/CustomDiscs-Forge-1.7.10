package me.navoei.customdiscs;

import java.util.Locale;

public final class AudioFileFormat {
    private AudioFileFormat() {
    }

    public static String extension(String filename) {
        int dot = filename.lastIndexOf('.');
        return dot < 0 ? "" : filename.substring(dot + 1).toLowerCase(Locale.ROOT);
    }

    public static boolean isSupported(String extension) {
        return "wav".equals(extension) || "mp3".equals(extension) || "flac".equals(extension);
    }

    public static String cacheName(String hash, String extension) {
        if (!isHash(hash) || !isSupported(extension)) {
            throw new IllegalArgumentException("Invalid audio cache key.");
        }
        return hash + "." + extension;
    }

    public static boolean isHash(String hash) {
        if (hash == null || hash.length() != 64) {
            return false;
        }
        for (int i = 0; i < hash.length(); i++) {
            char c = hash.charAt(i);
            if (!((c >= '0' && c <= '9') || (c >= 'a' && c <= 'f'))) {
                return false;
            }
        }
        return true;
    }
}
