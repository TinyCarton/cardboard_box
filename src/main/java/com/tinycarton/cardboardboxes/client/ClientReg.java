package com.tinycarton.cardboardboxes.client;

import javax.annotation.Nullable;

import com.tinycarton.cardboardboxes.Cardboardboxes;
import com.tinycarton.cardboardboxes.box.BoxBlock;
import com.tinycarton.cardboardboxes.box.BoxBlockItem;

import net.minecraft.core.BlockPos;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.BlockAndTintGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RegisterColorHandlersEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.registries.RegistryObject;

/**
 * 客户端配色：16 色纸箱复用同一套灰白贴图（{@code box_side_tint.png} / {@code box_top_tint.png}），
 * 靠 tintindex 0 + DyeColor 的 MapColor 染色，避免打 17 份贴图。
 * <p>
 * 只有 {@code value = Dist.CLIENT} 时才注册，服务端不会加载这个类里的 GL 相关逻辑。
 */
@Mod.EventBusSubscriber(bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT, modid = Cardboardboxes.DOMAIN)
public class ClientReg {
    @SubscribeEvent
    public static void registerItemColor(RegisterColorHandlersEvent.Item event) {
        event.register(ClientReg::itemColor, Cardboardboxes.BOX_ITEM_COLORS.stream().map(RegistryObject::get).toArray(Item[]::new));
    }

    @SubscribeEvent
    public static void registerBlockColor(RegisterColorHandlersEvent.Block event) {
        event.register(ClientReg::blockColor, Cardboardboxes.BOX_COLORS.stream().map(RegistryObject::get).toArray(Block[]::new));
    }

    private static int itemColor(ItemStack stack, int tintIndex) {
        if (stack.getItem() instanceof BoxBlockItem box && box.color != null) {
            return box.color.getMapColor().col;
        }
        // -1 = 不染色，普通纸箱走原贴图
        return -1;
    }

    private static int blockColor(BlockState state, @Nullable BlockAndTintGetter level, @Nullable BlockPos pos, int tintIndex) {
        if (state.getBlock() instanceof BoxBlock box && box.color != null) {
            return box.color.getMapColor().col;
        }
        return -1;
    }
}
