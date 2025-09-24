package link.botwmcs.qubit;

import link.botwmcs.qubit.commands.PlecoCommands;
import link.botwmcs.qubit.modules.flea.FleaMarketMenu;
import link.botwmcs.qubit.commands.EcoCommands;
import link.botwmcs.qubit.commands.FleaCommands;
import link.botwmcs.qubit.modules.ecohelper.EcoHelper;
import link.botwmcs.qubit.modules.ecohelper.data.MoneyTypesSavedData;
import link.botwmcs.qubit.modules.ecohelper.data.WalletsSavedData;
import link.botwmcs.qubit.modules.pleco.CleanupItems;
import link.botwmcs.qubit.modules.restarter.AutoRestart;
import link.botwmcs.qubit.network.c2s.OpenFleaMenuPayload;
import link.botwmcs.qubit.registrations.MenuRegister;
import link.botwmcs.qubit.registrations.RegistryHelper;
import link.botwmcs.qubit.utils.restarter.Scheduler;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.inventory.MenuType;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.registries.DeferredRegister;
import org.slf4j.Logger;

import com.mojang.logging.LogUtils;

import net.minecraft.network.chat.Component;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.server.ServerStartingEvent;

import java.util.function.Supplier;

// The value here should match an entry in the META-INF/neoforge.mods.toml file
@Mod(Qubit.MODID)
public class Qubit {
    // Define mod id in a common place for everything to reference
    public static final String MODID = "qubit";
    // Directly reference a slf4j logger
    public static final Logger LOGGER = LogUtils.getLogger();

    // The constructor for the mod class is the first code that is run when your mod is loaded.
    // FML will recognize some parameter types like IEventBus or ModContainer and pass them in automatically.
    public Qubit(IEventBus modEventBus, ModContainer modContainer) {
        // Register the commonSetup method for modloading
        modEventBus.addListener(this::commonSetup);
        modEventBus.addListener(this::registerPayloads);

        // Register ourselves for server and other game events we are interested in.
        // Note that this is necessary if and only if we want *this* class (Qubit) to respond directly to events.
        // Do not add this line if there are no @SubscribeEvent-annotated functions in this class, like onServerStarting() below.
        NeoForge.EVENT_BUS.register(this);

        // Register our mod's ModConfigSpec so that FML can create and load the config file for us
        modContainer.registerConfig(ModConfig.Type.COMMON, Config.SPEC);

        RegistryHelper<MenuType<?>> menus = registryOf(BuiltInRegistries.MENU, modEventBus);
        MenuRegister.registerMenus(menus);
    }

    private void commonSetup(FMLCommonSetupEvent event) {
        // Some common setup code
        LOGGER.info("HELLO FROM COMMON SETUP");
    }


    // You can use SubscribeEvent and let the Event Bus discover methods to call
    @SubscribeEvent
    public void onServerStarting(ServerStartingEvent event) {
        // Do something when the server starts
        Scheduler.reset();
        if (event.getServer().isDedicatedServer()) {
            CleanupItems.bootstrap(event.getServer());
        }
    }

    @SubscribeEvent
    public void onServerTicking(ServerTickEvent.Post event) {
        Scheduler.tick();
        if (event.getServer().isDedicatedServer()) {
            AutoRestart.ticker(event.getServer());
        }
    }

    @SubscribeEvent
    public void onPlayerLoggedIn(PlayerEvent.PlayerLoggedInEvent event) {
        if (!Config.ECOHELPER.get()) return;
        if (!(event.getEntity() instanceof ServerPlayer serverPlayer)) return;

        MoneyTypesSavedData.get(serverPlayer.server).register(MoneyTypesSavedData.DEFAULT_TYPE());
        EcoHelper.ensureWallet(serverPlayer);

        final String defaultType = Config.DEFAULT_TYPE.get();
        final int basic = Config.BASIC_BAL.get();

        // 1) 确保全局币种表里有 defaultType
        var types = MoneyTypesSavedData.get(serverPlayer.server);
        if (!types.exists(defaultType)) {
            types.register(defaultType);
            types.setDirty();
        }

        // 2) 首次赠金逻辑：以“玩家钱包是否已有 defaultType”为准
        var ws = WalletsSavedData.get(serverPlayer.server);
        var wallet = ws.getOrCreate(serverPlayer.getUUID());
        boolean firstTime;
        synchronized (wallet) {
            firstTime = !wallet.hasType(defaultType);
            if (firstTime) {
                wallet.ensureType(defaultType);           // 新建该 type（余额0）
                if (basic != 0) wallet.add(defaultType, basic); // 赠金
            }
        }
        if (firstTime) ws.setDirty();

    }

    @SubscribeEvent
    public void onRegisterCommands(RegisterCommandsEvent event) {
        EcoCommands.register(event.getDispatcher());
        FleaCommands.register(event.getDispatcher());
        PlecoCommands.register(event.getDispatcher());
    }

    public void registerPayloads(RegisterPayloadHandlersEvent event) {
        var r = event.registrar(MODID);
        r.playToServer(
                OpenFleaMenuPayload.TYPE,
                OpenFleaMenuPayload.STREAM_CODEC,
                (payload, ctx ) -> {
                    var sp = ctx.player();
                    sp.getServer().execute(() -> {
                                sp.openMenu(new SimpleMenuProvider(
                                        (id, inv, p) -> new FleaMarketMenu(id, (ServerPlayer) p, payload.page()),
                                        Component.translatable("qubit.flea.title")
                                ));
                            }
                    );
                }
        );

    }

    /* ================= 内联的 RegistryHelpers 工厂 ================= */

    /** 传入原版注册表对象（如 BuiltInRegistries.ITEM / ENTITY_TYPE / DATA_SERIALIZER / CREATIVE_MODE_TAB） */
    public static <T> RegistryHelper<T> registryOf(Registry<T> vanillaRegistry, IEventBus modBus) {
        DeferredRegister<T> dr = DeferredRegister.create(vanillaRegistry, MODID);
        dr.register(modBus);
        return makeHelper(dr);
    }

    /** 传入注册表键（原版或 NeoForge 的 ResourceKey 都可） */
    public static <T> RegistryHelper<T> registryOf(ResourceKey<? extends Registry<T>> key, IEventBus modBus) {
        DeferredRegister<T> dr = DeferredRegister.create(key, MODID);
        dr.register(modBus);
        return makeHelper(dr);
    }

    /** 传入注册表名字（ResourceLocation），用于自定义/NeoForge 注册表 */
    public static <T> RegistryHelper<T> registryOf(ResourceLocation registryName, IEventBus modBus) {
        DeferredRegister<T> dr = DeferredRegister.create(registryName, MODID);
        dr.register(modBus);
        return makeHelper(dr);
    }

    private static <T> RegistryHelper<T> makeHelper(DeferredRegister<T> dr) {
        // 注意：DeferredRegister 使用 MODID 作为命名空间，所以只需传 path
        return new RegistryHelper<>() {
            @Override
            public <I extends T> Supplier<I> register(ResourceLocation id, Supplier<? extends I> sup) {
                return dr.register(id.getPath(), sup);
            }
        };
    }
}
