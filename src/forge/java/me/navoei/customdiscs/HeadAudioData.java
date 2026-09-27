package me.navoei.customdiscs;

import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.world.World;
import net.minecraft.world.WorldSavedData;
import net.minecraft.world.storage.MapStorage;

import java.util.HashMap;
import java.util.Map;

public final class HeadAudioData extends WorldSavedData {
    private static final String DATA_NAME = "CustomDiscsHeads";
    private final Map<String, NBTTagCompound> entries = new HashMap<String, NBTTagCompound>();

    public HeadAudioData() {
        super(DATA_NAME);
    }

    public HeadAudioData(String name) {
        super(name);
    }

    public static HeadAudioData get(World world) {
        MapStorage storage = world.perWorldStorage;
        HeadAudioData data = (HeadAudioData) storage.loadData(HeadAudioData.class, DATA_NAME);
        if (data == null) {
            data = new HeadAudioData();
            storage.setData(DATA_NAME, data);
        }
        return data;
    }

    public void put(int x, int y, int z, NBTTagCompound customData) {
        entries.put(key(x, y, z), (NBTTagCompound) customData.copy());
        markDirty();
    }

    public NBTTagCompound get(int x, int y, int z) {
        NBTTagCompound data = entries.get(key(x, y, z));
        return data == null ? null : (NBTTagCompound) data.copy();
    }

    public void remove(int x, int y, int z) {
        if (entries.remove(key(x, y, z)) != null) {
            markDirty();
        }
    }

    @Override
    public void readFromNBT(NBTTagCompound tag) {
        entries.clear();
        NBTTagList list = tag.getTagList("heads", 10);
        for (int i = 0; i < list.tagCount(); i++) {
            NBTTagCompound entry = list.getCompoundTagAt(i);
            entries.put(entry.getString("position"), entry.getCompoundTag("data"));
        }
    }

    @Override
    public void writeToNBT(NBTTagCompound tag) {
        NBTTagList list = new NBTTagList();
        for (Map.Entry<String, NBTTagCompound> entry : entries.entrySet()) {
            NBTTagCompound value = new NBTTagCompound();
            value.setString("position", entry.getKey());
            value.setTag("data", entry.getValue());
            list.appendTag(value);
        }
        tag.setTag("heads", list);
    }

    private String key(int x, int y, int z) {
        return x + "," + y + "," + z;
    }
}
