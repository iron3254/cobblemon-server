package com.cobbleshop.economy;

import com.cobbleshop.CobbleShop;
import com.mojang.serialization.Codec;
import java.util.function.Supplier;
import net.neoforged.neoforge.attachment.AttachmentType;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;

/**
 * 플레이어에게 붙여서 저장하는 데이터(어태치먼트) 목록.
 */
public final class ModAttachments {

    /** 어태치먼트 등록기. 모드 생성자에서 이벤트 버스에 연결한다. */
    public static final DeferredRegister<AttachmentType<?>> ATTACHMENT_TYPES =
            DeferredRegister.create(NeoForgeRegistries.ATTACHMENT_TYPES, CobbleShop.MOD_ID);

    /** 플레이어 잔액. 월드에 저장되고, 죽어도 유지된다(copyOnDeath). */
    public static final Supplier<AttachmentType<Long>> MONEY = ATTACHMENT_TYPES.register("money",
            () -> AttachmentType.builder(() -> 0L).serialize(Codec.LONG).copyOnDeath().build());

    private ModAttachments() {
    }
}
