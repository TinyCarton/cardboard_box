package com.tinycarton.cardboardbox.mods;

import com.tinycarton.cardboardbox.handler.HandlerManager;

import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.neoforged.neoforge.common.ModConfigSpec;

/**
 * 原版适配器：把"在生存里拆不动"的方块加进黑名单。
 * <p>
 * 黑名单默认关闭（见 {@code BlacklistSettings.EnableBlacklist}），
 * 开启后这些方块不能被装进纸箱。
 */
public class VanillaHandler extends ModHandler {

    @Override
    public void load(ModConfigSpec configuration) {
        banUnbreakable(Blocks.BEDROCK);
        banUnbreakable(Blocks.REINFORCED_DEEPSLATE);
        banUnbreakable(Blocks.COMMAND_BLOCK);
        banUnbreakable(Blocks.CHAIN_COMMAND_BLOCK);
        banUnbreakable(Blocks.REPEATING_COMMAND_BLOCK);
        banUnbreakable(Blocks.STRUCTURE_BLOCK);
        banUnbreakable(Blocks.JIGSAW);
        banUnbreakable(Blocks.BARRIER);
        banUnbreakable(Blocks.LIGHT);
        banUnbreakable(Blocks.END_PORTAL);
        banUnbreakable(Blocks.END_GATEWAY);
        banUnbreakable(Blocks.END_PORTAL_FRAME);
        banUnbreakable(Blocks.NETHER_PORTAL);
        banUnbreakable(Blocks.MOVING_PISTON);

        banBlockEntity(BlockEntityType.COMMAND_BLOCK);
        banBlockEntity(BlockEntityType.STRUCTURE_BLOCK);
        banBlockEntity(BlockEntityType.JIGSAW);
        banBlockEntity(BlockEntityType.END_GATEWAY);
    }

    private static void banUnbreakable(Block block) {
        HandlerManager.INSTANCE.banBlock(block);
    }

    private static void banBlockEntity(BlockEntityType<?> type) {
        HandlerManager.INSTANCE.banBlockEntity(type);
    }
}
