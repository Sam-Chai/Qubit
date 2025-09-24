package link.botwmcs.qubit.commands;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.LongArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import com.sun.jdi.connect.Connector;
import link.botwmcs.qubit.modules.ecohelper.EcoHelper;
import link.botwmcs.qubit.modules.ecohelper.data.MoneyTypesSavedData;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

import javax.swing.text.html.CSS;
import java.util.Set;
import java.util.stream.Collectors;

public class EcoCommands {
    private EcoCommands() {}

    private static final SuggestionProvider<CommandSourceStack> SUGGEST_MONEY_TYPES = (ctx, builder) -> {
        Set<String> all = EcoHelper.listMoneyTypes(ctx.getSource().getServer());
        return SharedSuggestionProvider.suggest(all, builder);
    };
    private static final SuggestionProvider<CommandSourceStack> SUGGEST_PLAYER_TYPES = (ctx, builder) -> {
        try {
            ServerPlayer sp = EntityArgument.getPlayer(ctx, "player");
            Set<String> have = EcoHelper.listPlayerMoneyTypes(sp);
            return SharedSuggestionProvider.suggest(have, builder);
        } catch (Exception ignore) {
            // 在 <player> 还没填好的时候，降级为全局建议
            Set<String> all = EcoHelper.listMoneyTypes(ctx.getSource().getServer());
            return SharedSuggestionProvider.suggest(all, builder);
        }
    };
    // 全局注册但该玩家还没有的币种（用于 /wallet types add）
    private static final SuggestionProvider<CommandSourceStack> SUGGEST_MISSING_TYPES_FOR_PLAYER =
            (ctx, builder) -> {
                try {
                    ServerPlayer sp = EntityArgument.getPlayer(ctx, "player");
                    Set<String> all = EcoHelper.listMoneyTypes(ctx.getSource().getServer());
                    Set<String> have = EcoHelper.listPlayerMoneyTypes(sp);
                    Set<String> missing = all.stream()
                            .filter(t -> !have.contains(t))
                            .collect(Collectors.toSet());
                    return SharedSuggestionProvider.suggest(missing, builder);
                } catch (Exception ignore) {
                    Set<String> all = EcoHelper.listMoneyTypes(ctx.getSource().getServer());
                    return SharedSuggestionProvider.suggest(all, builder);
                }
            };

    // 全局币种但过滤 default（用于 moneytype unregister，可选）
    private static final SuggestionProvider<CommandSourceStack> SUGGEST_GLOBAL_TYPES_NO_DEFAULT =
            (ctx, builder) -> {
                Set<String> all = EcoHelper.listMoneyTypes(ctx.getSource().getServer()).stream()
                        .filter(t -> !t.equals(MoneyTypesSavedData.DEFAULT_TYPE()))
                        .collect(Collectors.toSet());
                return SharedSuggestionProvider.suggest(all, builder);
            };


    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        LiteralArgumentBuilder<CommandSourceStack> wallet =
                Commands.literal("wallet")
                        .requires(commandSourceStack -> commandSourceStack.hasPermission(4))
                        .then(Commands.literal("get")
                                .then(Commands.argument("player", EntityArgument.player())
                                        .then(Commands.argument("type", StringArgumentType.word())
                                                .suggests(SUGGEST_PLAYER_TYPES)
                                                .executes(ctx -> {
                                                    ServerPlayer player = EntityArgument.getPlayer(ctx, "player");
                                                    String type = StringArgumentType.getString(ctx, "type");
                                                    long bal = EcoHelper.getBalance(player, type);
                                                    ctx.getSource().sendSuccess(
                                                            () -> Component.literal(String.format("[wallet] %s:%s = %d",
                                                                    player.getGameProfile().getName(), type, bal)),
                                                            false
                                                    );
                                                    return Command.SINGLE_SUCCESS;

                                                })
                                        )
                                )
                        )

                        .then(Commands.literal("set")
                                .then(Commands.argument("player", EntityArgument.player())
                                        .then(Commands.argument("type", StringArgumentType.word())
                                                .suggests(SUGGEST_MONEY_TYPES)
                                                .then(Commands.argument("amount", LongArgumentType.longArg())
                                                        .executes(ctx -> {
                                                            ServerPlayer player = EntityArgument.getPlayer(ctx, "player");
                                                            String type = StringArgumentType.getString(ctx, "type");
                                                            long amount = LongArgumentType.getLong(ctx, "amount");
                                                            try {
                                                                EcoHelper.setBalance(player, type, amount);
                                                            } catch (IllegalArgumentException ex) {
                                                                ctx.getSource().sendFailure(Component.literal("[wallet] unknown money type: " + type));
                                                                return 0;
                                                            }
                                                            ctx.getSource().sendSuccess(
                                                                    () -> Component.literal(String.format(
                                                                            "[wallet] set %s:%s -> %d",
                                                                            player.getGameProfile().getName(), type, amount)),
                                                                    true
                                                            );
                                                            return Command.SINGLE_SUCCESS;
                                                        })
                                                )
                                        )
                                )
                        )

