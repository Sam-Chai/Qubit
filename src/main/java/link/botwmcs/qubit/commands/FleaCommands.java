package link.botwmcs.qubit.commands;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import link.botwmcs.qubit.modules.flea.FleaMarketMenu;
import link.botwmcs.qubit.modules.flea.FleaMarketManager;
import link.botwmcs.qubit.utils.flea.RandomItemGenerator;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.commands.arguments.coordinates.Vec3Argument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.UUID;

public class FleaCommands {
    private FleaCommands() {}

    // ==== 权限节点（供 LuckPerms 配置） ====
    public static final String PERM_OPEN          = "qubit.flea.open";
    public static final String PERM_OPEN_OTHERS   = "qubit.flea.admin.open.others";
    public static final String PERM_ADD           = "qubit.flea.admin.add";
    public static final String PERM_REMOVE        = "qubit.flea.admin.remove";
    public static final String PERM_CLEAR         = "qubit.flea.admin.clear";
    public static final String PERM_LIST          = "qubit.flea.admin.list";
    public static final String PREM_GEN           = "qubit.flea.admin.generator";

    public static void register(CommandDispatcher<CommandSourceStack> d) {
        d.register(Commands.literal("flea")

                // /flea open [page]
                .then(Commands.literal("open")
                        .requires(src -> has(src, PERM_OPEN) && src.isPlayer())
                        .executes(ctx -> openSelf(ctx, 0))
                        .then(Commands.argument("page", IntegerArgumentType.integer(0))
                                .executes(ctx -> openSelf(ctx, IntegerArgumentType.getInteger(ctx, "page"))))
                        // /flea open <player> [page]
                        .then(Commands.argument("player", EntityArgument.player())
                                .requires(src -> has(src, PERM_OPEN_OTHERS))
                                .executes(ctx -> openOther(ctx, 0))
                                .then(Commands.argument("page", IntegerArgumentType.integer(0))
                                        .executes(ctx -> openOther(ctx, IntegerArgumentType.getInteger(ctx, "page")))))
                )

                // /flea add [price] [count]
                .then(Commands.literal("add")
                        .requires(src -> has(src, PERM_ADD) && src.isPlayer())
                        .executes(ctx -> addFromHand(ctx, null, null))
                        .then(Commands.argument("price", IntegerArgumentType.integer(1))
                                .executes(ctx -> addFromHand(ctx, IntegerArgumentType.getInteger(ctx, "price"), null))
                                .then(Commands.argument("count", IntegerArgumentType.integer(1))
                                        .executes(ctx -> addFromHand(ctx,
                                                IntegerArgumentType.getInteger(ctx, "price"),
                                                IntegerArgumentType.getInteger(ctx, "count")))))
                )

                // /flea remove <uuid>
                .then(Commands.literal("remove")
                        .requires(src -> has(src, PERM_REMOVE) && src.isPlayer())
                        .then(Commands.argument("uuid", StringArgumentType.word())
                                .executes(ctx -> removeById(ctx, StringArgumentType.getString(ctx, "uuid"))))
                )

                // /flea clear
                .then(Commands.literal("clear")
                        .requires(src -> has(src, PERM_CLEAR))
                        .executes(FleaCommands::clearAll)
                )

                // /flea list [page]
                .then(Commands.literal("list")
                        .requires(src -> has(src, PERM_LIST))
                        .executes(ctx -> listPage(ctx, 0))
                        .then(Commands.argument("page", IntegerArgumentType.integer(0))
                                .executes(ctx -> listPage(ctx, IntegerArgumentType.getInteger(ctx, "page"))))
                )

                // /flea info <uuid>
                .then(Commands.literal("info")
                        .requires(src -> has(src, PERM_LIST))
                        .then(Commands.argument("uuid", StringArgumentType.word())
                                .executes(ctx -> info(ctx, StringArgumentType.getString(ctx, "uuid"))))
                )

                .then(Commands.literal("generator")
                        .requires(src -> has(src, PREM_GEN))
                        .then(Commands.argument("count", IntegerArgumentType.integer(1))
                                .then(Commands.argument("pos", Vec3Argument.vec3())
                                        .executes(ctx -> {
                                            CommandSourceStack src = ctx.getSource();
                                            int count = IntegerArgumentType.getInteger(ctx, "count");
                                            Vec3 pos = Vec3Argument.getVec3(ctx, "pos");

                                            if (!(src.getLevel() instanceof ServerLevel level)) {
                                                src.sendFailure(Component.literal("只能在服务端执行该命令。"));
                                                return 0;
                                            }

                                            RandomItemGenerator.start(level, pos, count);
                                            src.sendSuccess(() -> Component.literal(
                                                    "生成器已启动：位置 " + fmt(pos) + "，每 tick 1 个，共 " + count + " 个。"), true);
                                            return Command.SINGLE_SUCCESS;
                                        })
                                )
                        )
                )

        );
    }

    // ===== handlers =====
    private static int openSelf(CommandContext<CommandSourceStack> ctx, int page) {
        ServerPlayer sp = ctx.getSource().getPlayer();
        if (sp == null) {
            ctx.getSource().sendFailure(Component.literal("This subcommand can only be used by a player."));
            return 0;
        }

        sp.openMenu(new SimpleMenuProvider((id, inv, p) -> new FleaMarketMenu(id, sp, page),
                Component.translatable("qubit.flea.title")),
                buf -> buf.writeVarInt(page)
        );

        ctx.getSource().sendSuccess(() -> Component.literal("Opened flea market (page " + page + ")"), false);
        return 1;
    }

