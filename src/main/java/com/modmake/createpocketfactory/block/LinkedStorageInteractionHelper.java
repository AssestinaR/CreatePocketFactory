package com.modmake.createpocketfactory.block;

import com.simibubi.create.content.equipment.wrench.IWrenchable;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.level.BlockEvent;
import org.jetbrains.annotations.Nullable;

public final class LinkedStorageInteractionHelper {
    private LinkedStorageInteractionHelper() {
    }

    public static boolean fireBreakEvent(Level level, BlockPos pos, @Nullable Player player) {
        BlockEvent.BreakEvent event = new BlockEvent.BreakEvent(level, pos, level.getBlockState(pos), player);
        NeoForge.EVENT_BUS.post(event);
        return !event.isCanceled();
    }

    public static InteractionResult finishSneakWrench(BlockState state, UseOnContext context, ServerLevel serverLevel,
                                                      @Nullable Player player) {
        return finishSneakWrench(state, context, serverLevel, player, () -> {
        });
    }

    public static InteractionResult finishSneakWrench(BlockState state, UseOnContext context, ServerLevel serverLevel,
                                                      @Nullable Player player, Runnable beforeDestroy) {
        Level level = context.getLevel();
        BlockPos pos = context.getClickedPos();
        if (player != null && !player.isCreative()) {
            Block.getDrops(state, serverLevel, pos, level.getBlockEntity(pos), player, context.getItemInHand())
                    .forEach(itemStack -> player.getInventory().placeItemBackInInventory(itemStack));
        }
        state.spawnAfterBreak(serverLevel, pos, ItemStack.EMPTY, true);
        beforeDestroy.run();
        level.destroyBlock(pos, false);
        IWrenchable.playRemoveSound(level, pos);
        return InteractionResult.SUCCESS;
    }
}
