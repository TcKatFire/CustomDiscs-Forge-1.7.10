package me.navoei.customdiscs;

import cpw.mods.fml.common.network.simpleimpl.IMessage;
import cpw.mods.fml.common.network.simpleimpl.IMessageHandler;
import cpw.mods.fml.common.network.simpleimpl.MessageContext;
import io.netty.buffer.ByteBuf;
import net.minecraft.entity.player.EntityPlayerMP;

public final class ClientLanguageMessage implements IMessage {
    public String language = "en";

    public ClientLanguageMessage() {
    }

    public ClientLanguageMessage(String language) {
        this.language = language;
    }

    @Override
    public void fromBytes(ByteBuf buffer) {
        language = PacketStrings.read(buffer, 32);
        if (!language.matches("[A-Za-z_\\-]{2,32}")) {
            language = "en";
        }
    }

    @Override
    public void toBytes(ByteBuf buffer) {
        PacketStrings.write(buffer, language, 32);
    }

    public static final class Handler implements IMessageHandler<ClientLanguageMessage, IMessage> {
        @Override
        public IMessage onMessage(ClientLanguageMessage message, MessageContext context) {
            EntityPlayerMP player = context.getServerHandler().playerEntity;
            PlayerLanguageManager.set(player.getUniqueID(), message.language);
            return null;
        }
    }
}
