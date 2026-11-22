package com.tinycarton.cardboardbox.handler;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/**
 * 单个方块（或方块实体）的搬运钩子。
 * <p>
 * 默认实现"什么都不特殊处理"，纸箱会用原版逻辑；需要特殊处理的 mod 方块
 * （比如多方块结构、需要重新绑定的机器）可以继承它并注册到 {@link HandlerManager}。
 * <p>
 * 四个钩子的调用时机：
 * <ul>
 *     <li>{@link #save}：目前在主流程中未被调用（留给后续扩展 / 过滤敏感 NBT）</li>
 *     <li>{@link #placeBlock}：方块放置之前，返回 true 表示"我自己放了，别再走原版放置"</li>
 *     <li>{@link #postPlaceBlock}：方块放置之后，用于修正朝向等</li>
 *     <li>{@link #loadData}：放置后把 NBT 写进新的方块实体</li>
 * </ul>
 */
public class Handler {
    /**
     * 保存阶段的过滤钩子。
     *
     * @param tag 已经由方块实体自己写好的 NBT
     * @return 处理后的 NBT，永远不要返回 null
     */
    public CompoundTag save(CompoundTag tag) {
        return tag;
    }

    /** 把纸箱里存的 NBT 写进新的方块实体（1.21.1 需要组件解析用的 Provider） */
    public void loadData(BlockEntity blockEntity, CompoundTag tag, net.minecraft.core.HolderLookup.Provider registries) {
        blockEntity.loadWithComponents(tag, registries);
    }

    /**
     * 自定义放置。
     *
     * @return true = 已处理放置，纸箱不再走原版放置；false = 交给原版 {@code BlockItem#placeBlock}
     */
    public boolean placeBlock(Player player, Level level, BlockPos pos, InteractionHand hand, Direction direction, float hitX, float hitY, float hitZ, BlockState state, CompoundTag saveData) {
        return false;
    }

    /** 放置完成后的修正钩子（例如让箱子朝向玩家） */
    public void postPlaceBlock(Player player, Level level, BlockPos pos, InteractionHand hand, Direction direction, float hitX, float hitY, float hitZ, BlockState state, CompoundTag saveData) {
    }
}
