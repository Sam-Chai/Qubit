package link.botwmcs.qubit.modules.restarter;

import link.botwmcs.qubit.Config;
import link.botwmcs.qubit.Qubit;
import link.botwmcs.qubit.utils.restarter.Scheduler;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

@OnlyIn(Dist.DEDICATED_SERVER)
public final class AutoRestart {
    private static final int TPS = 20;

    // === 计时锚 & 周期 ===
    // 用 nanoTime 做“锚点 + 周期”的绝对调度，避免一轮轮累加带来的漂移
    private static long anchorNanos = 0L;     // 第一次武装时刻（nanoTime）
    private static long periodNanos = 0L;     // 每轮的固定时长（H:M）


    // 下一次触发点（从 anchor + n*period 推导）
    private static long nextTriggerNanos = 0L;

    // 每秒检查
    private static int tickAcc = 0;

    // 倒计时提示的去重
    private static boolean w10m, w5m, w60s, w30s, w10s;
    private static int lastSecond = -1;
    // 进入关服阶段后置 true，倒计时逻辑不再运行
    private static boolean stopping = false;
    // 记录上一次的周期，只在变化时才重置触发点
    private static long currentPeriodNanos = -1L;

    private AutoRestart() {}

    public static void ticker(MinecraftServer server) {
        if (server == null) return;
        if (!Config.AUTO_RESTART.get()) return;
        if (server.isShutdown()) return;
        if (stopping) return;
        // 首次武装（或进程刚重启）
        if (anchorNanos == 0L) {
            setupNewCycleFromNow();
            currentPeriodNanos = periodNanos;
            return;
        }

        tickAcc++;
        if (tickAcc < TPS) return;
        tickAcc = 0;

        // 期望周期
        long delaySec = Math.max(0L,
                (long) Config.RESTART_HOUR.get() * 3600L + (long) Config.RESTART_MINUTE.get() * 60L);
        long desiredPeriod = Math.max(1L, delaySec) * 1_000_000_000L;

        // ★ 只有周期真的改变时才重置触发点；否则不动 nextTriggerNanos
        if (desiredPeriod != currentPeriodNanos) {
            currentPeriodNanos = desiredPeriod;
            periodNanos = desiredPeriod;
            // 从“现在”开始一整轮（也可以换成对齐到锚点的整数倍）
            nextTriggerNanos = System.nanoTime() + periodNanos;
            resetWarns();
        }

        // 计算剩余秒（ceil，且不为负）
        long remainingNanos = nextTriggerNanos - System.nanoTime();
        int seconds = (int) Math.max(0, (remainingNanos + 999_999_999L) / 1_000_000_000L);


        // TODO: MAYBE IS NOT ZERO
        if (seconds >= 1) {
            warnIfNeeded(server, seconds);
            return;
        }

        // ===== seconds == 0：进入关服阶段 =====
        stopping = true;

        broadcast(server, Component.translatable("qubit.restarter.warn.restart")
                .withStyle(ChatFormatting.RED, ChatFormatting.BOLD));
        StopBeep(server);

        Scheduler.runLater(20, () -> {
            try {
                server.getCommands().performPrefixedCommand(server.createCommandSourceStack(), Config.RESTART_COMMAND.get());
            } catch (Throwable t) {
                t.printStackTrace();
            }
            try { server.halt(false); } catch (Throwable ignore) {}
        });

        return; // 不要再 re-arm / scheduleNextCycle




        // broadcast(server, Component.translatable("qubit.restarter.warn.restart").withStyle(ChatFormatting.RED, ChatFormatting.BOLD));


    }

    private static void setupNewCycleFromNow() {
        anchorNanos = System.nanoTime();
        long delaySec = Math.max(0L,
                (long) Config.RESTART_HOUR.get() * 3600L + (long) Config.RESTART_MINUTE.get() * 60L);
        periodNanos = Math.max(1L, delaySec) * 1_000_000_000L;
        nextTriggerNanos = anchorNanos + periodNanos;
        resetWarns();
        tickAcc = 0;
    }

