package com.cobbleshop.trade;

import com.cobbleshop.gui.LockedSlot;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

/**
 * 거래 창 하나(플레이어 한 명 기준). 같은 {@link TradeSession}을 두 사람이 각자의 TradeMenu로 본다.
 * 내 쪽 칸만 아이템을 넣고 뺄 수 있고, 상대 쪽 칸과 장식 칸은 잠겨 있다.
 */
public class TradeMenu extends AbstractContainerMenu {

    private static final int TRADE_SLOTS = TradeSession.SIZE;

    private final TradeSession session;
    private final boolean sideA;

    TradeMenu(int containerId, Inventory playerInventory, TradeSession session, boolean sideA) {
        super(MenuType.GENERIC_9x6, containerId);
        this.session = session;
        this.sideA = sideA;

        Container container = session.container();
        for (int i = 0; i < TRADE_SLOTS; i++) {
            int x = 8 + (i % 9) * 18;
            int y = 18 + (i / 9) * 18;
            addSlot(TradeSession.isItemSlot(i, sideA)
                    ? new Slot(container, i, x, y)
                    : new LockedSlot(container, i, x, y));
        }
        // 6줄 상자 창과 같은 위치에 플레이어 인벤토리를 배치한다
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) {
                addSlot(new Slot(playerInventory, col + row * 9 + 9, 8 + col * 18, 139 + row * 18));
            }
        }
        for (int col = 0; col < 9; col++) {
            addSlot(new Slot(playerInventory, col, 8 + col * 18, 197));
        }
    }

    @Override
    public void clicked(int slotId, int button, ClickType clickType, Player player) {
        if (session.isFinished()) {
            return;
        }
        if (slotId == TradeSession.buttonSlot(sideA)) {
            if (clickType == ClickType.PICKUP) {
                session.toggleAccept(sideA);
            }
            if (!session.isFinished()) {
                sendAllDataToRemote();
            }
            return;
        }
        if (slotId >= 0 && slotId < TRADE_SLOTS && !TradeSession.isItemSlot(slotId, sideA)) {
            sendAllDataToRemote();
            return;
        }
        super.clicked(slotId, button, clickType, player);
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        Slot slot = slots.get(index);
        if (!slot.hasItem() || !slot.mayPickup(player)) {
            return ItemStack.EMPTY;
        }
        ItemStack stack = slot.getItem();
        ItemStack original = stack.copy();

        if (index < TRADE_SLOTS) {
            // 거래 칸 → 내 인벤토리
            if (!moveItemStackTo(stack, TRADE_SLOTS, slots.size(), true)) {
                return ItemStack.EMPTY;
            }
        } else if (!moveToMySide(stack)) {
            // 내 인벤토리 → 내 거래 칸
            return ItemStack.EMPTY;
        }

        if (stack.isEmpty()) {
            slot.setByPlayer(ItemStack.EMPTY);
        } else {
            slot.setChanged();
        }
        return original;
    }

    // 내 쪽 칸은 줄마다 4칸씩 떨어져 있어서, 한 칸씩 범위를 지정해 옮긴다 (같은 아이템 칸 먼저, 빈칸 나중)
    private boolean moveToMySide(ItemStack stack) {
        boolean moved = false;
        for (int pass = 0; pass < 2 && !stack.isEmpty(); pass++) {
            for (int i = 0; i < TRADE_SLOTS && !stack.isEmpty(); i++) {
                if (!TradeSession.isItemSlot(i, sideA)) {
                    continue;
                }
                boolean empty = !slots.get(i).hasItem();
                if ((pass == 0) != empty) {
                    moved |= moveItemStackTo(stack, i, i + 1, false);
                }
            }
        }
        return moved;
    }

    @Override
    public boolean stillValid(Player player) {
        return !session.isFinished() && player.isAlive();
    }

    @Override
    public void removed(Player player) {
        super.removed(player);
        if (player instanceof ServerPlayer serverPlayer) {
            session.onMenuClosed(serverPlayer);
        }
    }
}
