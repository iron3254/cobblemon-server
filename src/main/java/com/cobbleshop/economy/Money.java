package com.cobbleshop.economy;

import net.minecraft.world.entity.player.Player;

/**
 * 게임 내 화폐(코인)를 다루는 도우미. 잔액은 항상 서버에서만 바뀐다.
 */
public final class Money {

    private Money() {
    }

    /** 플레이어의 현재 잔액을 가져온다. */
    public static long get(Player player) {
        return player.getData(ModAttachments.MONEY);
    }

    /** 잔액을 지정한 값으로 바꾼다. 음수는 0으로 맞춘다. */
    public static void set(Player player, long amount) {
        player.setData(ModAttachments.MONEY, Math.max(0L, amount));
    }

    /** 잔액에 돈을 더한다. */
    public static void add(Player player, long amount) {
        set(player, get(player) + amount);
    }

    /** 잔액이 충분하면 차감하고 true, 부족하면 아무것도 하지 않고 false를 돌려준다. */
    public static boolean trySpend(Player player, long amount) {
        long balance = get(player);
        if (amount < 0 || balance < amount) {
            return false;
        }
        set(player, balance - amount);
        return true;
    }

    /** 1234 → "1,234코인" 처럼 보기 좋게 바꾼다. */
    public static String format(long amount) {
        return String.format("%,d코인", amount);
    }
}
