package com.cobbleshop.config;

import com.cobbleshop.CobbleShop;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.neoforged.fml.loading.FMLPaths;

/**
 * config/cobbleshop 폴더의 JSON 설정(상점, 뽑기)을 읽어 보관한다.
 * 파일이 없으면 모드 안에 들어 있는 기본 설정을 복사해 만든다.
 */
public final class ConfigManager {

    private static Map<String, ShopDefinition> shops = Map.of();
    private static Map<String, GachaDefinition> gachas = Map.of();

    private ConfigManager() {
    }

    /** 불러온 상점 목록 (ID → 상점). */
    public static Map<String, ShopDefinition> shops() {
        return shops;
    }

    /** 불러온 뽑기 목록 (ID → 뽑기). */
    public static Map<String, GachaDefinition> gachas() {
        return gachas;
    }

    /**
     * 설정 파일을 다시 읽는다. 읽다가 오류가 나면 기존 설정을 그대로 유지한다.
     *
     * @return 결과 메시지 (명령어 피드백용)
     */
    public static String load() {
        Path dir = FMLPaths.CONFIGDIR.get().resolve(CobbleShop.MOD_ID);
        try {
            Files.createDirectories(dir);
            Path shopsFile = copyDefaultIfMissing(dir, "shops.json");
            Path gachaFile = copyDefaultIfMissing(dir, "gacha.json");

            Map<String, ShopDefinition> newShops = parseShops(readJson(shopsFile));
            Map<String, GachaDefinition> newGachas = parseGachas(readJson(gachaFile));
            shops = newShops;
            gachas = newGachas;

            String message = "상점 " + shops.size() + "개, 뽑기 " + gachas.size() + "개를 불러왔습니다.";
            CobbleShop.LOGGER.info(message);
            return message;
        } catch (Exception e) {
            CobbleShop.LOGGER.error("설정 파일을 읽지 못했습니다", e);
            return "설정 파일 오류: " + e.getMessage();
        }
    }

    private static Path copyDefaultIfMissing(Path dir, String fileName) throws IOException {
        Path target = dir.resolve(fileName);
        if (Files.notExists(target)) {
            try (InputStream in = ConfigManager.class.getResourceAsStream("/defaults/" + fileName)) {
                if (in == null) {
                    throw new IOException("기본 설정 파일이 모드 안에 없습니다: " + fileName);
                }
                Files.copy(in, target);
            }
        }
        return target;
    }

    private static JsonObject readJson(Path file) throws IOException {
        return JsonParser.parseString(Files.readString(file, StandardCharsets.UTF_8)).getAsJsonObject();
    }

    private static Map<String, ShopDefinition> parseShops(JsonObject root) {
        Map<String, ShopDefinition> result = new LinkedHashMap<>();
        for (Map.Entry<String, JsonElement> shopJson : root.entrySet()) {
            JsonObject obj = shopJson.getValue().getAsJsonObject();
            List<ShopEntry> entries = new ArrayList<>();
            for (JsonElement element : obj.getAsJsonArray("items")) {
                JsonObject item = element.getAsJsonObject();
                entries.add(new ShopEntry(
                        item.get("item").getAsString(),
                        Math.max(1, getInt(item, "count", 1)),
                        getLong(item, "buy", -1),
                        getLong(item, "sell", -1)));
            }
            result.put(shopJson.getKey(), new ShopDefinition(
                    getString(obj, "title", shopJson.getKey()), Collections.unmodifiableList(entries)));
        }
        return Collections.unmodifiableMap(result);
    }

    private static Map<String, GachaDefinition> parseGachas(JsonObject root) {
        Map<String, GachaDefinition> result = new LinkedHashMap<>();
        for (Map.Entry<String, JsonElement> gachaJson : root.entrySet()) {
            JsonObject obj = gachaJson.getValue().getAsJsonObject();
            List<GachaReward> rewards = new ArrayList<>();
            for (JsonElement element : obj.getAsJsonArray("rewards")) {
                JsonObject reward = element.getAsJsonObject();
                String itemId = getString(reward, "item", "");
                int weight = getInt(reward, "weight", 0);
                if (weight <= 0) {
                    continue;
                }
                rewards.add(new GachaReward(
                        getString(reward, "name", itemId),
                        itemId,
                        Math.max(1, getInt(reward, "count", 1)),
                        getString(reward, "command", ""),
                        getString(reward, "icon", itemId.isEmpty() ? "minecraft:paper" : itemId),
                        weight,
                        reward.has("broadcast") && reward.get("broadcast").getAsBoolean()));
            }
            if (rewards.isEmpty()) {
                CobbleShop.LOGGER.warn("뽑기 '{}'에 보상이 없어서 건너뜁니다.", gachaJson.getKey());
                continue;
            }
            result.put(gachaJson.getKey(), new GachaDefinition(
                    getString(obj, "title", gachaJson.getKey()),
                    Math.max(0, getLong(obj, "cost", 100)),
                    Collections.unmodifiableList(rewards)));
        }
        return Collections.unmodifiableMap(result);
    }

    private static String getString(JsonObject obj, String key, String fallback) {
        return obj.has(key) ? obj.get(key).getAsString() : fallback;
    }

    private static int getInt(JsonObject obj, String key, int fallback) {
        return obj.has(key) ? obj.get(key).getAsInt() : fallback;
    }

    private static long getLong(JsonObject obj, String key, long fallback) {
        return obj.has(key) ? obj.get(key).getAsLong() : fallback;
    }
}
