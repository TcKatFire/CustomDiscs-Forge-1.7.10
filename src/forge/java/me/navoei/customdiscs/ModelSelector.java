package me.navoei.customdiscs;

import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.item.ItemStack;
import net.minecraft.util.ChatComponentText;

import java.util.List;

public final class ModelSelector {
    private ModelSelector() {
    }

    public static void open(EntityPlayerMP player) {
        ItemStack held = player.getHeldItem();
        List<ModelRegistry.Entry> entries = ModelRegistry.getEntries(held);
        if (entries.isEmpty()) {
            player.addChatMessage(new ChatComponentText(Localization.forPlayer(
                    player, "model-selector-empty").replace('&', '\u00a7')));
            return;
        }
        CustomDiscsMod.NETWORK.sendTo(new ModelListMessage(entries), player);
    }

    public static final class ModelListMessage implements cpw.mods.fml.common.network.simpleimpl.IMessage {
        public int[] values = new int[0];
        public String[] names = new String[0];

        public ModelListMessage() {
        }

        public ModelListMessage(List<ModelRegistry.Entry> entries) {
            if (entries.size() > 256) {
                throw new IllegalArgumentException("At most 256 models may be displayed.");
            }
            values = new int[entries.size()];
            names = new String[entries.size()];
            for (int i = 0; i < entries.size(); i++) {
                values[i] = entries.get(i).value;
                names[i] = entries.get(i).name;
            }
        }

        @Override
        public void fromBytes(io.netty.buffer.ByteBuf buffer) {
            int count = buffer.readUnsignedShort();
            if (count > 256) {
                throw new IllegalArgumentException("Too many model entries.");
            }
            values = new int[count];
            names = new String[count];
            for (int i = 0; i < count; i++) {
                values[i] = buffer.readInt();
                names[i] = PacketStrings.read(buffer, 128);
            }
        }

        @Override
        public void toBytes(io.netty.buffer.ByteBuf buffer) {
            buffer.writeShort(values.length);
            for (int i = 0; i < values.length; i++) {
                buffer.writeInt(values[i]);
                PacketStrings.write(buffer, names[i], 128);
            }
        }
    }

    public static final class SelectMessage implements cpw.mods.fml.common.network.simpleimpl.IMessage {
        public int value;

        public SelectMessage() {
        }

        public SelectMessage(int value) {
            this.value = value;
        }

        @Override
        public void fromBytes(io.netty.buffer.ByteBuf buffer) {
            value = buffer.readInt();
        }

        @Override
        public void toBytes(io.netty.buffer.ByteBuf buffer) {
            buffer.writeInt(value);
        }

        public static final class Handler implements cpw.mods.fml.common.network.simpleimpl.IMessageHandler<SelectMessage, cpw.mods.fml.common.network.simpleimpl.IMessage> {
            @Override
            public cpw.mods.fml.common.network.simpleimpl.IMessage onMessage(
                    final SelectMessage message, cpw.mods.fml.common.network.simpleimpl.MessageContext context) {
                final EntityPlayerMP player = context.getServerHandler().playerEntity;
                ServerTaskQueue.enqueue(new Runnable() {
                    @Override
                    public void run() {
                        if (player.worldObj == null || !player.worldObj.playerEntities.contains(player)) {
                            return;
                        }
                        ItemStack held = player.getHeldItem();
                        if (!player.canCommandSenderUseCommand(0, "customdiscs")
                                || !ItemData.isCustom(held)
                                || !ModelRegistry.contains(held, message.value)) {
                            return;
                        }
                        ItemData.getOrCreate(held).setInteger(ItemData.MODEL, message.value);
                        player.addChatMessage(new ChatComponentText(Localization.forPlayer(
                                player, "set-model-success").replace('&', '\u00a7')));
                    }
                });
                return null;
            }
        }
    }
}
