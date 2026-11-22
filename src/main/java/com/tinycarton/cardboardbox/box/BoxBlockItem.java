package com.tinycarton.cardboardbox.box;

import static com.tinycarton.cardboardbox.box.BoxBlock.BLOCK_ENTITY_DATA_TAG;
import static com.tinycarton.cardboardbox.box.BoxBlock.STORE_ITEM_TAG;

import java.util.List;

import com.tinycarton.cardboardbox.CardboardBox;
import com.tinycarton.cardboardbox.handler.CanPickUpResult;
import com.tinycarton.cardboardbox.handler.Handler;
import com.tinycarton.cardboardbox.handler.HandlerManager;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/**
 * 纸箱物品（BlockItem），整个模组的核心交互。
 * <p>
 * 内容存在 ItemStack 的 {@link DataComponents#CUSTOM_DATA} 组件里：
 * {@code storedItem}（方块状态 id）与 {@code tileData}（方块实体 NBT）。
 */
public class BoxBlockItem extends BlockItem {

    public BoxBlockItem(Block block) {
        super(block, new Item.Properties());
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        Level level = context.getLevel();
        if (level.isClientSide) {
            return InteractionResult.SUCCESS;
        }

        Player player = context.getPlayer();
        InteractionHand hand = context.getHand();
        final ItemStack heldItemStack = context.getItemInHand();
        if (!heldItemStack.isEmpty()) {
            final BlockState storeBlock = getStoredBlock(heldItemStack);
            if (storeBlock.getBlock() != Blocks.AIR) {
                return tryToPlaceBlock(new BlockPlaceContext(context));
            } else {
                return tryToPickupBlock(player, level, context.getClickedPos(), hand);
            }
        }
        return InteractionResult.FAIL;
    }

    /**
     * 把点击的方块装进箱子：任何方块都能装，没有方块实体的只保存方块状态。
     */
    protected InteractionResult tryToPickupBlock(Player player, Level level, BlockPos pos, InteractionHand hand) {
        CanPickUpResult result = HandlerManager.INSTANCE.canPickUp(level, pos);

        if (result == CanPickUpResult.CAN_PICK_UP) {
            final BlockState state = level.getBlockState(pos).getBlock().defaultBlockState();
            final BlockEntity blockEntity = level.getBlockEntity(pos);
            final CompoundTag tag = blockEntity != null ? blockEntity.saveWithId(level.registryAccess()) : null;

            level.removeBlockEntity(pos);
            BlockState boxState = getBlock().defaultBlockState().setValue(BoxBlock.FACING, player.getDirection().getOpposite());
            level.setBlock(pos, boxState, Block.UPDATE_ALL);

            if (level.getBlockEntity(pos) instanceof BoxBlockEntity boxBlockEntity) {
                boxBlockEntity.setStateForPlacement(state);
                boxBlockEntity.setDataForPlacement(tag);
                player.getItemInHand(hand).shrink(1);
            }
            return InteractionResult.SUCCESS;
        }

        if (result == CanPickUpResult.BANNED_BLOCK_ENTITY) {
            player.displayClientMessage(Component.translatable(CardboardBox.BOX_BLOCK.getDescriptionId() + ".banned.tile"), true);
        } else if (result == CanPickUpResult.BANNED_BLOCK) {
            player.displayClientMessage(Component.translatable(CardboardBox.BOX_BLOCK.getDescriptionId() + ".banned.block"), true);
        } else if (result == CanPickUpResult.NOT_WHITELISTED) {
            player.displayClientMessage(Component.translatable(CardboardBox.BOX_BLOCK.getDescriptionId() + ".notWhitelisted"), true);
        }
        return InteractionResult.SUCCESS;
    }

