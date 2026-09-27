package me.navoei.customdiscs.client;

import cpw.mods.fml.common.network.simpleimpl.IMessage;
import cpw.mods.fml.common.network.simpleimpl.IMessageHandler;
import cpw.mods.fml.common.network.simpleimpl.MessageContext;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import me.navoei.customdiscs.AudioChunkMessage;
import me.navoei.customdiscs.ModelSelector;
import me.navoei.customdiscs.PlaybackMessage;
import net.minecraft.util.ChatComponentText;

import java.util.concurrent.atomic.AtomicLong;
import java.util.logging.Level;
import java.util.logging.Logger;

@SideOnly(Side.CLIENT)
public final class ClientPacketHandler {
    private static final Logger LOGGER = Logger.getLogger("CustomDiscs");
    private static final long MALFORMED_LOG_INTERVAL_MS = 10000L;
    private static final AtomicLong LAST_MALFORMED_LOG = new AtomicLong();

    private ClientPacketHandler() {
    }

    public static final class PlaybackHandler implements IMessageHandler<PlaybackMessage, IMessage> {
        @Override
        public IMessage onMessage(final PlaybackMessage message, MessageContext context) {
            if (message.decodeError != null) {
                logMalformedPlayback(message);
                context.getClientHandler().getNetworkManager().closeChannel(
                        new ChatComponentText("Invalid CustomDiscs playback packet. "
                                + "Reconnect with matching CustomDiscs versions."));
                return null;
            }
            ClientPlaybackManager.INSTANCE.enqueue(new Runnable() {
                @Override
                public void run() {
                    ClientPlaybackManager.INSTANCE.receive(message);
                }
            });
            return null;
        }
    }

    private static void logMalformedPlayback(PlaybackMessage message) {
        long now = System.currentTimeMillis();
        long previous = LAST_MALFORMED_LOG.get();
        if (now - previous >= MALFORMED_LOG_INTERVAL_MS
                && LAST_MALFORMED_LOG.compareAndSet(previous, now)) {
            LOGGER.log(Level.WARNING, "Rejected malformed CustomDiscs playback packet: "
                    + message.decodeError + "; " + message.decodeDiagnostic);
        }
    }

    public static final class ChunkHandler implements IMessageHandler<AudioChunkMessage, IMessage> {
        @Override
        public IMessage onMessage(AudioChunkMessage message, MessageContext context) {
            ClientAudioCache.accept(message);
            return null;
        }
    }

    public static final class ModelListHandler implements IMessageHandler<ModelSelector.ModelListMessage, IMessage> {
        @Override
        public IMessage onMessage(final ModelSelector.ModelListMessage message, MessageContext context) {
            ClientPlaybackManager.INSTANCE.enqueue(new Runnable() {
                @Override
                public void run() {
                    net.minecraft.client.Minecraft.getMinecraft().displayGuiScreen(
                            new ModelSelectorScreen(message.values, message.names));
                }
            });
            return null;
        }
    }
}
