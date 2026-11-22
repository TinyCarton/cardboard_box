package com.tinycarton.cardboardbox.handler;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.tinycarton.cardboardbox.box.BoxBlockItem;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;

/**
 * 纸箱全局状态：黑名单 + 每个方块的自定义处理器。
 * <p>
 * 黑名单有两层：
 * <ul>
 *     <li>方块层 {@link #blockBanList}：整个世界都不许搬（活塞、信标、潜影盒……）</li>
 *     <li>方块实体层 {@link #blockEntityBanList}：类型不允许（例如刷怪笼 BE）</li>
 * </ul>
 * 另外 {@code mods.toml} 里的配置会把 Minecraft/其他 mod 的所有方块实体自动列成开关
 * （见 {@code ModHandler#buildConfig}），玩家可在 config 里逐个放开。
 * <p>
 * 注意：<b>没有方块实体的普通方块默认也可以装</b>，此时只保存方块状态。
 */
public class HandlerManager {
    /** 单例，方便静态注册 */
    public static final HandlerManager INSTANCE = new HandlerManager();

    /** 方块实体 Class → 处理器（尚未使用，保留扩展位） */
    public static final Map<Class<? extends BlockEntity>, Handler> pickupHandlerMap = new HashMap<>();
    /** 方块 → 处理器，覆盖默认放置/加载行为 */
    public static final Map<Block, Handler> handlerMap = new HashMap<>();
    /** 被禁用的方块实体类型 */
    public static final List<BlockEntityType<?>> blockEntityBanList = new ArrayList<>();
    /** 被禁用的方块 */
    public static final List<Block> blockBanList = new ArrayList<>();

    /** 是否启用白名单（默认启用） */
    public static boolean whitelistEnabled = true;
    /** 是否启用黑名单（默认禁用） */
    public static boolean blacklistEnabled = false;

    /** 为某个方块实体类注册处理器（预留） */
    public void registerPickupHandler(Class<? extends BlockEntity> clazz, Handler handler) {
        pickupHandlerMap.put(clazz, handler);
    }

    /** 为某个方块注册交互处理器 */
    public void registerHandler(Block block, Handler handler) {
        handlerMap.put(block, handler);
    }

    public Handler getHandler(Block block) {
        return handlerMap.get(block);
    }

    /** 禁用某个方块实体类型（可重复调用） */
    public void banBlockEntity(BlockEntityType<?> tile) {
        if (!blockEntityBanList.contains(tile)) {
            blockEntityBanList.add(tile);
        }
    }

    /** 禁用某个方块（可重复调用） */
    public void banBlock(Block block) {
        if (!blockBanList.contains(block)) {
            blockBanList.add(block);
        }
    }

    /**
     * 判断某个位置能否装进纸箱。
     * <p>
     * 判定顺序：
     * <ol>
     *     <li>黑名单（启用时）优先——同时命中黑白名单的方块按黑名单处理</li>
     *     <li>白名单（启用时）——不在名单内的方块被拒绝</li>
     *     <li>其余方块允许，没有方块实体的只保存方块状态</li>
     * </ol>
     *
     * @see BoxBlockItem#tryToPickupBlock
     */
    public CanPickUpResult canPickUp(Level world, BlockPos pos) {
        Block block = world.getBlockState(pos).getBlock();

        // 硬编码：纸箱不能装纸箱。
        // 套娃会让 NBT 每层嵌套一次，最终把数据撑爆（甚至会拖垮存档读写），
        // 因此这条限制写死在代码里，不受白名单/黑名单配置影响。
        if (block instanceof com.tinycarton.cardboardbox.box.BoxBlock) {
            return CanPickUpResult.BANNED_BLOCK;
        }

        // 黑名单优先级高于白名单
        if (blacklistEnabled) {
            if (blockBanList.contains(block)) {
                return CanPickUpResult.BANNED_BLOCK;
            }
            BlockEntity blockEntity = world.getBlockEntity(pos);
            if (blockEntity != null && blockEntityBanList.contains(blockEntity.getType())) {
                return CanPickUpResult.BANNED_BLOCK_ENTITY;
            }
        }

        if (whitelistEnabled && !WhitelistManager.isAllowed(block)) {
            return CanPickUpResult.NOT_WHITELISTED;
        }

        return CanPickUpResult.CAN_PICK_UP;
    }
}
