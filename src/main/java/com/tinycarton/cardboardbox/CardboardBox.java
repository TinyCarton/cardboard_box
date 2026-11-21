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
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.entity.BlockEntityType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 模组主类：注册纸箱方块、方块实体与物品，并装载搬运黑名单。
 * <p>
 * 纸箱 = 带一个"待还原方块状态 + 待还原 NBT"的 BlockEntity：
 * <ul>
 *     <li>空箱右键方块：把目标方块换成纸箱，方块状态与（如果有的）方块实体 NBT 存进纸箱 BE</li>
 *     <li>带内容纸箱右键地面：把纸箱换回原方块并写回 NBT</li>
 *     <li>装好的纸箱物品只用 ItemStack NBT 记录内容，可以放进背包、箱子、漏斗</li>
 * </ul>
 * Fabric 版本：直接注册到 {@link BuiltInRegistries}，通过 {@link ModInitializer} 入口启动。
 */
public class CardboardBox implements ModInitializer {
    /** 模组 id，同时也是所有注册名的命名空间 */
    public static final String MOD_ID = "cardboardbox";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    // ===== 方块 =====
    public static final BoxBlock BOX_BLOCK = Registry.register(BuiltInRegistries.BLOCK, id("cardboardbox"), new BoxBlock());

    // ===== 方块实体 =====
    /**
     * 纸箱方块实体。方块实体类型必须把纸箱方块登记为有效方块，
     * 否则区块重新加载时 Minecraft 会丢弃该方块实体，纸箱里的内容随之消失。
     */
    public static final BlockEntityType<BoxBlockEntity> BOX_BLOCK_ENTITY_TYPE = Registry.register(
            BuiltInRegistries.BLOCK_ENTITY_TYPE,
            id("box"),
            FabricBlockEntityTypeBuilder.create(BoxBlockEntity::new, BOX_BLOCK).build());

    // ===== 物品 =====
    public static final BoxBlockItem BOX_ITEM = Registry.register(
            BuiltInRegistries.ITEM,
            id("cardboardbox"),
            new BoxBlockItem(BOX_BLOCK));

    @Override
    public void onInitialize() {
        // 纸箱自己不能被装进纸箱（防止无限套娃 / 存档膨胀）
        HandlerManager.INSTANCE.banBlock(BOX_BLOCK);
        HandlerManager.INSTANCE.banBlockEntity(BOX_BLOCK_ENTITY_TYPE);

        // 注册内置的 minecraft 处理器（黑名单 + 箱子朝向修正），读取配置
        ModHandler.modSupportHandlerMap.put("minecraft", VanillaHandler.class);
        ModHandler.loadHandlerData();

        ItemGroupEvents.modifyEntriesEvent(CreativeModeTabs.FUNCTIONAL_BLOCKS).register(entries -> entries.accept(BOX_ITEM));
    }

    /** 生成本模组命名空间下的 {@link ResourceLocation} */
    public static ResourceLocation id(String path) {
        return new ResourceLocation(MOD_ID, path);
    }
}
