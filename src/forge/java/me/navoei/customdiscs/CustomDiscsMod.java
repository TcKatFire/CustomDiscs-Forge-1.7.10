package me.navoei.customdiscs;

import cpw.mods.fml.common.Mod;
import cpw.mods.fml.common.SidedProxy;
import cpw.mods.fml.common.network.NetworkCheckHandler;
import cpw.mods.fml.common.event.FMLInitializationEvent;
import cpw.mods.fml.common.event.FMLPreInitializationEvent;
import cpw.mods.fml.common.event.FMLServerStartingEvent;
import cpw.mods.fml.common.event.FMLServerStartedEvent;
import cpw.mods.fml.common.network.NetworkRegistry;
import cpw.mods.fml.common.network.simpleimpl.SimpleNetworkWrapper;
import cpw.mods.fml.relauncher.Side;
import net.minecraft.command.CommandHandler;
import net.minecraft.server.MinecraftServer;

import java.util.Map;
import java.util.logging.Logger;

@Mod(
        modid = CustomDiscsMod.MOD_ID,
        name = "CustomDiscs",
        version = CustomDiscsMod.VERSION,
        acceptedMinecraftVersions = "[1.7.10]"
)
public final class CustomDiscsMod {
    public static final String MOD_ID = "customdiscs";
    public static final String VERSION = NetworkProtocol.MOD_VERSION;
    private static final Logger LOGGER = Logger.getLogger("CustomDiscs");
    public static final SimpleNetworkWrapper NETWORK =
            NetworkRegistry.INSTANCE.newSimpleChannel(MOD_ID);

    @SidedProxy(
            clientSide = "me.navoei.customdiscs.client.ClientProxy",
            serverSide = "me.navoei.customdiscs.CommonProxy"
    )
    public static CommonProxy proxy;

    @NetworkCheckHandler
    public boolean checkNetworkCompatibility(Map<String, String> remoteVersions, Side side) {
        String remoteVersion = remoteVersions.get(MOD_ID);
        boolean compatible = NetworkProtocol.isCompatible(remoteVersions);
        LOGGER.info(NetworkProtocol.handshakeDiagnostic(side.toString(), remoteVersion));
        return compatible;
    }

    @Mod.EventHandler
    public void preInitialize(FMLPreInitializationEvent event) {
        ModConfig.load(event);
        MusicFiles.initialize(event.getModConfigurationDirectory());
        ModelRegistry.reload();
        Localization.reload();
        proxy.preInitialize(event);
    }

    @Mod.EventHandler
    public void initialize(FMLInitializationEvent event) {
        proxy.initialize(event);
    }

    @Mod.EventHandler
    public void serverStarting(FMLServerStartingEvent event) {
        CommandRegistration.register((CommandHandler) event.getServer().getCommandManager());
    }

    @Mod.EventHandler
    public void serverStarted(FMLServerStartedEvent event) {
        MinecraftServer server = MinecraftServer.getServer();
        if (server == null || !(server.getCommandManager() instanceof CommandHandler)) {
            throw new IllegalStateException("CustomDiscs could not access the server command handler.");
        }
        // Recheck after all FMLServerStartingEvent handlers to report late command collisions.
        CommandRegistration.register((CommandHandler) server.getCommandManager());
    }
}
