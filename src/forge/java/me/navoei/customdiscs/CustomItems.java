package me.navoei.customdiscs;

import cpw.mods.fml.common.registry.GameRegistry;

public final class CustomItems {
    public static CustomHornItem horn;

    private CustomItems() {
    }

    public static void register() {
        horn = new CustomHornItem();
        GameRegistry.registerItem(horn, "custom_horn");
    }
}
