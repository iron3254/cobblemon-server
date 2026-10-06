package com.cobbleshop.gui;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

/**
 * 상자 창 모양을 빌려 쓰는 버튼식 GUI의 부모 클래스.
 * 위쪽 칸은 전부 잠겨 있고, 클릭하면 {@link #onButtonClick}이 불린다.
 * 클라이언트는 평범한 상자 창으로 보기 때문에 클라이언트에 모드가 없어도 동작한다.
 */
public abstract class ChestUiMenu extends AbstractContainerMenu {

    /** 위쪽 버튼 칸들의 아이템을 담는 보관함. */
    protected final SimpleContainer display;
    /** 위쪽 칸의 줄 수 (1~6). */
    protected final int rows;
    /** 이 창을 보고 있는 플레이어. */
    protected final ServerPlayer viewer;

    protected ChestUiMenu(int containerId, Inventory playerInventory, int rows) {
        super(menuTypeFor(rows), containerId);
        this.rows = rows;
        this.display = new SimpleContainer(rows * 9);
        this.viewer = (ServerPlayer) playerInventory.player;

        // 슬롯 순서와 위치는 바닐라 상자 창(ChestMenu)과 똑같이 맞춰야 클라이언트 화면과 어긋나지 않는다
        for (int row = 0; row < rows; row++) {
            for (int col = 0; col < 9; col++) {
                addSlot(new LockedSlot(display, col + row * 9, 8 + col * 18, 18 + row * 18));
            }
        }
        int offset = (rows - 4) * 18;
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) {
                addSlot(new Slot(playerInventory, col + row * 9 + 9, 8 + col * 18, 103 + row * 18 + offset));
            }
        }
        for (int col = 0; col < 9; col++) {
            addSlot(new Slot(playerInventory, col, 8 + col * 18, 161 + offset));
        }
    }

    /** 줄 수에 맞는 바닐라 상자 창 종류를 돌려준다. */
    public static MenuType<?> menuTypeFor(int rows) {
        return switch (rows) {
            case 1 -> MenuType.GENERIC_9x1;
            case 2 -> MenuType.GENERIC_9x2;
            case 3 -> MenuType.GENERIC_9x3;
            case 4 -> MenuType.GENERIC_9x4;
            case 5 -> MenuType.GENERIC_9x5;
            default -> MenuType.GENERIC_9x6;
        };
    }

    @Override
    public void clicked(int slotId, int button, ClickType clickType, Player player) {
        if (slotId >= 0 && slotId < rows * 9) {
            // super.clicked()를 부르지 않으므로 아이템이 움직이지 않는다
            onButtonClick(slotId, button, clickType);
            // 클라이언트는 아이템을 집었다고 미리 그려 두었으므로 서버 상태로 다시 맞춘다
            sendAllDataToRemote();
            return;
        }
        super.clicked(slotId, button, clickType, player);
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        return ItemStack.EMPTY;
    }

    @Override
    public boolean stillValid(Player player) {
        return player.isAlive();
    }

    /** 칸 하나를 채우는 단축 메서드. */
    protected void setIcon(int slot, ItemStack stack) {
        display.setItem(slot, stack);
    }

    /**
     * 위쪽 칸이 클릭되었을 때 불린다.
     *
     * @param slot      클릭한 칸 번호 (0부터, 왼쪽 위 → 오른쪽 아래)
     * @param button    0 = 좌클릭, 1 = 우클릭 (숫자키 교체일 땐 숫자키 번호)
     * @param clickType 클릭 종류. PICKUP = 일반 클릭, QUICK_MOVE = Shift+클릭
     */
    protected abstract void onButtonClick(int slot, int button, ClickType clickType);
}