                        .then(Commands.literal("add")
                                .then(Commands.argument("player", EntityArgument.player())
                                        .then(Commands.argument("type", StringArgumentType.word())
                                                .suggests(SUGGEST_MONEY_TYPES)
                                                .then(Commands.argument("delta", LongArgumentType.longArg())
                                                        .executes(ctx -> {
                                                            ServerPlayer player = EntityArgument.getPlayer(ctx, "player");
                                                            String type = StringArgumentType.getString(ctx, "type");
                                                            long delta = LongArgumentType.getLong(ctx, "delta");
                                                            long next;
                                                            try {
                                                                next = EcoHelper.addBalance(player, type, delta);
                                                            } catch (IllegalArgumentException ex) {
                                                                ctx.getSource().sendFailure(Component.literal("[wallet] unknown money type: " + type));
                                                                return 0;
                                                            }
                                                            ctx.getSource().sendSuccess(
                                                                    () -> Component.literal(String.format(
                                                                            "[wallet] add %s:%s %+d => %d",
                                                                            player.getGameProfile().getName(), type, delta, next)),
                                                                    true
                                                            );
                                                            return Command.SINGLE_SUCCESS;
                                                        })
                                                )
                                        )
                                )
                        )

                        .then(Commands.literal("types")
                                .then(Commands.literal("add")
                                        .then(Commands.argument("player", EntityArgument.player())
                                                .then(Commands.argument("type", StringArgumentType.word())
                                                        .suggests(SUGGEST_MISSING_TYPES_FOR_PLAYER)
                                                        .executes(ctx -> {
                                                            ServerPlayer player = EntityArgument.getPlayer(ctx, "player");
                                                            String type = StringArgumentType.getString(ctx, "type");
                                                            if (!EcoHelper.moneyTypeExists(player.server, type)) {
                                                                ctx.getSource().sendFailure(Component.literal("[wallet] unknown money type: " + type));
                                                                return 0;
                                                            }
                                                            boolean ok = EcoHelper.addMoneyType(player, type);
                                                            ctx.getSource().sendSuccess(
                                                                    () -> Component.literal(ok
                                                                            ? "[wallet] added type '" + type + "' for " + player.getGameProfile().getName()
                                                                            : "[wallet] type already exists for player"),
                                                                    true
                                                            );
                                                            return ok ? Command.SINGLE_SUCCESS : 0;
                                                        })
                                                )
                                        )
                                )
                                .then(Commands.literal("remove")
                                        .then(Commands.argument("player", EntityArgument.player())
                                                .then(Commands.argument("type", StringArgumentType.word())
                                                        .suggests(SUGGEST_PLAYER_TYPES)
                                                        .executes(ctx -> {
                                                            ServerPlayer player = EntityArgument.getPlayer(ctx, "player");
                                                            String type = StringArgumentType.getString(ctx, "type");
                                                            boolean ok = EcoHelper.removeMoneyType(player, type);
                                                            if (!ok) {
                                                                ctx.getSource().sendFailure(Component.literal("[wallet] cannot remove type '" + type + "' (maybe default or not present)"));
                                                                return 0;
                                                            }
                                                            ctx.getSource().sendSuccess(
                                                                    () -> Component.literal("[wallet] removed type '" + type + "' for " + player.getGameProfile().getName()),
                                                                    true
                                                            );
                                                            return Command.SINGLE_SUCCESS;
                                                        })
                                                )
                                        )
                                )
                                .then(Commands.literal("list")
                                        .then(Commands.argument("player", EntityArgument.player())
                                                .executes(ctx -> {
                                                    ServerPlayer player = EntityArgument.getPlayer(ctx, "player");
                                                    Set<String> types = EcoHelper.listPlayerMoneyTypes(player);
                                                    ctx.getSource().sendSuccess(
                                                            () -> Component.literal("[wallet] " + player.getGameProfile().getName()
                                                                    + " types: " + String.join(", ", types)),
                                                            false
                                                    );
                                                    return Command.SINGLE_SUCCESS;
                                                })
                                        )
                                )
                        );
        // ===== /moneytype =====
        LiteralArgumentBuilder<CommandSourceStack> moneytype =
                Commands.literal("moneytype")
                        .requires(css -> css.hasPermission(2))

                        .then(Commands.literal("register")
                                .then(Commands.argument("type", StringArgumentType.word())
                                        .executes(ctx -> {
                                            String type = StringArgumentType.getString(ctx, "type");
                                            boolean ok = EcoHelper.registerMoneyType(ctx.getSource().getServer(), type);
                                            if (!ok) {
                                                ctx.getSource().sendFailure(Component.literal("[moneytype] type already exists: " + type));
                                                return 0;
                                            }
                                            ctx.getSource().sendSuccess(
                                                    () -> Component.literal("[moneytype] registered: " + type),
                                                    true
                                            );
                                            return Command.SINGLE_SUCCESS;
                                        })
                                )
                        )
                        .then(Commands.literal("unregister")
                                .then(Commands.argument("type", StringArgumentType.word())
                                        .suggests(SUGGEST_GLOBAL_TYPES_NO_DEFAULT)
                                        .executes(ctx -> {
                                            String type = StringArgumentType.getString(ctx, "type");
                                            boolean ok = EcoHelper.unregisterMoneyType(ctx.getSource().getServer(), type);
                                            if (!ok) {
                                                ctx.getSource().sendFailure(Component.literal("[moneytype] cannot unregister '" + type + "' (maybe default or not exists)"));
                                                return 0;
                                            }
                                            ctx.getSource().sendSuccess(
                                                    () -> Component.literal("[moneytype] unregistered: " + type),
                                                    true
                                            );
                                            return Command.SINGLE_SUCCESS;
                                        })
                                )
                        )
                        .then(Commands.literal("list")
                                .executes(ctx -> {
                                    Set<String> all = EcoHelper.listMoneyTypes(ctx.getSource().getServer());
                                    ctx.getSource().sendSuccess(
                                            () -> Component.literal("[moneytype] " + String.join(", ", all)),
                                            false
                                    );
                                    return Command.SINGLE_SUCCESS;
                                })
                        );

        dispatcher.register(wallet);
        dispatcher.register(moneytype);
    }

}
