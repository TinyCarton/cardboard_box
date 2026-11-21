package com.tinycarton.cardboardbox.mods;

import static com.tinycarton.cardboardbox.CardboardBox.LOGGER;

import java.util.HashMap;
import java.util.Map;

import com.tinycarton.cardboardbox.handler.HandlerManager;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraftforge.common.ForgeConfigSpec;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.IForgeRegistry;

/**
 * "每个 mod 一份"的纸箱适配层。
 * <p>
 * 主类里用 {@code ModHandler.modSupportHandlerMap.put("minecraft", VanillaHandler.class)} 登记，
 * 之后 <b>只有该 mod 已加载</b>时才会实例化并调用 {@link #build} / {@link #load}。
 * <p>
 * 想给别的 mod 做适配，只要：
 * <pre>{@code
 * public class CreateHandler extends ModHandler {
 *     private ForgeConfigSpec.BooleanValue allowShafts;
 *     @Override public void build(ForgeConfigSpec.Builder b) {
 *         allowShafts = b.define("Create.allowShafts", true);
 *     }
 *     @Override public void load(ForgeConfigSpec cfg) {
 *         if (!allowShafts.get()) HandlerManager.INSTANCE.banBlockEntity(BlockEntityType.SHAFT);
 *         // 或者 registerHandler(Blocks.X, new Handler() { ... })
 *     }
 * }
 * }</pre>
 * 然后在主类构造里 {@code ModHandler.modSupportHandlerMap.put("create", CreateHandler.class)}。
 */
public class ModHandler {
    /** modId → 处理器类 */
    public static final Map<String, Class<? extends ModHandler>> modSupportHandlerMap = new HashMap<>();
    /** modId → 已实例化的处理器 */
    public static final Map<String, ModHandler> modSupportHandlerMap_instances = new HashMap<>();

    protected static IForgeRegistry<BlockEntityType<?>> BLOCK_ENTITIES_REGISTRY;
    /** 配置键（"命名空间:方块实体id"）→ 是否禁用 */
    public static final Map<String, ForgeConfigSpec.BooleanValue> blockEntityBanConfigMap = new HashMap<>();

    /** 允许子类在加载阶段读取配置 */
    public void load(ForgeConfigSpec configuration) {
    }

    /** 允许子类往配置里加自己的选项 */
    public void build(ForgeConfigSpec.Builder b) {
    }

    /**
     * 构建配置。必须早于注册表冻结后的初始化阶段（主类构造里就调用）。
     */
    public static ForgeConfigSpec buildHandlerData() {
        ForgeConfigSpec.Builder b = new ForgeConfigSpec.Builder();
        LOGGER.info("ModHandler#buildHandlerData() -> Loading data and data handlers");
        loadBlockEntityRegistry();
        buildHandlers(b);
        buildConfig(b);
        return b.build();
    }

    /** 应用配置：子类处理器 + 自动生成的黑名单开关 */
    public static void loadHandlerData(ForgeConfigSpec configuration) {
        LOGGER.info("ModHandler#loadHandlerData() -> Accessed Block Entity Registry: {}", BLOCK_ENTITIES_REGISTRY != null);
        processHandlers(configuration);
        LOGGER.info("ModHandler#loadHandlerData() -> Finished loading data handlers");
        loadConfig(configuration);
        LOGGER.info("ModHandler#loadHandlerData() -> Finished loading configurations");
    }

    private static void buildHandlers(ForgeConfigSpec.Builder b) {
        for (Map.Entry<String, Class<? extends ModHandler>> entry : modSupportHandlerMap.entrySet()) {
            // minecraft 永远存在，其他 mod 只在已加载时启用
            if (ModList.get().isLoaded(entry.getKey()) || entry.getKey().equals("minecraft")) {
                try {
                    ModHandler instance = entry.getValue().getDeclaredConstructor().newInstance();
                    modSupportHandlerMap_instances.put(entry.getKey(), instance);
                    instance.build(b);
                } catch (ReflectiveOperationException e) {
                    LOGGER.error("Failed to create handler for mod {}", entry.getKey(), e);
                }
            }
        }
    }

