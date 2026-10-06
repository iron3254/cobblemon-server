package com.cobbleshop.gui;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import net.minecraft.ChatFormatting;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Unit;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.ItemLore;

/**
 * 창(GUI)에 버튼처럼 놓을 아이콘 아이템을 만드는 도우미.
 */
public final class Icons {

    private Icons() {
    }

    /**
     * 이름과 설명(lore)을 붙인 아이콘을 만든다.
     *
     * @param stack 원본 아이템 (그대로 수정된다)
     * @param name  표시 이름. null이면 원래 이름 유지
     * @param lore  설명 줄 목록
     */
    public static ItemStack decorate(ItemStack stack, Component name, List<Component> lore) {
        if (name != null) {
            stack.set(DataComponents.CUSTOM_NAME, noItalic(name));
        }
        List<Component> lines = new ArrayList<>();
        for (Component line : lore) {
            lines.add(noItalic(line));
        }
        stack.set(DataComponents.LORE, new ItemLore(lines));
        return stack;
    }

    /** 아이템 종류 + 이름 + 회색 설명 줄로 아이콘을 만든다. */
    public static ItemStack button(Item item, Component name, String... lore) {
        List<Component> lines = new ArrayList<>();
        for (String line : lore) {
            lines.add(Component.literal(line).withStyle(ChatFormatting.GRAY));
        }
        return decorate(new ItemStack(item), name, lines);
    }

    /** 빈칸을 채우는 색유리판. 마우스를 올려도 설명이 뜨지 않는다. */
    public static ItemStack filler(Item pane) {
        ItemStack stack = new ItemStack(pane);
        stack.set(DataComponents.CUSTOM_NAME, Component.literal(" "));
        stack.set(DataComponents.HIDE_TOOLTIP, Unit.INSTANCE);
        return stack;
    }

    /** "cobblemon:poke_ball" 같은 ID로 아이템을 찾는다. 설치 안 된 모드의 아이템이면 비어 있다. */
    public static Optional<Item> findItem(String id) {
        ResourceLocation key = ResourceLocation.tryParse(id);
        if (key == null) {
            return Optional.empty();
        }
        return BuiltInRegistries.ITEM.getOptional(key).filter(item -> item != Items.AIR);
    }

    private static Component noItalic(Component component) {
        // 마인크래프트는 이름을 바꾼 아이템을 기본으로 기울임체로 보여주기 때문에 꺼 준다
        return component.copy().withStyle(style -> style.withItalic(false));
    }
}
