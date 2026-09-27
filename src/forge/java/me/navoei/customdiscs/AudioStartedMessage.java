package me.navoei.customdiscs;

import cpw.mods.fml.common.network.simpleimpl.IMessage;
import cpw.mods.fml.common.network.simpleimpl.IMessageHandler;
import cpw.mods.fml.common.network.simpleimpl.MessageContext;
import io.netty.buffer.ByteBuf;
import net.minecraft.entity.player.EntityPlayerMP;

import java.util.UUID;

public final class AudioStartedMessage implements IMessage {
    public UUID id;

    public AudioStartedMessage() {
    }

    public AudioStartedMessage(UUID id) {
        this.id = id;
    }

    @Override
    public void fromBytes(ByteBuf buffer) {
        id = new UUID(buffer.readLong(), buffer.readLong());
    }

    @Override
    public void toBytes(ByteBuf buffer) {
        buffer.writeLong(id.getMostSignificantBits());
        buffer.writeLong(id.getLeastSignificantBits());
    }

    public static final class Handler implements IMessageHandler<AudioStartedMessage, IMessage> {
        @Override
        public IMessage onMessage(AudioStartedMessage message, MessageContext context) {
            final EntityPlayerMP player = context.getServerHandler().playerEntity;
            final UUID id = message.id;
            ServerTaskQueue.enqueue(new Runnable() {
                @Override
                public void run() {
                    PlaybackService.started(id, player);
                }
            });
            return null;
        }
    }
}
