package com.cobbleshop;

import com.cobbleshop.command.ModCommands;
import com.cobbleshop.config.ConfigManager;
import com.cobbleshop.economy.ModAttachments;
import com.cobbleshop.teleport.TpaManager;
import com.cobbleshop.trade.TradeManager;
import com.mojang.logging.LogUtils;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerStartingEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import org.slf4j.Logger;

/**
 * 모드의 시작점. NeoForge가 게임을 켤 때 이 클래스를 가장 먼저 만든다.
 */
@Mod(CobbleShop.MOD_ID)
public class CobbleShop {

    /** 모드 고유 ID. neoforge.mods.toml의 modId와 같아야 한다. */
    public static final String MOD_ID = "cobbleshop";

    /** 서버 콘솔에 로그를 남길 때 사용한다. */
    public static final Logger LOGGER = LogUtils.getLogger();

    /**
     * @param modBus 모드 전용 이벤트 버스. 등록(register) 작업은 여기에 연결한다.
     */
    public CobbleShop(IEventBus modBus) {
        ModAttachments.ATTACHMENT_TYPES.register(modBus);

        NeoForge.EVENT_BUS.addListener(this::onServerStarting);
        NeoForge.EVENT_BUS.addListener(this::onRegisterCommands);
        NeoForge.EVENT_BUS.addListener(this::onPlayerLoggedOut);
        NeoForge.EVENT_BUS.addListener(this::onLivingDeath);
        NeoForge.EVENT_BUS.addListener(this::onServerTick);
    }

    private void onServerTick(ServerTickEvent.Post event) {
        TpaManager.tick(event.getServer());
    }

    private void onServerStarting(ServerStartingEvent event) {
        ConfigManager.load();
    }

    private void onRegisterCommands(RegisterCommandsEvent event) {
        ModCommands.register(event.getDispatcher());
    }

    // 접속 종료 시 플레이어 데이터가 저장되기 전에 거래를 취소해야 아이템이 사라지지 않는다
    private void onPlayerLoggedOut(PlayerEvent.PlayerLoggedOutEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            TradeManager.cancelFor(player, "접속을 종료했습니다.");
            TpaManager.clearFor(player);
        }
    }

    // 죽기 직전에 거래 중이던 아이템을 인벤토리로 돌려놔야 사망 시 정상적으로 떨어진다
    private void onLivingDeath(LivingDeathEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            TradeManager.cancelFor(player, "쓰러졌습니다.");
        }
    }
}
