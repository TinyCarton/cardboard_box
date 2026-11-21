package com.tinycarton.cardboardbox.mods;

import com.tinycarton.cardboardbox.handler.Handler;
import com.tinycarton.cardboardbox.handler.HandlerManager;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;

/**
 * 原版 Minecraft 的纸箱适配。
 * <p>
 * 做两件事：
 * <ol>
 *     <li>补一个 {@link Handler}：箱子被纸箱放回去以后朝向会翻，这里改成朝玩家</li>
 *     <li>默认黑名单只包含原版中「无法破坏」的方块（基岩、命令方块、屏障、传送门等）</li>
 * </ol>
 */
public class VanillaHandler extends ModHandler {

    @Override
    public void load() {
        if (Config.get().blackListMobSpawners) {
            HandlerManager.INSTANCE.banBlock(Blocks.SPAWNER);
            HandlerManager.INSTANCE.banBlockEntity(BlockEntityType.MOB_SPAWNER);
        }

        // 修正箱子朝向：纸箱还原时 getStateForPlacement 会按"玩家朝向的反方向"给出 FACING，
        // 实际放下去经常反着，这里统一按玩家朝向反方向设置。
        HandlerManager.INSTANCE.registerHandler(Blocks.CHEST, new Handler() {
            @Override
            public void postPlaceBlock(Player player, Level level, BlockPos pos, InteractionHand hand, Direction direction, float hitX, float hitY, float hitZ, BlockState state, CompoundTag saveData) {
                BlockState blockstate = level.getBlockState(pos);
                if (blockstate.getBlock() == Blocks.CHEST && blockstate.getValue(ChestBlock.FACING) != player.getDirection().getOpposite()) {
                    blockstate = blockstate.setValue(ChestBlock.FACING, player.getDirection().getOpposite());
                    level.setBlockAndUpdate(pos, blockstate);
                }
            }
        });

        // ===== 默认黑名单：原版中无法破坏的方块 =====
        // 这些方块要么硬度为 -1，要么只能在创造模式/管理员权限下移除，
        // 允许装进纸箱等同于给了玩家绕过权限的搬运手段。
        banUnbreakable(Blocks.BEDROCK);
        banUnbreakable(Blocks.REINFORCED_DEEPSLATE);
        banUnbreakable(Blocks.COMMAND_BLOCK);
        banUnbreakable(Blocks.CHAIN_COMMAND_BLOCK);
        banUnbreakable(Blocks.REPEATING_COMMAND_BLOCK);
        banUnbreakable(Blocks.STRUCTURE_BLOCK);
        banUnbreakable(Blocks.JIGSAW);
        banUnbreakable(Blocks.BARRIER);
        banUnbreakable(Blocks.LIGHT);
        banUnbreakable(Blocks.END_PORTAL);
        banUnbreakable(Blocks.END_GATEWAY);
        banUnbreakable(Blocks.END_PORTAL_FRAME);
        banUnbreakable(Blocks.NETHER_PORTAL);
        banUnbreakable(Blocks.MOVING_PISTON);

        // 对应方块实体类型同样禁用，避免通过其他路径被装走
        HandlerManager.INSTANCE.banBlockEntity(BlockEntityType.COMMAND_BLOCK);
        HandlerManager.INSTANCE.banBlockEntity(BlockEntityType.STRUCTURE_BLOCK);
        HandlerManager.INSTANCE.banBlockEntity(BlockEntityType.JIGSAW);
        HandlerManager.INSTANCE.banBlockEntity(BlockEntityType.END_PORTAL);
        HandlerManager.INSTANCE.banBlockEntity(BlockEntityType.END_GATEWAY);
    }

    private static void banUnbreakable(Block block) {
        HandlerManager.INSTANCE.banBlock(block);
    }
}
