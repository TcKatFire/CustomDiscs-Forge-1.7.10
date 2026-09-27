package me.navoei.customdiscs;

import cpw.mods.fml.common.network.simpleimpl.IMessage;
import cpw.mods.fml.common.network.simpleimpl.IMessageHandler;
import cpw.mods.fml.common.network.simpleimpl.MessageContext;
import cpw.mods.fml.relauncher.Side;
import io.netty.buffer.ByteBuf;
import net.minecraft.entity.player.EntityPlayerMP;

public final class NetworkBootstrap {
    private NetworkBootstrap() {
    }

    public static void registerServerMessages() {
        CustomDiscsMod.NETWORK.registerMessage(
                AudioRequestMessage.Handler.class, AudioRequestMessage.class,
                NetworkProtocol.AUDIO_REQUEST_MESSAGE_ID, Side.SERVER);
        CustomDiscsMod.NETWORK.registerMessage(
                AudioFinishedMessage.Handler.class, AudioFinishedMessage.class,
                NetworkProtocol.AUDIO_FINISHED_MESSAGE_ID, Side.SERVER);
        CustomDiscsMod.NETWORK.registerMessage(
                ModelSelector.SelectMessage.Handler.class, ModelSelector.SelectMessage.class,
                NetworkProtocol.MODEL_SELECT_MESSAGE_ID, Side.SERVER);
        CustomDiscsMod.NETWORK.registerMessage(
                ClientLanguageMessage.Handler.class, ClientLanguageMessage.class,
                NetworkProtocol.CLIENT_LANGUAGE_MESSAGE_ID, Side.SERVER);
        CustomDiscsMod.NETWORK.registerMessage(
                AudioStartedMessage.Handler.class, AudioStartedMessage.class,
                NetworkProtocol.AUDIO_STARTED_MESSAGE_ID, Side.SERVER);
    }

    public static void registerClientboundDiscriminators() {
        CustomDiscsMod.NETWORK.registerMessage(
                OutboundPlaybackHandler.class, PlaybackMessage.class,
                NetworkProtocol.PLAYBACK_MESSAGE_ID, Side.CLIENT);
        CustomDiscsMod.NETWORK.registerMessage(
                OutboundChunkHandler.class, AudioChunkMessage.class,
                NetworkProtocol.AUDIO_CHUNK_MESSAGE_ID, Side.CLIENT);
        CustomDiscsMod.NETWORK.registerMessage(
                OutboundModelListHandler.class, ModelSelector.ModelListMessage.class,
                NetworkProtocol.MODEL_LIST_MESSAGE_ID, Side.CLIENT);
    }

    public static final class OutboundPlaybackHandler
            implements IMessageHandler<PlaybackMessage, IMessage> {
        @Override
        public IMessage onMessage(PlaybackMessage message, MessageContext context) {
            return null;
        }
    }

    public static final class OutboundChunkHandler
            implements IMessageHandler<AudioChunkMessage, IMessage> {
        @Override
        public IMessage onMessage(AudioChunkMessage message, MessageContext context) {
            return null;
        }
    }

    public static final class OutboundModelListHandler
            implements IMessageHandler<ModelSelector.ModelListMessage, IMessage> {
        @Override
        public IMessage onMessage(ModelSelector.ModelListMessage message, MessageContext context) {
            return null;
        }
    }

    public static final class AudioFinishedMessage implements IMessage {
        public java.util.UUID id;
        public boolean completed = true;

        public AudioFinishedMessage() {
        }

        public AudioFinishedMessage(java.util.UUID id) {
            this.id = id;
        }

        public AudioFinishedMessage(java.util.UUID id, boolean completed) {
            this.id = id;
            this.completed = completed;
        }

        @Override
        public void fromBytes(ByteBuf buffer) {
            id = new java.util.UUID(buffer.readLong(), buffer.readLong());
            completed = buffer.readBoolean();
        }

        @Override
        public void toBytes(ByteBuf buffer) {
            buffer.writeLong(id.getMostSignificantBits());
            buffer.writeLong(id.getLeastSignificantBits());
            buffer.writeBoolean(completed);
        }

        public static final class Handler implements IMessageHandler<AudioFinishedMessage, IMessage> {
            @Override
            public IMessage onMessage(AudioFinishedMessage message, MessageContext context) {
                final EntityPlayerMP player = context.getServerHandler().playerEntity;
                final java.util.UUID id = message.id;
                final boolean completed = message.completed;
                ServerTaskQueue.enqueue(new Runnable() {
                    @Override
                    public void run() {
                        PlaybackService.finished(id, player, completed);
                    }
                });
                return null;
            }
        }
    }
}
