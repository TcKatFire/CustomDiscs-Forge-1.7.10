package me.navoei.customdiscs;

import cpw.mods.fml.common.event.FMLPreInitializationEvent;
import cpw.mods.fml.common.event.FMLInitializationEvent;
import cpw.mods.fml.common.FMLCommonHandler;
import cpw.mods.fml.common.registry.GameRegistry;
import cpw.mods.fml.relauncher.Side;
import net.minecraftforge.common.MinecraftForge;

public class CommonProxy {
    public void preInitialize(FMLPreInitializationEvent event) {
        NetworkBootstrap.registerServerMessages();
        if (FMLCommonHandler.instance().getSide() == Side.SERVER) {
            NetworkBootstrap.registerClientboundDiscriminators();
        }
        CustomItems.register();
    }

    public void initialize(FMLInitializationEvent event) {
        ServerEventHandler handler = new ServerEventHandler();
        MinecraftForge.EVENT_BUS.register(handler);
        FMLCommonHandler.instance().bus().register(handler);
    }
}
