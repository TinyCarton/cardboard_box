package com.tinycarton.cardboardbox.mods;

import java.util.HashMap;
import java.util.Map;

import com.tinycarton.cardboardbox.CardboardBox;
import com.tinycarton.cardboardbox.handler.HandlerManager;
import com.tinycarton.cardboardbox.handler.WhitelistManager;

import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.entity.BlockEntityType;

/**
 * "每个 mod 一份"的纸箱适配层。
 * <p>
 * 主类里用 {@code ModHandler.modSupportHandlerMap.put("minecraft", VanillaHandler.class)} 登记，
 * 之后 <b>只有该 mod 已加载</b>时才会实例化并调用 {@link #load()}。
 * <p>
 * 想给别的 mod 做适配：
 * <pre>{@code
 * public class CreateHandler extends ModHandler {
 *     @Override
 *     public void load() {
 *         HandlerManager.INSTANCE.banBlockEntity(BlockEntityType.SHAFT);
 *         HandlerManager.INSTANCE.registerHandler(SomeModBlocks.MACHINE.get(), new Handler() { ... });
 *     }
 * }
 * }</pre>
 * 然后在主类 onInitialize 里 {@code ModHandler.modSupportHandlerMap.put("create", CreateHandler.class)}。
 */
public class ModHandler {
    /** modId → 处理器类 */
    public static final Map<String, Class<? extends ModHandler>> modSupportHandlerMap = new HashMap<>();
    /** modId → 已实例化的处理器 */
    public static final Map<String, ModHandler> modSupportHandlerMap_instances = new HashMap<>();

    /** 子类在这里注册黑名单 / handler */
    public void load() {
    }

    /**
     * 读取配置 → 实例化处理器 → 应用配置，最后回写配置文件。
     */
    public static void loadHandlerData() {
        Config.load();
        buildHandlers();
        processHandlers();
        applyConfig();
        Config.save();
        CardboardBox.LOGGER.info("Loaded cardboard box handlers and configuration");
    }

    private static void buildHandlers() {
        for (Map.Entry<String, Class<? extends ModHandler>> entry : modSupportHandlerMap.entrySet()) {
            // minecraft 永远存在，其他 mod 只在已加载时启用
            if (!entry.getKey().equals("minecraft") && !FabricLoader.getInstance().isModLoaded(entry.getKey())) {
                continue;
            }
            try {
                ModHandler instance = entry.getValue().getDeclaredConstructor().newInstance();
                modSupportHandlerMap_instances.put(entry.getKey(), instance);
            } catch (ReflectiveOperationException e) {
                CardboardBox.LOGGER.error("Failed to create handler for mod {}", entry.getKey(), e);
            }
        }
    }

    private static void processHandlers() {
        for (Map.Entry<String, ModHandler> entry : modSupportHandlerMap_instances.entrySet()) {
            try {
                entry.getValue().load();
            } catch (Exception e) {
                CardboardBox.LOGGER.error("Failed to load handler for mod {}", entry.getKey(), e);
            }
        }
    }

    /**
     * 应用白名单/黑名单开关，并把注册表里所有方块实体类型写进配置（默认只反映显式黑名单）。
     */
    private static void applyConfig() {
        Config.Data data = Config.get();

        // 白名单/黑名单开关；黑名单优先级更高
        HandlerManager.whitelistEnabled = data.whitelistEnabled;
        HandlerManager.blacklistEnabled = data.blacklistEnabled;

        // 白名单文件：首次启动按「带方块实体的方块」自动生成
        WhitelistManager.init(FabricLoader.getInstance().getConfigDir());

        for (ResourceLocation id : BuiltInRegistries.BLOCK_ENTITY_TYPE.keySet()) {
            BlockEntityType<?> type = BuiltInRegistries.BLOCK_ENTITY_TYPE.get(id);
            if (type == null) {
                continue;
            }
            String key = id.toString();
            boolean shouldBan = HandlerManager.blockEntityBanList.contains(type);
            if (!data.blockEntityBanList.containsKey(key)) {
                data.blockEntityBanList.put(key, shouldBan);
            }
            if (Boolean.TRUE.equals(data.blockEntityBanList.get(key))) {
                HandlerManager.INSTANCE.banBlockEntity(type);
            } else if (shouldBan) {
                // 配置里被玩家放开 → 从黑名单移除
                HandlerManager.blockEntityBanList.remove(type);
            }
        }
    }
}
