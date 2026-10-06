package com.cobbleshop.config;

import java.util.List;

/**
 * 상점 하나의 정보. shops.json의 항목 하나와 같다.
 *
 * @param title   창 위쪽에 표시되는 제목
 * @param entries 판매 상품 목록 (순서대로 칸에 배치)
 */
public record ShopDefinition(String title, List<ShopEntry> entries) {
}
