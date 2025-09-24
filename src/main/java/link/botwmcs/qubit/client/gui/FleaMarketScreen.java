package link.botwmcs.qubit.client.gui;

import link.botwmcs.qubit.modules.flea.FleaMarketMenu;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;

public class FleaMarketScreen extends AbstractContainerScreen<FleaMarketMenu> {
    /** 原版大箱子（54格）背景 */
    private static final ResourceLocation BG = ResourceLocation.withDefaultNamespace("textures/gui/container/generic_54.png");
    private Button prevBtn, nextBtn;

    /** 6 行容器：宽 176，高 114 + 6*18 = 222（与原版一致） */
    public FleaMarketScreen(FleaMarketMenu menu, Inventory playerInv, Component title) {
        super(menu, playerInv, title);
        this.imageWidth  = 176;
        this.imageHeight = 222;
        // 标题与玩家背包标题默认会在 renderLabels 里绘制
        this.inventoryLabelY = this.imageHeight - 94; // 原版常量
    }

    @Override
    protected void init() {
        super.init(); // 计算 leftPos/topPos

        // 上一页 / 下一页 按钮（用原版容器按钮通道下发到服务端）
        final int btnY = this.topPos + 4;
        this.prevBtn = this.addRenderableWidget(Button.builder(Component.literal("«"), b -> clickMenuButton(0))
                .bounds(this.leftPos + this.imageWidth - 27 - 27, btnY, 20, 12).build());
        this.nextBtn = this.addRenderableWidget(Button.builder(Component.literal("»"), b -> clickMenuButton(1))
                .bounds(this.leftPos + this.imageWidth - 27, btnY, 20, 12).build());
        updateButtons();
    }

    private void clickMenuButton(int id) {
        // 通过原版 packet：ServerboundContainerButtonClickPacket
        Minecraft.getInstance().gameMode.handleInventoryButtonClick(this.menu.containerId, id);
    }

    private void updateButtons() {
        int pi = this.menu.getPageIndex();
        int pc = Math.max(1, this.menu.getPageCount());
        this.prevBtn.active = (pi > 0);
        this.nextBtn.active = (pi + 1 < pc);
        this.prevBtn.setTooltip(Tooltip.create(Component.translatable("qubit.flea.prev_page")));
        this.nextBtn.setTooltip(Tooltip.create(Component.translatable("qubit.flea.next_page")));
    }

    @Override
    protected void containerTick() {
        super.containerTick();
        // DataSlot 在每次同步后会更新本地字段，这里每 tick 校正按钮状态
        updateButtons();
    }

    @Override
    protected void renderBg(GuiGraphics guiGraphics, float v, int i, int i1) {
        int x = (this.width - this.imageWidth) / 2;
        int y = (this.height - this.imageHeight) / 2;
        guiGraphics.blit(BG, this.leftPos, this.topPos, 0, 0, this.imageWidth, 6 * 18 + 17); // 上半部分（6行容器）
        guiGraphics.blit(BG, this.leftPos, this.topPos + 6 * 18 + 17, 0, 6 * 18 + 17, this.imageWidth, 96); // 玩家背包区域
    }

    @Override
    protected void renderLabels(GuiGraphics g, int mouseX, int mouseY) {
        // 标题与玩家背包标题（与大箱子一致）
        g.drawString(this.font, this.title, 8, 6, 0x404040, false);
        g.drawString(this.font, this.playerInventoryTitle, 8, this.inventoryLabelY, 0x404040, false);

        int pi = this.menu.getPageIndex();
        int pc = Math.max(1, this.menu.getPageCount());
        String pageStr = (pi + 1) + " / " + pc;
        // 右上角展示页码
        int w = this.font.width(pageStr);
        g.drawString(this.font, pageStr, this.imageWidth - 8 - w, 6, 0x8B8B8B, false);    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partial) {
        super.render(g, mouseX, mouseY, partial);
        this.renderTooltip(g, mouseX, mouseY);
    }
}
