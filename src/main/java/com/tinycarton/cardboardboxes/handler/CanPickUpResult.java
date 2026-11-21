package com.tinycarton.cardboardboxes.handler;

/**
 * 装方块前的检查结果，决定给玩家哪条提示。
 * <p>
 * 顺序对应 {@link HandlerManager#canPickUp} 的判断链：
 * 方块被禁 → 方块实体被禁 → 方块实体没有数据 → 可以装。
 */
public enum CanPickUpResult {
    /** 可以装进纸箱 */
    CAN_PICK_UP,
    /** 方块在黑名单里 */
    BANNED_BLOCK,
    /** 方块实体类型在黑名单里 */
    BANNED_BLOCK_ENTITY,
    /** 方块没有方块实体（没有可保存的数据） */
    NO_BLOCK_ENTITY,
    /** 方块实体存在但没有任何 NBT 数据，装了也没意义 */
    NO_DATA;
}
