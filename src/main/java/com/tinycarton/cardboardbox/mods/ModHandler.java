package com.tinycarton.cardboardbox.mods;

import static com.tinycarton.cardboardbox.CardboardBox.LOGGER;

import java.util.HashMap;
import java.util.Map;

import com.tinycarton.cardboardbox.handler.HandlerManager;
import com.tinycarton.cardboardbox.handler.WhitelistManager;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.neoforged.fml.ModList;
import net.neoforged.fml.loading.FMLPaths;
import net.neoforged.neoforge.common.ModConfigSpec;

/**
 * "每个 mod 一份"的纸箱适配层。
 */
public class ModHandler {
    /** modId → 适配器类 */
    public static final Map<String, Class<? extends ModHandler>> modSupportHandlerMap = new HashMap<>();
    /** modId → 已实例化的适配器 */
    public static final Map<String, ModHandler> modSupportHandlerMap_instances = new HashMap<>();
    /** 配置键（"命名空间:方块实体id"）→ 是否禁用 */
    public static final Map<String, ModConfigSpec.BooleanValue> blockEntityBanConfigMap = new HashMap<>();

    /** 是否启用白名单（默认启用） */
    private static ModConfigSpec.BooleanValue whitelistEnabled;
    /** 是否启用黑名单（默认禁用；优先级高于白名单） */
    private static ModConfigSpec.BooleanValue blacklistEnabled;

    public void load(ModConfigSpec configuration) {
    }

    public void build(ModConfigSpec.Builder b) {
    }

    /** 构建配置：白名单/黑名单开关 + 所有方块实体类型的禁用开关 */
    public static ModConfigSpec buildHandlerData() {
        ModConfigSpec.Builder b = new ModConfigSpec.Builder();
        LOGGER.info("ModHandler#buildHandlerData() -> Loading data and data handlers");

        whitelistEnabled = b.comment("Only blocks listed in config/cardboardbox_whitelist.json can be picked up. The file is generated on first launch from all block-entity blocks.")
                .define("WhitelistSettings.EnableWhitelist", true);
        blacklistEnabled = b.comment("Enable the built-in blacklist. Takes priority over the whitelist when both are enabled.")
                .define("BlacklistSettings.EnableBlacklist", false);

        String comment = """
                Auto generated list of block entities registered in Minecraft that can be blocked from use with the box. \
                If a block entity does not show up on this list it is already black listed. Set value to 'true' to disable interaction.""";
        b.comment(comment).push("tile_ban_list");
        for (Identifier id : BuiltInRegistries.BLOCK_ENTITY_TYPE.keySet()) {
            BlockEntityType<?> type = BuiltInRegistries.BLOCK_ENTITY_TYPE.getValue(id);
            if (id != null && type != null) {
                try {
                    blockEntityBanConfigMap.put(id.toString(), b.define(id.toString(), HandlerManager.blockEntityBanList.contains(type)));
                } catch (Exception e) {
                    LOGGER.error("ModHandler#buildConfig() -> Failed to add entry to config [{} > {}]", id, type, e);
                }
            }
        }
        b.pop();

        return b.build();
    }

    /** 应用配置：子类适配器 + 黑白名单开关 + 白名单文件 */
    public static void loadHandlerData() {
        for (Map.Entry<String, Class<? extends ModHandler>> entry : modSupportHandlerMap.entrySet()) {
            if (!entry.getKey().equals("minecraft") && !ModList.get().isLoaded(entry.getKey())) {
                continue;
            }
            try {
                ModHandler instance = entry.getValue().getDeclaredConstructor().newInstance();
                modSupportHandlerMap_instances.put(entry.getKey(), instance);
                instance.load(null);
            } catch (ReflectiveOperationException e) {
                LOGGER.error("Failed to create handler for mod {}", entry.getKey(), e);
            }
        }

        for (Identifier id : BuiltInRegistries.BLOCK_ENTITY_TYPE.keySet()) {
            BlockEntityType<?> type = BuiltInRegistries.BLOCK_ENTITY_TYPE.getValue(id);
            if (id != null && type != null) {
                try {
                    boolean shouldBan = HandlerManager.blockEntityBanList.contains(type);
                    ModConfigSpec.BooleanValue value = blockEntityBanConfigMap.get(id.toString());
                    if (value != null ? value.get() : false) {
                        HandlerManager.INSTANCE.banBlockEntity(type);
                    } else if (shouldBan) {
                        HandlerManager.blockEntityBanList.remove(type);
                    }
                } catch (Exception e) {
                    LOGGER.error("ModHandler#loadConfig() -> Failed to process config [{} > {}]", id, type, e);
                }
            }
        }

        WhitelistManager.init(FMLPaths.CONFIGDIR.get());
        HandlerManager.whitelistEnabled = whitelistEnabled != null && whitelistEnabled.get();
        HandlerManager.blacklistEnabled = blacklistEnabled != null && blacklistEnabled.get();
    }
}
