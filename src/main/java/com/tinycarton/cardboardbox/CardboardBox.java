package com.tinycarton.cardboardbox;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.tinycarton.cardboardbox.box.BoxBlock;
import com.tinycarton.cardboardbox.box.BoxBlockEntity;
import com.tinycarton.cardboardbox.box.BoxBlockItem;
import com.tinycarton.cardboardbox.handler.HandlerManager;
import com.tinycarton.cardboardbox.mods.ModHandler;
import com.tinycarton.cardboardbox.mods.VanillaHandler;

import net.minecraft.world.item.CreativeModeTabs;
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
 * 模组主类：注册纸箱方块、方块实体与物品，并在通用初始化阶段装载搬运黑名单。
 * <p>
 * 纸箱 = 带一个"待还原方块状态 + 待还原 NBT"的 BlockEntity：
 * <ul>
 *     <li>空箱右键方块：把目标方块换成纸箱，方块状态与（如果有的）方块实体 NBT 存进纸箱 BE</li>
 *     <li>带内容纸箱右键地面：把纸箱换回原方块并写回 NBT</li>
 *     <li>装好的纸箱物品只用 ItemStack NBT 记录内容，可以放进背包、箱子、漏斗</li>
 * </ul>
 */
@Mod.EventBusSubscriber(bus = Bus.MOD)
@Mod(CardboardBox.DOMAIN)
public class CardboardBox {
    /** 模组 id，同时也是所有注册名的命名空间 */
    public static final String DOMAIN = "cardboardbox";
    public static final Logger LOGGER = LogManager.getLogger();

    // ===== 方块 =====
    public static final DeferredRegister<Block> BLOCKS = DeferredRegister.create(ForgeRegistries.BLOCKS, DOMAIN);
    public static final RegistryObject<BoxBlock> BOX_BLOCK = BLOCKS.register("cardboard_box", BoxBlock::new);

    // ===== 方块实体 =====
    public static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITY_TYPES = DeferredRegister.create(ForgeRegistries.BLOCK_ENTITY_TYPES, DOMAIN);
    /**
     * 纸箱方块实体。{@code BlockEntityType} 必须把纸箱方块登记为有效方块，
     * 否则区块重新加载时 {@code isValid} 校验失败，纸箱里的内容会被丢弃。
     */
    public static final RegistryObject<BlockEntityType<BoxBlockEntity>> BOX_BLOCK_ENTITY_TYPE = BLOCK_ENTITY_TYPES.register("box",
            () -> BlockEntityType.Builder.of(BoxBlockEntity::new, BOX_BLOCK.get()).build(null));

    // ===== 物品 =====
    public static final DeferredRegister<Item> ITEMS = DeferredRegister.create(ForgeRegistries.ITEMS, DOMAIN);
    public static final RegistryObject<BoxBlockItem> BOX_ITEM = ITEMS.register("cardboard_box", () -> new BoxBlockItem(BOX_BLOCK.get()));

    /** 通用配置：自动生成的所有方块实体黑名单 + 各 mod 处理器选项 */
    private static ForgeConfigSpec config;

    public CardboardBox() {
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
        HandlerManager.INSTANCE.banBlockEntity(BOX_BLOCK_ENTITY_TYPE.get());

        // 读取配置并应用黑名单
        ModHandler.loadHandlerData(config);
    }

    private void onCreativeModeTabBuildContents(BuildCreativeModeTabContentsEvent event) {
        if (event.getTabKey() == CreativeModeTabs.FUNCTIONAL_BLOCKS) {
            event.accept(BOX_ITEM.get());
        }
    }
}
