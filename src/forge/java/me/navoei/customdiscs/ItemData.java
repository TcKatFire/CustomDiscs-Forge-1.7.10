package me.navoei.customdiscs;

import net.minecraft.item.ItemRecord;
import net.minecraft.item.ItemSkull;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.nbt.NBTTagString;

public final class ItemData {
    public static final String FILE = "audio";
    public static final String TITLE = "title";
    public static final String RANGE = "range";
    public static final String COOLDOWN = "cooldown";
    public static final String MODEL = "model";
    private static final String ROOT = "customdiscs";

    private ItemData() {
    }

    public static boolean isDisc(ItemStack stack) {
        return stack != null && stack.getItem() instanceof ItemRecord;
    }

    public static boolean isHead(ItemStack stack) {
        return stack != null && stack.getItem() instanceof ItemSkull;
    }

    public static boolean isHorn(ItemStack stack) {
        return stack != null && stack.getItem() == CustomItems.horn;
    }

    public static AudioCategory category(ItemStack stack) {
        if (isDisc(stack)) {
            return AudioCategory.MUSIC_DISC;
        }
        if (isHorn(stack)) {
            return AudioCategory.HORN;
        }
        if (isHead(stack)) {
            return AudioCategory.PLAYER_HEAD;
        }
        return null;
    }

    public static NBTTagCompound get(ItemStack stack) {
        if (stack == null) {
            return null;
        }
        NBTTagCompound root = stack.getTagCompound();
        return root == null || !root.hasKey(ROOT, 10) ? null : root.getCompoundTag(ROOT);
    }

    public static NBTTagCompound getOrCreate(ItemStack stack) {
        NBTTagCompound root = stack.getTagCompound();
        if (root == null) {
            root = new NBTTagCompound();
            stack.setTagCompound(root);
        }
        if (!root.hasKey(ROOT, 10)) {
            root.setTag(ROOT, new NBTTagCompound());
        }
        return root.getCompoundTag(ROOT);
    }

    public static String getAudio(ItemStack stack) {
        NBTTagCompound data = get(stack);
        return data != null && data.hasKey(FILE, 8) ? data.getString(FILE) : null;
    }

    public static String getTitle(ItemStack stack) {
        NBTTagCompound data = get(stack);
        return data != null && data.hasKey(TITLE, 8) ? data.getString(TITLE) : "Unknown";
    }

    public static float getRange(ItemStack stack, float defaultRange, float maximumRange) {
        NBTTagCompound data = get(stack);
        float range = data != null && data.hasKey(RANGE, 99)
                ? data.getFloat(RANGE) : defaultRange;
        if (Float.isNaN(range) || Float.isInfinite(range)) {
            range = defaultRange;
        }
        return Math.max(1.0F, Math.min(range, maximumRange));
    }

    public static int getCooldown(ItemStack stack) {
        NBTTagCompound data = get(stack);
        int cooldown = data != null && data.hasKey(COOLDOWN, 3)
                ? data.getInteger(COOLDOWN) : ModConfig.hornCooldownTicks;
        return Math.max(1, Math.min(cooldown, ModConfig.hornMaximumCooldownTicks));
    }

    public static boolean isCustom(ItemStack stack) {
        return category(stack) != null && getAudio(stack) != null;
    }

    public static void setCustom(ItemStack stack, String filename, String title) {
        NBTTagCompound data = getOrCreate(stack);
        NBTTagCompound display = stack.getTagCompound().getCompoundTag("display");
        if (!data.hasKey("originalDisplay", 10)) {
            data.setTag("originalDisplay", (NBTTagCompound) display.copy());
        }
        data.setString(FILE, filename);
        data.setString(TITLE, title);

        NBTTagList lore = new NBTTagList();
        lore.appendTag(new NBTTagString("\u00a77" + title));
        display.setTag("Lore", lore);
        stack.getTagCompound().setTag("display", display);
    }

    public static void clearCustom(ItemStack stack) {
        if (stack == null || !stack.hasTagCompound()) {
            return;
        }
        NBTTagCompound root = stack.getTagCompound();
        NBTTagCompound data = get(stack);
        NBTTagCompound originalDisplay = data != null && data.hasKey("originalDisplay", 10)
                ? (NBTTagCompound) data.getCompoundTag("originalDisplay").copy() : null;
        root.removeTag(ROOT);
        if (originalDisplay != null && !originalDisplay.hasNoTags()) {
            root.setTag("display", originalDisplay);
        } else if (root.hasKey("display", 10)) {
            NBTTagCompound display = root.getCompoundTag("display");
            display.removeTag("Lore");
            if (display.hasNoTags()) {
                root.removeTag("display");
            }
        }
        if (root.hasNoTags()) {
            stack.setTagCompound(null);
        }
    }

    public static void clearModel(ItemStack stack) {
        NBTTagCompound data = get(stack);
        if (data == null) {
            return;
        }
        data.removeTag(MODEL);
        if (data.hasNoTags()) {
            stack.getTagCompound().removeTag(ROOT);
        }
        if (stack.getTagCompound().hasNoTags()) {
            stack.setTagCompound(null);
        }
    }

    public static int getModel(ItemStack stack) {
        NBTTagCompound data = get(stack);
        return data != null && data.hasKey(MODEL, 3) ? data.getInteger(MODEL) : 0;
    }
}
