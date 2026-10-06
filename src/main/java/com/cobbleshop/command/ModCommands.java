package com.cobbleshop.command;

import com.cobbleshop.config.ConfigManager;
import com.cobbleshop.config.GachaDefinition;
import com.cobbleshop.config.ShopDefinition;
import com.cobbleshop.economy.Money;
import com.cobbleshop.gacha.GachaMenu;
import com.cobbleshop.shop.ShopMenu;
import com.cobbleshop.trade.TradeManager;
import com.cobbleshop.util.PlayerUtil;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.LongArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import java.util.function.Predicate;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

/**
 * 모드의 명령어 목록.
 * <ul>
 *   <li>/money · /money pay &lt;플레이어&gt; &lt;금액&gt;</li>
 *   <li>/money give|take|set &lt;플레이어&gt; &lt;금액&gt; (관리자)</li>
 *   <li>/shop &lt;상점ID&gt; [플레이어] · /gacha &lt;뽑기ID&gt; [플레이어]</li>
 *   <li>/trade &lt;플레이어&gt; · /trade accept &lt;플레이어&gt; · /trade deny</li>
 *   <li>/cobbleshop reload (관리자)</li>
 * </ul>
 */
public final class ModCommands {

    // 권한 레벨 2 = 커맨드 블록과 같은 관리자 권한
    private static final Predicate<CommandSourceStack> ADMIN = source -> source.hasPermission(2);

    private static final SuggestionProvider<CommandSourceStack> SHOP_IDS =
            (context, builder) -> SharedSuggestionProvider.suggest(ConfigManager.shops().keySet(), builder);
    private static final SuggestionProvider<CommandSourceStack> GACHA_IDS =
            (context, builder) -> SharedSuggestionProvider.suggest(ConfigManager.gachas().keySet(), builder);

    private ModCommands() {
    }

    /** 모든 명령어를 등록한다. */
    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("money")
                .executes(context -> showBalance(context.getSource().getPlayerOrException()))
                .then(Commands.literal("pay")
                        .then(Commands.argument("target", EntityArgument.player())
                                .then(Commands.argument("amount", LongArgumentType.longArg(1))
                                        .executes(ModCommands::pay))))
                .then(adminMoney("give"))
                .then(adminMoney("take"))
                .then(adminMoney("set")));

        dispatcher.register(Commands.literal("shop")
                .then(Commands.argument("id", StringArgumentType.word()).suggests(SHOP_IDS)
                        .executes(context -> openShop(context, context.getSource().getPlayerOrException()))
                        .then(Commands.argument("target", EntityArgument.player()).requires(ADMIN)
                                .executes(context -> openShop(context, EntityArgument.getPlayer(context, "target"))))));

        dispatcher.register(Commands.literal("gacha")
                .then(Commands.argument("id", StringArgumentType.word()).suggests(GACHA_IDS)
                        .executes(context -> openGacha(context, context.getSource().getPlayerOrException()))
                        .then(Commands.argument("target", EntityArgument.player()).requires(ADMIN)
                                .executes(context -> openGacha(context, EntityArgument.getPlayer(context, "target"))))));

        dispatcher.register(Commands.literal("trade")
                .then(Commands.literal("accept")
                        .then(Commands.argument("from", EntityArgument.player())
                                .executes(context -> {
                                    TradeManager.accept(context.getSource().getPlayerOrException(),
                                            EntityArgument.getPlayer(context, "from"));
                                    return 1;
                                })))
                .then(Commands.literal("deny")
                        .executes(context -> {
                            TradeManager.deny(context.getSource().getPlayerOrException());
                            return 1;
                        }))
                .then(Commands.argument("target", EntityArgument.player())
                        .executes(context -> {
                            TradeManager.request(context.getSource().getPlayerOrException(),
                                    EntityArgument.getPlayer(context, "target"));
                            return 1;
                        })));

        dispatcher.register(Commands.literal("cobbleshop").requires(ADMIN)
                .then(Commands.literal("reload")
                        .executes(context -> {
                            String result = ConfigManager.load();
                            context.getSource().sendSuccess(() -> Component.literal(result), true);
                            return 1;
                        })));
    }

    private static LiteralArgumentBuilder<CommandSourceStack> adminMoney(String action) {
        return Commands.literal(action).requires(ADMIN)
                .then(Commands.argument("target", EntityArgument.player())
                        .then(Commands.argument("amount", LongArgumentType.longArg(0))
                                .executes(context -> changeMoney(context, action))));
    }

    private static int showBalance(ServerPlayer player) {
        PlayerUtil.success(player, "내 잔액: " + Money.format(Money.get(player)));
        return 1;
    }

    private static int pay(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        ServerPlayer from = context.getSource().getPlayerOrException();
        ServerPlayer to = EntityArgument.getPlayer(context, "target");
        long amount = LongArgumentType.getLong(context, "amount");
        if (from == to) {
            PlayerUtil.fail(from, "자기 자신에게는 보낼 수 없습니다.");
            return 0;
        }
        if (!Money.trySpend(from, amount)) {
            PlayerUtil.fail(from, "잔액이 부족합니다.");
            return 0;
        }
        Money.add(to, amount);
        PlayerUtil.success(from, to.getGameProfile().getName() + "님에게 " + Money.format(amount) + "을 보냈습니다.");
        PlayerUtil.success(to, from.getGameProfile().getName() + "님에게서 " + Money.format(amount) + "을 받았습니다.");
        return 1;
    }

    private static int changeMoney(CommandContext<CommandSourceStack> context, String action)
            throws CommandSyntaxException {
        ServerPlayer target = EntityArgument.getPlayer(context, "target");
        long amount = LongArgumentType.getLong(context, "amount");
        switch (action) {
            case "give" -> Money.add(target, amount);
            case "take" -> Money.set(target, Money.get(target) - amount);
            default -> Money.set(target, amount);
        }
        String message = target.getGameProfile().getName() + "의 잔액: " + Money.format(Money.get(target));
        context.getSource().sendSuccess(() -> Component.literal(message), true);
        return 1;
    }

    private static int openShop(CommandContext<CommandSourceStack> context, ServerPlayer player) {
        String id = StringArgumentType.getString(context, "id");
        ShopDefinition shop = ConfigManager.shops().get(id);
        if (shop == null) {
            context.getSource().sendFailure(Component.literal("상점을 찾을 수 없습니다: " + id));
            return 0;
        }
        ShopMenu.open(player, shop);
        return 1;
    }

    private static int openGacha(CommandContext<CommandSourceStack> context, ServerPlayer player) {
        String id = StringArgumentType.getString(context, "id");
        GachaDefinition gacha = ConfigManager.gachas().get(id);
        if (gacha == null) {
            context.getSource().sendFailure(Component.literal("뽑기를 찾을 수 없습니다: " + id));
            return 0;
        }
        GachaMenu.open(player, gacha);
        return 1;
    }
}
