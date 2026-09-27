package me.navoei.customdiscs;

import cpw.mods.fml.common.network.simpleimpl.IMessage;
import cpw.mods.fml.common.network.simpleimpl.IMessageHandler;
import cpw.mods.fml.common.network.simpleimpl.MessageContext;
import io.netty.buffer.ByteBuf;
import net.minecraft.entity.player.EntityPlayerMP;

public final class AudioRequestMessage implements IMessage {
    public String hash = "";
    public String extension = "";

    public AudioRequestMessage() {
    }

    public AudioRequestMessage(String hash, String extension) {
        this.hash = hash;
        this.extension = extension;
    }

    @Override
    public void fromBytes(ByteBuf buffer) {
        hash = PacketStrings.read(buffer, 64);
        extension = PacketStrings.read(buffer, 4);
        if (!AudioFileFormat.isHash(hash) || !AudioFileFormat.isSupported(extension)) {
            throw new IllegalArgumentException("Invalid audio request.");
        }
    }

    @Override
    public void toBytes(ByteBuf buffer) {
        PacketStrings.write(buffer, hash, 64);
        PacketStrings.write(buffer, extension, 4);
    }

    public static final class Handler implements IMessageHandler<AudioRequestMessage, IMessage> {
        @Override
        public IMessage onMessage(AudioRequestMessage message, MessageContext context) {
            final EntityPlayerMP player = context.getServerHandler().playerEntity;
            final String hash = message.hash;
            final String extension = message.extension;
            ServerTaskQueue.enqueue(new Runnable() {
                @Override
                public void run() {
                    AudioTransferService.send(player, hash, extension);
                }
            });
            return null;
        }
    }
}
