package com.assestinar.createpocketfactory.block;

import com.assestinar.createpocketfactory.block.entity.LinkedFluidTankBlockEntity;
import com.assestinar.createpocketfactory.block.entity.ModBlockEntities;
import com.assestinar.createpocketfactory.world.LinkedStorageManualBindingHelper;
import com.simibubi.create.AllBlocks;
import com.simibubi.create.content.fluids.tank.FluidTankBlock;
import com.simibubi.create.content.fluids.tank.FluidTankBlockEntity;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.phys.HitResult;

public class LinkedFluidTankBlock extends FluidTankBlock {
    public LinkedFluidTankBlock(Properties properties) {
        super(properties, false);
    }

    @Override
    public BlockEntityType<? extends FluidTankBlockEntity> getBlockEntityType() {
        return ModBlockEntities.LINKED_FLUID_TANK.get();
    }

    @Override
    public InteractionResult onSneakWrenched(BlockState state, UseOnContext context) {
        Level level = context.getLevel();
        BlockPos pos = context.getClickedPos();
        Player player = context.getPlayer();

        if (!(level instanceof ServerLevel serverLevel)) {
            return InteractionResult.SUCCESS;
        }

        if (!LinkedStorageInteractionHelper.fireBreakEvent(level, pos, player)) {
            return InteractionResult.SUCCESS;
        }

        if (!(level.getBlockEntity(pos) instanceof LinkedFluidTankBlockEntity tank)) {
            return LinkedStorageInteractionHelper.finishSneakWrench(state, context, serverLevel, player);
        }

        LinkedFluidTankBlockEntity boundController = resolveBoundController(tank);
        if (boundController == null) {
            return LinkedStorageInteractionHelper.finishSneakWrench(state, context, serverLevel, player);
        }

        return LinkedStorageInteractionHelper.finishSneakWrench(state, context, serverLevel, player, () -> {
            LinkedStorageManualBindingHelper.markPendingTriggerRemoval(level, pos);
            LinkedStorageManualBindingHelper.collapseFluidTankFromPlayer(boundController, pos);
        });
    }

    @Override
    public boolean onDestroyedByPlayer(BlockState state, Level level, BlockPos pos, Player player, boolean willHarvest,
                                       FluidState fluid) {
        if (!level.isClientSide() && level.getBlockEntity(pos) instanceof LinkedFluidTankBlockEntity tank) {
            LinkedFluidTankBlockEntity boundController = resolveBoundController(tank);
            if (boundController != null) {
                LinkedStorageManualBindingHelper.markPendingTriggerRemoval(level, pos);
                LinkedStorageManualBindingHelper.collapseFluidTankFromPlayer(boundController, pos);
                level.destroyBlock(pos, !player.isCreative());
                return false;
            }
        }
        return super.onDestroyedByPlayer(state, level, pos, player, willHarvest, fluid);
    }

    @Override
    public void onRemove(BlockState state, Level world, BlockPos pos, BlockState newState, boolean isMoving) {
        if (state.getBlock() == newState.getBlock()) {
            super.onRemove(state, world, pos, newState, isMoving);
            return;
        }

        if (LinkedStorageManualBindingHelper.consumePendingTriggerRemoval(world, pos)) {
            super.onRemove(state, world, pos, newState, isMoving);
            return;
        }

        if (LinkedStorageManualBindingHelper.isStructureCollapsing(world, pos)) {
            super.onRemove(state, world, pos, newState, isMoving);
            return;
        }

        if (world.getBlockEntity(pos) instanceof LinkedFluidTankBlockEntity tank) {
            LinkedFluidTankBlockEntity boundController = resolveBoundController(tank);
            if (!world.isClientSide() && boundController != null) {
                LinkedStorageManualBindingHelper.unlinkFluidTank(boundController, pos);
            } else {
                tank.clearSharedRegistration();
            }
        }
        super.onRemove(state, world, pos, newState, isMoving);
    }

    @Override
    protected List<ItemStack> getDrops(BlockState state, LootParams.Builder params) {
        return List.of(new ItemStack(AllBlocks.FLUID_TANK.get()));
    }

    @Override
    public ItemStack getCloneItemStack(BlockState state, HitResult target, LevelReader level, BlockPos pos, Player player) {
        return com.assestinar.createpocketfactory.item.ModEnchantments.applyLinkedEnchantment(
                new ItemStack(asItem()),
                player.level().registryAccess()
        );
    }

    private static LinkedFluidTankBlockEntity resolveBoundController(LinkedFluidTankBlockEntity tank) {
        if (tank.isController() && tank.hasBinding()) {
            return tank;
        }
        FluidTankBlockEntity controllerBE = tank.getControllerBE();
        return controllerBE instanceof LinkedFluidTankBlockEntity linkedController && linkedController.hasBinding()
                ? linkedController
                : null;
    }
}