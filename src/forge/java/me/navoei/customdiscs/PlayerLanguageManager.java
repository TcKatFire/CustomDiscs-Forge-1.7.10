package me.navoei.customdiscs;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class PlayerLanguageManager {
    private static final Map<UUID, String> LANGUAGES = new ConcurrentHashMap<UUID, String>();

    private PlayerLanguageManager() {
    }

    public static void set(UUID player, String language) {
        if (language == null) {
            return;
        }
        String code = language.toLowerCase(java.util.Locale.ROOT);
        if (code.length() > 2) {
            code = code.substring(0, 2);
        }
        if (code.matches("[a-z]{2}")) {
            LANGUAGES.put(player, code);
        }
    }

    public static String get(UUID player) {
        return LANGUAGES.get(player);
    }

    public static void remove(UUID player) {
        LANGUAGES.remove(player);
    }
}
