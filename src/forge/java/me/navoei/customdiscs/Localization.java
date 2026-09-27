package me.navoei.customdiscs;

import net.minecraft.entity.player.EntityPlayer;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

public final class Localization {
    private static final String[] LANGUAGES = {"en", "fr", "de", "ru", "es", "pt", "zh", "pl",
            "nl", "it", "tr", "cs", "hu", "ko", "tt"};
    private static volatile Map<String, Map<String, String>> translations = Collections.emptyMap();

    private Localization() {
    }

    public static synchronized void reload() {
        File directory = new File(MusicFiles.getDataDirectory(), "langs");
        if (!directory.isDirectory() && !directory.mkdirs()) {
            throw new IllegalStateException("Could not create language directory.");
        }
        Map<String, Map<String, String>> loaded = new HashMap<String, Map<String, String>>();
        for (String code : LANGUAGES) {
            File file = new File(directory, code + ".yml");
            if (!file.isFile()) {
                copyBundled(code, file);
            }
            loaded.put(code, read(file));
        }
        File[] customFiles = directory.listFiles();
        if (customFiles == null) {
            throw new IllegalStateException("Could not list language directory.");
        }
        for (File file : customFiles) {
            String name = file.getName();
            if (file.isFile() && name.matches("[a-z]{2}\\.yml")) {
                String code = name.substring(0, 2);
                if (!loaded.containsKey(code)) {
                    loaded.put(code, read(file));
                }
            }
        }
        translations = Collections.unmodifiableMap(loaded);
    }

    public static String forPlayer(EntityPlayer player, String key) {
        String language = PlayerLanguageManager.get(player.getUniqueID());
        if (language == null || !translations.containsKey(language)) {
            language = ModConfig.defaultLanguage;
        }
        Map<String, String> values = translations.get(language);
        if (values == null) {
            values = translations.get("en");
        }
        String message = values == null ? null : values.get(key);
        if (message == null) {
            Map<String, String> english = translations.get("en");
            message = english == null ? key : english.get(key);
        }
        return message == null ? key : message;
    }

    public static String defaultMessage(String key) {
        Map<String, String> values = translations.get(ModConfig.defaultLanguage);
        if (values == null) {
            values = translations.get("en");
        }
        String message = values == null ? null : values.get(key);
        return message == null ? key : message;
    }

    private static Map<String, String> read(File file) {
        Map<String, String> values = new HashMap<String, String>();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(
                new FileInputStream(file), "UTF-8"))) {
            String line;
            while ((line = reader.readLine()) != null) {
                String trimmed = line.trim();
                if (trimmed.isEmpty() || trimmed.startsWith("#")) {
                    continue;
                }
                int separator = trimmed.indexOf(':');
                if (separator <= 0) {
                    continue;
                }
                String key = trimmed.substring(0, separator).trim();
                String value = unquote(trimmed.substring(separator + 1).trim());
                values.put(key, value);
            }
        } catch (IOException e) {
            throw new IllegalStateException("Could not read " + file.getName(), e);
        }
        return Collections.unmodifiableMap(values);
    }

    private static String unquote(String value) {
        if (value.length() >= 2 && value.charAt(0) == '\'' && value.charAt(value.length() - 1) == '\'') {
            return value.substring(1, value.length() - 1).replace("''", "'");
        }
        if (value.length() >= 2 && value.charAt(0) == '"' && value.charAt(value.length() - 1) == '"') {
            return value.substring(1, value.length() - 1).replace("\\\"", "\"").replace("\\n", "\n");
        }
        return value;
    }

    private static void copyBundled(String code, File destination) {
        try (InputStream input = Localization.class.getResourceAsStream("/langs/" + code + ".yml")) {
            if (input == null) {
                throw new IOException("Bundled language file is missing: " + code);
            }
            try (FileOutputStream output = new FileOutputStream(destination)) {
                byte[] buffer = new byte[4096];
                int count;
                while ((count = input.read(buffer)) != -1) {
                    output.write(buffer, 0, count);
                }
            }
        } catch (IOException e) {
            throw new IllegalStateException("Could not create language file " + code + ".", e);
        }
    }
}
