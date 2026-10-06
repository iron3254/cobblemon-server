package com.cobbleshop.util;

import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

/**
 * 플레이어에게 아이템을 주고 빼고, 메시지와 소리를 보내는 도우미.
 */
public final class PlayerUtil {

    private PlayerUtil() {
    }

    /** 아이템을 개수만큼 지급한다. 인벤토리가 꽉 차면 발밑에 떨어뜨린다. */
    public static void giveItem(ServerPlayer player, Item item, int amount) {
        int maxStack = new ItemStack(item).getMaxStackSize();
        while (amount > 0) {
            int size = Math.min(amount, maxStack);
            player.getInventory().placeItemBackInInventory(new ItemStack(item, size));
            amount -= size;
        }
    }

    /** 아이템 묶음 여러 개를 지급한다. 인벤토리가 꽉 차면 발밑에 떨어뜨린다. */
    public static void giveAll(ServerPlayer player, List<ItemStack> stacks) {
        for (ItemStack stack : stacks) {
            if (!stack.isEmpty()) {
                player.getInventory().placeItemBackInInventory(stack);
            }
        }
    }

    /** 인벤토리(갑옷/보조손 제외)에 있는 해당 아이템의 개수를 센다. 이름 바꾼 아이템 등은 제외한다. */
    public static int countItem(ServerPlayer player, Item item) {
        ItemStack template = new ItemStack(item);
        int total = 0;
        for (ItemStack stack : player.getInventory().items) {
            if (ItemStack.isSameItemSameComponents(stack, template)) {
                total += stack.getCount();
            }
        }
        return total;
    }

    /** 인벤토리에서 해당 아이템을 개수만큼 없앤다. 먼저 {@link #countItem}으로 충분한지 확인할 것. */
    public static void removeItem(ServerPlayer player, Item item, int amount) {
        ItemStack template = new ItemStack(item);
        for (ItemStack stack : player.getInventory().items) {
            if (amount <= 0) {
                break;
            }
            if (ItemStack.isSameItemSameComponents(stack, template)) {
                int take = Math.min(amount, stack.getCount());
                stack.shrink(take);
                amount -= take;
            }
        }
        player.getInventory().setChanged();
    }

    /** 초록색 성공 메시지. */
    public static void success(ServerPlayer player, String message) {
        player.sendSystemMessage(Component.literal(message).withStyle(ChatFormatting.GREEN));
    }

    /** 빨간색 실패 메시지. */
    public static void fail(ServerPlayer player, String message) {
        player.sendSystemMessage(Component.literal(message).withStyle(ChatFormatting.RED));
    }

    /** 채팅에서 클릭하면 명령어가 실행되는 굵은 글씨 버튼을 만든다. */
    public static Component clickable(String text, ChatFormatting color, String command) {
        return Component.literal(text).withStyle(style -> style
                .withColor(color).withBold(true)
                .withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, command)));
    }

    /** 본인에게만 들리는 소리를 재생한다. */
    public static void sound(ServerPlayer player, SoundEvent sound, float pitch) {
        player.playNotifySound(sound, SoundSource.PLAYERS, 0.7f, pitch);
    }
}
