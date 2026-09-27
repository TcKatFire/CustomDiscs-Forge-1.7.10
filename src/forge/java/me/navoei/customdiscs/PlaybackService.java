package me.navoei.customdiscs;

import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.entity.Entity;
import net.minecraft.item.ItemStack;
import net.minecraft.world.World;
import net.minecraft.world.WorldServer;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;
import java.util.logging.Logger;

public final class PlaybackService {
    private static final Logger LOGGER = Logger.getLogger("CustomDiscs");
    private static final Map<UUID, Track> TRACKS = new ConcurrentHashMap<UUID, Track>();
    private static final Set<String> COMPLETED_BLOCKS = Collections.newSetFromMap(
            new ConcurrentHashMap<String, Boolean>());

    private PlaybackService() {
    }

    private static void finishIfComplete(Track track) {
        if (!track.recipients.isEmpty() && track.finished.containsAll(track.recipients)) {
            if (track.hasCompletedPlayback && track.sourceType == PlaybackMessage.BLOCK_SOURCE) {
                COMPLETED_BLOCKS.add(blockKey(track.dimension, (int) Math.floor(track.x),
                        (int) Math.floor(track.y), (int) Math.floor(track.z)));
            }
            stop(track);
        }
    }

    public static UUID startBlock(ItemStack stack, World world, int x, int y, int z,
                                  AudioCategory category, String title) {
        float defaultRange = defaultRange(category);
        float maximumRange = maximumRange(category);
        float range = ItemData.getRange(stack, defaultRange, maximumRange);
        return start(stack, world, PlaybackMessage.BLOCK_SOURCE,
                x + 0.5D, y + 0.5D, z + 0.5D, -1, category, range, title);
    }

    public static UUID startEntity(ItemStack stack, EntityPlayerMP player) {
        AudioCategory category = AudioCategory.HORN;
        float range = ItemData.getRange(stack, ModConfig.hornRange, ModConfig.hornMaximumRange);
        return start(stack, player.worldObj, PlaybackMessage.ENTITY_SOURCE,
                player.posX, player.posY + player.getEyeHeight() * 0.5D, player.posZ,
                player.getEntityId(), category, range, ItemData.getTitle(stack));
    }

    private static UUID start(ItemStack stack, World world, byte sourceType,
                              double x, double y, double z, int entityId,
                              AudioCategory category, float range, String title) {
        if (stack == null || !ItemData.isCustom(stack) || world == null || world.isRemote
                || !isEnabled(category)) {
            return null;
        }
        String filename = ItemData.getAudio(stack);
        File file;
        String hash;
        try {
            file = MusicFiles.resolve(filename);
            long maximumBytes = (long) ModConfig.maximumDownloadSizeMb * 1024L * 1024L;
            if (!file.isFile() || file.length() < 1L || file.length() > maximumBytes) {
                return null;
            }
            hash = MusicFiles.sha256(file);
        } catch (IOException e) {
            LOGGER.log(Level.WARNING, "Cannot play custom audio " + filename, e);
            return null;
        }
        UUID id = UUID.randomUUID();
        Track track = new Track(id, hash, file, world.provider.dimensionId,
                sourceType, x, y, z, entityId, category, range, title,
                AudioFileFormat.extension(file.getName()));
        TRACKS.put(id, track);
        AudioTransferService.register(hash, file);
        PlaybackMessage message = track.toMessage();
        for (Object object : world.playerEntities) {
            if (object instanceof EntityPlayerMP) {
                EntityPlayerMP player = (EntityPlayerMP) object;
                track.viewers.add(player.getUniqueID());
                if (isWithinRange(track, player)) {
                    track.recipients.add(player.getUniqueID());
                }
                CustomDiscsMod.NETWORK.sendTo(message, player);
            }
        }
        if (isPlayingMessageEnabled(category)) {
            String displayTitle = title == null || title.trim().isEmpty() ? "Unknown" : title;
            for (Object object : world.playerEntities) {
                if (!(object instanceof EntityPlayerMP)) {
                    continue;
                }
                EntityPlayerMP player = (EntityPlayerMP) object;
                double dx = player.posX - x;
                double dy = player.posY + player.getEyeHeight() - y;
                double dz = player.posZ - z;
                if (dx * dx + dy * dy + dz * dz <= range * range) {
                    player.addChatMessage(new net.minecraft.util.ChatComponentText(
                            Localization.forPlayer(player, "now-playing")
                                    .replace("%song_name%", displayTitle).replace('&', '\u00a7')));
                }
            }
        }
        return id;
    }

