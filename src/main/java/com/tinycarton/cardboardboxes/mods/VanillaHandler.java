package com.tinycarton.cardboardboxes.mods;

import com.tinycarton.cardboardboxes.handler.Handler;
import com.tinycarton.cardboardboxes.handler.HandlerManager;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.common.ForgeConfigSpec;

/**
 * 原版 Minecraft 的纸箱适配。
 * <p>
 * 做两件事：
 * <ol>
 *     <li>补一个 {@link Handler}：箱子被纸箱放回去以后朝向会翻，这里改成朝玩家</li>
 *     <li>把一大堆"搬了会崩 / 会刷物品 / 会破坏世界"的原版方块与方块实体加进黑名单</li>
 * </ol>
 */
public class VanillaHandler extends ModHandler {
    private ForgeConfigSpec.BooleanValue spawnerVal;

    @Override
    public void build(ForgeConfigSpec.Builder b) {
        spawnerVal = b.comment("Prevents mobs spawners from being placed into cardboard boxes").define("BlackListSettings.BlackListMobSpawners", true);
    }

    @Override
    public void load(ForgeConfigSpec configuration) {
        if (spawnerVal.get()) {
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

        // ===== 会崩 / 会刷物品 / 有全局副作用的方块 =====
        // 信标
        HandlerManager.INSTANCE.banBlock(Blocks.BEACON);
        HandlerManager.INSTANCE.banBlockEntity(BlockEntityType.BEACON);
        // 活塞家族：活塞头部/移动中的活塞属于临时方块，搬走会留下损坏状态
        HandlerManager.INSTANCE.banBlock(Blocks.PISTON);
        HandlerManager.INSTANCE.banBlock(Blocks.MOVING_PISTON);
        HandlerManager.INSTANCE.banBlock(Blocks.PISTON_HEAD);
        HandlerManager.INSTANCE.banBlock(Blocks.STICKY_PISTON);
        HandlerManager.INSTANCE.banBlockEntity(BlockEntityType.PISTON);
        // 阳光探测器 / 末影箱 / 比较器 / 命令方块
        HandlerManager.INSTANCE.banBlock(Blocks.DAYLIGHT_DETECTOR);
        HandlerManager.INSTANCE.banBlockEntity(BlockEntityType.DAYLIGHT_DETECTOR);
        HandlerManager.INSTANCE.banBlock(Blocks.ENDER_CHEST);
        HandlerManager.INSTANCE.banBlockEntity(BlockEntityType.ENDER_CHEST);
        HandlerManager.INSTANCE.banBlock(Blocks.COMPARATOR);
        HandlerManager.INSTANCE.banBlockEntity(BlockEntityType.COMPARATOR);
        HandlerManager.INSTANCE.banBlock(Blocks.COMMAND_BLOCK);
        HandlerManager.INSTANCE.banBlockEntity(BlockEntityType.COMMAND_BLOCK);
        // 末地传送门
        HandlerManager.INSTANCE.banBlock(Blocks.END_PORTAL);
        HandlerManager.INSTANCE.banBlock(Blocks.END_PORTAL_FRAME);
        HandlerManager.INSTANCE.banBlockEntity(BlockEntityType.END_PORTAL);
        // 音符盒 / 附魔台
        HandlerManager.INSTANCE.banBlock(Blocks.NOTE_BLOCK);
        HandlerManager.INSTANCE.banBlock(Blocks.ENCHANTING_TABLE);
        HandlerManager.INSTANCE.banBlockEntity(BlockEntityType.ENCHANTING_TABLE);
        // 告示牌（BE 里存文字，但抄写用途容易做刷物品用途 / 原版限制搬运）
        HandlerManager.INSTANCE.banBlockEntity(BlockEntityType.SIGN);
        // 头颅（含朝向变体与墙上的变体）
        HandlerManager.INSTANCE.banBlock(Blocks.SKELETON_SKULL);
        HandlerManager.INSTANCE.banBlock(Blocks.SKELETON_WALL_SKULL);
        HandlerManager.INSTANCE.banBlock(Blocks.WITHER_SKELETON_SKULL);
        HandlerManager.INSTANCE.banBlock(Blocks.WITHER_SKELETON_WALL_SKULL);
        HandlerManager.INSTANCE.banBlock(Blocks.CREEPER_HEAD);
        HandlerManager.INSTANCE.banBlock(Blocks.CREEPER_WALL_HEAD);
        HandlerManager.INSTANCE.banBlock(Blocks.DRAGON_HEAD);
        HandlerManager.INSTANCE.banBlock(Blocks.DRAGON_WALL_HEAD);
        HandlerManager.INSTANCE.banBlock(Blocks.PLAYER_HEAD);
        HandlerManager.INSTANCE.banBlock(Blocks.PLAYER_WALL_HEAD);
        HandlerManager.INSTANCE.banBlock(Blocks.ZOMBIE_HEAD);
        HandlerManager.INSTANCE.banBlock(Blocks.ZOMBIE_WALL_HEAD);
        HandlerManager.INSTANCE.banBlockEntity(BlockEntityType.SKULL);
        // 炼药锅 / 花盆（内容是方块状态，不是 BE 数据，还原容易丢）
        HandlerManager.INSTANCE.banBlock(Blocks.CAULDRON);
        HandlerManager.INSTANCE.banBlock(Blocks.FLOWER_POT);

        // 潜影盒：本身就能搬运物品，允许装进纸箱会造成"箱中箱"嵌套 → 刷物品与存档膨胀
        HandlerManager.INSTANCE.banBlock(Blocks.SHULKER_BOX);
        HandlerManager.INSTANCE.banBlock(Blocks.WHITE_SHULKER_BOX);
        HandlerManager.INSTANCE.banBlock(Blocks.ORANGE_SHULKER_BOX);
        HandlerManager.INSTANCE.banBlock(Blocks.MAGENTA_SHULKER_BOX);
        HandlerManager.INSTANCE.banBlock(Blocks.LIGHT_BLUE_SHULKER_BOX);
        HandlerManager.INSTANCE.banBlock(Blocks.YELLOW_SHULKER_BOX);
        HandlerManager.INSTANCE.banBlock(Blocks.LIME_SHULKER_BOX);
        HandlerManager.INSTANCE.banBlock(Blocks.LIGHT_GRAY_SHULKER_BOX);
        HandlerManager.INSTANCE.banBlock(Blocks.PINK_SHULKER_BOX);
        HandlerManager.INSTANCE.banBlock(Blocks.GRAY_SHULKER_BOX);
        HandlerManager.INSTANCE.banBlock(Blocks.CYAN_SHULKER_BOX);
        HandlerManager.INSTANCE.banBlock(Blocks.PURPLE_SHULKER_BOX);
        HandlerManager.INSTANCE.banBlock(Blocks.BLUE_SHULKER_BOX);
        HandlerManager.INSTANCE.banBlock(Blocks.BROWN_SHULKER_BOX);
        HandlerManager.INSTANCE.banBlock(Blocks.GREEN_SHULKER_BOX);
        HandlerManager.INSTANCE.banBlock(Blocks.RED_SHULKER_BOX);
        HandlerManager.INSTANCE.banBlock(Blocks.BLACK_SHULKER_BOX);
        HandlerManager.INSTANCE.banBlockEntity(BlockEntityType.SHULKER_BOX);
    }
}
