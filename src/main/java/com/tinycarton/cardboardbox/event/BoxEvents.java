package com.tinycarton.cardboardbox.event;

import com.tinycarton.cardboardbox.CardboardBox;
import com.tinycarton.cardboardbox.box.BoxBlock;
import com.tinycarton.cardboardbox.box.BoxBlockItem;

import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.event.entity.player.ItemTooltipEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * 交互与 tooltip 的跨版本入口。
 * <p>
 * 1.20.1 ~ 1.20.4 可以靠重写 {@code Block#use} / {@code Item#appendHoverText} 实现，
 * 但 1.20.5 起前者被 {@code useItemOn(ItemStack, ...)} 取代、后者参数换成了
 * {@code Item.TooltipContext}，签名不同就无法用同一份字节码覆盖。
 * <p>
 * 因此统一改走 Forge 事件：事件在 1.20 ~ 1.20.6 都存在，跨版本行为一致。
 */
@Mod.EventBusSubscriber(modid = CardboardBox.DOMAIN, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class BoxEvents {

    private BoxEvents() {
    }

    /** 右键：握空箱装方块 / 右键纸箱取箱 */
    @SubscribeEvent
    public static void onRightClickBlock(PlayerInteractEvent.RightClickBlock event) {
        Level level = event.getLevel();
        if (level.isClientSide) {
            return;
        }
        BlockPos pos = event.getPos();
        BlockState state = level.getBlockState(pos);

        // 目标是纸箱：走取箱 / 还原
        if (state.getBlock() instanceof BoxBlock box
                && level.getBlockEntity(pos) instanceof com.tinycarton.cardboardbox.box.BoxBlockEntity) {
            if (box.useBox(level, pos, event.getEntity())) {
                event.setCanceled(true);
            }
            return;
        }

        // 手持空纸箱：尝试把目标方块装进去
        ItemStack held = event.getItemStack();
        if (held.getItem() instanceof BoxBlockItem item) {
            if (item.getStoredBlock(held).getBlock() == Blocks.AIR) {
                InteractionResult result = item.tryToPickupBlock(event.getEntity(), level, pos, event.getHand());
                if (result == InteractionResult.SUCCESS) {
                    event.setCanceled(true);
                }
            }
        }
    }

    /** 左键敲纸箱：把存的方块放回去 */
    @SubscribeEvent
    public static void onLeftClickBlock(PlayerInteractEvent.LeftClickBlock event) {
        Level level = event.getLevel();
        if (level.isClientSide) {
            return;
        }
        BlockPos pos = event.getPos();
        if (level.getBlockState(pos).getBlock() instanceof BoxBlock box
                && level.getBlockEntity(pos) instanceof com.tinycarton.cardboardbox.box.BoxBlockEntity) {
            box.restoreStoredBlock(level, pos, event.getEntity());
        }
    }

    /** tooltip：显示箱子里的方块 */
    @SubscribeEvent
    public static void onItemTooltip(ItemTooltipEvent event) {
        ItemStack stack = event.getItemStack();
        if (stack.getItem() instanceof BoxBlockItem item) {
            BlockState stored = item.getStoredBlock(stack);
            if (stored.getBlock() != Blocks.AIR) {
                event.getToolTip().add(Component.translatable(stored.getBlock().getDescriptionId()));
            }
        }
    }
}