    private static int openOther(CommandContext<CommandSourceStack> ctx, int page) {
        ServerPlayer target;
        try {
            target = EntityArgument.getPlayer(ctx, "player"); // 若不在线/不匹配，这里会抛异常
        } catch (CommandSyntaxException e) {
            ctx.getSource().sendFailure(Component.literal(e.getRawMessage().getString()));
            return 0;
        }
        target.openMenu(new SimpleMenuProvider(
                (id, inv, p) -> new FleaMarketMenu(id, target, page),
                Component.translatable("qubit.flea.title")
        ));
        ctx.getSource().sendSuccess(() -> Component.literal("Opened for 1 player, page " + page), true);
        return 1;

    }

    private static int addFromHand(CommandContext<CommandSourceStack> ctx, Integer priceMaybe, Integer countMaybe) {
        ServerPlayer sp = ctx.getSource().getPlayer();
        if (sp == null) {
            ctx.getSource().sendFailure(Component.literal("This subcommand can only be used by a player."));
            return 0;
        }

        ItemStack hand = sp.getMainHandItem();
        if (hand.isEmpty()) {
            ctx.getSource().sendFailure(Component.literal("Empty hand."));
            return 0;
        }

        int count = (countMaybe == null) ? hand.getCount() : Math.min(countMaybe, hand.getCount());
        if (count <= 0) {
            ctx.getSource().sendFailure(Component.literal("Invalid count."));
            return 0;
        }

        // 拷贝要上架的物品
        ItemStack copied = hand.copy();
        copied.setCount(count);

        // 价格：若未指定，走 Flea 的自动定价
        long price = (priceMaybe == null)
                ? FleaMarketManager.PriceGenerator.priceFor(copied, FleaMarketManager.get().rng())
                : priceMaybe.longValue();

        // 上架
        FleaMarketManager.get().ingestDrop(copied, sp.serverLevel(), sp.blockPosition());

        // 从玩家手上扣除对应数量
        hand.shrink(count);

        ctx.getSource().sendSuccess(() ->
                Component.literal("Listed: ").append(copied.getHoverName())
                        .append(" x" + count + " @ " + price), true);
        return 1;
    }

    private static int removeById(CommandContext<CommandSourceStack> ctx, String uuidStr) {
        UUID id;
        try {
            id = UUID.fromString(uuidStr);
        } catch (IllegalArgumentException e) {
            ctx.getSource().sendFailure(Component.literal("Bad UUID: " + uuidStr));
            return 0;
        }
        var opt = FleaMarketManager.get().get(id);
        if (opt.isEmpty()) {
            ctx.getSource().sendFailure(Component.literal("No such offer."));
            return 0;
        }
        // 直接移除
        FleaMarketManager.get().remove(id);
        ctx.getSource().sendSuccess(() -> Component.literal("Removed: " + id), true);
        return 1;
    }

    private static int clearAll(CommandContext<CommandSourceStack> ctx) {
        int n = FleaMarketManager.get().clearAll();
        ctx.getSource().sendSuccess(() -> Component.literal("Cleared " + n + " offer(s)."), true);
        return n;
    }

    private static int listPage(CommandContext<CommandSourceStack> ctx, int page) {
        var mgr = FleaMarketManager.get();
        var list = mgr.listPage(page);
        int pages = Math.max(1, mgr.totalPages());

        DateTimeFormatter fmt = DateTimeFormatter.ofPattern("MM-dd HH:mm:ss").withZone(ZoneId.systemDefault());
        ctx.getSource().sendSuccess(() ->
                        Component.literal("Flea Market Page " + page + "/" + (pages - 1) + " (" + list.size() + " items)"),
                false);

        for (FleaMarketManager.Offer o : list) {
            String when = fmt.format(Instant.ofEpochMilli(o.ts));
            String line = String.format(Locale.ROOT, "%s | %s x%d | %d | %s",
                    o.id, o.stack.getDisplayName().getString(), o.stack.getCount(), o.price, when);
            ctx.getSource().sendSuccess(() -> Component.literal(line), false);
        }
        return list.size();
    }

    private static int info(CommandContext<CommandSourceStack> ctx, String uuidStr) {
        UUID id;
        try {
            id = UUID.fromString(uuidStr);
        } catch (IllegalArgumentException e) {
            ctx.getSource().sendFailure(Component.literal("Bad UUID: " + uuidStr));
            return 0;
        }
        var opt = FleaMarketManager.get().get(id);
        if (opt.isEmpty()) {
            ctx.getSource().sendFailure(Component.literal("No such offer."));
            return 0;
        }
        FleaMarketManager.Offer o = opt.get();
        DateTimeFormatter fmt = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss").withZone(ZoneId.systemDefault());
        ctx.getSource().sendSuccess(() -> Component.literal("UUID: " + o.id), false);
        ctx.getSource().sendSuccess(() -> Component.literal("Item: ").append(o.stack.getHoverName()), false);
        ctx.getSource().sendSuccess(() -> Component.literal("Count: " + o.stack.getCount()), false);
        ctx.getSource().sendSuccess(() -> Component.literal("Price: " + o.price), false);
        ctx.getSource().sendSuccess(() -> Component.literal("Since: " + fmt.format(Instant.ofEpochMilli(o.ts))), false);
        return 1;
    }

    private static boolean has(CommandSourceStack src, String node) {
        // 你可以在这里接 LuckPerms（若存在）：
        // if (LuckPermsHook.available()) return LuckPermsHook.has(src, node);
        // 兜底：控制台/命令方块/OP 允许，普通玩家默认仅允许 open
        if (!src.isPlayer()) return true;
        ServerPlayer sp = src.getPlayer();
        if (sp == null) return true;
        if (node.equals(PERM_OPEN)) return true; // 玩家默认能打开
        return sp.hasPermissions(2); // 需要 OP 等级 2+
    }

    private static String fmt(Vec3 v) { return String.format("(%.2f, %.2f, %.2f)", v.x, v.y, v.z); }



}
