package com.cobbleshop.gacha;

import com.cobbleshop.CobbleShop;
import com.cobbleshop.config.GachaDefinition;
import com.cobbleshop.config.GachaReward;
import com.cobbleshop.economy.Money;
import com.cobbleshop.gui.ChestUiMenu;
import com.cobbleshop.gui.Icons;
import com.cobbleshop.util.PlayerUtil;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * 뽑기 창. 가운데 버튼을 누르면 돈을 내고 가중치 랜덤으로 보상을 받는다.
 * 좌클릭 = 1회, Shift+좌클릭 = 10회.
 */
public class GachaMenu extends ChestUiMenu {

    private static final int ROWS = 3;
    private static final int SLOT_TABLE = 11;
    private static final int SLOT_ROLL = 13;
    private static final int SLOT_BALANCE = 15;
    private static final int SLOT_RESULT = 22;
    private static final int MULTI_ROLL = 10;

    private final GachaDefinition gacha;
    private ItemStack lastResult = ItemStack.EMPTY;

    private GachaMenu(int containerId, Inventory playerInventory, GachaDefinition gacha) {
        super(containerId, playerInventory, ROWS);
        this.gacha = gacha;
        refresh();
    }

    /** 플레이어에게 뽑기 창을 연다. */
    public static void open(ServerPlayer player, GachaDefinition gacha) {
        player.openMenu(new SimpleMenuProvider(
                (containerId, inventory, p) -> new GachaMenu(containerId, inventory, gacha),
                Component.literal(gacha.title())));
    }

    private void refresh() {
        for (int i = 0; i < ROWS * 9; i++) {
            setIcon(i, Icons.filler(Items.PURPLE_STAINED_GLASS_PANE));
        }
        setIcon(SLOT_TABLE, tableIcon());
        setIcon(SLOT_ROLL, Icons.button(Items.NETHER_STAR,
                Component.literal("뽑기!").withStyle(ChatFormatting.LIGHT_PURPLE, ChatFormatting.BOLD),
                "1회: " + Money.format(gacha.cost()),
                MULTI_ROLL + "회: " + Money.format(gacha.cost() * MULTI_ROLL),
                "",
                "좌클릭: 1회 뽑기",
                "Shift+좌클릭: " + MULTI_ROLL + "회 뽑기"));
        setIcon(SLOT_BALANCE, Icons.button(Items.GOLD_INGOT,
                Component.literal("내 잔액: " + Money.format(Money.get(viewer))).withStyle(ChatFormatting.GOLD)));
        setIcon(SLOT_RESULT, lastResult.isEmpty()
                ? Icons.button(Items.GRAY_STAINED_GLASS_PANE, Component.literal("결과가 여기에 표시됩니다"))
                : lastResult.copy());
    }

    private ItemStack tableIcon() {
        List<Component> lore = new ArrayList<>();
        for (GachaReward reward : gacha.rewards()) {
            lore.add(Component.literal(String.format("%s  %.2f%%", reward.name(), gacha.chanceOf(reward)))
                    .withStyle(reward.broadcast() ? ChatFormatting.GOLD : ChatFormatting.GRAY));
        }
        return Icons.decorate(new ItemStack(Items.BOOK),
                Component.literal("확률표").withStyle(ChatFormatting.AQUA), lore);
    }

    @Override
    protected void onButtonClick(int slot, int button, ClickType clickType) {
        if (slot != SLOT_ROLL || button != 0) {
            return;
        }
        if (clickType == ClickType.PICKUP) {
            roll(1);
        } else if (clickType == ClickType.QUICK_MOVE) {
            roll(MULTI_ROLL);
        }
        refresh();
    }

    private void roll(int times) {
        long total = gacha.cost() * times;
        if (!Money.trySpend(viewer, total)) {
            PlayerUtil.fail(viewer, "잔액이 부족합니다. (필요: " + Money.format(total) + ")");
            PlayerUtil.sound(viewer, SoundEvents.VILLAGER_NO, 1.0f);
            return;
        }

        boolean jackpot = false;
        for (int i = 0; i < times; i++) {
            GachaReward reward = gacha.pick(viewer.getRandom());
            giveReward(reward);
            jackpot |= reward.broadcast();
            lastResult = Icons.decorate(resultIcon(reward),
                    Component.literal(reward.name()).withStyle(ChatFormatting.YELLOW),
                    List.of(Component.literal("최근 결과").withStyle(ChatFormatting.GRAY)));
        }
        PlayerUtil.sound(viewer, jackpot ? SoundEvents.UI_TOAST_CHALLENGE_COMPLETE : SoundEvents.PLAYER_LEVELUP, 1.0f);
    }

    private ItemStack resultIcon(GachaReward reward) {
        return Icons.findItem(reward.iconId()).map(ItemStack::new).orElseGet(() -> new ItemStack(Items.PAPER));
    }

    private void giveReward(GachaReward reward) {
        if (!reward.itemId().isEmpty()) {
            Icons.findItem(reward.itemId()).ifPresentOrElse(
                    item -> PlayerUtil.giveItem(viewer, item, reward.count()),
                    () -> CobbleShop.LOGGER.warn("뽑기 보상 아이템 '{}'을(를) 찾을 수 없습니다.", reward.itemId()));
        }
        MinecraftServer server = viewer.getServer();
        if (!reward.command().isEmpty() && server != null) {
            String command = reward.command().replace("{player}", viewer.getGameProfile().getName());
            // 서버 콘솔 권한으로 실행. 결과 메시지는 숨긴다
            server.getCommands().performPrefixedCommand(
                    server.createCommandSourceStack().withSuppressedOutput(), command);
        }

        PlayerUtil.success(viewer, "[뽑기] " + reward.name() + " 획득!");
        if (reward.broadcast() && server != null) {
            server.getPlayerList().broadcastSystemMessage(
                    Component.literal("[뽑기] " + viewer.getGameProfile().getName() + "님이 "
                            + reward.name() + "을(를) 뽑았습니다!").withStyle(ChatFormatting.GOLD),
                    false);
        }
    }
}
