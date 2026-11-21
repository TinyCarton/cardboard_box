package com.tinycarton.cardboardbox.box;

import com.tinycarton.cardboardbox.CardboardBox;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/**
 * 纸箱方块实体：只存两样东西。
 * <ul>
 *     <li>{@link #placementState}：被装走的方块状态（含朝向、朝向属性等）</li>
 *     <li>{@link #placementData}：被装走的方块实体 NBT（箱子里的物品、机器设置等）</li>
 * </ul>
 * 存档里的 NBT 键与反编译结果完全一致：{@code storedTile}（int，Block.getId）与 {@code tileData}（CompoundTag）。
 * <p>
 * 这个 BE 没有任何 ticking 逻辑，也从不做网络同步：数据只在服务端进出纸箱时使用。
 */
public class BoxBlockEntity extends BlockEntity {
    private BlockState placementState;
    private CompoundTag placementData;

    public BoxBlockEntity(BlockPos pos, BlockState state) {
        super(CardboardBox.BOX_BLOCK_ENTITY_TYPE, pos, state);
    }

    @Override
    public void load(CompoundTag tag) {
        super.load(tag);
        if (tag.contains("storedTile")) {
            setStateForPlacement(Block.stateById(tag.getInt("storedTile")));
            if (tag.contains("tileData")) {
                setDataForPlacement(tag.getCompound("tileData"));
            }
        }
    }

    @Override
    public void saveAdditional(CompoundTag tag) {
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

    /** @return 被装走的方块实体 NBT；可能为 null（方块没有 BE 或没数据） */
    public CompoundTag getDataForPlacement() {
        return placementData;
    }

    public void setDataForPlacement(CompoundTag placementData) {
        this.placementData = placementData;
        setChanged();
    }
}
