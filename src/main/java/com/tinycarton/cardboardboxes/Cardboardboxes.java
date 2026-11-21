package com.tinycarton.cardboardboxes;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.tinycarton.cardboardboxes.box.BoxBlock;
import com.tinycarton.cardboardboxes.box.BoxBlockEntity;
import com.tinycarton.cardboardboxes.box.BoxBlockItem;
import com.tinycarton.cardboardboxes.handler.HandlerManager;
import com.tinycarton.cardboardboxes.mods.ModHandler;
import com.tinycarton.cardboardboxes.mods.VanillaHandler;

import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraftforge.common.ForgeConfigSpec;
import net.minecraftforge.event.BuildCreativeModeTabContentsEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.Mod.EventBusSubscriber.Bus;
import net.minecraftforge.fml.config.ModConfig;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

/**
 * 模组主类：负责注册方块 / 方块实体 / 物品，并在通用初始化阶段装载"搬运黑名单"与交互处理器。
 * <p>
 * 整体思路（一句话）：<b>纸箱 = 带一个"待还原方块状态 + 待还原 NBT"的 BlockEntity</b>。
 * <ul>
 *     <li>空箱右键方块：把目标方块换成纸箱，目标方块的 BlockState 与 NBT 存进纸箱 BE（{@link BoxBlockItem#tryToPickupBlock}）</li>
 *     <li>带内容纸箱右键地面：把纸箱换回原方块并写回 NBT（{@link BoxBlockItem#tryToPlaceBlock}）</li>
 *     <li>装好的纸箱物品本身只用 ItemStack NBT 记录内容（storedItem / tileData），所以可以放进背包、箱子、漏斗</li>
 * </ul>
 */
@Mod.EventBusSubscriber(bus = Bus.MOD)
@Mod(Cardboardboxes.DOMAIN)
public class Cardboardboxes {
    /** 模组 id，同时也是所有注册名的命名空间 */
    public static final String DOMAIN = "cardboardboxes";
    public static final Logger LOGGER = LogManager.getLogger();

    // ===== 方块 =====
    public static final DeferredRegister<Block> BLOCKS = DeferredRegister.create(ForgeRegistries.BLOCKS, DOMAIN);
    /** 无染色纸箱：cardboardboxes:cardboardbox */
    public static final RegistryObject<BoxBlock> BOX_BLOCK = BLOCKS.register("cardboardbox", () -> new BoxBlock(null));
    /** 16 个染色纸箱：cardboardboxes:box_white ... box_pink（顺序由 {@link TabSortedColors} 决定，保证创造模式栏里排列好看） */
    public static final List<RegistryObject<BoxBlock>> BOX_COLORS = Arrays.stream(TabSortedColors.values())
            .map(TabSortedColors::toDyeColor)
            .map(color -> BLOCKS.register("box_" + color.getName(), () -> new BoxBlock(color)))
            .toList();

    // ===== 方块实体 =====
    public static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITY_TYPES = DeferredRegister.create(ForgeRegistries.BLOCK_ENTITY_TYPES, DOMAIN);
    /**
     * 所有纸箱（含 16 色）共用一个 BE 类型。
     * <p>
     * 必须把 17 个方块全部登记为有效方块：区块重新加载时 Minecraft 会用
     * {@code BlockEntityType#isValid} 校验，只登记 cardboardbox 的话彩色纸箱的方块实体会被丢弃，
     * 箱子里的东西也随之消失。
     */
    public static final RegistryObject<BlockEntityType<BoxBlockEntity>> BOX_BLOCK_ENTITY_TYPE = BLOCK_ENTITY_TYPES.register("box", () -> {
        List<BoxBlock> validBlocks = new ArrayList<>(BOX_COLORS.stream().map(RegistryObject::get).toList());
        validBlocks.add(0, BOX_BLOCK.get());
        return BlockEntityType.Builder.of(BoxBlockEntity::new, validBlocks.toArray(new BoxBlock[0])).build(null);
    });

    // ===== 物品 =====
    public static final DeferredRegister<Item> ITEMS = DeferredRegister.create(ForgeRegistries.ITEMS, DOMAIN);
    public static final RegistryObject<BoxBlockItem> BOX_ITEM = ITEMS.register("cardboardbox", () -> new BoxBlockItem(BOX_BLOCK.get(), null));
    /** 彩色纸箱物品，注册名与方块一一对应（box_red → box_red） */
    public static final List<RegistryObject<BoxBlockItem>> BOX_ITEM_COLORS = BOX_COLORS.stream()
            .map(defBlock -> ITEMS.register(defBlock.getId().getPath(), () -> new BoxBlockItem(defBlock.get(), defBlock.get().color)))
            .toList();

    /** 通用配置：自动生成的所有方块实体黑名单 + 各 mod 处理器选项 */
    private static ForgeConfigSpec config;

    public Cardboardboxes() {
        IEventBus modBus = FMLJavaModLoadingContext.get().getModEventBus();

        modBus.addListener(this::setup);
        modBus.addListener(this::onCreativeModeTabBuildContents);

        // 注册内置的 minecraft 处理器（黑名单 + 箱子朝向修正）
        ModHandler.modSupportHandlerMap.put("minecraft", VanillaHandler.class);

        // 配置必须在注册表可用之前构建（黑名单要遍历 BLOCK_ENTITY_TYPES 注册表）
        config = ModHandler.buildHandlerData();
        ModLoadingContext.get().registerConfig(ModConfig.Type.COMMON, config);
        LOGGER.info("Finished building the config -> {}", config);

        BLOCKS.register(modBus);
        BLOCK_ENTITY_TYPES.register(modBus);
        ITEMS.register(modBus);
    }

    private void setup(final FMLCommonSetupEvent e) {
        // 纸箱自己不能被装进纸箱（防止无限套娃 / 存档膨胀）
        HandlerManager.INSTANCE.banBlock(BOX_BLOCK.get());
        BOX_COLORS.forEach(defBlock -> HandlerManager.INSTANCE.banBlock(defBlock.get()));
        HandlerManager.INSTANCE.banBlockEntity(BOX_BLOCK_ENTITY_TYPE.get());

        // 读取配置并应用黑名单
        ModHandler.loadHandlerData(config);
    }

    private void onCreativeModeTabBuildContents(BuildCreativeModeTabContentsEvent event) {
        if (event.getTabKey() == CreativeModeTabs.FUNCTIONAL_BLOCKS || event.getTabKey() == CreativeModeTabs.COLORED_BLOCKS) {
            event.accept(BOX_ITEM.get());
            BOX_COLORS.forEach(defBlock -> event.accept(defBlock.get()));
        }
    }

    /**
     * 创造模式物品栏里的颜色排序：按"从浅到深、再按色相"排列，而不是按 DyeColor 枚举顺序。
     */
    public enum TabSortedColors {
        WHITE(0),
        LIGHT_GRAY(8),
        GRAY(7),
        BLACK(15),
        BROWN(12),
        RED(14),
        ORANGE(1),
        YELLOW(4),
        LIME(5),
        GREEN(13),
        CYAN(9),
        LIGHT_BLUE(3),
        BLUE(11),
        PURPLE(10),
        MAGENTA(2),
        PINK(6);

        private final int dyeColorId;

        TabSortedColors(int dyeColorId) {
            this.dyeColorId = dyeColorId;
        }

        public DyeColor toDyeColor() {
            return DyeColor.byId(dyeColorId);
        }
    }
}