    /**
     * 把箱子里的内容放回世界。
     */
    protected InteractionResult tryToPlaceBlock(BlockPlaceContext context) {
        BlockPos pos = context.getClickedPos();
        InteractionHand hand = context.getHand();
        float hitX = (float) context.getClickLocation().x;
        float hitY = (float) context.getClickLocation().y;
        float hitZ = (float) context.getClickLocation().z;
        if (!context.canPlace()) {
            pos = pos.relative(context.getClickedFace());
        }

        final ItemStack heldItemStack = context.getItemInHand();
        final BlockState storedBlockState = getStoredBlock(heldItemStack);
        final CompoundTag storedBlockEntityData = getStoredBlockEntityData(heldItemStack);

        if (context.getPlayer().mayUseItemAt(pos, context.getClickedFace(), heldItemStack)
                && context.getLevel().getBlockState(pos).canBeReplaced()) {
            Handler handler = HandlerManager.INSTANCE.getHandler(storedBlockState.getBlock());

            BlockState stateToPlace = storedBlockState.getBlock().defaultBlockState();
            if (!storedBlockState.canSurvive(context.getLevel(), pos)) {
                BlockState adjusted = storedBlockState.getBlock().getStateForPlacement(context);
                if (adjusted != null) {
                    stateToPlace = adjusted;
                }
            }

            if (handler != null && handler.placeBlock(context.getPlayer(), context.getLevel(), pos, hand, context.getClickedFace(), hitX, hitY, hitZ, storedBlockState, storedBlockEntityData)
                    || placeBlock(context, stateToPlace)) {
                BlockState blockstate = context.getLevel().getBlockState(pos);

                if (handler != null) {
                    handler.postPlaceBlock(context.getPlayer(), context.getLevel(), pos, hand, context.getClickedFace(), hitX, hitY, hitZ, storedBlockState, storedBlockEntityData);
                    blockstate = context.getLevel().getBlockState(pos);
                }

                if (storedBlockEntityData != null) {
                    BlockEntity blockEntity = context.getLevel().getBlockEntity(pos);
                    if (blockEntity != null) {
                        if (handler != null) {
                            handler.loadData(blockEntity, storedBlockEntityData, context.getLevel().registryAccess());
                        } else {
                            blockEntity.loadWithComponents(storedBlockEntityData, context.getLevel().registryAccess());
                        }
                    }
                }

                context.getLevel().updateNeighborsAt(pos, blockstate.getBlock());

                SoundType soundtype = blockstate.getSoundType();
                context.getLevel().playSound(null, pos, soundtype.getPlaceSound(), SoundSource.BLOCKS, (soundtype.getVolume() + 1.0F) / 2.0F, soundtype.getPitch() * 0.8F);

                heldItemStack.shrink(1);

                if (!context.getPlayer().isCreative() && !context.getPlayer().getInventory().add(new ItemStack(getBlock()))) {
                    context.getPlayer().drop(new ItemStack(getBlock()), false);
                }
            }
            return InteractionResult.SUCCESS;
        }
        return InteractionResult.FAIL;
    }

    @Override
    public void appendHoverText(ItemStack stack, Item.TooltipContext context, net.minecraft.world.item.component.TooltipDisplay display, java.util.function.Consumer<Component> tooltip, TooltipFlag flagIn) {
        BlockState state = getStoredBlock(stack);
        if (state.getBlock() != Blocks.AIR) {
            tooltip.accept(Component.translatable(state.getBlock().getDescriptionId()));
        }
    }

    /** @return 物品里存的方块状态；没有内容返回空气状态 */
    public BlockState getStoredBlock(ItemStack stack) {
        CompoundTag tag = getStoredTag(stack);
        return tag != null && tag.contains(STORE_ITEM_TAG)
                ? Block.stateById(tag.getInt(STORE_ITEM_TAG).orElse(0))
                : Blocks.AIR.defaultBlockState();
    }

    /** @return 物品里存的方块实体 NBT；没有内容返回 null */
    public CompoundTag getStoredBlockEntityData(ItemStack stack) {
        CompoundTag tag = getStoredTag(stack);
        return tag != null && tag.contains(BLOCK_ENTITY_DATA_TAG)
                ? tag.getCompound(BLOCK_ENTITY_DATA_TAG).orElse(null)
                : null;
    }

    private static CompoundTag getStoredTag(ItemStack stack) {
        CustomData data = stack.get(DataComponents.CUSTOM_DATA);
        return data != null ? data.copyTag() : null;
    }
}
