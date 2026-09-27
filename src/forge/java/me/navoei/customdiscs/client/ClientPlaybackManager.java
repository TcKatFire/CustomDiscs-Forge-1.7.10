package me.navoei.customdiscs.client;

import cpw.mods.fml.common.network.NetworkRegistry;
import cpw.mods.fml.common.gameevent.TickEvent;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import net.minecraft.client.Minecraft;
import net.minecraft.entity.Entity;
import net.minecraft.util.ChatComponentText;
import me.navoei.customdiscs.AudioCategory;
import me.navoei.customdiscs.AudioRequestMessage;
import me.navoei.customdiscs.AudioStartedMessage;
import me.navoei.customdiscs.CustomDiscsMod;
import me.navoei.customdiscs.ClientLanguageMessage;
import me.navoei.customdiscs.NetworkBootstrap;
import me.navoei.customdiscs.PlaybackMessage;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentLinkedQueue;

@SideOnly(Side.CLIENT)
public final class ClientPlaybackManager {
    public static final ClientPlaybackManager INSTANCE = new ClientPlaybackManager();
    private static final int MAX_TRACKS = 32;
    private final Map<UUID, Track> tracks = new HashMap<UUID, Track>();
    private final ConcurrentLinkedQueue<Runnable> clientTasks = new ConcurrentLinkedQueue<Runnable>();
    private int previousDimension = Integer.MIN_VALUE;
    private boolean languageSent;

    private ClientPlaybackManager() {
    }

    public void receive(PlaybackMessage message) {
        if (message.action == PlaybackMessage.STOP) {
            Track removed = tracks.remove(message.playbackId);
            if (removed != null) {
                ClientAudioMixer.INSTANCE.stop(message.playbackId);
            }
            return;
        }
        Track track = new Track(message);
        tracks.put(message.playbackId, track);
        if (tracks.size() > MAX_TRACKS) {
            tracks.remove(message.playbackId);
            Minecraft.getMinecraft().thePlayer.addChatMessage(
                    new ChatComponentText("\u00a7cToo many CustomDiscs tracks are active."));
        }
    }

    public void enqueue(Runnable task) {
        clientTasks.add(task);
    }

    @SubscribeEvent
    public void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }
        Runnable task;
        while ((task = clientTasks.poll()) != null) {
            task.run();
        }
        Minecraft minecraft = Minecraft.getMinecraft();
        if (minecraft.thePlayer == null || minecraft.theWorld == null) {
            stopAll(minecraft.thePlayer != null && minecraft.getNetHandler() != null);
            previousDimension = Integer.MIN_VALUE;
            languageSent = false;
            return;
        }
        if (!languageSent) {
            CustomDiscsMod.NETWORK.sendToServer(
                    new ClientLanguageMessage(minecraft.gameSettings.language));
            languageSent = true;
        }
        int dimension = minecraft.thePlayer.dimension;
        if (previousDimension != Integer.MIN_VALUE && previousDimension != dimension) {
            stopAll(true);
        }
        previousDimension = dimension;
        double listenerX = minecraft.thePlayer.posX;
        double listenerY = minecraft.thePlayer.posY + minecraft.thePlayer.getEyeHeight();
        double listenerZ = minecraft.thePlayer.posZ;
        float yaw = minecraft.thePlayer.rotationYaw;
        ClientAudioMixer.INSTANCE.setListener(listenerX, listenerY, listenerZ, yaw);

        for (Track track : new ArrayList<Track>(tracks.values())) {
            if (track.message.dimension != dimension) {
                stop(track);
                continue;
            }
            updateSource(track, minecraft);
            double dx = track.x - listenerX;
            double dy = track.y - listenerY;
            double dz = track.z - listenerZ;
            if (!track.started && dx * dx + dy * dy + dz * dz
                    <= track.message.range * track.message.range) {
                if (!track.cacheChecked) {
                    track.cachedFile = ClientAudioCache.find(
                            track.message.hash, track.message.extension);
                    track.cacheChecked = true;
                }
                if (track.cachedFile != null) {
                    start(track, track.cachedFile);
                } else if (!track.requested) {
                    track.requested = true;
                    CustomDiscsMod.NETWORK.sendToServer(
                            new AudioRequestMessage(track.message.hash, track.message.extension));
                }
            }
            if (track.started) {
                ClientAudioMixer.INSTANCE.update(track.message.playbackId,
                        track.x, track.y, track.z, track.message.range,
                        track.message.masterGain * ClientAudioSettings.get(
                                AudioCategory.fromId(track.message.category)));
            }
        }
    }

    public void assetAvailable(final String hash) {
        enqueue(new Runnable() {
            @Override
            public void run() {
                for (Track track : new ArrayList<Track>(tracks.values())) {
                    if (!track.started && track.message.hash.equals(hash)) {
                        File cached = ClientAudioCache.find(hash, track.message.extension);
                        if (cached == null) {
                            continue;
                        }
                        track.cachedFile = cached;
                        track.cacheChecked = true;
                    }
                }
            }
        });
    }

    void audioFinished(UUID id) {
        Track track = tracks.remove(id);
        if (track != null) {
            CustomDiscsMod.NETWORK.sendToServer(
                    new NetworkBootstrap.AudioFinishedMessage(id));
        }
    }

    void audioFinishedFromMixer(final UUID id) {
        enqueue(new Runnable() {
            @Override
            public void run() {
                audioFinished(id);
            }
        });
    }

    private void start(Track track, File file) {
        if (!ClientAudioMixer.INSTANCE.play(track.message.playbackId, file,
                track.x, track.y, track.z, track.message.range,
                track.message.masterGain * ClientAudioSettings.get(
                        AudioCategory.fromId(track.message.category)))) {
            return;
        }
        track.started = true;
        CustomDiscsMod.NETWORK.sendToServer(
                new AudioStartedMessage(track.message.playbackId));
    }

    private void updateSource(Track track, Minecraft minecraft) {
        if (track.message.sourceType != PlaybackMessage.ENTITY_SOURCE) {
            return;
        }
        Entity source = minecraft.theWorld.getEntityByID(track.message.entityId);
        if (source != null) {
            track.x = source.posX;
            track.y = source.posY + source.getEyeHeight() * 0.5D;
            track.z = source.posZ;
        }
    }

    private void stop(Track track) {
        tracks.remove(track.message.playbackId);
        ClientAudioMixer.INSTANCE.stop(track.message.playbackId);
    }

    private void stopAll(boolean notifyServer) {
        for (UUID id : new ArrayList<UUID>(tracks.keySet())) {
            ClientAudioMixer.INSTANCE.stop(id);
            if (notifyServer) {
                CustomDiscsMod.NETWORK.sendToServer(
                        new NetworkBootstrap.AudioFinishedMessage(id, false));
            }
        }
        tracks.clear();
    }

    private static final class Track {
        private final PlaybackMessage message;
        private double x;
        private double y;
        private double z;
        private boolean requested;
        private boolean started;
        private boolean cacheChecked;
        private File cachedFile;

        private Track(PlaybackMessage message) {
            this.message = message;
            x = message.x;
            y = message.y;
            z = message.z;
        }
    }
}
