package link.botwmcs.qubit.modules.flea;

import link.botwmcs.qubit.modules.ecohelper.EcoHelper;
import link.botwmcs.qubit.modules.ecohelper.data.MoneyTypesSavedData;
import net.minecraft.Util;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;

import javax.annotation.Nullable;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public final class FleaMarketManager {
    private static final FleaMarketManager INSTANCE = new FleaMarketManager();
    public static FleaMarketManager get() { return INSTANCE; }

    private FleaMarketManager() {}

    public static final int ROWS = 6, COLS = 9, PAGE_SIZE = ROWS * COLS;

    private final Map<UUID, Offer> offers = new ConcurrentHashMap<>();
    private final RandomSource rng = RandomSource.create();

    // === Offer ===
    public static final class Offer {
        public final UUID id;
        public final ItemStack stack; // 原物品（服务端持有）
        public final long price;      // 价格（整数货币单位）
        public final long ts;         // 上架时间戳
        public Offer(UUID id, ItemStack stack, long price, long ts) {
            this.id = id; this.stack = stack; this.price = price; this.ts = ts;
        }
    }

    // === 定价器（可自由替换） ===
    public static final class PriceGenerator {
        public static long priceFor(ItemStack s, RandomSource rng) {
            // 基础值：按稀有度给权重
            float base = switch (s.getRarity()) {
                case COMMON -> 5f;
                case UNCOMMON -> 15f;
                case RARE -> 40f;
                case EPIC -> 100f;
            };

            // 数量系数：sqrt递减
            base *= Math.sqrt(Math.max(1, s.getCount()));

            // 耐久/附魔溢价（如果有）
            if (s.has(DataComponents.ENCHANTMENTS)) base *= 1.5f;
            if (s.isEnchanted()) base *= 1.3f;

            // 随机波动 ±20%
            float jitter = 0.8f + rng.nextFloat() * 0.4f;
            long price = Math.max(1, Math.round(base * jitter));

            // 可再按黑/白名单、物品类别做修正……
            return price;
        }
    }

    public void ingestDrop(ItemStack raw, ServerLevel level, BlockPos pos) {
        // 可在此附加一些源头信息（世界、坐标），目前不展示，仅用于未来溯源/统计
        ItemStack stored = raw.copy();
        stored.setCount(Math.min(stored.getCount(), stored.getMaxStackSize()));

        long price = PriceGenerator.priceFor(stored, rng);
        Offer offer = new Offer(UUID.randomUUID(), stored, price, Util.getMillis());
        offers.put(offer.id, offer);
    }

    public List<Offer> listPage(int pageIdx) {
        // 稳定排序：按时间倒序（新上架靠前）。根据需要改成价格、名称等
        return offers.values().stream()
                .sorted(Comparator.<Offer>comparingLong(o -> o.ts).reversed())
                .skip((long) pageIdx * PAGE_SIZE)
                .limit(PAGE_SIZE)
                .toList();
    }

    public int totalPages() {
        int size = offers.size();
        return (size + PAGE_SIZE - 1) / PAGE_SIZE;
    }

    public Optional<Offer> get(UUID id) {
        return Optional.ofNullable(offers.get(id));
    }

    /**
     * 与 EcoHelper 对接的购买逻辑。
     * @param buyer  买家
     * @param id     商品 UUID
     * @param typeId 币种（传 null 则使用 EcoHelper 的默认币种）
     */
    public boolean tryBuy(ServerPlayer buyer, UUID id, @Nullable String typeId) {
        // 1) 取币种，确保存在；确保玩家钱包初始化
        final String currency = (typeId != null && !typeId.isBlank())
                ? typeId
                : MoneyTypesSavedData.DEFAULT_TYPE(); // EcoHelper里已有该常量

        if (!EcoHelper.moneyTypeExists(buyer.server, currency)) {
            buyer.displayClientMessage(Component.literal("Unknown money type: " + currency), true);
            return false;
        }
        EcoHelper.ensureWallet(buyer);
        if (!EcoHelper.hasMoneyType(buyer, currency)) {
            // 没这个币种就给玩家钱包增加此币种（初始为0）
            EcoHelper.addMoneyType(buyer, currency);
        }

        // 2) 查找待购商品 & 余额校验
        Offer offer = offers.get(id);
        if (offer == null) {
            buyer.displayClientMessage(Component.translatable("qubit.flea.sold"), true);
            return false;
        }
        long balance = EcoHelper.getBalance(buyer, currency);
        if (balance < offer.price) {
            buyer.displayClientMessage(Component.translatable("qubit.flea.no_money"), true);
            return false;
        }

        // 3) 先做“可放入”检查，避免先扣费后失败
        if (!canFitCompletely(buyer.getInventory(), offer.stack)) {
            buyer.displayClientMessage(Component.translatable("qubit.flea.no_space"), true);
            return false;
        }

        // 4) 原子“抢购”：先从货架移除，移除失败说明被别人买了
        Offer removed = offers.remove(id);
        if (removed == null) {
            buyer.displayClientMessage(Component.translatable("qubit.flea.sold"), true);
            return false;
        }

        // 5) 扣费（失败则回滚上架）
        boolean charged = false;
        try {
            EcoHelper.addBalance(buyer, currency, -removed.price);
            charged = true;
        } catch (Throwable t) {
            // 扣费异常，物品放回市场
            offers.putIfAbsent(removed.id, removed);
            buyer.displayClientMessage(Component.literal("Payment failed: " + t.getMessage()), true);
            return false;
        }

        // 6) 发货；若极端情况下此时背包满了，做“退款+回滚”
        ItemStack toGive = removed.stack.copy();
        boolean inserted = buyer.getInventory().add(toGive);
        if (!inserted) {
            // 退款
            EcoHelper.addBalance(buyer, currency, +removed.price);
            // 物品放回市场
            offers.putIfAbsent(removed.id, removed);
            buyer.displayClientMessage(Component.translatable("qubit.flea.no_space"), true);
            return false;
        }

        // 7) 成功反馈
        buyer.level().playSound(null, buyer.blockPosition(), SoundEvents.VILLAGER_YES, SoundSource.PLAYERS, 1f, 1f);
        buyer.displayClientMessage(Component.translatable("qubit.flea.buy_ok",
                removed.stack.getHoverName(), removed.stack.getCount(), removed.price), false);
        return true;
    }

    /**
     * 仅判断“能否完整放入”玩家背包；不改变背包内容。
     * 思路：统计可堆叠空位与同类可叠加余量，判断总可容纳数量是否 >= 待放数量。
     */
    private static boolean canFitCompletely(Inventory inv, ItemStack stack) {
        if (stack.isEmpty()) return true;
        int needed = stack.getCount();

        // 先用同类栈的剩余空间
        for (int i = 0; i < inv.getContainerSize(); i++) {
            ItemStack cur = inv.getItem(i);
            if (cur.isEmpty()) continue;
            if (!ItemStack.isSameItemSameComponents(cur, stack)) continue;
            int max = Math.min(cur.getMaxStackSize(), inv.getMaxStackSize());
            int room = Math.max(0, max - cur.getCount());
            if (room > 0) {
                int used = Math.min(room, needed);
                needed -= used;
                if (needed <= 0) return true;
            }
        }
        // 再用空槽（按物品最大堆叠数）
        int perStack = Math.min(stack.getMaxStackSize(), inv.getMaxStackSize());
        for (int i = 0; i < inv.getContainerSize(); i++) {
            if (inv.getItem(i).isEmpty()) {
                int used = Math.min(perStack, needed);
                needed -= used;
                if (needed <= 0) return true;
            }
        }
        return needed <= 0;
    }

    // 暴露 rng 给外部用于即时定价（/flea add 未指定价格）
    public RandomSource rng() { return this.rng; }

    // 下架
    public boolean remove(UUID id) { return offers.remove(id) != null; }

    // 清空，返回清掉的数量
    public int clearAll() { int n = offers.size(); offers.clear(); return n; }

    public static void openFlea(ServerPlayer serverPlayer, int startPage) {
        int pageCount = Math.max(0, get().totalPages());
        MenuProvider provider = new SimpleMenuProvider((id, inv, p) -> new FleaMarketMenu(id, serverPlayer, startPage), Component.translatable("qubit.flea.title"));
        serverPlayer.openMenu(provider, buf -> {
            buf.writeVarInt(Math.max(0, startPage));
            buf.writeVarInt(pageCount);
        });
    }



}
