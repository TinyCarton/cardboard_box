package com.tinycarton.cardboardbox.box;

import com.tinycarton.cardboardbox.CardboardBox;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

/**
 * 纸箱方块实体：只存两样东西。
 * <ul>
 *     <li>{@link #placementState}：被装走的方块状态（含朝向等属性）</li>
 *     <li>{@link #placementData}：被装走的方块实体 NBT</li>
 * </ul>
 * 存档键：{@code storedTile}（int，{@link Block#getId}）与 {@code tileData}（CompoundTag）。
 * <p>
 * 26.1.2 的方块实体读写走 {@link ValueInput} / {@link ValueOutput}，
 * 不再直接操作 {@link CompoundTag}。
 */
public class BoxBlockEntity extends BlockEntity {
    private BlockState placementState;
    private CompoundTag placementData;

    public BoxBlockEntity(BlockPos pos, BlockState state) {
        super(CardboardBox.BOX_BLOCK_ENTITY.get(), pos, state);
    }

    @Override
    protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        input.getInt("storedTile").ifPresent(id -> setStateForPlacement(Block.stateById(id)));
        input.read("tileData", CompoundTag.CODEC).ifPresent(this::setDataForPlacement);
    }

    @Override
    protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        if (getStateForPlacement() != null) {
            output.putInt("storedTile", Block.getId(placementState));
            if (getDataForPlacement() != null) {
                output.store("tileData", CompoundTag.CODEC, getDataForPlacement());
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
