package com.tinycarton.cardboardbox.box;

import com.tinycarton.cardboardbox.CardboardBox;

import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/**
 * 纸箱方块实体：只存两样东西。
 * <ul>
 *     <li>{@link #placementState}：被装走的方块状态（含朝向等属性）</li>
 *     <li>{@link #placementData}：被装走的方块实体 NBT</li>
 * </ul>
 * 存档键：{@code storedTile}（int，Block.getId）与 {@code tileData}（CompoundTag）。
 * <p>
 * 1.21.1 的方块实体读写统一走 {@code loadAdditional} / {@code saveAdditional}，
 * 额外带一个 {@link HolderLookup.Provider} 用于组件解析。
 */
public class BoxBlockEntity extends BlockEntity {
    private BlockState placementState;
    private CompoundTag placementData;

    public BoxBlockEntity(BlockPos pos, BlockState state) {
        super(CardboardBox.BOX_BLOCK_ENTITY_TYPE, pos, state);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        if (tag.contains("storedTile")) {
            setStateForPlacement(Block.stateById(tag.getInt("storedTile")));
            if (tag.contains("tileData")) {
                setDataForPlacement(tag.getCompound("tileData"));
            }
        }
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        if (getStateForPlacement() != null) {
            tag.putInt("storedTile", Block.getId(placementState));
            if (getDataForPlacement() != null) {
                tag.put("tileData", getDataForPlacement());
            }
        }
    }

    /** @return 被装走的方块状态；空纸箱返回 null */
    public BlockState getStateForPlacement() {
        return placementState;
    }

    public void setStateForPlacement(BlockState state) {
        this.placementState = state;
        setChanged();
    }

    /** @return 被装走的方块实体 NBT；可能为 null */
    public CompoundTag getDataForPlacement() {
        return placementData;
    }

    public void setDataForPlacement(CompoundTag placementData) {
        this.placementData = placementData;
        setChanged();
    }
}
