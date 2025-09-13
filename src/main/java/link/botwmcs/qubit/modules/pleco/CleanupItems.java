package link.botwmcs.qubit.modules.pleco;

import link.botwmcs.qubit.Config;
import link.botwmcs.qubit.utils.restarter.Scheduler;
import net.minecraft.ChatFormatting;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Predicate;

@OnlyIn(Dist.DEDICATED_SERVER)
public final class CleanupItems {
    private CleanupItems() {}

    private static final List<Integer> WARN_SECONDS = List.of(600, 300, 60, 30, 10, 5);
    private static MinecraftServer SERVER;

    public static void bootstrap(MinecraftServer server) {
        SERVER = server;
        scheduleNextRound();
    }
    public static void scheduleNextRound() {
        if (SERVER == null) return;
        if (!Config.PLECO.get()) return;

        long periodTicks = Math.max(1, Config.PLECO_HOUR.get() * 60 * 60 * 20
                + Config.PLECO_MINUTE.get() * 60 * 20);

        long now = Scheduler.nowTick();
        long fireAt = now + periodTicks;

        // 预告
        for (int s : WARN_SECONDS) {
            long warnAt = fireAt - s * 20L;
            if (warnAt > now) {
                int delay = (int) (warnAt - now);
                Scheduler.runLater(delay, () -> {
                    broadcast(Component.translatable("qubit.pleco.warn.preparing", s).withStyle(ChatFormatting.YELLOW, ChatFormatting.BOLD));
                });
            }
        }

        // 真正清理
        Scheduler.runLater((int) periodTicks, () -> {
            int removed = cleanupAll();
            broadcast(Component.translatable("qubit.pleco.info.cleaned", removed).withStyle(ChatFormatting.YELLOW, ChatFormatting.BOLD));
            scheduleNextRound();
        });
    }

    public static int runNowAndReschedule() {
        if (SERVER == null) return 0;
        int removed = cleanupAll();
        broadcast(Component.translatable("qubit.pleco.info.cleaned", removed).withStyle(ChatFormatting.YELLOW, ChatFormatting.BOLD));
        scheduleNextRound();
        return removed;
    }

    // === 实际清理逻辑 ===
    private static int cleanupAll() {
        final boolean ignoreNamed = Config.IGNORE_NAMED_ITEMS.get();
        Set<ResourceLocation> whitelist = parseIdList(Config.WHITELIST_ITEM_IDS.get());
        Set<ResourceLocation> blacklist = parseIdList(Config.BLACKLIST_ITEM_IDS.get());

        Predicate<? super ItemEntity> filter = ie -> {
            if (!ie.isAlive()) return false;
            ItemStack stack = ie.getItem();
            if (stack.isEmpty()) return false;

            Item item = stack.getItem();
            ResourceLocation id = item.builtInRegistryHolder().key().location();

            if (!whitelist.isEmpty() && !whitelist.contains(id)) return false;
            if (blacklist.contains(id)) return false;
            if (ignoreNamed && stack.has(DataComponents.CUSTOM_NAME)) return false;

            return true;
        };

        int removed = 0;
        for (ServerLevel level : SERVER.getAllLevels()) {
            List<? extends ItemEntity> items = level.getEntities(
                    net.minecraft.world.level.entity.EntityTypeTest.forClass(ItemEntity.class),
                    filter
            );
            for (ItemEntity ie : items) {
                ie.discard();
                removed++;
            }
        }
        return removed;
    }

    private static Set<ResourceLocation> parseIdList(List<? extends String> raw) {
        Set<ResourceLocation> set = new HashSet<>();
        for (String s : raw) {
            try {
                if (s != null && !s.isBlank()) set.add(ResourceLocation.parse(s.trim()));
            } catch (Exception ignored) {}
        }
        return set;
    }

    public static void broadcast(Component msg) {
        SERVER.getPlayerList().broadcastSystemMessage(msg, false);
        for (ServerPlayer p : SERVER.getPlayerList().getPlayers()) {
            p.playNotifySound(SoundEvents.NOTE_BLOCK_BELL.value(), SoundSource.PLAYERS, 1.0F, 1.0F);
        }
    }

}
