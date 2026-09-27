package me.navoei.customdiscs;

import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.PlayerEvent;
import cpw.mods.fml.common.gameevent.TickEvent;
import cpw.mods.fml.common.gameevent.PlayerEvent.PlayerLoggedOutEvent;
import cpw.mods.fml.relauncher.Side;
import net.minecraft.entity.item.EntityItem;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.init.Blocks;
import net.minecraft.init.Items;
import net.minecraft.item.ItemSkull;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.block.BlockJukebox.TileEntityJukebox;
import net.minecraft.util.MathHelper;
import net.minecraft.world.World;
import net.minecraftforge.event.world.ChunkEvent;
import net.minecraftforge.event.world.NoteBlockEvent;
import net.minecraftforge.event.world.WorldEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.event.world.BlockEvent;
import net.minecraftforge.event.world.ExplosionEvent;
import net.minecraft.world.ChunkPosition;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

public final class ServerEventHandler {
    private final Map<String, Boolean> observedJukeboxes = new HashMap<String, Boolean>();

    @SubscribeEvent
    public void onInteract(PlayerInteractEvent event) {
        if (event.world.isRemote || event.entityPlayer == null) {
            return;
        }
        ItemStack held = event.entityPlayer.getHeldItem();
        if (event.action != PlayerInteractEvent.Action.RIGHT_CLICK_BLOCK
                || event.world.getBlock(event.x, event.y, event.z) != Blocks.jukebox) {
            return;
        }
        TileEntity tile = event.world.getTileEntity(event.x, event.y, event.z);
        if (!(tile instanceof TileEntityJukebox)) {
            return;
        }
        TileEntityJukebox jukebox = (TileEntityJukebox) tile;
        ItemStack record = jukebox.func_145856_a();
        if (ItemData.isCustom(record) && ModConfig.musicDiscEnabled) {
            event.setCanceled(true);
            jukebox.func_145857_a(null);
            event.world.markBlockForUpdate(event.x, event.y, event.z);
            EntityItem dropped = new EntityItem(event.world, event.x + 0.5D,
                    event.y + 1.0D, event.z + 0.5D, record);
            event.world.spawnEntityInWorld(dropped);
            PlaybackService.stopAt(event.world.provider.dimensionId, event.x, event.y, event.z);
            return;
        }
        if (!ModConfig.musicDiscEnabled || !ItemData.isDisc(held)
                || !ItemData.isCustom(held) || jukebox.func_145856_a() != null) {
            return;
        }
        if (!PlaybackService.isAvailable(held)) {
            event.setCanceled(true);
            event.entityPlayer.addChatMessage(new net.minecraft.util.ChatComponentText(
                    "\u00a7cSound file not found."));
            return;
        }
        event.setCanceled(true);
        ItemStack inserted = held.copy();
        inserted.stackSize = 1;
        jukebox.func_145857_a(inserted);
        if (!event.entityPlayer.capabilities.isCreativeMode) {
            held.stackSize--;
            if (held.stackSize <= 0) {
                event.entityPlayer.setCurrentItemOrArmor(0, null);
            }
        }
        event.world.markBlockForUpdate(event.x, event.y, event.z);
        PlaybackService.startBlock(inserted, event.world, event.x, event.y, event.z,
                AudioCategory.MUSIC_DISC, ItemData.getTitle(inserted));
    }

    @SubscribeEvent
    public void onNoteBlock(NoteBlockEvent.Play event) {
        if (event.world.isRemote || event.block != Blocks.noteblock) {
            return;
        }
        int x = event.x;
        int y = event.y + 1;
        int z = event.z;
        NBTTagCompound headData = null;
        if (event.world.getBlock(x, y, z) instanceof net.minecraft.block.BlockSkull) {
            headData = HeadAudioData.get(event.world).get(x, y, z);
        }
        final int dimension = event.world.provider.dimensionId;
        if (!prepareHeadNote(event, headData, new Runnable() {
            @Override
            public void run() {
                PlaybackService.stopAt(dimension, event.x, event.y, event.z);
            }
        })) {
            return;
        }
        ItemStack playback = new ItemStack(Items.skull);
        playback.setTagCompound(new NBTTagCompound());
        playback.getTagCompound().setTag("customdiscs", headData);
        PlaybackService.startBlock(playback, event.world, event.x, event.y, event.z,
                AudioCategory.PLAYER_HEAD, ItemData.getTitle(playback));
    }

    static boolean prepareHeadNote(NoteBlockEvent.Play event, NBTTagCompound headData,
                                   Runnable stopAtSource) {
        stopAtSource.run();
        if (headData == null || !headData.hasKey(ItemData.FILE, 8)) {
            return false;
        }
        event.setCanceled(true);
        return true;
    }

