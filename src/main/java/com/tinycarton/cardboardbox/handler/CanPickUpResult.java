package com.tinycarton.cardboardbox.handler;

/**
 * 装方块前的检查结果，决定给玩家哪条提示。
 * <p>
 * 判定顺序：黑名单（启用时）→ 白名单（启用时）→ 可以装。
 * 黑名单优先级高于白名单：同时命中的方块按黑名单处理。
 * 没有方块实体的普通方块同样可以装，只是不保存额外数据。
 */
public enum CanPickUpResult {
    /** 可以装进纸箱 */
    CAN_PICK_UP,
    /** 方块在黑名单里 */
    BANNED_BLOCK,
    /** 方块实体类型在黑名单里（方块本身允许，但它带的数据不安全） */
    BANNED_BLOCK_ENTITY,
    /** 启用了白名单，但该方块不在白名单内 */
    NOT_WHITELISTED
}
