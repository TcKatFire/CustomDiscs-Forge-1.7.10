package me.navoei.customdiscs;

import cpw.mods.fml.common.network.simpleimpl.IMessage;
import io.netty.buffer.ByteBuf;

import java.util.UUID;
import java.util.logging.Logger;

public final class PlaybackMessage implements IMessage {
    private static final Logger LOGGER = Logger.getLogger("CustomDiscs");
    public static final byte START = 0;
    public static final byte STOP = 1;
    public static final byte BLOCK_SOURCE = 0;
    public static final byte ENTITY_SOURCE = 1;

    public byte action;
    public UUID playbackId;
    public byte sourceType;
    public int dimension;
    public double x;
    public double y;
    public double z;
    public int entityId;
    public byte category;
    public float range;
    public float masterGain;
    public String hash = "";
    public String extension = "";
    public String title = "";
    public String decodeError;
    public String decodeDiagnostic;

    public PlaybackMessage() {
    }

    public static PlaybackMessage start(UUID id, byte sourceType, int dimension,
                                        double x, double y, double z, int entityId,
                                        AudioCategory category, float range, float masterGain,
                                        String hash, String extension, String title) {
        PlaybackMessage message = new PlaybackMessage();
        message.action = START;
        message.playbackId = id;
        message.sourceType = sourceType;
        message.dimension = dimension;
        message.x = x;
        message.y = y;
        message.z = z;
        message.entityId = entityId;
        message.category = (byte) category.getId();
        message.range = range;
        message.masterGain = masterGain;
        message.hash = hash;
        message.extension = extension;
        message.title = title;
        return message;
    }

    public static PlaybackMessage stop(UUID id) {
        PlaybackMessage message = new PlaybackMessage();
        message.action = STOP;
        message.playbackId = id;
        return message;
    }

    @Override
    public void fromBytes(ByteBuf buffer) {
        decodeDiagnostic = rawHeader(buffer);
        try {
            decode(buffer);
        } catch (IllegalArgumentException | IndexOutOfBoundsException e) {
            decodeError = e.getClass().getSimpleName() + ": " + NetworkProtocol.safeVersion(
                    e.getMessage() == null ? "invalid packet" : e.getMessage());
        }
    }

    static String rawHeader(ByteBuf buffer) {
        return rawHeader(buffer, buffer.readerIndex());
    }

    static String rawHeader(ByteBuf buffer, int start) {
        return "action=" + rawByte(buffer, start)
                + " source=" + rawByte(buffer, start + 17)
                + " category=" + rawByte(buffer, start + 18);
    }

    private static String rawByte(ByteBuf buffer, int index) {
        if (index < buffer.readerIndex() || index >= buffer.writerIndex()) {
            return "<absent>";
        }
        return String.format("0x%02x", buffer.getUnsignedByte(index));
    }

    private void decode(ByteBuf buffer) {
        int actionId = buffer.readUnsignedByte();
        if (actionId > STOP) {
            throw new IllegalArgumentException("Unknown playback action.");
        }
        action = (byte) actionId;
        playbackId = new UUID(buffer.readLong(), buffer.readLong());
        if (action == STOP) {
            return;
        }
        int source = buffer.readUnsignedByte();
        int categoryId = buffer.readUnsignedByte();
        if (source > ENTITY_SOURCE) {
            throw new IllegalArgumentException("Unknown playback source.");
        }
        if (categoryId >= AudioCategory.values().length) {
            throw new IllegalArgumentException("Unknown playback category.");
        }
        sourceType = (byte) source;
        category = (byte) categoryId;
        dimension = buffer.readInt();
        x = buffer.readDouble();
        y = buffer.readDouble();
        z = buffer.readDouble();
        entityId = buffer.readInt();
        range = buffer.readFloat();
        masterGain = buffer.readFloat();
        hash = PacketStrings.read(buffer, 64);
        extension = PacketStrings.read(buffer, 4);
        title = PacketStrings.read(buffer, 512);
        if (!AudioFileFormat.isHash(hash) || !AudioFileFormat.isSupported(extension)
                || range <= 0.0F || range > 4096.0F
                || Float.isNaN(range) || Float.isInfinite(range)
                || masterGain < 0.0F || masterGain > 1.0F
                || Float.isNaN(masterGain) || Float.isInfinite(masterGain)
                || Double.isNaN(x) || Double.isInfinite(x)
                || Double.isNaN(y) || Double.isInfinite(y)
                || Double.isNaN(z) || Double.isInfinite(z)) {
            throw new IllegalArgumentException("Invalid playback metadata.");
        }
    }

    @Override
    public void toBytes(ByteBuf buffer) {
        int start = buffer.writerIndex();
        buffer.writeByte(action);
        buffer.writeLong(playbackId.getMostSignificantBits());
        buffer.writeLong(playbackId.getLeastSignificantBits());
        if (action == STOP) {
            logEncoded(start, buffer);
            return;
        }
        buffer.writeByte(sourceType);
        buffer.writeByte(category);
        buffer.writeInt(dimension);
        buffer.writeDouble(x);
        buffer.writeDouble(y);
        buffer.writeDouble(z);
        buffer.writeInt(entityId);
        buffer.writeFloat(range);
        buffer.writeFloat(masterGain);
        PacketStrings.write(buffer, hash, 64);
        PacketStrings.write(buffer, extension, 4);
        PacketStrings.write(buffer, title, 512);
        logEncoded(start, buffer);
    }

    private static void logEncoded(int start, ByteBuf buffer) {
        String discriminator = start > 0
                ? String.format("0x%02x", buffer.getUnsignedByte(start - 1)) : "<not-present>";
        LOGGER.info("playback_encode discriminator=" + discriminator
                + " body_bytes=" + (buffer.writerIndex() - start)
                + " header=" + rawHeader(buffer, start));
    }
}
