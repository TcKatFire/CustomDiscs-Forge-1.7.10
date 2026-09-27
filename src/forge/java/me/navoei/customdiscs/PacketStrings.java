package me.navoei.customdiscs;

import io.netty.buffer.ByteBuf;

import java.nio.charset.Charset;

final class PacketStrings {
    private static final Charset UTF8 = Charset.forName("UTF-8");

    private PacketStrings() {
    }

    static void write(ByteBuf buffer, String value, int maximumBytes) {
        byte[] bytes = value.getBytes(UTF8);
        if (bytes.length > maximumBytes || bytes.length > 65535) {
            throw new IllegalArgumentException("Packet string exceeds limit.");
        }
        buffer.writeShort(bytes.length);
        buffer.writeBytes(bytes);
    }

    static String read(ByteBuf buffer, int maximumBytes) {
        int length = buffer.readUnsignedShort();
        if (length > maximumBytes || length > buffer.readableBytes()) {
            throw new IllegalArgumentException("Invalid packet string length.");
        }
        byte[] bytes = new byte[length];
        buffer.readBytes(bytes);
        return new String(bytes, UTF8);
    }
}
