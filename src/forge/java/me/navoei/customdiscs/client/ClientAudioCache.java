package me.navoei.customdiscs.client;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import me.navoei.customdiscs.AudioChunkMessage;
import me.navoei.customdiscs.AudioFileFormat;
import me.navoei.customdiscs.MusicFiles;

import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.BitSet;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.logging.Level;
import java.util.logging.Logger;

@SideOnly(Side.CLIENT)
public final class ClientAudioCache {
    private static final Logger LOGGER = Logger.getLogger("CustomDiscs");
    private static final int MAX_PARALLEL_TRANSFERS = 4;
    private static final long TRANSFER_TIMEOUT_MILLIS = 120000L;
    private static final File CACHE = new File(
            net.minecraft.client.Minecraft.getMinecraft().mcDataDir, "customdiscs/cache");
    private static final Map<String, Incoming> INCOMING = new HashMap<String, Incoming>();
    private static final ExecutorService VERIFY = Executors.newSingleThreadExecutor(runnable -> {
        Thread thread = new Thread(runnable, "CustomDiscs-CacheVerify");
        thread.setDaemon(true);
        return thread;
    });

    private ClientAudioCache() {
    }

    public static File find(String hash, String extension) {
        if (!validHash(hash) || !AudioFileFormat.isSupported(extension)) {
            return null;
        }
        File file = cacheFile(hash, extension);
        if (!file.isFile()) {
            return null;
        }
        try {
            if (hash.equals(MusicFiles.sha256(file))) {
                return file;
            }
            if (!file.delete()) {
                LOGGER.warning("Could not remove corrupt CustomDiscs cache file " + file.getName());
            }
        } catch (IOException e) {
            LOGGER.log(Level.WARNING, "Could not verify cached audio " + file.getName(), e);
        }
        return null;
    }

    public static void accept(AudioChunkMessage chunk) {
        if (!validHash(chunk.hash)) {
            return;
        }
        final Incoming completed;
        synchronized (INCOMING) {
            expireTransfers();
            String key = transferKey(chunk.hash, chunk.extension);
            Incoming transfer = INCOMING.get(key);
            if (transfer == null) {
                if (INCOMING.size() >= MAX_PARALLEL_TRANSFERS) {
                    return;
                }
                try {
                    if (!CACHE.isDirectory() && !CACHE.mkdirs()) {
                        throw new IOException("Could not create audio cache directory.");
                    }
                    transfer = new Incoming(chunk.hash, chunk.extension,
                            chunk.chunkCount, chunk.totalLength);
                    INCOMING.put(key, transfer);
                } catch (IOException e) {
                    LOGGER.log(Level.WARNING, "Could not receive custom audio.", e);
                    return;
                }
            }
            if (transfer.chunkCount != chunk.chunkCount || transfer.totalLength != chunk.totalLength) {
                transfer.closeAndDelete();
                INCOMING.remove(key);
                return;
            }
            if (transfer.received.get(chunk.index)) {
                return;
            }
            try {
                transfer.file.seek((long) chunk.index * AudioChunkMessage.MAX_CHUNK_BYTES);
                transfer.file.write(chunk.data);
                transfer.lastActivity = System.currentTimeMillis();
                transfer.received.set(chunk.index);
                transfer.receivedCount++;
            } catch (IOException e) {
                transfer.closeAndDelete();
                INCOMING.remove(key);
                LOGGER.log(Level.WARNING, "Could not write received custom audio.", e);
                return;
            }
            if (transfer.receivedCount != transfer.chunkCount) {
                return;
            }
            try {
                transfer.file.setLength(transfer.totalLength);
                transfer.file.close();
            } catch (IOException e) {
                INCOMING.remove(key);
                transfer.closeAndDelete();
                LOGGER.log(Level.WARNING, "Could not close received custom audio.", e);
                return;
            }
            INCOMING.remove(key);
            completed = transfer;
        }
        VERIFY.execute(new Runnable() {
            @Override
            public void run() {
                verifyAndInstall(completed);
            }
        });
    }

    private static void verifyAndInstall(Incoming transfer) {
        try {
            if (!transfer.hash.equals(MusicFiles.sha256(transfer.temporary))) {
                throw new IOException("Received audio SHA-256 does not match.");
            }
            File target = cacheFile(transfer.hash, transfer.extension);
            try {
                Files.move(transfer.temporary.toPath(), target.toPath(), StandardCopyOption.ATOMIC_MOVE);
            } catch (AtomicMoveNotSupportedException e) {
                Files.move(transfer.temporary.toPath(), target.toPath(), StandardCopyOption.REPLACE_EXISTING);
            }
            ClientPlaybackManager.INSTANCE.assetAvailable(transfer.hash);
        } catch (IOException e) {
            if (!transfer.temporary.delete()) {
                transfer.temporary.deleteOnExit();
            }
            LOGGER.log(Level.WARNING, "Rejected custom audio transfer.", e);
        }
    }

    static File cacheFile(String hash, String extension) {
        return new File(CACHE, AudioFileFormat.cacheName(hash, extension));
    }

    private static String transferKey(String hash, String extension) {
        return hash + "." + extension;
    }

    private static boolean validHash(String hash) {
        return AudioFileFormat.isHash(hash);
    }

    private static void expireTransfers() {
        long now = System.currentTimeMillis();
        java.util.Iterator<Map.Entry<String, Incoming>> iterator = INCOMING.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<String, Incoming> entry = iterator.next();
            if (now - entry.getValue().lastActivity > TRANSFER_TIMEOUT_MILLIS) {
                entry.getValue().closeAndDelete();
                iterator.remove();
            }
        }
    }

    private static final class Incoming {
        private final String hash;
        private final String extension;
        private final int chunkCount;
        private final int totalLength;
        private final File temporary;
        private final RandomAccessFile file;
        private final BitSet received;
        private long lastActivity = System.currentTimeMillis();
        private int receivedCount;

        private Incoming(String hash, String extension, int chunkCount, int totalLength)
                throws IOException {
            this.hash = hash;
            this.extension = extension;
            this.chunkCount = chunkCount;
            this.totalLength = totalLength;
            this.temporary = new File(CACHE,
                    AudioFileFormat.cacheName(hash, extension) + ".part");
            this.file = new RandomAccessFile(temporary, "rw");
            this.received = new BitSet(chunkCount);
        }

        private void closeAndDelete() {
            try {
                file.close();
            } catch (IOException ignored) {
            }
            if (!temporary.delete()) {
                temporary.deleteOnExit();
            }
        }
    }
}
