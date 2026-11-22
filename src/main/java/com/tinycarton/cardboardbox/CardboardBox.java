package com.tinycarton.cardboardbox;

import com.tinycarton.cardboardbox.box.BoxBlock;
import com.tinycarton.cardboardbox.box.BoxBlockEntity;
import com.tinycarton.cardboardbox.box.BoxBlockItem;
import com.tinycarton.cardboardbox.handler.HandlerManager;
import com.tinycarton.cardboardbox.mods.ModHandler;
import com.tinycarton.cardboardbox.mods.VanillaHandler;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.itemgroup.v1.ItemGroupEvents;
import net.fabricmc.fabric.api.object.builder.v1.block.entity.FabricBlockEntityTypeBuilder;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.entity.BlockEntityType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 模组主类：注册纸箱方块、方块实体与物品，并装载搬运白名单 / 黑名单。
 */
public class CardboardBox implements ModInitializer {
    /** 模组 id，同时也是所有注册名的命名空间 */
    public static final String MOD_ID = "cardboardbox";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    // ===== 方块 =====
    public static final BoxBlock BOX_BLOCK = Registry.register(BuiltInRegistries.BLOCK, id("cardboard_box"), new BoxBlock());

    // ===== 方块实体 =====
    public static final BlockEntityType<BoxBlockEntity> BOX_BLOCK_ENTITY_TYPE = Registry.register(
            BuiltInRegistries.BLOCK_ENTITY_TYPE,
            id("box"),
            FabricBlockEntityTypeBuilder.create(BoxBlockEntity::new, BOX_BLOCK).build());

    // ===== 物品 =====
    public static final BoxBlockItem BOX_ITEM = Registry.register(
            BuiltInRegistries.ITEM,
            id("cardboard_box"),
            new BoxBlockItem(BOX_BLOCK));

    @Override
    public void onInitialize() {
        HandlerManager.INSTANCE.banBlock(BOX_BLOCK);
        HandlerManager.INSTANCE.banBlockEntity(BOX_BLOCK_ENTITY_TYPE);

        ModHandler.modSupportHandlerMap.put("minecraft", VanillaHandler.class);
        ModHandler.loadHandlerData();

        ItemGroupEvents.modifyEntriesEvent(CreativeModeTabs.FUNCTIONAL_BLOCKS).register(entries -> entries.accept(BOX_ITEM));
    }

    /** 生成本模组命名空间下的 {@link Identifier} */
    public static Identifier id(String path) {
        return Identifier.fromNamespaceAndPath(MOD_ID, path);
    }
}
