package com.cobbleshop.teleport;

import com.cobbleshop.util.PlayerUtil;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.phys.Vec3;

/**
 * 플레이어끼리 텔레포트 요청을 주고받는 기능(TPA).
 * <ul>
 *   <li>/tpa 대상: 내가 대상에게 가겠다고 요청</li>
 *   <li>/tpahere 대상: 대상이 나에게 오라고 요청</li>
 * </ul>
 * 수락하면 이동할 사람이 3초 동안 가만히 있어야 텔레포트된다. 싸움 중 도망치기를 막기 위해서다.
 */
public final class TpaManager {

    private static final long REQUEST_TIMEOUT_MS = 60_000;
    private static final int WARMUP_TICKS = 60; // 20틱 = 1초
    private static final double MAX_MOVE_SQR = 1.0; // 1블록 이상 움직이면 취소

    // 키 = 요청을 받은 사람, 값 = (요청한 사람 → 요청). 받은 순서를 기억해서 최신 요청을 찾는다
    private static final Map<UUID, LinkedHashMap<UUID, Request>> requests = new HashMap<>();
    // 키 = 이동할 사람
    private static final Map<UUID, Warmup> warmups = new HashMap<>();

    /**
     * @param here true면 /tpahere(받은 사람이 요청한 사람에게 이동)
     */
    private record Request(UUID requester, boolean here, long createdAt) {
        boolean isExpired() {
            return System.currentTimeMillis() - createdAt > REQUEST_TIMEOUT_MS;
        }
    }

    private record Warmup(UUID destination, Vec3 startPos, int ticksLeft) {
    }

    private TpaManager() {
    }

    /**
     * 텔레포트 요청을 보낸다.
     *
     * @param here false = /tpa (내가 간다), true = /tpahere (상대가 온다)
     */
    public static void request(ServerPlayer requester, ServerPlayer target, boolean here) {
        if (requester == target) {
            PlayerUtil.fail(requester, "자기 자신에게는 요청할 수 없습니다.");
            return;
        }
        LinkedHashMap<UUID, Request> received = requests.computeIfAbsent(target.getUUID(), key -> new LinkedHashMap<>());
        // 같은 사람이 다시 요청하면 지웠다가 넣어야 "가장 최근 요청"으로 순서가 바뀐다
        received.remove(requester.getUUID());
        received.put(requester.getUUID(), new Request(requester.getUUID(), here, System.currentTimeMillis()));

        String name = nameOf(requester);
        String text = here
                ? name + "님이 자기에게 오라고 요청했습니다. (60초) "
                : name + "님이 당신에게 오고 싶어 합니다. (60초) ";
        target.sendSystemMessage(Component.literal(text).withStyle(ChatFormatting.YELLOW)
                .append(PlayerUtil.clickable("[수락]", ChatFormatting.GREEN, "/tpaccept " + name))
                .append(" ")
                .append(PlayerUtil.clickable("[거절]", ChatFormatting.RED, "/tpdeny " + name)));
        PlayerUtil.success(requester, nameOf(target) + "님에게 텔레포트를 요청했습니다.");
    }

    /**
     * 받은 요청을 수락한다.
     *
     * @param from 수락할 요청을 보낸 사람. null이면 가장 최근 요청
     */
    public static void accept(ServerPlayer target, ServerPlayer from) {
        Request request = takeRequest(target, from);
        if (request == null) {
            PlayerUtil.fail(target, "받은 텔레포트 요청이 없거나 만료되었습니다.");
            return;
        }
        ServerPlayer requester = target.server.getPlayerList().getPlayer(request.requester());
        if (requester == null) {
            PlayerUtil.fail(target, "요청한 플레이어가 접속 중이 아닙니다.");
            return;
        }

        ServerPlayer mover = request.here() ? target : requester;
        ServerPlayer destination = request.here() ? requester : target;
        warmups.put(mover.getUUID(), new Warmup(destination.getUUID(), mover.position(), WARMUP_TICKS));

        PlayerUtil.success(target, "텔레포트 요청을 수락했습니다.");
        PlayerUtil.success(requester, nameOf(target) + "님이 요청을 수락했습니다.");
        mover.sendSystemMessage(Component.literal("3초 후 텔레포트합니다. 움직이지 마세요!")
                .withStyle(ChatFormatting.AQUA));
    }

