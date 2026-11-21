package com.tinycarton.cardboardboxes.box;

import javax.annotation.Nullable;

import com.tinycarton.cardboardboxes.Cardboardboxes;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;

/**
 * 纸箱方块。
 * <p>
 * 两种形态共用一个类：
 * <ul>
 *     <li>放置在世界里的"装满东西的纸箱"：状态由 {@link BoxBlockEntity} 记录，外观仍是纸箱</li>
 *     <li>物品形态的纸箱：内容记录在 ItemStack 的 NBT 里（{@link #toItemStack}）</li>
 * </ul>
 * 交互（{@link #attack} 与 {@link #use}）：
 * <ul>
 *     <li>左键敲击（{@link #attack}）：把纸箱里的东西原样放回去，玩家拿回一个空纸箱</li>
 *     <li>潜行 + 右键（{@link #use}）：把纸箱变成物品，内容包括存档数据一起带走</li>
 * </ul>
 */
public class BoxBlock extends BaseEntityBlock {
    /** ItemStack NBT：被装载方块的 BlockState.getId()（int） */
    public static final String STORE_ITEM_TAG = "storedItem";
    /** ItemStack NBT：被装载方块实体的 NBT */
    public static final String BLOCK_ENTITY_DATA_TAG = "tileData";

    /** 染色颜色，null = 未染色的普通纸箱 */
    public final DyeColor color;

    public BoxBlock(DyeColor color) {
        super(BlockBehaviour.Properties.of()
                .mapColor(color == null ? MapColor.DIRT : color.getMapColor())
                .strength(2f, 2f));
        this.color = color;
    }

    @Override
    public RenderShape getRenderShape(BlockState state) {
        // 使用普通方块模型（不是 ENTITYBLOCK_ANIMATED），这样可以用 blockstate/model 直接渲染
        return RenderShape.MODEL;
    }

    /**
     * 左键敲击纸箱：把存的方块放回原位，并把空纸箱还给玩家。
     * <p>
     * 对应反编译里的 {@code m_6256_} = {@code BlockBehaviour#attack}。
     */
    // 1.20.1 的 BlockBehaviour#attack 被标记为 @Deprecated，但左键交互仍然只有这一个入口
    @Override
    @SuppressWarnings("deprecation")
    public void attack(BlockState state, Level level, BlockPos pos, Player player) {
        if (!level.isClientSide) {
            if (level.getBlockEntity(pos) instanceof BoxBlockEntity boxBlockEntity && boxBlockEntity.getStateForPlacement() != null) {
                // 1) 把纸箱位置换回原来的方块
                if (level.setBlock(pos, boxBlockEntity.getStateForPlacement(), Block.UPDATE_ALL)) {
                    // 2) 写回方块实体数据（容器里的东西、机器设置等）
                    CompoundTag compound = boxBlockEntity.getDataForPlacement();
                    if (compound != null) {
                        BlockEntity blockEntity = level.getBlockEntity(pos);
                        if (blockEntity != null) {
                            blockEntity.load(compound);
                        }
                    }
                    // 3) 还给玩家一个空纸箱
                    if (!player.isCreative()) {
                        ItemStack stack = new ItemStack(this);
                        // 注意：add() 成功时 stack 会被清空，此时 spawnAtLocation 实际不会掉落东西；
                        // add() 失败才需要掉在地上。原版逻辑就是这样的写法，行为是正确的。
                        if (player.getInventory().add(stack)) {
                            player.spawnAtLocation(stack, 0F);
                        }
                    }
                }
            }
        }
    }

    /**
     * 潜行 + 右键：把纸箱收成物品（内容随 NBT 一起走）。
     */
    // 同上：BlockBehaviour#use 同样被标记弃用，1.20.1 右键交互只能重写它
    @Override
    @SuppressWarnings("deprecation")
    public InteractionResult use(BlockState state, Level level, BlockPos pos, Player player, InteractionHand handIn, BlockHitResult hit) {
        if (level.isClientSide) {
            return InteractionResult.PASS;
        }
        if (player.isShiftKeyDown()) {
            ItemStack stack = toItemStack(level, pos);
            if (stack != null) {
                if (player.getInventory().add(stack)) {
                    player.getInventory().setChanged();
                    level.removeBlock(pos, false);
                    return InteractionResult.SUCCESS;
                } else {
                    player.displayClientMessage(Component.translatable(Cardboardboxes.BOX_BLOCK.get().getDescriptionId() + ".inventoryFull"), true);
                    return InteractionResult.PASS;
                }
            } else {
                player.displayClientMessage(Component.translatable(Cardboardboxes.BOX_BLOCK.get().getDescriptionId() + ".error.stack.null"), true);
            }
        }
        return InteractionResult.PASS;
    }

    /**
     * 把世界里的纸箱转成带 NBT 的物品形态。
     *
     * @return 永远非 null：空纸箱（没有存储内容）就返回无 NBT 的箱子
     */
    public ItemStack toItemStack(BlockGetter level, BlockPos pos) {
        ItemStack stack = new ItemStack(this);

        if (level.getBlockEntity(pos) instanceof BoxBlockEntity blockEntity) {
            if (blockEntity.getStateForPlacement() != null) {
                stack.setTag(new CompoundTag());
                // 方块状态用全局 id 存，反序列化时用 Block.stateById 还原
                stack.getTag().putInt(STORE_ITEM_TAG, Block.getId(blockEntity.getStateForPlacement()));
                if (blockEntity.getDataForPlacement() != null) {
                    stack.getTag().put(BLOCK_ENTITY_DATA_TAG, blockEntity.getDataForPlacement());
                }
            } else {
                Cardboardboxes.LOGGER.warn("Box at {} has no stored state while being converted to an item", pos);
            }
        }
        return stack;
    }

    /**
     * 创造模式中键取方块 / 结构方块保存时使用，保证复制出来的物品也带内容。
     */
    @Override
    public ItemStack getCloneItemStack(BlockState state, HitResult target, BlockGetter level, BlockPos pos, Player player) {
        return toItemStack(level, pos);
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new BoxBlockEntity(pos, state);
    }
}
