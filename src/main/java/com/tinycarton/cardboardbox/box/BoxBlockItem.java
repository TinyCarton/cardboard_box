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
 * 右键逻辑分两条路：
 * <ol>
 *     <li>物品里没有 storedItem（空箱）→ 尝试把点击的方块装进箱子（{@link #tryToPickupBlock}）</li>
 *     <li>物品里有 storedItem（装满）→ 尝试把内容放回世界（{@link #tryToPlaceBlock}）</li>
 * </ol>
 * 服务端权威：客户端直接返回 SUCCESS，避免客户端做半套状态导致方块鬼影。
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
                // 箱子里有东西 → 放置
                return tryToPlaceBlock(new BlockPlaceContext(context));
            } else {
                // 空箱子 → 装方块
                return tryToPickupBlock(player, level, context.getClickedPos(), hand);
            }
        }
        return InteractionResult.FAIL;
    }

    /**
     * 把点击的方块装进箱子。
     * <p>
     * 任何方块都可以装：没有方块实体的方块只保存方块状态，有方块实体的额外保存其 NBT。
     * <p>
     * 步骤：校验黑名单 → 抓 BlockState → 抓 BE 的 saveWithId（可能为 null）→ 移除原 BE
     * → 放上纸箱方块并触发方块更新 → 把数据塞进纸箱 BE → 消耗一个箱子物品。
     */
    protected InteractionResult tryToPickupBlock(Player player, Level level, BlockPos pos, InteractionHand hand) {
        CanPickUpResult result = HandlerManager.INSTANCE.canPickUp(level, pos);

        if (result == CanPickUpResult.CAN_PICK_UP) {
            final BlockState state = level.getBlockState(pos);
            final BlockEntity blockEntity = level.getBlockEntity(pos);
            // 普通方块没有方块实体，此时只保存方块状态
            final CompoundTag tag = blockEntity != null ? blockEntity.saveWithId() : null;

            // 先移除旧 BE，再替换方块，避免旧 BE 残留
            level.removeBlockEntity(pos);
            // UPDATE_ALL = UPDATE_NEIGHBORS | UPDATE_CLIENTS：
            // 通知客户端，同时让周围方块（红石、栅栏连接、上方方块的支撑判定）立刻收到更新
            // 纸箱正面朝向玩家（仅水平四向）
            BlockState boxState = getBlock().defaultBlockState().setValue(BoxBlock.FACING, player.getDirection().getOpposite());
            level.setBlock(pos, boxState, Block.UPDATE_ALL);

            if (level.getBlockEntity(pos) instanceof BoxBlockEntity boxBlockEntity) {
                boxBlockEntity.setStateForPlacement(state);
                boxBlockEntity.setDataForPlacement(tag);

                // 消耗手上的纸箱
                player.getItemInHand(hand).shrink(1);
            }
            return InteractionResult.SUCCESS;
        }

        if (result == CanPickUpResult.BANNED_BLOCK_ENTITY) {
            player.displayClientMessage(Component.translatable(CardboardBox.BOX_BLOCK.get().getDescriptionId() + ".banned.tile"), true);
        } else if (result == CanPickUpResult.BANNED_BLOCK) {
            player.displayClientMessage(Component.translatable(CardboardBox.BOX_BLOCK.get().getDescriptionId() + ".banned.block"), true);
        }
        return InteractionResult.SUCCESS;
    }

    /**
     * 把箱子里的内容放回世界。
     * <p>
     * 关键点：
     * <ul>
     *     <li>优先沿用原来的方块状态（朝向、连接、水位等），放不下时才按点击面重新计算</li>
     *     <li>方块放置优先交给 {@link Handler}（模组方块可以自定义放置逻辑）</li>
     *     <li>没有 handler 就走原版 {@link BlockItem#placeBlock}</li>
     *     <li>放置后写回 NBT，并触发一次邻居更新</li>
     * </ul>
     */
    protected InteractionResult tryToPlaceBlock(BlockPlaceContext context) {
        BlockPos pos = context.getClickedPos();
        InteractionHand hand = context.getHand();
        float hitX = (float) context.getClickLocation().x;
        float hitY = (float) context.getClickLocation().y;
        float hitZ = (float) context.getClickLocation().z;
        // 点击位置不可替换时，沿点击面外移一格
        if (!context.canPlace()) {
            pos = pos.relative(context.getClickedFace());
        }

        final ItemStack heldItemStack = context.getItemInHand();
        final BlockState storedBlockState = getStoredBlock(heldItemStack);
        final CompoundTag storedBlockEntityData = getStoredBlockEntityData(heldItemStack);

        if (context.getPlayer().mayUseItemAt(pos, context.getClickedFace(), heldItemStack)
                && context.getLevel().getBlockState(pos).canBeReplaced()) {
            Handler handler = HandlerManager.INSTANCE.getHandler(storedBlockState.getBlock());

            // 优先还原原状态，保证朝向、连接、含水等属性一致
            BlockState stateToPlace = storedBlockState;
            if (!storedBlockState.canSurvive(context.getLevel(), pos)) {
                // 原状态在当前位置无法存活（例如悬空的火把），按点击面重新计算
                BlockState adjusted = storedBlockState.getBlock().getStateForPlacement(context);
                if (adjusted != null) {
                    stateToPlace = adjusted;
                }
            }

            // handler 优先；它返回 false 时退回原版放置逻辑（flag 11 已包含邻居更新）
            if (handler != null && handler.placeBlock(context.getPlayer(), context.getLevel(), pos, hand, context.getClickedFace(), hitX, hitY, hitZ, storedBlockState, storedBlockEntityData)
                    || placeBlock(context, stateToPlace)) {
                BlockState blockstate = context.getLevel().getBlockState(pos);

                // 放置后修正（例如箱子朝向）
                if (handler != null) {
                    handler.postPlaceBlock(context.getPlayer(), context.getLevel(), pos, hand, context.getClickedFace(), hitX, hitY, hitZ, storedBlockState, storedBlockEntityData);
                    blockstate = context.getLevel().getBlockState(pos);
                }

                // 写回方块实体数据
                if (storedBlockEntityData != null) {
                    BlockEntity blockEntity = context.getLevel().getBlockEntity(pos);
                    if (blockEntity != null) {
                        if (handler != null) {
                            handler.loadData(blockEntity, storedBlockEntityData);
                        } else {
                            blockEntity.load(storedBlockEntityData);
                        }
                    }
                }

                // 触发一次邻居更新：让周围方块按新放下的方块重新评估状态
                context.getLevel().updateNeighborsAt(pos, blockstate.getBlock());

                // 播放被放置方块自己的音效
                SoundType soundtype = blockstate.getBlock().getSoundType(blockstate, context.getLevel(), pos, context.getPlayer());
                context.getLevel().playSound(null, pos, soundtype.getPlaceSound(), SoundSource.BLOCKS, (soundtype.getVolume() + 1.0F) / 2.0F, soundtype.getPitch() * 0.8F);

                // 消耗箱子
                heldItemStack.shrink(1);

                // 返还一个空箱（背包满了就掉在脚下）
                if (!context.getPlayer().isCreative() && !context.getPlayer().getInventory().add(new ItemStack(getBlock()))) {
                    context.getPlayer().spawnAtLocation(new ItemStack(getBlock()), 0F);
                }
            }
            return InteractionResult.SUCCESS;
        }
        return InteractionResult.FAIL;
    }

    @Override
    public int getMaxStackSize(ItemStack stack) {
        // 装了东西的箱子不能叠加，空箱可以叠 64
        return stack.hasTag() ? 1 : 64;
    }

    @Override
    public void appendHoverText(ItemStack stack, Level worldIn, List<Component> tooltip, TooltipFlag flagIn) {
        if (stack.getTag() != null && stack.getTag().contains(STORE_ITEM_TAG)) {
            BlockState state = Block.stateById(stack.getTag().getInt(STORE_ITEM_TAG));
            tooltip.add(Component.translatable(state.getBlock().getDescriptionId()));
        }
    }

    /** @return 物品里存的方块状态；没有内容返回空气状态 */
    public BlockState getStoredBlock(ItemStack stack) {
        return stack.getTag() != null && stack.getTag().contains(STORE_ITEM_TAG)
                ? Block.stateById(stack.getTag().getInt(STORE_ITEM_TAG))
                : Blocks.AIR.defaultBlockState();
    }

    /** @return 物品里存的方块实体 NBT；方块没有方块实体时返回 null */
    public CompoundTag getStoredBlockEntityData(ItemStack stack) {
        return stack.getTag() != null && stack.getTag().contains(BLOCK_ENTITY_DATA_TAG)
                ? stack.getTag().getCompound(BLOCK_ENTITY_DATA_TAG)
                : null;
    }
}
