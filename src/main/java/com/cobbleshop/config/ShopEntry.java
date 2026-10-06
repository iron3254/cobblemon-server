package com.cobbleshop.config;

/**
 * 상점 상품 한 칸의 정보.
 *
 * @param itemId    아이템 ID (예: "cobblemon:poke_ball")
 * @param count     한 번에 사고파는 개수
 * @param buyPrice  구매 가격. 음수면 구매 불가
 * @param sellPrice 판매 가격. 음수면 판매 불가
 */
public record ShopEntry(String itemId, int count, long buyPrice, long sellPrice) {

    /** 살 수 있는 상품인지. */
    public boolean canBuy() {
        return buyPrice >= 0;
    }

    /** 팔 수 있는 상품인지. */
    public boolean canSell() {
        return sellPrice >= 0;
    }
}
