package com.cobbleshop.trade;

import com.cobbleshop.util.PlayerUtil;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

/**
 * 거래 요청과 진행 중인 거래를 관리한다.
 * 마인크래프트 서버 로직은 한 스레드에서만 돌기 때문에 일반 HashMap으로 충분하다.
 */
public final class TradeManager {

    private static final long REQUEST_TIMEOUT_MS = 60_000;

    private static final Map<UUID, TradeSession> sessions = new HashMap<>();
    // 키 = 요청을 받은 사람
    private static final Map<UUID, Request> requests = new HashMap<>();

    private record Request(UUID from, long createdAt) {
        boolean isExpired() {
            return System.currentTimeMillis() - createdAt > REQUEST_TIMEOUT_MS;
        }
    }

    private TradeManager() {
    }

    /** from이 to에게 거래를 요청한다. */
    public static void request(ServerPlayer from, ServerPlayer to) {
        if (from == to) {
            PlayerUtil.fail(from, "자기 자신과는 거래할 수 없습니다.");
            return;
        }
        if (isTrading(from) || isTrading(to)) {
            PlayerUtil.fail(from, "이미 거래 중인 플레이어가 있습니다.");
            return;
        }
        requests.put(to.getUUID(), new Request(from.getUUID(), System.currentTimeMillis()));

        String fromName = from.getGameProfile().getName();
        Component accept = Component.literal("[수락]").withStyle(style -> style
                .withColor(ChatFormatting.GREEN).withBold(true)
                .withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/trade accept " + fromName)));
        Component deny = Component.literal("[거절]").withStyle(style -> style
                .withColor(ChatFormatting.RED).withBold(true)
                .withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/trade deny")));
        to.sendSystemMessage(Component.literal(fromName + "님이 거래를 요청했습니다. (60초) ")
                .withStyle(ChatFormatting.YELLOW).append(accept).append(" ").append(deny));
        PlayerUtil.success(from, to.getGameProfile().getName() + "님에게 거래를 요청했습니다.");
    }

    /** target이 from의 요청을 수락하고 거래 창을 연다. */
    public static void accept(ServerPlayer target, ServerPlayer from) {
        Request request = requests.get(target.getUUID());
        if (request == null || !request.from().equals(from.getUUID()) || request.isExpired()) {
            PlayerUtil.fail(target, "받은 거래 요청이 없거나 만료되었습니다.");
            return;
        }
        requests.remove(target.getUUID());
        if (isTrading(from) || isTrading(target)) {
            PlayerUtil.fail(target, "이미 거래 중인 플레이어가 있습니다.");
            return;
        }
        TradeSession session = new TradeSession(from, target);
        sessions.put(from.getUUID(), session);
        sessions.put(target.getUUID(), session);
        session.open();
    }

    /** 받은 거래 요청을 거절한다. */
    public static void deny(ServerPlayer target) {
        Request request = requests.remove(target.getUUID());
        if (request == null) {
            PlayerUtil.fail(target, "받은 거래 요청이 없습니다.");
            return;
        }
        PlayerUtil.fail(target, "거래 요청을 거절했습니다.");
        ServerPlayer from = target.server.getPlayerList().getPlayer(request.from());
        if (from != null) {
            PlayerUtil.fail(from, target.getGameProfile().getName() + "님이 거래를 거절했습니다.");
        }
    }

    /**
     * 플레이어가 참여 중인 거래가 있으면 취소한다. (접속 종료, 사망 등)
     *
     * @param action "접속을 종료했습니다." 처럼 무슨 일이 있었는지
     */
    public static void cancelFor(ServerPlayer player, String action) {
        requests.remove(player.getUUID());
        TradeSession session = sessions.get(player.getUUID());
        if (session != null) {
            session.cancel(player.getGameProfile().getName() + "님이 " + action);
        }
    }

    /** 거래 중인지 확인한다. */
    public static boolean isTrading(ServerPlayer player) {
        return sessions.containsKey(player.getUUID());
    }

    static void remove(TradeSession session) {
        sessions.values().removeIf(s -> s == session);
    }
}
