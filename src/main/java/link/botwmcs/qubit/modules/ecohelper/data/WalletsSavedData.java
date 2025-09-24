package link.botwmcs.qubit.modules.ecohelper.data;

import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;
import link.botwmcs.qubit.modules.ecohelper.PlayerWallet;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.Map;
import java.util.UUID;

public final class WalletsSavedData extends SavedData {
    public static final String STORAGE_NAME = "ecohelper_wallets";
    private final Object2ObjectOpenHashMap<UUID, PlayerWallet> wallets = new Object2ObjectOpenHashMap<>();

    public WalletsSavedData() {}

    public synchronized PlayerWallet getOrCreate(UUID uuid) {
        return wallets.computeIfAbsent(uuid, u -> new PlayerWallet());
    }

    public synchronized PlayerWallet get(UUID uuid) {
        return wallets.get(uuid);
    }

    public synchronized void remove(UUID uuid) {
        wallets.remove(uuid);
        setDirty();
    }

    /** 当前缓存中的玩家数（可选） */
    public synchronized int size() {
        return wallets.size();
    }


    /* --------------------- 存取入口 --------------------- */
    public static WalletsSavedData get(MinecraftServer server) {
        ServerLevel level = server.overworld();
        return level.getDataStorage().computeIfAbsent(
                new SavedData.Factory<>(
                        WalletsSavedData::new,   // Supplier<T>
                        WalletsSavedData::load   // (CompoundTag, HolderLookup.Provider) -> T
                ),
                STORAGE_NAME
        );    }

    /* --------------------- NBT 序列化 --------------------- */
    public static WalletsSavedData load(CompoundTag tag, HolderLookup.Provider provider) {
        WalletsSavedData data = new WalletsSavedData();
        ListTag list = tag.getList("players", Tag.TAG_COMPOUND);
        for (int i = 0; i < list.size(); i++) {
            CompoundTag entry = list.getCompound(i);
            UUID uuid = entry.getUUID("uuid");
            CompoundTag wtag = entry.getCompound("wallet");
            // PlayerWallet.load(CompoundTag)
            PlayerWallet wallet = PlayerWallet.load(wtag);
            data.wallets.put(uuid, wallet);
        }
        return data;
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider provider) {
        ListTag list = new ListTag();
        for (Map.Entry<UUID, PlayerWallet> e : wallets.entrySet()) {
            CompoundTag entry = new CompoundTag();
            entry.putUUID("uuid", e.getKey());
            entry.put("wallet", e.getValue().save());
            list.add(entry);
        }
        tag.put("players", list);
        return tag;

    }
}