    /**
     * 받은 요청을 거절한다.
     *
     * @param from 거절할 요청을 보낸 사람. null이면 가장 최근 요청
     */
    public static void deny(ServerPlayer target, ServerPlayer from) {
        Request request = takeRequest(target, from);
        if (request == null) {
            PlayerUtil.fail(target, "받은 텔레포트 요청이 없습니다.");
            return;
        }
        PlayerUtil.fail(target, "텔레포트 요청을 거절했습니다.");
        ServerPlayer requester = target.server.getPlayerList().getPlayer(request.requester());
        if (requester != null) {
            PlayerUtil.fail(requester, nameOf(target) + "님이 텔레포트 요청을 거절했습니다.");
        }
    }

    /** 내가 보낸 요청을 모두 취소한다. */
    public static void cancelOwn(ServerPlayer requester) {
        boolean removed = false;
        for (LinkedHashMap<UUID, Request> received : requests.values()) {
            removed |= received.remove(requester.getUUID()) != null;
        }
        if (removed) {
            PlayerUtil.success(requester, "보낸 텔레포트 요청을 취소했습니다.");
        } else {
            PlayerUtil.fail(requester, "보낸 텔레포트 요청이 없습니다.");
        }
    }

    /** 매 틱마다 불러서 대기 중인 텔레포트를 진행한다. */
    public static void tick(MinecraftServer server) {
        Iterator<Map.Entry<UUID, Warmup>> iterator = warmups.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<UUID, Warmup> entry = iterator.next();
            Warmup warmup = entry.getValue();
            ServerPlayer mover = server.getPlayerList().getPlayer(entry.getKey());
            ServerPlayer destination = server.getPlayerList().getPlayer(warmup.destination());

            if (mover == null || destination == null) {
                if (mover != null) {
                    PlayerUtil.fail(mover, "상대가 접속을 종료해서 텔레포트가 취소되었습니다.");
                }
                iterator.remove();
                continue;
            }
            if (mover.position().distanceToSqr(warmup.startPos()) > MAX_MOVE_SQR) {
                PlayerUtil.fail(mover, "움직여서 텔레포트가 취소되었습니다.");
                iterator.remove();
                continue;
            }
            if (warmup.ticksLeft() > 1) {
                entry.setValue(new Warmup(warmup.destination(), warmup.startPos(), warmup.ticksLeft() - 1));
                continue;
            }

            iterator.remove();
            // 다른 차원(네더, 엔드 등)에 있어도 상대의 월드로 이동한다
            mover.teleportTo(destination.serverLevel(), destination.getX(), destination.getY(), destination.getZ(),
                    mover.getYRot(), mover.getXRot());
            PlayerUtil.sound(mover, SoundEvents.ENDERMAN_TELEPORT, 1.0f);
            PlayerUtil.success(mover, nameOf(destination) + "님에게 텔레포트했습니다.");
        }
    }

    /** 접속 종료한 플레이어와 관련된 요청과 대기를 정리한다. */
    public static void clearFor(ServerPlayer player) {
        UUID id = player.getUUID();
        requests.remove(id);
        for (LinkedHashMap<UUID, Request> received : requests.values()) {
            received.remove(id);
        }
        warmups.remove(id);
    }

    private static Request takeRequest(ServerPlayer target, ServerPlayer from) {
        LinkedHashMap<UUID, Request> received = requests.get(target.getUUID());
        if (received == null) {
            return null;
        }
        received.values().removeIf(Request::isExpired);

        Request request = null;
        if (from != null) {
            request = received.remove(from.getUUID());
        } else if (!received.isEmpty()) {
            // LinkedHashMap은 넣은 순서를 기억하므로 마지막 항목이 가장 최근 요청
            UUID latest = null;
            for (UUID key : received.keySet()) {
                latest = key;
            }
            request = received.remove(latest);
        }
        if (received.isEmpty()) {
            requests.remove(target.getUUID());
        }
        return request;
    }

    private static String nameOf(ServerPlayer player) {
        return player.getGameProfile().getName();
    }
}
