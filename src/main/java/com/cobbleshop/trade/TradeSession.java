package com.cobbleshop.trade;

import com.cobbleshop.gui.Icons;
import com.cobbleshop.util.PlayerUtil;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.ResolvableProfile;

/**
 * 두 플레이어의 거래 한 건. 두 사람이 같은 보관함(container)을 함께 보고,
 * 왼쪽 4칸 줄은 A, 오른쪽 4칸 줄은 B의 물건을 올리는 자리다.
 * 둘 다 수락 버튼을 누르면 물건이 교환되고, 물건이 바뀌면 수락이 풀린다.
 *
 * <pre>
 * A A A A | B B B B
 * A A A A | B B B B   (위 5줄: 물건 칸)
 * ...
 * 머리 수락 . . | . . 수락 머리   (맨 아래 줄)
 * </pre>
 */
public class TradeSession {

    /** 보관함 칸 수 (6줄 × 9칸). */
    public static final int SIZE = 54;
    private static final int HEAD_A = 45;
    private static final int BUTTON_A = 46;
    private static final int BUTTON_B = 52;
    private static final int HEAD_B = 53;

    private final ServerPlayer playerA;
    private final ServerPlayer playerB;
    private final SimpleContainer container = new SimpleContainer(SIZE);
    private TradeMenu menuA;
    private TradeMenu menuB;
    private boolean acceptA;
    private boolean acceptB;
    private boolean finished;
    // 버튼 아이콘을 바꾸는 것도 "내용 변경"으로 감지되므로, 그동안은 수락 해제를 막는다
    private boolean updatingUi;

    TradeSession(ServerPlayer playerA, ServerPlayer playerB) {
        this.playerA = playerA;
        this.playerB = playerB;
        container.addListener(changed -> onContentsChanged());
        drawFrame();
        drawButtons();
    }

    /** 해당 칸이 A(또는 B)가 물건을 올리는 칸인지 확인한다. */
    public static boolean isItemSlot(int slot, boolean sideA) {
        int row = slot / 9;
        int col = slot % 9;
        if (row >= 5) {
            return false;
        }
        return sideA ? col < 4 : col > 4;
    }

    /** A(또는 B)의 수락 버튼 칸 번호. */
    public static int buttonSlot(boolean sideA) {
        return sideA ? BUTTON_A : BUTTON_B;
    }

    /** 두 플레이어 모두에게 거래 창을 연다. */
    void open() {
        Component title = Component.literal("거래: " + nameOf(playerA) + " ↔ " + nameOf(playerB));
        playerA.openMenu(new SimpleMenuProvider((containerId, inventory, p) -> {
            menuA = new TradeMenu(containerId, inventory, this, true);
            return menuA;
        }, title));
        playerB.openMenu(new SimpleMenuProvider((containerId, inventory, p) -> {
            menuB = new TradeMenu(containerId, inventory, this, false);
            return menuB;
        }, title));
    }

    /** 두 사람이 함께 쓰는 보관함. */
    public SimpleContainer container() {
        return container;
    }

    /** 거래가 끝났는지(완료 또는 취소). */
    public boolean isFinished() {
        return finished;
    }

    /** 수락 버튼을 눌렀을 때. 둘 다 수락하면 거래를 완료한다. */
    public void toggleAccept(boolean sideA) {
        if (finished) {
            return;
        }
        if (sideA) {
            acceptA = !acceptA;
        } else {
            acceptB = !acceptB;
        }
        drawButtons();
        PlayerUtil.sound(playerA, SoundEvents.EXPERIENCE_ORB_PICKUP, 1.5f);
        PlayerUtil.sound(playerB, SoundEvents.EXPERIENCE_ORB_PICKUP, 1.5f);
        if (acceptA && acceptB) {
            complete();
        }
    }

    /** 누군가 창을 닫았을 때. 아직 끝나지 않았으면 취소한다. */
    public void onMenuClosed(ServerPlayer player) {
        cancel(nameOf(player) + "님이 창을 닫았습니다.");
    }

