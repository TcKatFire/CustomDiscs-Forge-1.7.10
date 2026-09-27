package me.navoei.customdiscs.client;

import cpw.mods.fml.common.event.FMLPreInitializationEvent;
import cpw.mods.fml.common.event.FMLInitializationEvent;
import cpw.mods.fml.common.FMLCommonHandler;
import cpw.mods.fml.client.registry.ClientRegistry;
import net.minecraftforge.client.ClientCommandHandler;
import net.minecraftforge.common.MinecraftForge;
import cpw.mods.fml.client.FMLClientHandler;
import cpw.mods.fml.common.gameevent.InputEvent;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import net.minecraft.client.settings.KeyBinding;
import net.minecraft.client.gui.GuiIngameMenu;
import net.minecraft.command.ICommand;
import me.navoei.customdiscs.CommonProxy;
import me.navoei.customdiscs.CustomItems;
import me.navoei.customdiscs.ModelRegistry;
import net.minecraft.init.Items;
import org.lwjgl.input.Keyboard;
import net.minecraft.client.Minecraft;

import java.util.logging.Logger;

@SideOnly(Side.CLIENT)
public final class ClientProxy extends CommonProxy {
    private static final Logger LOGGER = Logger.getLogger("CustomDiscs");
    private KeyBinding volumeKey;

    @Override
    public void preInitialize(FMLPreInitializationEvent event) {
        super.preInitialize(event);
        ClientNetworkBootstrap.registerMessages();
        ClientAudioSettings.load(event.getModConfigurationDirectory());
        registerClientCommand();
        volumeKey = new KeyBinding("key.customdiscs.volume", Keyboard.KEY_O,
                "key.categories.customdiscs");
        ClientRegistry.registerKeyBinding(volumeKey);
        registerModelRenderers();
    }

    @Override
    public void initialize(FMLInitializationEvent event) {
        super.initialize(event);
        FMLCommonHandler.instance().bus().register(ClientPlaybackManager.INSTANCE);
        FMLCommonHandler.instance().bus().register(this);
    }

    private void registerClientCommand() {
        Object existing = ClientCommandHandler.instance.getCommands().get("customdiscs");
        if (existing != null) {
            LOGGER.warning("Cannot register client-local /customdiscs volume command: already owned by "
                    + existing.getClass().getName() + ".");
            return;
        }
        ClientDiscsCommand command = new ClientDiscsCommand();
        ClientCommandHandler.instance.registerCommand(command);
        MinecraftForge.EVENT_BUS.register(command);
        LOGGER.info("Registered client-local /customdiscs volume command; other subcommands forward to server.");
    }

    @SubscribeEvent
    public void onKeyInput(InputEvent.KeyInputEvent event) {
        Minecraft minecraft = Minecraft.getMinecraft();
        if (volumeKey.isPressed() && (minecraft.currentScreen == null
                || minecraft.currentScreen instanceof GuiIngameMenu)) {
            minecraft.displayGuiScreen(new VolumeSettingsScreen(minecraft.currentScreen));
        }
    }

    private void registerModelRenderers() {
        CustomModelItemRenderer renderer = new CustomModelItemRenderer();
        net.minecraftforge.client.MinecraftForgeClient.registerItemRenderer(CustomItems.horn, renderer);
        net.minecraftforge.client.MinecraftForgeClient.registerItemRenderer(Items.skull, renderer);
        net.minecraftforge.client.MinecraftForgeClient.registerItemRenderer(Items.record_13, renderer);
        net.minecraftforge.client.MinecraftForgeClient.registerItemRenderer(Items.record_cat, renderer);
        net.minecraftforge.client.MinecraftForgeClient.registerItemRenderer(Items.record_blocks, renderer);
        net.minecraftforge.client.MinecraftForgeClient.registerItemRenderer(Items.record_chirp, renderer);
        net.minecraftforge.client.MinecraftForgeClient.registerItemRenderer(Items.record_far, renderer);
        net.minecraftforge.client.MinecraftForgeClient.registerItemRenderer(Items.record_mall, renderer);
        net.minecraftforge.client.MinecraftForgeClient.registerItemRenderer(Items.record_mellohi, renderer);
        net.minecraftforge.client.MinecraftForgeClient.registerItemRenderer(Items.record_stal, renderer);
        net.minecraftforge.client.MinecraftForgeClient.registerItemRenderer(Items.record_strad, renderer);
        net.minecraftforge.client.MinecraftForgeClient.registerItemRenderer(Items.record_ward, renderer);
        net.minecraftforge.client.MinecraftForgeClient.registerItemRenderer(Items.record_11, renderer);
        net.minecraftforge.client.MinecraftForgeClient.registerItemRenderer(Items.record_wait, renderer);
    }
}
