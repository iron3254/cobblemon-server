package com.cobbleshop.gui;

import net.minecraft.world.Container;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

/**
 * 보기만 가능하고 아이템을 넣거나 뺄 수 없는 칸. 버튼과 장식용으로 쓴다.
 */
public class LockedSlot extends Slot {

    public LockedSlot(Container container, int index, int x, int y) {
        super(container, index, x, y);
    }

    @Override
    public boolean mayPickup(Player player) {
        return false;
    }

    @Override
    public boolean mayPlace(ItemStack stack) {
        return false;
    }
}