    private static void processHandlers(ForgeConfigSpec configuration) {
        for (Map.Entry<String, ModHandler> entry : modSupportHandlerMap_instances.entrySet()) {
            try {
                entry.getValue().load(configuration);
            } catch (Exception e) {
                LOGGER.error("Failed to load handler for mod {}", entry.getKey(), e);
            }
        }
    }

    /**
     * 把注册表里所有方块实体类型都写进配置，默认值按"经验规则"判定：
     * 名字里带 cable / wire / pipe / tube / conduit / channel 的（线缆、管道、导管）
     * 默认禁用，其余默认允许。玩家可以逐个改成 true 来禁用。
     */
    public static void buildConfig(ForgeConfigSpec.Builder b) {
        String comment = """
                Auto generated list of block entities registered in Minecraft that can be blocked from use with the box. \
                If a block entity does not show up on this list it is already black listed. The reasoning behind blocking block entities is to prevent crashes or unwanted \
                interaction. Such as picking up a piston which can both causes issues and doesn't really matter. Set value to 'true' to disable interaction.""";
        b.comment(comment).push("tile_ban_list");
        if (BLOCK_ENTITIES_REGISTRY != null) {
            for (ResourceLocation name : BLOCK_ENTITIES_REGISTRY.getKeys()) {
                BlockEntityType<?> type = BLOCK_ENTITIES_REGISTRY.getValue(name);
                if (name != null && type != null) {
                    try {
                        String typeString = ForgeRegistries.BLOCK_ENTITY_TYPES.getKey(type).toString();
                        boolean shouldBan = HandlerManager.blockEntityBanList.contains(type) || isHeuristicBanned(typeString);
                        blockEntityBanConfigMap.put(typeString, b.define(typeString, shouldBan));
                    } catch (Exception e) {
                        LOGGER.error("ModHandler#buildConfig() -> Failed to add entry to config [{} > {}]", name, type, e);
                    }
                }
            }
        }
        b.pop();
    }

    private static void loadConfig(ForgeConfigSpec configuration) {
        if (BLOCK_ENTITIES_REGISTRY != null) {
            for (ResourceLocation name : BLOCK_ENTITIES_REGISTRY.getKeys()) {
                BlockEntityType<?> type = BLOCK_ENTITIES_REGISTRY.getValue(name);
                if (name != null && type != null) {
                    try {
                        String typeString = ForgeRegistries.BLOCK_ENTITY_TYPES.getKey(type).toString();
                        boolean shouldBan = HandlerManager.blockEntityBanList.contains(type) || isHeuristicBanned(typeString);
                        if (blockEntityBanConfigMap.containsKey(typeString) ? blockEntityBanConfigMap.get(typeString).get() : false) {
                            HandlerManager.INSTANCE.banBlockEntity(type);
                        } else if (shouldBan) {
                            // 配置里被玩家放开 → 从黑名单移除
                            HandlerManager.blockEntityBanList.remove(type);
                        }
                    } catch (Exception e) {
                        LOGGER.error("ModHandler#loadConfig() -> Failed to process config [{} > {}]", name, type, e);
                    }
                }
            }
        }
    }

    /** 名称经验规则：线缆/管道类方块实体默认不安全，不参与搬运 */
    private static boolean isHeuristicBanned(String typeString) {
        return typeString.contains("cable")
                || typeString.contains("wire")
                || typeString.contains("pipe")
                || typeString.contains("tube")
                || typeString.contains("conduit")
                || typeString.contains("channel");
    }

    private static void loadBlockEntityRegistry() {
        BLOCK_ENTITIES_REGISTRY = ForgeRegistries.BLOCK_ENTITY_TYPES;
    }
}
