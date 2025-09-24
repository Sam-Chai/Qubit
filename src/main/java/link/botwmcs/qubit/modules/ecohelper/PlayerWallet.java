package link.botwmcs.qubit.modules.ecohelper;

import it.unimi.dsi.fastutil.objects.Object2LongOpenHashMap;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;

import java.util.Map;
import java.util.Set;

public final class PlayerWallet {
    // 币种 -> 余额（用long承载，外部自行定义最小单位；也可改成BigInteger转字符串存储）
    private final Object2LongOpenHashMap<String> balances = new Object2LongOpenHashMap<>();

    public PlayerWallet() {}

    public synchronized long get(String moneyType) {
        return balances.getOrDefault(moneyType, 0L);
    }

    public synchronized void set(String moneyType, long amount) {
        balances.put(moneyType, amount);
    }

    public synchronized long add(String moneyType, long delta) {
        long now = get(moneyType);
        long next = now + delta;
        balances.put(moneyType, next);
        return next;
    }

    public synchronized boolean hasType(String moneyType) {
        return balances.containsKey(moneyType);
    }

    public synchronized void ensureType(String moneyType) {
        balances.putIfAbsent(moneyType, 0L);
    }

    public synchronized void removeType(String moneyType) {
        balances.removeLong(moneyType);
    }

    public synchronized Set<String> listTypes() {
        return balances.keySet();
    }

    /* ===================== NBT 序列化 ===================== */

    public CompoundTag save() {
        CompoundTag root = new CompoundTag();
        ListTag list = new ListTag();
        for (Map.Entry<String, Long> e : balances.object2LongEntrySet()) {
            CompoundTag eTag = new CompoundTag();
            eTag.putString("type", e.getKey());
            eTag.putLong("amount", e.getValue());
            list.add(eTag);
        }
        root.put("entries", list);
        return root;
    }

    public static PlayerWallet load(CompoundTag tag) {
        PlayerWallet w = new PlayerWallet();
        ListTag list = tag.getList("entries", Tag.TAG_COMPOUND);
        for (int i = 0; i < list.size(); i++) {
            CompoundTag e = list.getCompound(i);
            String type = e.getString("type");
            long amount = e.getLong("amount");
            w.set(type, amount);
        }
        return w;
    }
}
