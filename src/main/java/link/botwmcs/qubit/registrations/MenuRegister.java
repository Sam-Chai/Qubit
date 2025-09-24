package link.botwmcs.qubit.registrations;

import link.botwmcs.qubit.Qubit;
import link.botwmcs.qubit.modules.flea.FleaMarketMenu;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.inventory.MenuType;
import net.neoforged.neoforge.common.extensions.IMenuTypeExtension;

import java.util.function.Supplier;

/**
 * 封装 MenuType 的注册：兼容 NeoForge 1.21.1（使用 IMenuTypeExtension#create）。
 *
 * 用法示例：
 *   MenuRegister menus = new MenuRegister(registries.menus()); // 你的 RegistryHelper<MenuType<?>>
 *   Supplier<MenuType<MyMenu>> MY_MENU = menus.register(
 *       new ResourceLocation(MODID, "my_menu"),
 *       (windowId, inv, buf) -> new MyMenu(windowId, inv, buf)
 *   );
 *
 * 若客户端构造不需要 buf：
 *   Supplier<MenuType<MyMenu>> MY_MENU = menus.registerSimple(
 *       new ResourceLocation(MODID, "my_menu"),
 *       (windowId, inv) -> new MyMenu(windowId, inv, null)
 *   );
 */
public final class MenuRegister {
    public static Supplier<MenuType<FleaMarketMenu>> FLEA_MARKET;
    public static final ResourceLocation FLEA_ID = ResourceLocation.fromNamespaceAndPath(Qubit.MODID, "flea_market");

    public static void registerMenus(RegistryHelper<MenuType<?>> helper) {
        FLEA_MARKET = helper.register(FLEA_ID, () -> IMenuTypeExtension.create((windowId, inv, buf) -> new FleaMarketMenu(windowId, inv, buf)));
    }

    private MenuRegister() {}


}