    @SubscribeEvent
    public void onHeadPlace(BlockEvent.PlaceEvent event) {
        if (event.world.isRemote || !(event.itemInHand != null
                && event.itemInHand.getItem() instanceof ItemSkull)) {
            return;
        }
        NBTTagCompound data = ItemData.get(event.itemInHand);
        if (data != null && data.hasKey(ItemData.FILE, 8)) {
            HeadAudioData.get(event.world).put(event.x, event.y, event.z, data);
        } else {
            HeadAudioData.get(event.world).remove(event.x, event.y, event.z);
        }
    }

    @SubscribeEvent
    public void onBlockBreak(BlockEvent.BreakEvent event) {
        if (event.world.isRemote || event.isCanceled()) {
            return;
        }
        int dimension = event.world.provider.dimensionId;
        PlaybackService.stopAt(dimension, event.x, event.y, event.z);
        if (event.world.getBlock(event.x, event.y, event.z) instanceof net.minecraft.block.BlockSkull) {
            HeadAudioData data = HeadAudioData.get(event.world);
            NBTTagCompound custom = data.get(event.x, event.y, event.z);
            if (custom != null) {
                PlaybackService.stopAt(dimension, event.x, event.y - 1, event.z);
                final World world = event.world;
                final int x = event.x;
                final int y = event.y;
                final int z = event.z;
                ServerTaskQueue.enqueue(new Runnable() {
                    @Override
                    public void run() {
                        if (world.blockExists(x, y, z)
                                && !(world.getBlock(x, y, z)
                                instanceof net.minecraft.block.BlockSkull)) {
                            HeadAudioData.get(world).remove(x, y, z);
                        }
                    }
                });
            }
        }
    }

    @SubscribeEvent
    public void onHarvestDrops(BlockEvent.HarvestDropsEvent event) {
        if (event.world.isRemote || !(event.block instanceof net.minecraft.block.BlockSkull)) {
            return;
        }
        NBTTagCompound custom = HeadAudioData.get(event.world).get(event.x, event.y, event.z);
        if (custom == null) {
            return;
        }
        applyHeadDataToDrops(event.drops, custom);
        HeadAudioData.get(event.world).remove(event.x, event.y, event.z);
    }

    static void applyHeadDataToDrops(Iterable<ItemStack> drops, NBTTagCompound custom) {
        for (ItemStack drop : drops) {
            if (drop.getItem() instanceof ItemSkull) {
                drop.setTagCompound(withHeadData(drop.getTagCompound(), custom));
            }
        }
    }

    static NBTTagCompound withHeadData(NBTTagCompound root, NBTTagCompound custom) {
        if (root == null) {
            root = new NBTTagCompound();
        }
        root.setTag("customdiscs", (NBTTagCompound) custom.copy());
        return root;
    }

    @SubscribeEvent
    public void onExplosion(ExplosionEvent.Detonate event) {
        if (event.world.isRemote) {
            return;
        }
        int dimension = event.world.provider.dimensionId;
        HeadAudioData data = HeadAudioData.get(event.world);
        for (ChunkPosition position : event.getAffectedBlocks()) {
            if (!(event.world.getBlock(position.chunkPosX, position.chunkPosY,
                    position.chunkPosZ) instanceof net.minecraft.block.BlockSkull)) {
                continue;
            }
            if (data.get(position.chunkPosX, position.chunkPosY, position.chunkPosZ) != null) {
                data.remove(position.chunkPosX, position.chunkPosY, position.chunkPosZ);
                PlaybackService.stopAt(dimension, position.chunkPosX,
                        position.chunkPosY - 1, position.chunkPosZ);
            }
            PlaybackService.stopAt(dimension, position.chunkPosX,
                    position.chunkPosY, position.chunkPosZ);
        }
    }

