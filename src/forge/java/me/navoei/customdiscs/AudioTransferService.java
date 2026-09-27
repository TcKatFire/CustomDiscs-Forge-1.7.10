package me.navoei.customdiscs;

import net.minecraft.entity.player.EntityPlayerMP;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.RejectedExecutionException;
import java.util.logging.Level;
import java.util.logging.Logger;

public final class AudioTransferService {
    private static final Logger LOGGER = Logger.getLogger("CustomDiscs");
    private static final Map<String, Source> ACTIVE_FILES = new ConcurrentHashMap<String, Source>();
    private static final Map<java.util.UUID, Map<String, Long>> LAST_REQUESTS =
            new ConcurrentHashMap<java.util.UUID, Map<String, Long>>();
    private static final ExecutorService TRANSFERS = new ThreadPoolExecutor(1, 1, 0L,
            TimeUnit.MILLISECONDS, new ArrayBlockingQueue<Runnable>(32), runnable -> {
                Thread thread = new Thread(runnable, "CustomDiscs-FileTransfer");
                thread.setDaemon(true);
                return thread;
            });

    private AudioTransferService() {
    }

    public static synchronized void register(String hash, File file) {
        Source source = ACTIVE_FILES.get(hash);
        if (source == null) {
            ACTIVE_FILES.put(hash, new Source(file));
        } else {
            source.references++;
        }
    }

    public static synchronized void unregister(String hash) {
        Source source = ACTIVE_FILES.get(hash);
        if (source != null && --source.references <= 0) {
            ACTIVE_FILES.remove(hash);
        }
    }

    public static void send(final EntityPlayerMP player, final String hash,
                            final String extension) {
        if (player.worldObj == null || !player.worldObj.playerEntities.contains(player)) {
            return;
        }
        final Source source = ACTIVE_FILES.get(hash);
        final File file = source == null ? null : source.file;
        if (file == null || !file.isFile() || !AudioFileFormat.isSupported(extension)
                || !PlaybackService.canRequestAudio(hash, extension, player)) {
            return;
        }
        if (!allowRequest(player.getUniqueID(), hash)) {
            return;
        }
        try {
            TRANSFERS.execute(new Runnable() {
                @Override
                public void run() {
                    long length = file.length();
                    long limit = (long) ModConfig.maximumDownloadSizeMb * 1024L * 1024L;
                    if (length < 1L || length > limit || length > Integer.MAX_VALUE) {
                        return;
                    }
                    int total = (int) length;
                    int count = (total + AudioChunkMessage.MAX_CHUNK_BYTES - 1)
                            / AudioChunkMessage.MAX_CHUNK_BYTES;
                    try (FileInputStream input = new FileInputStream(file)) {
                        byte[] buffer = new byte[AudioChunkMessage.MAX_CHUNK_BYTES];
                        for (int index = 0; index < count; index++) {
                            int expected = Math.min(buffer.length, total - index * buffer.length);
                            int offset = 0;
                            while (offset < expected) {
                                int read = input.read(buffer, offset, expected - offset);
                                if (read < 0) {
                                    throw new IOException("Audio file changed during transfer.");
                                }
                                offset += read;
                            }
                            byte[] payload = new byte[expected];
                            System.arraycopy(buffer, 0, payload, 0, expected);
                            CustomDiscsMod.NETWORK.sendTo(
                                    new AudioChunkMessage(hash, extension, index, count, total,
                                            payload), player);
                        }
                    } catch (IOException e) {
                        LOGGER.log(Level.WARNING, "Unable to transfer custom audio to "
                                + player.getCommandSenderName(), e);
                    }
                }
            });
        } catch (RejectedExecutionException e) {
            forgetRequest(player.getUniqueID(), hash);
            LOGGER.warning("CustomDiscs audio transfer queue is full; request from "
                    + player.getCommandSenderName() + " was rejected.");
        }
    }

    public static void playerLeft(java.util.UUID player) {
        LAST_REQUESTS.remove(player);
    }

    private static synchronized boolean allowRequest(java.util.UUID player, String hash) {
        Map<String, Long> requests = LAST_REQUESTS.get(player);
        if (requests == null) {
            requests = new HashMap<String, Long>();
            LAST_REQUESTS.put(player, requests);
        }
        long now = System.currentTimeMillis();
        Long previous = requests.get(hash);
        if (previous != null && now - previous < 30000L) {
            return false;
        }
        if (requests.size() >= 128) {
            String oldestHash = null;
            long oldestTime = Long.MAX_VALUE;
            for (Map.Entry<String, Long> entry : requests.entrySet()) {
                if (entry.getValue() < oldestTime) {
                    oldestHash = entry.getKey();
                    oldestTime = entry.getValue();
                }
            }
            if (oldestHash != null) {
                requests.remove(oldestHash);
            }
        }
        requests.put(hash, now);
        return true;
    }

    private static synchronized void forgetRequest(java.util.UUID player, String hash) {
        Map<String, Long> requests = LAST_REQUESTS.get(player);
        if (requests != null) {
            requests.remove(hash);
            if (requests.isEmpty()) {
                LAST_REQUESTS.remove(player);
            }
        }
    }

    private static final class Source {
        private final File file;
        private int references = 1;

        private Source(File file) {
            this.file = file;
        }
    }
}
