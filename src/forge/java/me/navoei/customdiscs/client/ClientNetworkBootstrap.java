package me.navoei.customdiscs.client;

import cpw.mods.fml.relauncher.Side;
import me.navoei.customdiscs.AudioChunkMessage;
import me.navoei.customdiscs.CustomDiscsMod;
import me.navoei.customdiscs.ModelSelector;
import me.navoei.customdiscs.NetworkProtocol;
import me.navoei.customdiscs.PlaybackMessage;

final class ClientNetworkBootstrap {
    private ClientNetworkBootstrap() {
    }

    static void registerMessages() {
        CustomDiscsMod.NETWORK.registerMessage(
                ClientPacketHandler.PlaybackHandler.class, PlaybackMessage.class,
                NetworkProtocol.PLAYBACK_MESSAGE_ID, Side.CLIENT);
        CustomDiscsMod.NETWORK.registerMessage(
                ClientPacketHandler.ChunkHandler.class, AudioChunkMessage.class,
                NetworkProtocol.AUDIO_CHUNK_MESSAGE_ID, Side.CLIENT);
        CustomDiscsMod.NETWORK.registerMessage(
                ClientPacketHandler.ModelListHandler.class, ModelSelector.ModelListMessage.class,
                NetworkProtocol.MODEL_LIST_MESSAGE_ID, Side.CLIENT);
    }
}
