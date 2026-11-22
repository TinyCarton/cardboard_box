package com.tinycarton.cardboardbox;

import com.tinycarton.cardboardbox.box.BoxBlock;
import com.tinycarton.cardboardbox.box.BoxBlockEntity;
import com.tinycarton.cardboardbox.box.BoxBlockItem;
import com.tinycarton.cardboardbox.handler.HandlerManager;
import com.tinycarton.cardboardbox.mods.ModHandler;
import com.tinycarton.cardboardbox.mods.VanillaHandler;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.creativetab.v1.CreativeModeTabEvents;
import net.fabricmc.fabric.api.object.builder.v1.block.entity.FabricBlockEntityTypeBuilder;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 模组主类：注册纸箱方块、方块实体与物品，并装载搬运白名单 / 黑名单。
 * <p>
 * 26.x 的 {@code BlockBehaviour.Properties} 与 {@code Item.Properties} 必须带注册 id，
 * 否则方块构造时会抛 "Block id not set"。
 */
public class CardboardBox implements ModInitializer {
    /** 模组 id，同时也是所有注册名的命名空间 */
    public static final String MOD_ID = "cardboardbox";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    private static final ResourceKey<net.minecraft.world.level.block.Block> BOX_BLOCK_KEY =
            ResourceKey.create(Registries.BLOCK, id("cardboard_box"));
    private static final ResourceKey<Item> BOX_ITEM_KEY =
            ResourceKey.create(Registries.ITEM, id("cardboard_box"));

    // ===== 方块 =====
    public static final BoxBlock BOX_BLOCK = Registry.register(
            BuiltInRegistries.BLOCK,
            id("cardboard_box"),
            new BoxBlock(BlockBehaviour.Properties.of()
                    .setId(BOX_BLOCK_KEY)
                    .mapColor(MapColor.DIRT)
                    .strength(2f, 2f)));

    // ===== 方块实体 =====
    public static final BlockEntityType<BoxBlockEntity> BOX_BLOCK_ENTITY = Registry.register(
            BuiltInRegistries.BLOCK_ENTITY_TYPE,
            id("box"),
            FabricBlockEntityTypeBuilder.create(BoxBlockEntity::new, BOX_BLOCK).build());

    // ===== 物品 =====
    public static final BoxBlockItem BOX_ITEM = Registry.register(
            BuiltInRegistries.ITEM,
            id("cardboard_box"),
            new BoxBlockItem(BOX_BLOCK, new Item.Properties().setId(BOX_ITEM_KEY)));

    @Override
    public void onInitialize() {
        HandlerManager.INSTANCE.banBlock(BOX_BLOCK);
        HandlerManager.INSTANCE.banBlockEntity(BOX_BLOCK_ENTITY);

        ModHandler.modSupportHandlerMap.put("minecraft", VanillaHandler.class);
        ModHandler.loadHandlerData();

        CreativeModeTabEvents.modifyOutputEvent(CreativeModeTabs.FUNCTIONAL_BLOCKS).register(output -> output.accept(BOX_ITEM));
    }

    /** 生成本模组命名空间下的 {@link Identifier} */
    public static Identifier id(String path) {
        return Identifier.fromNamespaceAndPath(MOD_ID, path);
    }
}