    public static boolean isAvailable(ItemStack stack) {
        if (!ItemData.isCustom(stack)) {
            return false;
        }
        try {
            File file = MusicFiles.resolve(ItemData.getAudio(stack));
            return file.isFile() && file.length() > 0L
                    && file.length() <= (long) ModConfig.maximumDownloadSizeMb * 1024L * 1024L;
        } catch (IOException e) {
            return false;
        }
    }

    public static void stopAt(int dimension, int x, int y, int z) {
        COMPLETED_BLOCKS.remove(blockKey(dimension, x, y, z));
        for (Track track : new ArrayList<Track>(TRACKS.values())) {
            if (track.dimension == dimension && track.sourceType == PlaybackMessage.BLOCK_SOURCE
                    && (int) Math.floor(track.x) == x && (int) Math.floor(track.y) == y
                    && (int) Math.floor(track.z) == z) {
                stop(track);
            }
        }
    }

    public static void stopEntity(int dimension, int entityId) {
        for (Track track : new ArrayList<Track>(TRACKS.values())) {
            if (track.dimension == dimension && track.sourceType == PlaybackMessage.ENTITY_SOURCE
                    && track.entityId == entityId) {
                stop(track);
            }
        }
    }

    public static boolean isPlayingAt(int dimension, int x, int y, int z) {
        for (Track track : TRACKS.values()) {
            if (track.dimension == dimension && track.sourceType == PlaybackMessage.BLOCK_SOURCE
                    && (int) Math.floor(track.x) == x && (int) Math.floor(track.y) == y
                    && (int) Math.floor(track.z) == z) {
                return true;
            }
        }
        return false;
    }

    public static boolean hasCompletedAt(int dimension, int x, int y, int z) {
        return COMPLETED_BLOCKS.contains(blockKey(dimension, x, y, z));
    }

    public static void clearCompletedAt(int dimension, int x, int y, int z) {
        COMPLETED_BLOCKS.remove(blockKey(dimension, x, y, z));
    }

    public static List<Track> activeTracks() {
        return Collections.unmodifiableList(new ArrayList<Track>(TRACKS.values()));
    }

    public static boolean canRequestAudio(String hash, String extension, EntityPlayerMP player) {
        for (Track track : TRACKS.values()) {
            if (track.dimension == player.dimension && track.hash.equals(hash)
                    && track.extension.equals(extension)
                    && isWithinRange(track, player)) {
                track.recipients.add(player.getUniqueID());
                return true;
            }
        }
        return false;
    }

    public static void started(UUID id, EntityPlayerMP player) {
        Track track = TRACKS.get(id);
        if (track != null && track.dimension == player.dimension
                && track.viewers.contains(player.getUniqueID()) && isWithinRange(track, player)) {
            track.recipients.add(player.getUniqueID());
        }
    }

    public static void finished(UUID id, EntityPlayerMP player, boolean completed) {
        Track track = TRACKS.get(id);
        if (track == null || !track.viewers.contains(player.getUniqueID())) {
            return;
        }
        UUID playerId = player.getUniqueID();
        if (completed) {
            if (track.dimension != player.dimension || !track.recipients.contains(playerId)) {
                return;
            }
            track.finished.add(playerId);
            track.hasCompletedPlayback = true;
        } else {
            track.recipients.remove(playerId);
            track.finished.remove(playerId);
        }
        finishIfComplete(track);
        if (track.recipients.isEmpty()) {
            stop(track);
        }
    }

    public static void playerLeft(EntityPlayerMP player) {
        for (Track track : new ArrayList<Track>(TRACKS.values())) {
            track.recipients.remove(player.getUniqueID());
            track.finished.remove(player.getUniqueID());
            track.viewers.remove(player.getUniqueID());
            finishIfComplete(track);
            if (track.recipients.isEmpty()) {
                stop(track);
            }
        }
    }

    public static void stopDimension(int dimension) {
        for (Track track : new ArrayList<Track>(TRACKS.values())) {
            if (track.dimension == dimension) {
                stop(track);
            }
        }
    }

    public static void stopChunk(int dimension, int chunkX, int chunkZ) {
        for (Track track : new ArrayList<Track>(TRACKS.values())) {
            if (track.dimension == dimension && track.sourceType == PlaybackMessage.BLOCK_SOURCE
                    && ((int) Math.floor(track.x) >> 4) == chunkX
                    && ((int) Math.floor(track.z) >> 4) == chunkZ) {
                stop(track);
            }
        }
    }