    /** 调整 nextTriggerNanos，使其符合“锚点 + k*period 且 >= now”的最近一次 */
    private static void alignNextTriggerToPeriod() {
        final long now = System.nanoTime();
        if (now < anchorNanos) {
            // 极少数情况（系统休眠恢复导致单调时间基差异），重置锚点
            anchorNanos = now;
            nextTriggerNanos = anchorNanos + periodNanos;
            resetWarns();
            return;
        }
        // k = ceil((now - anchor)/period)
        long elapsed = now - anchorNanos;
        long cyclesDone = (elapsed + periodNanos - 1) / periodNanos; // 向上取整
        long candidate = anchorNanos + cyclesDone * periodNanos;
        if (candidate <= now) candidate += periodNanos;
        if (candidate != nextTriggerNanos) {
            nextTriggerNanos = candidate;
            resetWarns(); // 周期改变导致触发点变化，重置提醒
        }
    }

    private static void scheduleNextCycle() {
        final long now = System.nanoTime();
        if (periodNanos <= 0L) periodNanos = 1_000_000_000L;
        long elapsed = Math.max(0L, now - anchorNanos);
        long cyclesDone = (elapsed + periodNanos - 1) / periodNanos; // ceil
        nextTriggerNanos = anchorNanos + (cyclesDone + 1) * periodNanos;
        resetWarns();
        tickAcc = 0;
    }


    private static void warnIfNeeded(MinecraftServer server, int seconds) {
        if (seconds == 600 && !w10m) {
            w10m = true;
            broadcast(server, Component.translatable("qubit.restarter.warn.minutes", "10").withStyle(ChatFormatting.GREEN, ChatFormatting.BOLD));
            HiBeep(server);
        }
        if (seconds == 300 && !w5m) {
            w5m = true;
            broadcast(server, Component.translatable("qubit.restarter.warn.minutes", "5").withStyle(ChatFormatting.GREEN, ChatFormatting.BOLD));
            HiBeep(server);
        }
        if (seconds == 60 && !w60s) {
            w60s = true;
            broadcast(server, Component.translatable("qubit.restarter.warn.minutes", "1").withStyle(ChatFormatting.GREEN, ChatFormatting.BOLD));
            MidBeep(server);
        }
        if (seconds == 30 && !w30s) {
            w30s = true;
            broadcast(server, Component.translatable("qubit.restarter.warn.seconds", "30").withStyle(ChatFormatting.YELLOW, ChatFormatting.BOLD));
            MidBeep(server);
        }
        if (seconds == 10 && !w10s) {
            w10s = true;
            broadcast(server, Component.translatable("qubit.restarter.warn.seconds", "10").withStyle(ChatFormatting.YELLOW, ChatFormatting.BOLD));
            MidBeep(server);
        }

        if (seconds <= 6 && seconds >= 1 && lastSecond != seconds) {
            lastSecond = seconds;
            switch (seconds) {
                case 5 -> { broadcast(server, Component.translatable("qubit.restarter.warn.seconds", 5).withStyle(ChatFormatting.RED, ChatFormatting.BOLD)); HiBeep(server); }
                case 4 -> { broadcast(server, Component.translatable("qubit.restarter.warn.seconds", 4).withStyle(ChatFormatting.RED, ChatFormatting.BOLD)); MidBeep(server); }
                case 3 -> { broadcast(server, Component.translatable("qubit.restarter.warn.seconds", 3).withStyle(ChatFormatting.RED, ChatFormatting.BOLD)); MidBeep1(server); }
                case 2 -> { broadcast(server, Component.translatable("qubit.restarter.warn.seconds", 2).withStyle(ChatFormatting.RED, ChatFormatting.BOLD)); LowBeep(server); }
                case 1 -> { broadcast(server, Component.translatable("qubit.restarter.warn.seconds", 1).withStyle(ChatFormatting.RED, ChatFormatting.BOLD)); LowBeep(server); }
            }
        }
    }

    private static void broadcast(MinecraftServer server, Component component) {
        server.getPlayerList().broadcastSystemMessage(component, false);
    }

