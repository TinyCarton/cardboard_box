package com.tinycarton.cardboardbox.box;

import com.mojang.serialization.MapCodec;
import com.tinycarton.cardboardbox.CardboardBox;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
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
 * 物品形态的内容记录在 ItemStack 的 CUSTOM_DATA 组件里（{@link #toItemStack}）。
 * <p>
 * 交互：
 * <ul>
 *     <li>右键：只取下纸箱，里面的方块留在原位（空箱则直接取走空箱）</li>
 *     <li>潜行 + 右键：连同方块一起收进纸箱带走</li>
 *     <li>左键敲击：把存的方块放回原位，并把空纸箱还给玩家</li>
 * </ul>
 */
public class BoxBlock extends BaseEntityBlock {
    /** 水平朝向（仅平面四向） */
    public static final DirectionProperty FACING = BlockStateProperties.HORIZONTAL_FACING;
    /** 物品数据键：被装载方块的 BlockState.getId()（int） */
    public static final String STORE_ITEM_TAG = "storedItem";
    /** 物品数据键：被装载方块实体的 NBT（方块没有方块实体时不写入） */
    public static final String BLOCK_ENTITY_DATA_TAG = "tileData";

    public BoxBlock(BlockBehaviour.Properties properties) {
        super(properties);
        // 只有水平四向：默认朝北
        registerDefaultState(defaultBlockState().setValue(FACING, Direction.NORTH));
    }

    public BoxBlock() {
        this(BlockBehaviour.Properties.of()
                .mapColor(MapColor.DIRT)
                .strength(2f, 2f));
    }

    @Override
    protected MapCodec<BoxBlock> codec() {
        return simpleCodec(BoxBlock::new);
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
        return RenderShape.MODEL;
    }

    /**
     * 左键敲击纸箱：把存的方块放回原位，并把空纸箱还给玩家。
     */
    @Override
    protected void attack(BlockState state, Level level, BlockPos pos, Player player) {
        if (!level.isClientSide) {
            restoreStoredBlock(level, pos, player);
        }
    }

    /**
     * 右键纸箱：普通右键只取箱、潜行右键连方块一起取走。
     */
    @Override
    protected ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit) {
        if (level.isClientSide) {
            return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        }

        boolean hasContent = level.getBlockEntity(pos) instanceof BoxBlockEntity boxBlockEntity
                && boxBlockEntity.getStateForPlacement() != null;

        // 潜行右键 = 连方块一起取下；空箱右键 = 直接取走空箱
        if (player.isShiftKeyDown() || !hasContent) {
            ItemStack box = toItemStack(level, pos);
            if (box == null || box.isEmpty()) {
                player.displayClientMessage(Component.translatable(CardboardBox.BOX_BLOCK.getDescriptionId() + ".error.stack.null"), true);
                return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
            }
            if (!player.getInventory().add(box)) {
                player.displayClientMessage(Component.translatable(CardboardBox.BOX_BLOCK.getDescriptionId() + ".inventoryFull"), true);
                return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
            }
            player.getInventory().setChanged();
            level.removeBlock(pos, false);
            return ItemInteractionResult.SUCCESS;
        }

        return restoreStoredBlock(level, pos, player)
                ? ItemInteractionResult.SUCCESS
                : ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
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
        if (!level.setBlock(pos, boxBlockEntity.getStateForPlacement().getBlock().defaultBlockState(), Block.UPDATE_ALL)) {
            return false;
        }
        CompoundTag compound = boxBlockEntity.getDataForPlacement();
        if (compound != null) {
            BlockEntity blockEntity = level.getBlockEntity(pos);
            if (blockEntity != null) {
                blockEntity.loadWithComponents(compound, level.registryAccess());
            }
        }
        level.updateNeighborsAt(pos, level.getBlockState(pos).getBlock());

        if (!player.isCreative()) {
            ItemStack box = new ItemStack(this);
            if (player.getInventory().add(box)) {
                player.spawnAtLocation(box, 0F);
            }
        }
        return true;
    }

    /**
     * 把世界里的纸箱转成带数据的物品形态。
     *
     * @return 永远非 null：空纸箱（没有存储内容）就返回无数据的箱子
     */
    public ItemStack toItemStack(BlockGetter level, BlockPos pos) {
        ItemStack stack = new ItemStack(this);

        if (level.getBlockEntity(pos) instanceof BoxBlockEntity blockEntity) {
            if (blockEntity.getStateForPlacement() != null) {
                CompoundTag tag = new CompoundTag();
                tag.putInt(STORE_ITEM_TAG, Block.getId(blockEntity.getStateForPlacement()));
                if (blockEntity.getDataForPlacement() != null) {
                    tag.put(BLOCK_ENTITY_DATA_TAG, blockEntity.getDataForPlacement());
                }
                stack.set(net.minecraft.core.component.DataComponents.CUSTOM_DATA,
                        net.minecraft.world.item.component.CustomData.of(tag));
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
    public ItemStack getCloneItemStack(LevelReader level, BlockPos pos, BlockState state) {
        return toItemStack(level, pos);
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new BoxBlockEntity(pos, state);
    }
}