    private static void stop(Track track) {
        if (!TRACKS.remove(track.id, track)) {
            return;
        }
        AudioTransferService.unregister(track.hash);
        PlaybackMessage message = PlaybackMessage.stop(track.id);
        net.minecraft.server.MinecraftServer server = net.minecraft.server.MinecraftServer.getServer();
        if (server != null) {
            for (Object object : server.getConfigurationManager().playerEntityList) {
                if (object instanceof EntityPlayerMP) {
                    EntityPlayerMP player = (EntityPlayerMP) object;
                    if (track.viewers.contains(player.getUniqueID())) {
                        CustomDiscsMod.NETWORK.sendTo(message, player);
                    }
                }
            }
        }
    }

    private static float defaultRange(AudioCategory category) {
        if (category == AudioCategory.MUSIC_DISC) return ModConfig.musicDiscRange;
        if (category == AudioCategory.HORN) return ModConfig.hornRange;
        return ModConfig.headRange;
    }

    private static boolean isEnabled(AudioCategory category) {
        if (category == AudioCategory.MUSIC_DISC) return ModConfig.musicDiscEnabled;
        if (category == AudioCategory.HORN) return ModConfig.customHornEnabled;
        return ModConfig.customHeadEnabled;
    }

    private static boolean isPlayingMessageEnabled(AudioCategory category) {
        if (category == AudioCategory.MUSIC_DISC) return ModConfig.musicDiscPlayingEnabled;
        if (category == AudioCategory.HORN) return ModConfig.customHornPlayingEnabled;
        return ModConfig.customHeadPlayingEnabled;
    }

    private static float maximumRange(AudioCategory category) {
        if (category == AudioCategory.MUSIC_DISC) return ModConfig.musicDiscMaximumRange;
        if (category == AudioCategory.HORN) return ModConfig.hornMaximumRange;
        return ModConfig.headMaximumRange;
    }

    private static String blockKey(int dimension, int x, int y, int z) {
        return dimension + ":" + x + ":" + y + ":" + z;
    }

    private static boolean isWithinRange(Track track, EntityPlayerMP player) {
        double sourceX = track.x;
        double sourceY = track.y;
        double sourceZ = track.z;
        if (track.sourceType == PlaybackMessage.ENTITY_SOURCE) {
            net.minecraft.server.MinecraftServer server =
                    net.minecraft.server.MinecraftServer.getServer();
            WorldServer world = server == null ? null : server.worldServerForDimension(track.dimension);
            Entity source = world == null ? null : world.getEntityByID(track.entityId);
            if (source != null) {
                sourceX = source.posX;
                sourceY = source.posY + source.getEyeHeight() * 0.5D;
                sourceZ = source.posZ;
            }
        }
        double dx = player.posX - sourceX;
        double dy = player.posY + player.getEyeHeight() - sourceY;
        double dz = player.posZ - sourceZ;
        return dx * dx + dy * dy + dz * dz <= track.range * track.range;
    }

    public static final class Track {
        private final UUID id;
        private final String hash;
        private final File file;
        private final int dimension;
        private final byte sourceType;
        private final double x;
        private final double y;
        private final double z;
        private final int entityId;
        private final AudioCategory category;
        private final float range;
        private final String title;
        private final String extension;
        private final Set<UUID> recipients = Collections.newSetFromMap(
                new ConcurrentHashMap<UUID, Boolean>());
        private final Set<UUID> viewers = Collections.newSetFromMap(
                new ConcurrentHashMap<UUID, Boolean>());
        private volatile boolean hasCompletedPlayback;
        private final Set<UUID> finished = Collections.newSetFromMap(
                new ConcurrentHashMap<UUID, Boolean>());

        private Track(UUID id, String hash, File file, int dimension, byte sourceType,
                      double x, double y, double z, int entityId, AudioCategory category,
                      float range, String title, String extension) {
            this.id = id;
            this.hash = hash;
            this.file = file;
            this.dimension = dimension;
            this.sourceType = sourceType;
            this.x = x;
            this.y = y;
            this.z = z;
            this.entityId = entityId;
            this.category = category;
            this.range = range;
            this.title = title;
            this.extension = extension;
        }

        private PlaybackMessage toMessage() {
            return PlaybackMessage.start(id, sourceType, dimension, x, y, z, entityId,
                    category, range, category == AudioCategory.MUSIC_DISC
                            ? ModConfig.musicDiscVolume : 1.0F, hash, extension, title);
        }
    }
}
