package link.botwmcs.qubit.utils.flea;

import link.botwmcs.qubit.utils.restarter.Scheduler;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.Vec3;

import java.util.Optional;

public final class RandomItemGenerator {
    private RandomItemGenerator() {
    }

    /**
     * 启动一个逐 tick 产出会话：从下个 tick 开始，每 tick 1 个，共 count 个。
     */
    public static void start(ServerLevel level, Vec3 pos, int count) {
        if (count <= 0) return;
        new Session(level, pos, count).scheduleNext(); // 自排程
    }

    private static final class Session {
        private final ServerLevel level;
        private final Vec3 pos;
        private int remain;

        Session(ServerLevel level, Vec3 pos, int count) {
            this.level = level;
            this.pos = pos;
            this.remain = count;
        }

        void scheduleNext() {
            if (remain <= 0 || !level.getServer().isRunning()) return;
            // 延迟 1 tick 执行一次
            Scheduler.runLater(1, () -> {
                if (remain <= 0 || !level.getServer().isRunning()) return;

                ItemStack stack = randomItemStack(level);
                if (!stack.isEmpty()) {
                    ItemEntity ie = new ItemEntity(level, pos.x, pos.y, pos.z, stack);
                    ie.setDefaultPickUpDelay();

                    RandomSource r = level.random;
                    ie.setDeltaMovement(
                            (r.nextDouble() - 0.5) * 0.2,
                            0.2 + r.nextDouble() * 0.2,
                            (r.nextDouble() - 0.5) * 0.2
                    );
                    level.addFreshEntity(ie);
                }

                remain--;
                if (remain > 0) {
                    scheduleNext(); // 继续下一 tick
                }
            });
        }
    }

    private static ItemStack randomItemStack(ServerLevel level) {
        RandomSource rand = level.random;
        for (int i = 0; i < 20; i++) {
            Optional<Holder.Reference<Item>> opt = level.registryAccess()
                    .registryOrThrow(Registries.ITEM)
                    .getRandom(rand);
            if (opt.isEmpty()) break;
            Item item = opt.get().value();
            if (item == Items.AIR) continue;
            // 如需随机堆叠量，可替换为 1 + rand.nextInt(4)
            return new ItemStack(item, 1);
        }
        return ItemStack.EMPTY;
    }


}
