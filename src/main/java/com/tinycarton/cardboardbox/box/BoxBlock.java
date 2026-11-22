package com.tinycarton.cardboardbox.box;

import com.tinycarton.cardboardbox.CardboardBox;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.phys.BlockHitResult;

/**
 * 纸箱方块。
 * <p>
 * 世界里的"装满东西的纸箱"由 {@link BoxBlockEntity} 记录内容，外观仍是纸箱；
 * 物品形态的内容记录在 ItemStack 的 NBT 里（{@link #toItemStack}）。
 * <p>
 * 交互：
 * <ul>
 *     <li>左键敲击（{@link #attack}）：把纸箱里的方块原样放回去，玩家拿回一个空纸箱</li>
 *     <li>潜行 + 右键（{@link #use}）：把纸箱收成物品，内容一起带走</li>
 * </ul>
 */
public class BoxBlock extends BaseEntityBlock {
    /** 水平朝向（仅平面四向） */
    public static final DirectionProperty FACING = BlockStateProperties.HORIZONTAL_FACING;
    /** ItemStack NBT：被装载方块的 BlockState.getId()（int） */
    public static final String STORE_ITEM_TAG = "storedItem";
    /** ItemStack NBT：被装载方块实体的 NBT（方块没有方块实体时不写入） */
    public static final String BLOCK_ENTITY_DATA_TAG = "tileData";

    public BoxBlock() {
        super(BlockBehaviour.Properties.of()
                .mapColor(MapColor.DIRT)
                .strength(2f, 2f));
        // 只有水平四向：默认朝北
        registerDefaultState(defaultBlockState().setValue(FACING, Direction.NORTH));
    }

    /** 1.20.3+ 的 BaseEntityBlock 抽象方法 */
    protected com.mojang.serialization.MapCodec<? extends BaseEntityBlock> codec() {
        return com.mojang.serialization.MapCodec.unit(this);
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING);
    }

    /**
     * 放置朝向：只考虑水平方向，正面朝向玩家。
     */
    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return defaultBlockState().setValue(FACING, context.getHorizontalDirection().getOpposite());
    }

    @Override
    public RenderShape getRenderShape(BlockState state) {
        // 使用普通方块模型（不是 ENTITYBLOCK_ANIMATED），这样可以用 blockstate/model 直接渲染
        return RenderShape.MODEL;
    }

    /**
     * 左键敲击纸箱：把存的方块放回原位，并把空纸箱还给玩家。
     */
    // 1.20.1 的 BlockBehaviour#attack 被标记为 @Deprecated，但左键交互仍然只有这一个入口
    @Override
    @SuppressWarnings("deprecation")
    public void attack(BlockState state, Level level, BlockPos pos, Player player) {
        if (!level.isClientSide) {
            restoreStoredBlock(level, pos, player);
        }
    }

    /**
     * 右键纸箱：
     * <ul>
     *     <li>普通右键：只把纸箱取下，里面的方块留在原位（空箱则直接取走空箱）</li>
     *     <li>潜行 + 右键：连同方块一起收进纸箱带走</li>
     * </ul>
     */
    // 同上：BlockBehaviour#use 同样被标记弃用，1.20.1 右键交互只能重写它
    @Override
    @SuppressWarnings("deprecation")
    public InteractionResult use(BlockState state, Level level, BlockPos pos, Player player, InteractionHand handIn, BlockHitResult hit) {
        if (level.isClientSide) {
            return InteractionResult.PASS;
        }

        boolean hasContent = level.getBlockEntity(pos) instanceof BoxBlockEntity boxBlockEntity
                && boxBlockEntity.getStateForPlacement() != null;

        // 潜行右键 = 连方块一起取下；空箱右键 = 直接取走空箱
        if (player.isShiftKeyDown() || !hasContent) {
            ItemStack stack = toItemStack(level, pos);
            if (stack == null) {
                player.displayClientMessage(Component.translatable(CardboardBox.BOX_BLOCK.getDescriptionId() + ".error.stack.null"), true);
                return InteractionResult.PASS;
            }
            if (!player.getInventory().add(stack)) {
                player.displayClientMessage(Component.translatable(CardboardBox.BOX_BLOCK.getDescriptionId() + ".inventoryFull"), true);
                return InteractionResult.PASS;
            }
            player.getInventory().setChanged();
            // removeBlock 内部使用 flag 3，会触发周围方块的邻居更新
            level.removeBlock(pos, false);
            return InteractionResult.SUCCESS;
        }

        // 普通右键：只取下纸箱，内容方块留在原位
        return restoreStoredBlock(level, pos, player) ? InteractionResult.SUCCESS : InteractionResult.PASS;
    }

    /**
     * 把纸箱里存的方块放回原位，写回方块实体数据，并在非创造模式下返还一个空纸箱。
     *
     * @return 是否成功还原（纸箱为空时返回 false）
     */
    private boolean restoreStoredBlock(Level level, BlockPos pos, Player player) {
        if (!(level.getBlockEntity(pos) instanceof BoxBlockEntity boxBlockEntity) || boxBlockEntity.getStateForPlacement() == null) {
            return false;
        }
        // 1) 把纸箱位置换回原来的方块（UPDATE_ALL：通知客户端并触发周围方块的邻居更新）
        if (!level.setBlock(pos, boxBlockEntity.getStateForPlacement().getBlock().defaultBlockState(), Block.UPDATE_ALL)) {
            return false;
        }
        // 2) 写回方块实体数据（容器里的东西、机器设置等）
        CompoundTag compound = boxBlockEntity.getDataForPlacement();
        if (compound != null) {
            BlockEntity blockEntity = level.getBlockEntity(pos);
            if (blockEntity != null) {
                blockEntity.load(compound);
            }
        }
        // 3) 让周围方块重新评估（红石、栅栏连接、上方方块的支撑判定）
        level.updateNeighborsAt(pos, level.getBlockState(pos).getBlock());

        // 4) 还给玩家一个空纸箱
        if (!player.isCreative()) {
            ItemStack stack = new ItemStack(this);
            // add() 成功时 stack 会被清空，此时 spawnAtLocation 实际不会掉落东西；
            // add() 失败才需要掉在地上。
            if (player.getInventory().add(stack)) {
                player.spawnAtLocation(stack, 0F);
            }
        }
        return true;
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
                // 方块状态用全局 id 存，反序列化时用 Block.stateById 还原（含全部属性）
                stack.getTag().putInt(STORE_ITEM_TAG, Block.getId(blockEntity.getStateForPlacement()));
                if (blockEntity.getDataForPlacement() != null) {
                    stack.getTag().put(BLOCK_ENTITY_DATA_TAG, blockEntity.getDataForPlacement());
                }
            } else {
                CardboardBox.LOGGER.warn("Box at {} has no stored state while being converted to an item", pos);
            }
        }
        return stack;
    }

    /**
     * 创造模式中键取方块 / 结构方块保存时使用，保证复制出来的物品也带内容。
     */
    @Override
    public ItemStack getCloneItemStack(BlockGetter level, BlockPos pos, BlockState state) {
        return toItemStack(level, pos);
    }    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new BoxBlockEntity(pos, state);
    }
}
