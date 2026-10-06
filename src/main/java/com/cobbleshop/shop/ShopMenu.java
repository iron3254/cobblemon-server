package com.cobbleshop.shop;

import com.cobbleshop.CobbleShop;
import com.cobbleshop.config.ShopDefinition;
import com.cobbleshop.config.ShopEntry;
import com.cobbleshop.economy.Money;
import com.cobbleshop.gui.ChestUiMenu;
import com.cobbleshop.gui.Icons;
import com.cobbleshop.util.PlayerUtil;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * 상점 창. 위 5줄은 상품, 맨 아래 줄은 페이지 이동과 잔액 표시.
 * 좌클릭 = 구매, 우클릭 = 판매, Shift를 누르면 묶음으로 처리한다.
 */
public class ShopMenu extends ChestUiMenu {

    private static final int ROWS = 6;
    private static final int PAGE_SIZE = 45;
    private static final int SLOT_PREV = 45;
    private static final int SLOT_INFO = 49;
    private static final int SLOT_NEXT = 53;

    private final List<Product> products = new ArrayList<>();
    private int page;

    /** 설정의 상품 정보와 실제 아이템을 묶어 둔 것. */
    private record Product(ShopEntry entry, Item item) {
    }

    private ShopMenu(int containerId, Inventory playerInventory, ShopDefinition shop) {
        super(containerId, playerInventory, ROWS);
        for (ShopEntry entry : shop.entries()) {
            Optional<Item> item = Icons.findItem(entry.itemId());
            if (item.isPresent()) {
                products.add(new Product(entry, item.get()));
            } else {
                CobbleShop.LOGGER.warn("상점 '{}': 아이템 '{}'을(를) 찾을 수 없어 건너뜁니다.", shop.title(), entry.itemId());
            }
        }
        refresh();
    }

    /** 플레이어에게 상점 창을 연다. */
    public static void open(ServerPlayer player, ShopDefinition shop) {
        player.openMenu(new SimpleMenuProvider(
                (containerId, inventory, p) -> new ShopMenu(containerId, inventory, shop),
                Component.literal(shop.title())));
    }

    private int pageCount() {
        return Math.max(1, (products.size() + PAGE_SIZE - 1) / PAGE_SIZE);
    }

    private void refresh() {
        for (int i = 0; i < PAGE_SIZE; i++) {
            int index = page * PAGE_SIZE + i;
            setIcon(i, index < products.size() ? productIcon(products.get(index)) : ItemStack.EMPTY);
        }
        for (int i = PAGE_SIZE; i < ROWS * 9; i++) {
            setIcon(i, Icons.filler(Items.YELLOW_STAINED_GLASS_PANE));
        }
        if (page > 0) {
            setIcon(SLOT_PREV, Icons.button(Items.ARROW, Component.literal("◀ 이전 페이지")));
        }
        if (page < pageCount() - 1) {
            setIcon(SLOT_NEXT, Icons.button(Items.ARROW, Component.literal("다음 페이지 ▶")));
        }
        setIcon(SLOT_INFO, Icons.button(Items.GOLD_INGOT,
                Component.literal("내 잔액: " + Money.format(Money.get(viewer))).withStyle(ChatFormatting.GOLD),
                "페이지 " + (page + 1) + " / " + pageCount()));
    }

    private ItemStack productIcon(Product product) {
        ShopEntry entry = product.entry();
        List<Component> lore = new ArrayList<>();
        lore.add(entry.canBuy()
                ? Component.literal("구매: " + Money.format(entry.buyPrice())).withStyle(ChatFormatting.GREEN)
                : Component.literal("구매 불가").withStyle(ChatFormatting.DARK_GRAY));
        lore.add(entry.canSell()
                ? Component.literal("판매: " + Money.format(entry.sellPrice())).withStyle(ChatFormatting.YELLOW)
                : Component.literal("판매 불가").withStyle(ChatFormatting.DARK_GRAY));
        lore.add(Component.empty());
        if (entry.canBuy()) {
            lore.add(Component.literal("좌클릭: 구매 · Shift+좌클릭: 묶음 구매").withStyle(ChatFormatting.GRAY));
        }
        if (entry.canSell()) {
            lore.add(Component.literal("우클릭: 판매 · Shift+우클릭: 전부 판매").withStyle(ChatFormatting.GRAY));
        }
        return Icons.decorate(new ItemStack(product.item(), entry.count()), null, lore);
    }

    @Override
    protected void onButtonClick(int slot, int button, ClickType clickType) {
        if (slot == SLOT_PREV && page > 0) {
            page--;
            refresh();
            return;
        }
        if (slot == SLOT_NEXT && page < pageCount() - 1) {
            page++;
            refresh();
            return;
        }
        int index = page * PAGE_SIZE + slot;
        if (slot >= PAGE_SIZE || index >= products.size()) {
            return;
        }

        boolean shift = clickType == ClickType.QUICK_MOVE;
        if (clickType != ClickType.PICKUP && !shift) {
            return;
        }
        Product product = products.get(index);
        if (button == 0) {
            // 묶음 구매 = 한 칸(보통 64개)을 채울 만큼
            int bulk = Math.max(1, new ItemStack(product.item()).getMaxStackSize() / product.entry().count());
            buy(product, shift ? bulk : 1);
        } else if (button == 1) {
            sell(product, shift ? Integer.MAX_VALUE : 1);
        }
        refresh();
    }

    private void buy(Product product, int times) {
        ShopEntry entry = product.entry();
        if (!entry.canBuy()) {
            PlayerUtil.fail(viewer, "이 상품은 구매할 수 없습니다.");
            return;
        }
        long total = entry.buyPrice() * times;
        if (!Money.trySpend(viewer, total)) {
            PlayerUtil.fail(viewer, "잔액이 부족합니다. (필요: " + Money.format(total) + ")");
            PlayerUtil.sound(viewer, SoundEvents.VILLAGER_NO, 1.0f);
            return;
        }
        int amount = entry.count() * times;
        PlayerUtil.giveItem(viewer, product.item(), amount);
        PlayerUtil.success(viewer, product.item().getDescription().getString() + " " + amount
                + "개를 " + Money.format(total) + "에 구매했습니다.");
        PlayerUtil.sound(viewer, SoundEvents.EXPERIENCE_ORB_PICKUP, 1.2f);
    }

    private void sell(Product product, int maxTimes) {
        ShopEntry entry = product.entry();
        if (!entry.canSell()) {
            PlayerUtil.fail(viewer, "이 상품은 판매할 수 없습니다.");
            return;
        }
        int owned = PlayerUtil.countItem(viewer, product.item());
        int times = Math.min(maxTimes, owned / entry.count());
        if (times <= 0) {
            PlayerUtil.fail(viewer, "판매할 아이템이 부족합니다. (" + entry.count() + "개 필요)");
            PlayerUtil.sound(viewer, SoundEvents.VILLAGER_NO, 1.0f);
            return;
        }
        int amount = entry.count() * times;
        long total = entry.sellPrice() * times;
        PlayerUtil.removeItem(viewer, product.item(), amount);
        Money.add(viewer, total);
        PlayerUtil.success(viewer, product.item().getDescription().getString() + " " + amount
                + "개를 " + Money.format(total) + "에 판매했습니다.");
        PlayerUtil.sound(viewer, SoundEvents.EXPERIENCE_ORB_PICKUP, 0.8f);
    }
}
