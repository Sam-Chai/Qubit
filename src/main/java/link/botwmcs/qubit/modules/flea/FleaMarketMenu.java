package link.botwmcs.qubit.modules.flea;

import link.botwmcs.qubit.modules.ecohelper.data.MoneyTypesSavedData;
import link.botwmcs.qubit.registrations.MenuRegister;
import net.minecraft.ChatFormatting;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.*;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ItemLore;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class FleaMarketMenu extends AbstractContainerMenu {
    private static MenuType<FleaMarketMenu> TYPE() { return MenuRegister.FLEA_MARKET.get(); }

    @Nullable private final ServerPlayer serverPlayer; // 服务端非空
    @Nullable private final Inventory clientInv;       // 客户端非空
    private int pageIndex;
    private int pageCount = 1;
    private final List<UUID> currentIds = new ArrayList<>(FleaMarketManager.PAGE_SIZE);
    // 同步槽（原版会自动把服务端 get() 的值发给客户端，客户端 set() 接收）
    private final DataSlot dsPageIndex = new DataSlot() {
        @Override public int get() { return FleaMarketMenu.this.pageIndex; }
        @Override public void set(int v) { FleaMarketMenu.this.pageIndex = Math.max(0, v); }
    };
    private final DataSlot dsPageCount = new DataSlot() {
        @Override public int get() { return FleaMarketMenu.this.pageCount; }
        @Override public void set(int v) { FleaMarketMenu.this.pageCount = Math.max(1, v); }
    };

    /* ===== 服务端构造 ===== */
    public FleaMarketMenu(int id, ServerPlayer sp, int pageIndex) {
        super(TYPE(), id);
        this.serverPlayer = sp;
        this.clientInv = null;
        this.pageIndex = Math.max(0, pageIndex);
        recalcPageCountClamp();
        addDataSlot(dsPageIndex);
        addDataSlot(dsPageCount);
        layoutSlots();
        refreshPage();
    }

    /* ===== 客户端构造（OpenScreenPacket 走这里；buf 可能为 null）===== */
    public FleaMarketMenu(int id, Inventory inv, @Nullable FriendlyByteBuf buf) {
        super(TYPE(), id);
        this.serverPlayer = null;
        this.clientInv = inv; // 关键：存下客户端的玩家背包
        if (buf != null && buf.readableBytes() >= 2) {
            this.pageIndex = buf.readVarInt();
            this.pageCount = Math.max(1, buf.readVarInt());
        } else {
            this.pageIndex = 0;
            this.pageCount = 1;
        }
        addDataSlot(dsPageIndex);
        addDataSlot(dsPageCount);

        layoutSlots();
        // 客户端的物品展示由服务端同步（broadcastChanges），或你用 S2C payload 做首屏填充
    }

    /** 统一获取玩家背包：服务端用 ServerPlayer，客户端用从构造器传入的 Inventory */
    private Inventory playerInventory() {
        return (this.serverPlayer != null)
                ? this.serverPlayer.getInventory()
                : java.util.Objects.requireNonNull(this.clientInv, "clientInv is null on client");
    }

    private void recalcPageCountClamp() {
        int count = FleaMarketManager.get().totalPages(); // 由你的管理器提供
        this.pageCount = Math.max(1, count);
        if (this.pageIndex >= this.pageCount) {
            this.pageIndex = this.pageCount - 1;
        }
        if (this.pageIndex < 0) this.pageIndex = 0;
    }

    private void layoutSlots() {
        // 6x9 网格，从 (8,18) 开始，每格18像素，对齐大箱子布局
        int index = 0;
        for (int r = 0; r < 6; r++) {
            for (int c = 0; c < 9; c++) {
                int x = 8 + c * 18;
                int y = 18 + r * 18;
                final int slotIdx = index++;
                this.addSlot(new Slot(new SimpleContainer(1), 0, x, y) {
                    @Override public boolean mayPickup(Player p) { return false; }
                    @Override public boolean mayPlace(ItemStack s) { return false; }
                });
            }
        }
        // 玩家背包槽位（只展示/不可交互也可）：这里允许交互，方便购买后整理
        int yInv = 140;
        Inventory inv = playerInventory();
        for (int r = 0; r < 3; r++) {
            for (int c = 0; c < 9; c++) {
                this.addSlot(new Slot(inv, c + r * 9 + 9, 8 + c * 18, yInv + r * 18));
            }
        }
        for (int c = 0; c < 9; c++) {
            this.addSlot(new Slot(inv, c, 8 + c * 18, yInv + 58));
        }
    }

    public void refreshPage() {
        currentIds.clear();
        recalcPageCountClamp();
        List<FleaMarketManager.Offer> page = FleaMarketManager.get().listPage(pageIndex);

        // 填充展品（带价格 lore 与“点击购买”提示），并锁到对应槽位
        for (int i = 0; i < FleaMarketManager.PAGE_SIZE; i++) {
            FleaMarketManager.Offer offer = i < page.size() ? page.get(i) : null;
            Slot slot = this.slots.get(i);
            if (offer == null) {
                slot.set(ItemStack.EMPTY);
            } else {
                currentIds.add(offer.id);
                ItemStack display = offer.stack.copyWithCount(Math.min(offer.stack.getCount(), offer.stack.getMaxStackSize()));
                display.set(DataComponents.CUSTOM_NAME, display.getHoverName().copy().withStyle(ChatFormatting.YELLOW));
                ItemLore lore = display.getOrDefault(DataComponents.LORE, ItemLore.EMPTY);
                lore = lore.withLineAdded(Component.translatable("qubit.flea.price", offer.price).withStyle(ChatFormatting.GOLD));
                lore = lore.withLineAdded(Component.translatable("qubit.flea.hint.click_to_buy").withStyle(ChatFormatting.GRAY, ChatFormatting.ITALIC));
                display.set(DataComponents.LORE, lore);
                slot.set(display);
            }
        }

    }

    @Override
    public void clicked(int slotId, int button, ClickType clickType, Player player) {
        super.clicked(slotId, button, clickType, player);
        if (!(player instanceof ServerPlayer sp)) return;
        if (slotId < 0 || slotId >= FleaMarketManager.PAGE_SIZE) return;

        if (slotId < currentIds.size()) {
            UUID id = currentIds.get(slotId);
            boolean ok = FleaMarketManager.get().tryBuy(sp, id, MoneyTypesSavedData.DEFAULT_TYPE());
            if (ok) {
                refreshPage(); // 实时刷新当前页，去掉已售
                broadcastChanges();
            }
        }
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        // 展示槽（0..53）一律不处理
        if (index >= 0 && index < FleaMarketManager.PAGE_SIZE) {
            return ItemStack.EMPTY;
        }
        // 其余（玩家背包/快捷栏）按默认不搬运
        return ItemStack.EMPTY;
    }

    @Override
    public boolean stillValid(Player player) {
        return true;
    }

    // —— 只允许在有效范围内翻页；失败返回 true 以吞掉按钮（避免客户端重复点）——
    @Override
    public boolean clickMenuButton(Player player, int id) {
        if (!(player instanceof ServerPlayer)) return false;

        if (id == 0) { // prev
            if (this.pageIndex > 0) {
                this.pageIndex--;
                refreshPage();
                broadcastChanges();
            }
            return true;
        } else if (id == 1) { // next
            if (this.pageIndex + 1 < this.pageCount) {
                this.pageIndex++;
                refreshPage();
                broadcastChanges();
            }
            return true;
        }
        return false;
    }

    // —— 提供给 Screen 的便捷访问器（客户端可直接读到 DataSlot 同步值）——
    public int getPageIndex() { return this.pageIndex; }
    public int getPageCount() { return this.pageCount; }
}
