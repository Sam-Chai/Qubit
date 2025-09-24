package link.botwmcs.qubit.modules.ecohelper;

import link.botwmcs.qubit.modules.ecohelper.data.MoneyTypesSavedData;
import link.botwmcs.qubit.modules.ecohelper.data.WalletsSavedData;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

import javax.annotation.Nullable;
import java.util.Set;
import java.util.UUID;

public final class EcoHelper {
    private EcoHelper() {}

    public static boolean registerMoneyType(MinecraftServer server, String typeId) {
        var types = MoneyTypesSavedData.get(server);
        boolean ok = types.register(typeId);
        if (ok) types.setDirty();
        return ok;
    }

    public static boolean unregisterMoneyType(MinecraftServer server, String typeId) {
        var types = MoneyTypesSavedData.get(server);
        boolean ok = types.unregister(typeId);
        if (ok) types.setDirty();
        return ok;
    }

    public static boolean moneyTypeExists(MinecraftServer server, String typeId) {
        return MoneyTypesSavedData.get(server).exists(typeId);
    }

    public static Set<String> listMoneyTypes(MinecraftServer server) {
        return MoneyTypesSavedData.get(server).snapshot();
    }

    /* =================== 玩家钱包 =================== */
    /** 确保玩家已有钱包及默认币种 */
    public static void ensureWallet(ServerPlayer player) {
        var data = WalletsSavedData.get(player.server);
        var wallet = data.getOrCreate(player.getUUID());
        wallet.ensureType(MoneyTypesSavedData.DEFAULT_TYPE());
        data.setDirty();
    }

    /** 给玩家新增币种（余额初始 0） */
    public static boolean addMoneyType(ServerPlayer player, String typeId) {
        var server = player.server;
        if (!moneyTypeExists(server, typeId)) return false;
        var data = WalletsSavedData.get(server);
        var wallet = data.getOrCreate(player.getUUID());
        synchronized (wallet) {
            if (wallet.hasType(typeId)) return false;
            wallet.ensureType(typeId);
        }
        data.setDirty();
        return true;
    }

    /** 删除玩家的钱包中的某币种 */
    public static boolean removeMoneyType(ServerPlayer player, String typeId) {
        if (MoneyTypesSavedData.DEFAULT_TYPE().equals(typeId)) return false;
        var data = WalletsSavedData.get(player.server);
        var wallet = data.getOrCreate(player.getUUID());
        synchronized (wallet) {
            if (!wallet.hasType(typeId)) return false;
            wallet.removeType(typeId);
        }
        data.setDirty();
        return true;
    }

    /** 读取余额（不存在则视为 0） */
    public static long getBalance(ServerPlayer player, String typeId) {
        var data = WalletsSavedData.get(player.server);
        var wallet = data.getOrCreate(player.getUUID());
        synchronized (wallet) {
            wallet.ensureType(typeId); // 若币种未初始化则创建并置 0
            return wallet.get(typeId);
        }
    }

    /** 设置余额（可为负） */
    public static long setBalance(ServerPlayer player, String typeId, long amount) {
        var server = player.server;
        if (!moneyTypeExists(server, typeId)) {
            // 未注册的币种不允许写入
            throw new IllegalArgumentException("Unknown money type: " + typeId);
        }
        var data = WalletsSavedData.get(server);
        var wallet = data.getOrCreate(player.getUUID());
        synchronized (wallet) {
            wallet.set(typeId, amount);
        }
        data.setDirty();
        return amount;
    }

    /** 增减余额（返回变更后的余额） */
    public static long addBalance(ServerPlayer player, String typeId, long delta) {
        var server = player.server;
        if (!moneyTypeExists(server, typeId)) {
            throw new IllegalArgumentException("Unknown money type: " + typeId);
        }
        var data = WalletsSavedData.get(server);
        var wallet = data.getOrCreate(player.getUUID());
        long next;
        synchronized (wallet) {
            wallet.ensureType(typeId);
            next = wallet.add(typeId, delta);
        }
        data.setDirty();
        return next;
    }

    /** 列出玩家拥有的币种（仅钱包内存在的） */
    public static Set<String> listPlayerMoneyTypes(ServerPlayer player) {
        var data = WalletsSavedData.get(player.server);
        var wallet = data.getOrCreate(player.getUUID());
        synchronized (wallet) {
            return Set.copyOf(wallet.listTypes());
        }
    }

    /** 删除玩家整份钱包（例如封禁/清档） */
    public static void wipeWallet(ServerPlayer player) {
        var data = WalletsSavedData.get(player.server);
        data.remove(player.getUUID());
        data.setDirty();
    }

    public static boolean hasMoneyType(ServerPlayer player, String typeId) {
        var data = WalletsSavedData.get(player.server);
        var wallet = data.getOrCreate(player.getUUID());
        synchronized (wallet) {
            return wallet.hasType(typeId);
        }
    }

    @Nullable
    public static Long peekBalance(ServerPlayer player, String typeId) {
        var server = player.server;
        if (!moneyTypeExists(server, typeId)) return null;
        var data = WalletsSavedData.get(server);
        var wallet = data.getOrCreate(player.getUUID());
        synchronized (wallet) {
            if (!wallet.hasType(typeId)) return null;
            return wallet.get(typeId);
        }
    }

    /* ===== 离线 UUID 操作（控制台命令） ===== */

    public static long getBalance(MinecraftServer server, UUID uuid, String typeId) {
        var data = WalletsSavedData.get(server);
        var wallet = data.getOrCreate(uuid);
        synchronized (wallet) {
            wallet.ensureType(typeId);
            return wallet.get(typeId);
        }
    }

    public static long setBalance(MinecraftServer server, UUID uuid, String typeId, long amount) {
        if (!moneyTypeExists(server, typeId)) {
            throw new IllegalArgumentException("Unknown money type: " + typeId);
        }
        var data = WalletsSavedData.get(server);
        var wallet = data.getOrCreate(uuid);
        synchronized (wallet) {
            wallet.set(typeId, amount);
        }
        data.setDirty();
        return amount;
    }

    public static long addBalance(MinecraftServer server, UUID uuid, String typeId, long delta) {
        if (!moneyTypeExists(server, typeId)) {
            throw new IllegalArgumentException("Unknown money type: " + typeId);
        }
        var data = WalletsSavedData.get(server);
        var wallet = data.getOrCreate(uuid);
        long next;
        synchronized (wallet) {
            wallet.ensureType(typeId);
            next = wallet.add(typeId, delta);
        }
        data.setDirty();
        return next;
    }


}
