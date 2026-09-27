package me.navoei.customdiscs;

import net.minecraft.init.Items;
import net.minecraft.item.ItemStack;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class ModelRegistry {
    private static final Pattern GROUP = Pattern.compile("^(disc|horn|head):$");
    private static final Pattern MATERIAL = Pattern.compile("^([a-z0-9_]+):$");
    private static final Pattern VALUE = Pattern.compile("^-\\s*value:\\s*(\\d+)\\s*$");
    private static final Pattern NAME = Pattern.compile("^name:\\s*(.+?)\\s*$");
    private static Map<String, List<Entry>> models = Collections.emptyMap();

    private ModelRegistry() {
    }

    public static synchronized void reload() {
        File file = new File(MusicFiles.getDataDirectory(), "models.yml");
        if (!file.isFile()) {
            copyDefault(file);
        }
        Map<String, List<Entry>> loaded = new HashMap<String, List<Entry>>();
        String group = null;
        String material = null;
        int value = 0;
        String name = null;
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(
                new FileInputStream(file), "UTF-8"))) {
            String line;
            while ((line = reader.readLine()) != null) {
                String trimmed = line.trim();
                if (trimmed.isEmpty() || trimmed.startsWith("#")) {
                    continue;
                }
                Matcher groupMatch = GROUP.matcher(trimmed);
                if (groupMatch.matches()) {
                    add(loaded, group, material, value, name);
                    group = groupMatch.group(1);
                    material = null;
                    value = 0;
                    name = null;
                    continue;
                }
                Matcher materialMatch = MATERIAL.matcher(trimmed);
                if (materialMatch.matches() && !trimmed.startsWith("-")) {
                    add(loaded, group, material, value, name);
                    material = materialMatch.group(1);
                    value = 0;
                    name = null;
                    continue;
                }
                Matcher valueMatch = VALUE.matcher(trimmed);
                if (valueMatch.matches()) {
                    add(loaded, group, material, value, name);
                    value = Integer.parseInt(valueMatch.group(1));
                    name = null;
                    continue;
                }
                Matcher nameMatch = NAME.matcher(trimmed);
                if (nameMatch.matches() && value > 0) {
                    name = unquote(nameMatch.group(1));
                }
            }
            add(loaded, group, material, value, name);
        } catch (IOException e) {
            throw new IllegalStateException("Could not load models.yml.", e);
        }
        Map<String, List<Entry>> immutable = new HashMap<String, List<Entry>>();
        for (Map.Entry<String, List<Entry>> entry : loaded.entrySet()) {
            immutable.put(entry.getKey(), Collections.unmodifiableList(entry.getValue()));
        }
        models = Collections.unmodifiableMap(immutable);
    }

    public static List<Entry> getEntries(ItemStack stack) {
        String key = itemKey(stack);
        if (key == null) {
            return Collections.emptyList();
        }
        List<Entry> result = models.get(key);
        return result == null ? Collections.<Entry>emptyList() : result;
    }

    public static int findValue(ItemStack stack, String name) {
        for (Entry entry : getEntries(stack)) {
            if (entry.name.equalsIgnoreCase(name)) {
                return entry.value;
            }
        }
        return -1;
    }

    public static boolean contains(ItemStack stack, int value) {
        for (Entry entry : getEntries(stack)) {
            if (entry.value == value) {
                return true;
            }
        }
        return false;
    }

    public static String texturePath(ItemStack stack, int value) {
        String key = itemKey(stack);
        if (key == null) {
            return null;
        }
        return "textures/items/models/" + key + "/" + value + ".png";
    }

    private static String itemKey(ItemStack stack) {
        AudioCategory category = ItemData.category(stack);
        if (category == null) {
            return null;
        }
        if (category == AudioCategory.HORN) {
            return "horn/goat_horn";
        }
        if (category == AudioCategory.PLAYER_HEAD) {
            String[] heads = {"player_head", "wither_skeleton_skull", "skeleton_skull",
                    "zombie_head", "creeper_head", "dragon_head"};
            int damage = stack.getMetadata();
            if (damage < 0 || damage >= heads.length) {
                return null;
            }
            return "head/" + heads[damage];
        }
        String disc = discKey(stack);
        return disc == null ? null : "disc/" + disc;
    }

    private static String discKey(ItemStack stack) {
        if (stack.getItem() == Items.record_13) return "music_disc_13";
        if (stack.getItem() == Items.record_cat) return "music_disc_cat";
        if (stack.getItem() == Items.record_blocks) return "music_disc_blocks";
        if (stack.getItem() == Items.record_chirp) return "music_disc_chirp";
        if (stack.getItem() == Items.record_far) return "music_disc_far";
        if (stack.getItem() == Items.record_mall) return "music_disc_mall";
        if (stack.getItem() == Items.record_mellohi) return "music_disc_mellohi";
        if (stack.getItem() == Items.record_stal) return "music_disc_stal";
        if (stack.getItem() == Items.record_strad) return "music_disc_strad";
        if (stack.getItem() == Items.record_ward) return "music_disc_ward";
        if (stack.getItem() == Items.record_11) return "music_disc_11";
        if (stack.getItem() == Items.record_wait) return "music_disc_wait";
        return null;
    }

    private static void add(Map<String, List<Entry>> target, String group, String material,
                            int value, String name) {
        if (group == null || material == null || value < 1 || name == null || name.isEmpty()) {
            return;
        }
        String key = group + "/" + material;
        List<Entry> entries = target.get(key);
        if (entries == null) {
            entries = new ArrayList<Entry>();
            target.put(key, entries);
        }
        entries.add(new Entry(value, name));
    }

    private static String unquote(String value) {
        String result = value.trim();
        if (result.length() >= 2 && ((result.charAt(0) == '"' && result.charAt(result.length() - 1) == '"')
                || (result.charAt(0) == '\'' && result.charAt(result.length() - 1) == '\''))) {
            result = result.substring(1, result.length() - 1);
        }
        return result.replace("\\\"", "\"").replace("\\'", "'");
    }

    private static void copyDefault(File target) {
        File parent = target.getParentFile();
        if (!parent.isDirectory() && !parent.mkdirs()) {
            throw new IllegalStateException("Could not create model configuration directory.");
        }
        try (InputStream input = ModelRegistry.class.getResourceAsStream("/models.yml")) {
            if (input == null) {
                throw new IOException("Bundled models.yml was not found.");
            }
            try (FileOutputStream output = new FileOutputStream(target)) {
                byte[] buffer = new byte[4096];
                int count;
                while ((count = input.read(buffer)) != -1) {
                    output.write(buffer, 0, count);
                }
            }
        } catch (IOException e) {
            throw new IllegalStateException("Could not create models.yml.", e);
        }
    }

    public static final class Entry {
        public final int value;
        public final String name;

        public Entry(int value, String name) {
            this.value = value;
            this.name = name;
        }
    }
}
