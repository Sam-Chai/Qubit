package link.botwmcs.qubit.modules.ecohelper.data;

import it.unimi.dsi.fastutil.objects.ObjectOpenHashSet;
import link.botwmcs.qubit.Config;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.Collections;
import java.util.Set;

public final class MoneyTypesSavedData extends SavedData {
    public static final String STORAGE_NAME = "ecohelper_money_types";
//    public static final String DEFAULT_TYPE = Config.DEFAULT_TYPE.get();
    public static String DEFAULT_TYPE() {
        return Config.DEFAULT_TYPE.get();
    }

    private final ObjectOpenHashSet<String> types = new ObjectOpenHashSet<>();

    public MoneyTypesSavedData() {
        // 确保默认币种永远存在
        types.add(DEFAULT_TYPE());
    }

    public synchronized boolean register(String type) {
        boolean added = types.add(type);
        if (added) setDirty();
        return added;
    }

    public synchronized boolean unregister(String type) {
        if (DEFAULT_TYPE().equals(type)) return false; // 默认币种不可删除
        boolean removed = types.remove(type);
        if (removed) setDirty();
        return removed;
    }

    public synchronized boolean exists(String type) {
        return types.contains(type);
    }

    public synchronized Set<String> snapshot() {
        return Collections.unmodifiableSet(new ObjectOpenHashSet<>(types));
    }

    /* ------------------ 存取入口 ------------------ */
    public static MoneyTypesSavedData get(MinecraftServer server) {
        ServerLevel level = server.overworld();
        return level.getDataStorage().computeIfAbsent(
                new SavedData.Factory<>(
                        MoneyTypesSavedData::new,   // Supplier<T>
                        MoneyTypesSavedData::load   // (CompoundTag, HolderLookup.Provider) -> T
                ),
                STORAGE_NAME
        );
    }

    /* ------------------ NBT 序列化 ------------------ */
    public static MoneyTypesSavedData load(CompoundTag tag, HolderLookup.Provider provider) {
        MoneyTypesSavedData data = new MoneyTypesSavedData();
        data.types.clear();
        ListTag list = tag.getList("types", Tag.TAG_STRING);
        for (int i = 0; i < list.size(); i++) data.types.add(list.getString(i));
        data.types.add(DEFAULT_TYPE());
        return data;

    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider provider) {
        ListTag list = new ListTag();
        for (String t : types) {
            list.add(StringTag.valueOf(t));
        }
        tag.put("types", list);
        return tag;

    }

}
