package me.navoei.customdiscs;

import cpw.mods.fml.common.network.simpleimpl.IMessage;
import io.netty.buffer.ByteBuf;

import java.util.logging.Logger;

public final class AudioChunkMessage implements IMessage {
    public static final int MAX_CHUNK_BYTES = 24000;
    private static final int ABSOLUTE_MAX_AUDIO_BYTES = 512 * 1024 * 1024;
    private static final Logger LOGGER = Logger.getLogger("CustomDiscs");

    public String hash = "";
    public String extension = "";
    public int index;
    public int chunkCount;
    public int totalLength;
    public byte[] data = new byte[0];

    public AudioChunkMessage() {
    }

    public AudioChunkMessage(String hash, String extension, int index, int chunkCount,
                             int totalLength, byte[] data) {
        this.hash = hash;
        this.extension = extension;
        this.index = index;
        this.chunkCount = chunkCount;
        this.totalLength = totalLength;
        this.data = data;
    }

    @Override
    public void fromBytes(ByteBuf buffer) {
        hash = PacketStrings.read(buffer, 64);
        extension = PacketStrings.read(buffer, 4);
        index = buffer.readInt();
        chunkCount = buffer.readInt();
        totalLength = buffer.readInt();
        int size = buffer.readUnsignedShort();
        if (!AudioFileFormat.isHash(hash) || !AudioFileFormat.isSupported(extension) || totalLength < 1
                || totalLength > ABSOLUTE_MAX_AUDIO_BYTES
                || chunkCount != (totalLength + MAX_CHUNK_BYTES - 1) / MAX_CHUNK_BYTES
                || index < 0 || index >= chunkCount || size > MAX_CHUNK_BYTES
                || size != Math.min(MAX_CHUNK_BYTES, totalLength - index * MAX_CHUNK_BYTES)
                || size > buffer.readableBytes()) {
            throw new IllegalArgumentException("Invalid audio chunk.");
        }
        data = new byte[size];
        buffer.readBytes(data);
    }

    @Override
    public void toBytes(ByteBuf buffer) {
        int start = buffer.writerIndex();
        PacketStrings.write(buffer, hash, 64);
        PacketStrings.write(buffer, extension, 4);
        buffer.writeInt(index);
        buffer.writeInt(chunkCount);
        buffer.writeInt(totalLength);
        buffer.writeShort(data.length);
        buffer.writeBytes(data);
        if (index == 0) {
            String discriminator = start > 0
                    ? String.format("0x%02x", buffer.getUnsignedByte(start - 1)) : "<not-present>";
            LOGGER.info("audio_chunk_encode discriminator=" + discriminator
                    + " first_chunk_bytes=" + (buffer.writerIndex() - start));
        }
    }
}