    @SubscribeEvent
    public void onWorldTick(TickEvent.WorldTickEvent event) {
        if (event.phase != TickEvent.Phase.END || event.world.isRemote
                || event.world.getTotalWorldTime() % 10L != 0L) {
            return;
        }

        for (Object object : event.world.loadedTileEntityList) {
            if (!(object instanceof TileEntityJukebox)) {
                continue;
            }
            TileEntityJukebox jukebox = (TileEntityJukebox) object;
            ItemStack record = jukebox.func_145856_a();
            int x = jukebox.xCoord;
            int y = jukebox.yCoord;
            int z = jukebox.zCoord;
            boolean custom = ModConfig.musicDiscEnabled && ItemData.isCustom(record)
                    && ItemData.isDisc(record) && PlaybackService.isAvailable(record);
            boolean active = PlaybackService.isPlayingAt(event.world.provider.dimensionId, x, y, z);
            String key = jukeboxKey(event.world.provider.dimensionId, x, y, z);
            boolean previouslyObserved = observedJukeboxes.containsKey(key);
            Boolean wasCustom = observedJukeboxes.put(key, custom);
            if (!custom) {
                PlaybackService.clearCompletedAt(event.world.provider.dimensionId, x, y, z);
            }
            boolean completed = PlaybackService.hasCompletedAt(
                    event.world.provider.dimensionId, x, y, z);
            if (shouldStartJukebox(custom, active, completed,
                    previouslyObserved, Boolean.TRUE.equals(wasCustom))) {
                PlaybackService.startBlock(record, event.world, x, y, z,
                        AudioCategory.MUSIC_DISC, ItemData.getTitle(record));
            } else if (shouldStopJukebox(custom, active)) {
                PlaybackService.stopAt(event.world.provider.dimensionId, x, y, z);
            }
        }
    }

    static boolean shouldStartJukebox(boolean custom, boolean active, boolean completed) {
        return custom && !active && !completed;
    }

    static boolean shouldStartJukebox(boolean custom, boolean active, boolean completed,
                                      boolean previouslyObserved, boolean wasCustom) {
        return previouslyObserved && custom && !wasCustom
                && shouldStartJukebox(custom, active, completed);
    }

    static boolean shouldStopJukebox(boolean custom, boolean active) {
        return !custom && active;
    }

    private static String jukeboxKey(int dimension, int x, int y, int z) {
        return dimension + ":" + x + ":" + y + ":" + z;
    }

    @SubscribeEvent
    public void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase == TickEvent.Phase.END) {
            ServerTaskQueue.drain();
        }
    }

    @SubscribeEvent
    public void onPlayerLogout(PlayerLoggedOutEvent event) {
        if (event.player instanceof EntityPlayerMP) {
            EntityPlayerMP player = (EntityPlayerMP) event.player;
            PlaybackService.playerLeft(player);
            PlaybackService.stopEntity(player.dimension, player.getEntityId());
            AudioTransferService.playerLeft(player.getUniqueID());
            PlayerLanguageManager.remove(player.getUniqueID());
        }
    }

    @SubscribeEvent
    public void onChunkUnload(ChunkEvent.Unload event) {
        if (!event.world.isRemote) {
            PlaybackService.stopChunk(event.world.provider.dimensionId,
                    event.getChunk().xPosition, event.getChunk().zPosition);
            Iterator<String> iterator = observedJukeboxes.keySet().iterator();
            while (iterator.hasNext()) {
                String[] coordinates = iterator.next().split(":");
                if (coordinates.length == 4 && coordinates[0].equals(
                        Integer.toString(event.world.provider.dimensionId))) {
                    int x = Integer.parseInt(coordinates[1]);
                    int z = Integer.parseInt(coordinates[3]);
                    if ((x >> 4) == event.getChunk().xPosition
                            && (z >> 4) == event.getChunk().zPosition) {
                        iterator.remove();
                    }
                }
            }
        }
    }

    @SubscribeEvent
    public void onWorldUnload(WorldEvent.Unload event) {
        if (!event.world.isRemote) {
            PlaybackService.stopDimension(event.world.provider.dimensionId);
            String prefix = event.world.provider.dimensionId + ":";
            Iterator<String> iterator = observedJukeboxes.keySet().iterator();
            while (iterator.hasNext()) {
                if (iterator.next().startsWith(prefix)) {
                    iterator.remove();
                }
            }
        }
    }

    public static void playHorn(net.minecraft.entity.player.EntityPlayer player, ItemStack stack) {
        if (!(player instanceof EntityPlayerMP) || !ItemData.isCustom(stack)
                || !ModConfig.customHornEnabled) {
            return;
        }
        EntityPlayerMP serverPlayer = (EntityPlayerMP) player;
        long currentTick = serverPlayer.worldObj.getTotalWorldTime();
        long cooldownUntil = serverPlayer.getEntityData().getLong("customdiscs_horn_cooldown");
        if (currentTick < cooldownUntil) {
            return;
        }
        if (PlaybackService.startEntity(stack, serverPlayer) != null) {
            int cooldown = Math.min(ItemData.getCooldown(stack), ModConfig.hornMaximumCooldownTicks);
            serverPlayer.getEntityData().setLong("customdiscs_horn_cooldown", currentTick + cooldown);
        }
    }
}
