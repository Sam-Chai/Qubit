package link.botwmcs.qubit.utils.restarter;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * 极简 tick 级延时调度器：在主线程每 tick 调用 {@link #tick()}，用 {@link #runLater} 延迟执行任务。
 * 线程不安全，默认用于服务端主线程（Minecraft 的 ServerTick）。
 */
public final class Scheduler {
    private static long tickCounter = 0L;

    public static final class Handle {
        private volatile boolean cancelled = false;
        /** 取消任务（仅在尚未触发前有效） */
        public void cancel() { this.cancelled = true; }
        public boolean isCancelled() { return cancelled; }
    }

    private record Task(long dueTick, Runnable job, Handle handle) {}

    /** 主队列：等待执行的任务 */
    private static final List<Task> QUEUE = new ArrayList<>();
    /** 暂存队列：在一次 tick 执行过程中产生的新任务 */
    private static final List<Task> PENDING = new ArrayList<>();
    /** 标记当前是否处于 tick 遍历中 */
    private static boolean ticking = false;

    private Scheduler() {}

    /**
     * 安排在 {@code ticks} 个 tick 之后运行 {@code job}。
     * @return 可选的取消句柄
     */
    public static Handle runLater(int ticks, Runnable job) {
        if (ticks < 1) ticks = 1;
        Handle h = new Handle();
        Task task = new Task(tickCounter + ticks, job, h);
        if (ticking) {
            // 正在遍历：先放到 PENDING，避免修改 QUEUE 触发 CME
            PENDING.add(task);
        } else {
            QUEUE.add(task);
        }
        return h;
    }

    /** 每个 ServerTick 调用一次推进调度器（在 ServerTickEvent.Post 中调用） */
    public static void tick() {
        tickCounter++;
        if (QUEUE.isEmpty() && PENDING.isEmpty()) return;

        // 本轮开始：合并上轮遗留的 PENDING（通常为空）
        if (!PENDING.isEmpty()) {
            QUEUE.addAll(PENDING);
            PENDING.clear();
        }

        ticking = true;
        try {
            // 用迭代器遍历 + 安全删除；本轮产生的新任务进 PENDING，不影响 QUEUE 结构
            Iterator<Task> it = QUEUE.iterator();
            while (it.hasNext()) {
                Task t = it.next();
                if (t.handle.cancelled) { it.remove(); continue; }
                if (t.dueTick <= tickCounter) {
                    try { t.job.run(); } catch (Throwable ex) { ex.printStackTrace(); }
                    it.remove();
                }
            }
        } finally {
            ticking = false;
        }

        // 一次性合并本轮产生的新任务
        if (!PENDING.isEmpty()) {
            QUEUE.addAll(PENDING);
            PENDING.clear();
        }
    }

    /** 清空所有尚未执行的任务并把计数归零（例如服务器启动/重载时可调用） */
    public static void reset() {
        tickCounter = 0L;
        QUEUE.clear();
        PENDING.clear();
        ticking = false;
    }

    /** 当前调度器内部的“逻辑 tick”值（偶尔用来排程相对时间） */
    public static long nowTick() { return tickCounter; }


}
