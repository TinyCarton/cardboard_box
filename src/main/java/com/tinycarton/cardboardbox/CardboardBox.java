package com.tinycarton.cardboardbox;

import com.tinycarton.cardboardbox.box.BoxBlock;
import com.tinycarton.cardboardbox.box.BoxBlockEntity;
import com.tinycarton.cardboardbox.box.BoxBlockItem;
import com.tinycarton.cardboardbox.handler.HandlerManager;
import com.tinycarton.cardboardbox.mods.ModHandler;
import com.tinycarton.cardboardbox.mods.VanillaHandler;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.ModLoadingContext;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;
import net.neoforged.neoforge.event.BuildCreativeModeTabContentsEvent;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 模组主类：注册纸箱方块、方块实体与物品，并装载搬运白名单 / 黑名单。
 * <p>
 * NeoForge 1.20.2 起包名从 {@code net.minecraftforge} 改为 {@code net.neoforged}。
 * 这里用通用的 {@link DeferredRegister}（而不是 1.20.4+ 才有的 Blocks/Items 便捷子类），
 * 同一份代码也能在 1.20.3/1.20.4 上编译。
 */
@Mod(CardboardBox.MOD_ID)
public class CardboardBox {
    /** 模组 id，同时也是所有注册名的命名空间 */
    public static final String MOD_ID = "cardboardbox";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    // ===== 注册器 =====
    public static final DeferredRegister<Block> BLOCKS = DeferredRegister.create(BuiltInRegistries.BLOCK, MOD_ID);
    public static final DeferredRegister<Item> ITEMS = DeferredRegister.create(BuiltInRegistries.ITEM, MOD_ID);
    public static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITY_TYPES =
            DeferredRegister.create(BuiltInRegistries.BLOCK_ENTITY_TYPE, MOD_ID);

    // ===== 方块 =====
    public static final DeferredHolder<Block, BoxBlock> BOX_BLOCK = BLOCKS.register("cardboard_box", () -> new BoxBlock());

    // ===== 方块实体 =====
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<BoxBlockEntity>> BOX_BLOCK_ENTITY_TYPE =
            BLOCK_ENTITY_TYPES.register("box",
                    () -> BlockEntityType.Builder.of(BoxBlockEntity::new, BOX_BLOCK.get()).build(null));

    // ===== 物品 =====
    public static final DeferredHolder<Item, BoxBlockItem> BOX_ITEM =
            ITEMS.register("cardboard_box", () -> new BoxBlockItem(BOX_BLOCK.get()));

    public CardboardBox(IEventBus modBus, ModContainer container) {
        BLOCKS.register(modBus);
        ITEMS.register(modBus);
        BLOCK_ENTITY_TYPES.register(modBus);

        ModLoadingContext.get().registerConfig(ModConfig.Type.COMMON, ModHandler.buildHandlerData());

        ModHandler.modSupportHandlerMap.put("minecraft", VanillaHandler.class);

        modBus.addListener(this::onCommonSetup);
        modBus.addListener(this::onBuildCreativeTab);
    }

    /**
     * 注册完成 + 配置加载后：登记纸箱自身到黑名单，并装载各 mod 适配器与白名单。
     */
    private void onCommonSetup(FMLCommonSetupEvent event) {
        event.enqueueWork(() -> {
            HandlerManager.INSTANCE.banBlock(BOX_BLOCK.get());
            HandlerManager.INSTANCE.banBlockEntity(BOX_BLOCK_ENTITY_TYPE.get());
            ModHandler.loadHandlerData();
        });
    }

    /** 把纸箱放进"功能方块"创造栏 */
    private void onBuildCreativeTab(BuildCreativeModeTabContentsEvent event) {
        if (event.getTabKey() == CreativeModeTabs.FUNCTIONAL_BLOCKS) {
            event.accept(BOX_ITEM.get());
        }
    }

    /** 生成本模组命名空间下的 {@link ResourceLocation} */
    public static ResourceLocation id(String path) {
        return new ResourceLocation(MOD_ID, path);
    }
}