    /**
     * 거래를 취소하고 올려 둔 물건을 각자에게 돌려준다.
     *
     * @param reason 두 사람에게 보여줄 취소 이유
     */
    public void cancel(String reason) {
        if (finished) {
            return;
        }
        finished = true;
        PlayerUtil.giveAll(playerA, takeItems(true));
        PlayerUtil.giveAll(playerB, takeItems(false));
        PlayerUtil.fail(playerA, "거래가 취소되었습니다. " + reason);
        PlayerUtil.fail(playerB, "거래가 취소되었습니다. " + reason);
        closeMenus();
    }

    private void complete() {
        finished = true;
        List<ItemStack> fromA = takeItems(true);
        List<ItemStack> fromB = takeItems(false);
        PlayerUtil.giveAll(playerA, fromB);
        PlayerUtil.giveAll(playerB, fromA);
        PlayerUtil.success(playerA, nameOf(playerB) + "님과의 거래가 완료되었습니다!");
        PlayerUtil.success(playerB, nameOf(playerA) + "님과의 거래가 완료되었습니다!");
        PlayerUtil.sound(playerA, SoundEvents.PLAYER_LEVELUP, 1.0f);
        PlayerUtil.sound(playerB, SoundEvents.PLAYER_LEVELUP, 1.0f);
        closeMenus();
    }

    private void onContentsChanged() {
        if (updatingUi || finished) {
            return;
        }
        // 수락한 뒤에 물건을 바꿔치기하는 사기를 막기 위해, 물건이 바뀌면 수락을 모두 해제한다
        if (acceptA || acceptB) {
            acceptA = false;
            acceptB = false;
            drawButtons();
        }
    }

    private List<ItemStack> takeItems(boolean sideA) {
        List<ItemStack> items = new ArrayList<>();
        for (int i = 0; i < SIZE; i++) {
            if (isItemSlot(i, sideA)) {
                ItemStack stack = container.removeItemNoUpdate(i);
                if (!stack.isEmpty()) {
                    items.add(stack);
                }
            }
        }
        return items;
    }

    private void closeMenus() {
        TradeManager.remove(this);
        if (playerA.containerMenu == menuA) {
            playerA.closeContainer();
        }
        if (playerB.containerMenu == menuB) {
            playerB.closeContainer();
        }
    }

    private void drawFrame() {
        updatingUi = true;
        for (int row = 0; row < 6; row++) {
            container.setItem(row * 9 + 4, Icons.filler(Items.GRAY_STAINED_GLASS_PANE));
        }
        for (int slot = 45; slot < SIZE; slot++) {
            if (slot != 49) {
                container.setItem(slot, Icons.filler(Items.BLACK_STAINED_GLASS_PANE));
            }
        }
        container.setItem(HEAD_A, headOf(playerA));
        container.setItem(HEAD_B, headOf(playerB));
        updatingUi = false;
    }

    private void drawButtons() {
        updatingUi = true;
        container.setItem(BUTTON_A, buttonIcon(playerA, acceptA));
        container.setItem(BUTTON_B, buttonIcon(playerB, acceptB));
        updatingUi = false;
    }

    private static ItemStack buttonIcon(ServerPlayer owner, boolean accepted) {
        if (accepted) {
            return Icons.button(Items.LIME_WOOL,
                    Component.literal(nameOf(owner) + ": ✔ 수락함").withStyle(ChatFormatting.GREEN),
                    "물건이 바뀌면 자동으로 해제됩니다.",
                    "다시 클릭하면 수락을 취소합니다.");
        }
        return Icons.button(Items.RED_WOOL,
                Component.literal(nameOf(owner) + ": 대기 중").withStyle(ChatFormatting.RED),
                "본인 버튼을 클릭해서 거래를 수락하세요.",
                "둘 다 수락하면 교환됩니다.");
    }

    private static ItemStack headOf(ServerPlayer player) {
        ItemStack head = new ItemStack(Items.PLAYER_HEAD);
        head.set(DataComponents.PROFILE, new ResolvableProfile(player.getGameProfile()));
        return Icons.decorate(head, Component.literal(nameOf(player)).withStyle(ChatFormatting.YELLOW), List.of());
    }

    private static String nameOf(ServerPlayer player) {
        return player.getGameProfile().getName();
    }
}
