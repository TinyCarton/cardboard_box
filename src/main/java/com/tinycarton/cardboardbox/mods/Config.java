package com.tinycarton.cardboardbox.mods;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.tinycarton.cardboardbox.CardboardBox;

import net.fabricmc.loader.api.FabricLoader;

/**
 * 极简 JSON 配置，落在 {@code config/cardboardbox.json}。
 * <p>
 * Fabric 侧没有声明式配置规范，这里用 Gson 直接读写一个扁平结构：
 * <ul>
 *     <li>{@code blackListMobSpawners}：是否禁止搬运刷怪笼</li>
 *     <li>{@code blockEntityBanList}：方块实体 id → 是否禁用（首次启动自动填充全部已注册类型）</li>
 * </ul>
 */
public final class Config {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static Data data = new Data();

    private Config() {
    }

    /** 配置内容 */
    public static final class Data {
        public boolean blackListMobSpawners = false;
        public Map<String, Boolean> blockEntityBanList = new LinkedHashMap<>();
    }

    public static Data get() {
        return data;
    }

    public static Path path() {
        return FabricLoader.getInstance().getConfigDir().resolve(CardboardBox.MOD_ID + ".json");
    }

    public static void load() {
        Path path = path();
        if (!Files.exists(path)) {
            return;
        }
        try {
            String json = Files.readString(path, StandardCharsets.UTF_8);
            Data read = GSON.fromJson(json, Data.class);
            if (read != null) {
                if (read.blockEntityBanList == null) {
                    read.blockEntityBanList = new LinkedHashMap<>();
                }
                data = read;
            }
        } catch (Exception e) {
            CardboardBox.LOGGER.error("Failed to read config {}, using defaults", path, e);
        }
    }

    public static void save() {
        Path path = path();
        try {
            Files.createDirectories(path.getParent());
            Files.writeString(path, GSON.toJson(data), StandardCharsets.UTF_8);
        } catch (IOException e) {
            CardboardBox.LOGGER.error("Failed to write config {}", path, e);
        }
    }
}
