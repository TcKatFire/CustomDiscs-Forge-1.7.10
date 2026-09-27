package me.navoei.customdiscs;

import net.minecraft.creativetab.CreativeTabs;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.world.World;
import net.minecraft.entity.player.EntityPlayer;

public final class CustomHornItem extends Item {
    public CustomHornItem() {
        setUnlocalizedName("customdiscs.horn");
        setTextureName(CustomDiscsMod.MOD_ID + ":horn");
        setCreativeTab(CreativeTabs.tabMisc);
        setMaxStackSize(1);
    }

    @Override
    public ItemStack onItemRightClick(ItemStack stack, World world, EntityPlayer player) {
        if (!world.isRemote) {
            ServerEventHandler.playHorn(player, stack);
        }
        return stack;
    }
}
