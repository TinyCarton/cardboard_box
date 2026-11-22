package com.tinycarton.cardboardbox.compat;

import java.lang.reflect.Method;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraftforge.fml.loading.FMLLoader;

/**
 * 1.20 ~ 1.20.6 的跨版本兼容层。
 * <p>
 * 用 1.20（Forge 46）编译，运行时按实际 MC 版本走不同代码路径：
 * <ul>
 *     <li>1.20 ~ 1.20.4：物品数据走 {@link ItemStack#getTag}/{@link ItemStack#setTag}，
 *         方块实体走 {@code saveWithId()} / {@code load(CompoundTag)}</li>
 *     <li>1.20.5 ~ 1.20.6：物品数据迁到 {@code DataComponents.CUSTOM_DATA}，
 *         方块实体读写需要 {@code HolderLookup.Provider}（全部反射，避免链接期报错）</li>
 * </ul>
 */
public final class Compat {
    /** 是否为 1.20.5 及以上（数据组件时代） */
    public static final boolean MODERN;

    private static Method legacyGetTag;
    private static Method legacySetTag;
    private static Method legacySaveWithId;
    private static Method legacyLoad;

    private static Object modernCustomDataType;
    private static Method modernCustomDataOf;
    private static Method modernCustomDataCopyTag;
    private static Method modernGetComponent;
    private static Method modernSetComponent;
    private static Method modernSaveWithId;
    private static Method modernLoadWithComponents;

    static {
        String mc = FMLLoader.versionInfo().mcVersion();
        MODERN = isAtLeast(mc, 1, 20, 5);

        try {
            if (MODERN) {
                Class<?> dataComponents = Class.forName("net.minecraft.core.component.DataComponents");
                modernCustomDataType = dataComponents.getField("CUSTOM_DATA").get(null);

                Class<?> customDataClass = Class.forName("net.minecraft.world.item.component.CustomData");
                modernCustomDataOf = customDataClass.getMethod("of", CompoundTag.class);
                modernCustomDataCopyTag = customDataClass.getMethod("copyTag");

                Class<?> componentType = Class.forName("net.minecraft.core.component.DataComponentType");
                modernGetComponent = ItemStack.class.getMethod("get", componentType);
                modernSetComponent = ItemStack.class.getMethod("set", componentType, Object.class);

                modernSaveWithId = BlockEntity.class.getMethod("saveWithId", Class.forName("net.minecraft.core.HolderLookup$Provider"));
                modernLoadWithComponents = BlockEntity.class.getMethod("loadWithComponents", CompoundTag.class,
                        Class.forName("net.minecraft.core.HolderLookup$Provider"));
            } else {
                legacyGetTag = ItemStack.class.getMethod("getTag");
                legacySetTag = ItemStack.class.getMethod("setTag", CompoundTag.class);
                legacySaveWithId = BlockEntity.class.getMethod("saveWithId");
                legacyLoad = BlockEntity.class.getMethod("load", CompoundTag.class);
            }
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("cardboardbox: 兼容层初始化失败（MC " + mc + "）", e);
        }
    }

    private Compat() {
    }

    // ===== 物品数据 =====

    /** 读取物品上保存的 CompoundTag；没有则返回 null */
    @SuppressWarnings("deprecation")
    public static CompoundTag getTag(ItemStack stack) {
        if (MODERN) {
            try {
                Object customData = modernGetComponent.invoke(stack, modernCustomDataType);
                return customData == null ? null : (CompoundTag) modernCustomDataCopyTag.invoke(customData);
            } catch (ReflectiveOperationException e) {
                return null;
            }
        }
        return stack.getTag();
    }

    /** 写入物品数据；tag 为 null 时清除 */
    @SuppressWarnings("deprecation")
    public static void setTag(ItemStack stack, CompoundTag tag) {
        if (MODERN) {
            try {
                if (tag == null) {
                    modernSetComponent.invoke(stack, modernCustomDataType, null);
                } else {
                    modernSetComponent.invoke(stack, modernCustomDataType, modernCustomDataOf.invoke(null, tag));
                }
            } catch (ReflectiveOperationException ignored) {
            }
            return;
        }
        stack.setTag(tag);
    }

    public static boolean hasTag(ItemStack stack) {
        return getTag(stack) != null;
    }

    // ===== 方块实体数据 =====

    /** 备份方块实体数据（装方块时用） */
    public static CompoundTag saveBlockEntity(BlockEntity blockEntity, Object registryAccess) {
        try {
            if (MODERN) {
                return (CompoundTag) modernSaveWithId.invoke(blockEntity, registryAccess);
            }
            return (CompoundTag) legacySaveWithId.invoke(blockEntity);
        } catch (ReflectiveOperationException e) {
            return null;
        }
    }

    /** 把备份的数据写回方块实体（放方块时用） */
    public static void loadBlockEntity(BlockEntity blockEntity, CompoundTag tag, Object registryAccess) {
        try {
            if (MODERN) {
                modernLoadWithComponents.invoke(blockEntity, tag, registryAccess);
            } else {
                legacyLoad.invoke(blockEntity, tag);
            }
        } catch (ReflectiveOperationException ignored) {
        }
    }

    // ===== 版本比较 =====

    private static boolean isAtLeast(String version, int major, int minor, int patch) {
        String[] parts = version.split("\\.");
        int[] v = new int[3];
        for (int i = 0; i < 3 && i < parts.length; i++) {
            try {
                v[i] = Integer.parseInt(parts[i].trim());
            } catch (NumberFormatException ignored) {
                v[i] = 0;
            }
        }
        if (v[0] != major) {
            return v[0] > major;
        }
        if (v[1] != minor) {
            return v[1] > minor;
        }
        return v[2] >= patch;
    }
}
