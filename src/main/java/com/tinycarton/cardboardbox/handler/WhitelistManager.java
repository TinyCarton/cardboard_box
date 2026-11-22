package com.tinycarton.cardboardbox.handler;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashSet;
import java.util.Set;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.tinycarton.cardboardbox.CardboardBox;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.block.Block;

/**
 * 纸箱白名单：只有列在这份配置里的方块才能被装进纸箱。
 * <p>
 * 文件位置 {@code config/cardboardbox_whitelist.json}，格式：
 * <pre>{@code
 * {
 *   "blocks": [ "minecraft:chest", "minecraft:furnace" ]
 * }
 * }</pre>
 * 白名单不在代码里硬编码：首次启动（文件不存在时）会扫描注册表，把所有
 * 「带方块实体」的方块自动写入，之后完全由配置文件决定。
 */
public final class WhitelistManager {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final String FILE_NAME = "cardboardbox_whitelist.json";
    private static final Set<Identifier> ALLOWED = new LinkedHashSet<>();
    private static Path file;

    private WhitelistManager() {
    }

    /**
     * 载入白名单；文件不存在时先按「有方块实体的方块」生成一份再读取。
     *
     * @param configDir 各加载器的 config 目录
     */
    public static synchronized void init(Path configDir) {
        file = configDir.resolve(FILE_NAME);
        if (!Files.exists(file)) {
            generateDefaults();
        }
        load();
    }

    /** 扫描注册表：只收录带方块实体的方块 */
    private static void generateDefaults() {
        Set<Identifier> defaults = new LinkedHashSet<>();
        for (Identifier id : BuiltInRegistries.BLOCK.keySet()) {
            Block block = BuiltInRegistries.BLOCK.get(id).map(ref -> ref.value()).orElse(null);
            if (block != null && block.defaultBlockState().hasBlockEntity()) {
                defaults.add(id);
            }
        }
        write(defaults);
        CardboardBox.LOGGER.info("Generated default whitelist with {} block entity blocks at {}", defaults.size(), file);
    }

    private static void write(Set<Identifier> ids) {
        JsonObject root = new JsonObject();
        JsonArray array = new JsonArray();
        ids.forEach(id -> array.add(id.toString()));
        root.add("blocks", array);
        try {
            if (file.getParent() != null) {
                Files.createDirectories(file.getParent());
            }
            Files.writeString(file, GSON.toJson(root), StandardCharsets.UTF_8);
        } catch (IOException e) {
            CardboardBox.LOGGER.error("Failed to write whitelist {}", file, e);
        }
    }

    private static void load() {
        ALLOWED.clear();
        try {
            String json = Files.readString(file, StandardCharsets.UTF_8);
            JsonObject root = JsonParser.parseString(json).getAsJsonObject();
            JsonArray array = root.getAsJsonArray("blocks");
            if (array != null) {
                for (JsonElement element : array) {
                    Identifier id = Identifier.tryParse(element.getAsString());
                    if (id != null) {
                        ALLOWED.add(id);
                    }
                }
            }
        } catch (Exception e) {
            CardboardBox.LOGGER.error("Failed to read whitelist {}", file, e);
        }
        CardboardBox.LOGGER.info("Loaded {} whitelisted blocks", ALLOWED.size());
    }

    /** 该方块是否在白名单内 */
    public static boolean isAllowed(Block block) {
        Identifier id = BuiltInRegistries.BLOCK.getKey(block);
        return id != null && ALLOWED.contains(id);
    }

    public static int size() {
        return ALLOWED.size();
    }
}
