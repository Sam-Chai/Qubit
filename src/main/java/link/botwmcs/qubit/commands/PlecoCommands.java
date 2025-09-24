package link.botwmcs.qubit.commands;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.CommandDispatcher;
import link.botwmcs.qubit.modules.pleco.CleanupItems;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

public class PlecoCommands {
    private PlecoCommands() {}

    public static final String PERM_CLEAR = "qubit.pleco.clear";

    public static void register(CommandDispatcher<CommandSourceStack> d) {
        d.register(
                Commands.literal("pleco")
                        .requires(src -> has(src, PERM_CLEAR)) // 需要 OP 权限
                        .then(Commands.literal("clear")
                                .executes(ctx -> {
                                    CommandSourceStack src = ctx.getSource();
                                    int result = CleanupItems.runNowAndReschedule();
                                    src.sendSuccess(
                                            () -> Component.literal("Pleco 清理完成，本次处理: " + result),
                                            true
                                    );
                                    return Command.SINGLE_SUCCESS;
                                })
                        )

        );
    }

    private static boolean has(CommandSourceStack src, String node) {
        // 你可以在这里接 LuckPerms（若存在）：
        // if (LuckPermsHook.available()) return LuckPermsHook.has(src, node);
        // 兜底：控制台/命令方块/OP 允许，普通玩家默认仅允许 open
        if (!src.isPlayer()) return true;
        ServerPlayer sp = src.getPlayer();
        if (sp == null) return true;
        return sp.hasPermissions(2); // 需要 OP 等级 2+
    }
}
