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
 *     <li>{@code storedTile}：被装走的方块状态 id</li>
 *     <li>{@code tileData}：被装走的方块实体 NBT</li>
 * </ul>
 * <p>
 * 为兼容 1.20 ~ 1.20.6：1.20.1~1.20.4 的 {@code load/saveAdditional} 签名在 1.20.5 变成
 * {@code loadAdditional(CompoundTag, HolderLookup.Provider)}，同一份字节码覆盖不到，
 * 所以数据统一写进 Forge 的 persistent data（{@link BlockEntity#getPersistentData()}），
 * 1.20.1 的 load/save 只是顺手同步一份。
 */
public class BoxBlockEntity extends BlockEntity {
    private static final String STORED_TILE = "storedTile";
    private static final String TILE_DATA = "tileData";

    private BlockState placementState;
    private CompoundTag placementData;

    public BoxBlockEntity(BlockPos pos, BlockState state) {
        super(CardboardBox.BOX_BLOCK_ENTITY_TYPE.get(), pos, state);
    }

    // 1.20.1 的 BlockEntity#load 是 final，没法覆盖；数据统一走 persistent data
    private void readFrom(CompoundTag tag) {
        if (tag.contains(STORED_TILE)) {
            setStateForPlacement(Block.stateById(tag.getInt(STORED_TILE)));
            if (tag.contains(TILE_DATA)) {
                setDataForPlacement(tag.getCompound(TILE_DATA));
            }
        }
    }

    private void writeTo(CompoundTag tag) {
        if (placementState != null) {
            tag.putInt(STORED_TILE, Block.getId(placementState));
            if (placementData != null) {
                tag.put(TILE_DATA, placementData);
            }
        }
    }

    // ===== 跨版本读写（persistent data）=====

    /** @return 被装走的方块状态；空纸箱返回 null */
    public BlockState getStateForPlacement() {
        if (placementState == null) {
            CompoundTag pdc = getPersistentData();
            if (pdc.contains(STORED_TILE)) {
                readFrom(pdc);
            }
        }
        return placementState;
    }

    public void setStateForPlacement(BlockState state) {
        this.placementState = state;
        writeTo(getPersistentData());
        setChanged();
    }

    /** @return 被装走的方块实体 NBT；可能为 null */
    public CompoundTag getDataForPlacement() {
        return placementData;
    }

    public void setDataForPlacement(CompoundTag placementData) {
        this.placementData = placementData;
        writeTo(getPersistentData());
        setChanged();
    }
}