    private static void HiBeep(MinecraftServer server) {
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            Scheduler.runLater(1, () -> {
                player.playNotifySound(SoundEvents.NOTE_BLOCK_HARP.value(), SoundSource.PLAYERS, 1.0F, 3.0F);
                player.playNotifySound(SoundEvents.NOTE_BLOCK_BASS.value(), SoundSource.PLAYERS, 1.0F, 3.0F);
            });

            Scheduler.runLater(3, () -> {
                player.playNotifySound(SoundEvents.NOTE_BLOCK_HARP.value(), SoundSource.PLAYERS, 1.0F, 3.0F);
                player.playNotifySound(SoundEvents.NOTE_BLOCK_BASS.value(), SoundSource.PLAYERS, 1.0F, 3.0F);
            });
        }
    }

    private static void MidBeep(MinecraftServer server) {
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            Scheduler.runLater(1, () -> {
                player.playNotifySound(SoundEvents.NOTE_BLOCK_HARP.value(), SoundSource.PLAYERS, 1.0F, 2.0F);
                player.playNotifySound(SoundEvents.NOTE_BLOCK_BASS.value(), SoundSource.PLAYERS, 1.0F, 2.0F);
            });

            Scheduler.runLater(3, () -> {
                player.playNotifySound(SoundEvents.NOTE_BLOCK_HARP.value(), SoundSource.PLAYERS, 1.0F, 2.0F);
                player.playNotifySound(SoundEvents.NOTE_BLOCK_BASS.value(), SoundSource.PLAYERS, 1.0F, 2.0F);
            });
        }
    }

    private static void MidBeep1(MinecraftServer server) {
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            Scheduler.runLater(1, () -> {
                player.playNotifySound(SoundEvents.NOTE_BLOCK_HARP.value(), SoundSource.PLAYERS, 1.0F, 1.0F);
                player.playNotifySound(SoundEvents.NOTE_BLOCK_BASS.value(), SoundSource.PLAYERS, 1.0F, 1.0F);
            });

            Scheduler.runLater(3, () -> {
                player.playNotifySound(SoundEvents.NOTE_BLOCK_HARP.value(), SoundSource.PLAYERS, 1.0F, 3.0F);
                player.playNotifySound(SoundEvents.NOTE_BLOCK_BASS.value(), SoundSource.PLAYERS, 1.0F, 1.0F);
            });
        }
    }

    private static void LowBeep(MinecraftServer server) {
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            Scheduler.runLater(1, () -> {
                player.playNotifySound(SoundEvents.NOTE_BLOCK_HARP.value(), SoundSource.PLAYERS, 1.0F, 0.0F);
                player.playNotifySound(SoundEvents.NOTE_BLOCK_BASS.value(), SoundSource.PLAYERS, 1.0F, 0.0F);
            });

            Scheduler.runLater(3, () -> {
                player.playNotifySound(SoundEvents.NOTE_BLOCK_HARP.value(), SoundSource.PLAYERS, 1.0F, 0.0F);
                player.playNotifySound(SoundEvents.NOTE_BLOCK_BASS.value(), SoundSource.PLAYERS, 1.0F, 0.0F);
            });
        }
    }

    private static void StopBeep(MinecraftServer server) {
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            Scheduler.runLater(1, () -> {
                player.playNotifySound(SoundEvents.NOTE_BLOCK_HARP.value(), SoundSource.PLAYERS, 1.0F, 3.0F);
                player.playNotifySound(SoundEvents.NOTE_BLOCK_BASS.value(), SoundSource.PLAYERS, 1.0F, 3.0F);
            });

            Scheduler.runLater(4, () -> {
                player.playNotifySound(SoundEvents.NOTE_BLOCK_HARP.value(), SoundSource.PLAYERS, 1.0F, 2.0F);
                player.playNotifySound(SoundEvents.NOTE_BLOCK_BASS.value(), SoundSource.PLAYERS, 1.0F, 2.0F);
            });

            Scheduler.runLater(7, () -> {
                player.playNotifySound(SoundEvents.NOTE_BLOCK_HARP.value(), SoundSource.PLAYERS, 1.0F, 1.0F);
                player.playNotifySound(SoundEvents.NOTE_BLOCK_BASS.value(), SoundSource.PLAYERS, 1.0F, 2.0F);
            });

            Scheduler.runLater(10, () -> {
                player.playNotifySound(SoundEvents.NOTE_BLOCK_HARP.value(), SoundSource.PLAYERS, 1.0F, 0.0F);
                player.playNotifySound(SoundEvents.NOTE_BLOCK_BASS.value(), SoundSource.PLAYERS, 1.0F, 0.0F);
            });


        }
    }


    private static void resetWarns() {
        w10m = w5m = w60s = w30s = w10s = false;
        lastSecond = -1;
    }




}
