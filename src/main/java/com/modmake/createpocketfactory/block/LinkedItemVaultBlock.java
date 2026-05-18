package com.modmake.createpocketfactory.block;

import com.modmake.createpocketfactory.block.entity.LinkedItemVaultBlockEntity;
import com.modmake.createpocketfactory.block.entity.ModBlockEntities;
import com.modmake.createpocketfactory.world.LinkedStorageManualBindingHelper;
import com.simibubi.create.AllBlocks;
import com.simibubi.create.content.logistics.vault.ItemVaultBlock;
import com.simibubi.create.content.logistics.vault.ItemVaultBlockEntity;
import java.util.List;
import org.jetbrains.annotations.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction.Axis;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.phys.HitResult;

public class LinkedItemVaultBlock extends ItemVaultBlock {
    public LinkedItemVaultBlock(Properties properties) {
        super(properties);
    }

    public static boolean isVault(BlockState state) {
        return state.getBlock() instanceof ItemVaultBlock && state.hasProperty(HORIZONTAL_AXIS);
    }

    @Nullable
    public static Axis getVaultAxis(BlockState state) {
        if (!isVault(state)) {
            return null;
        }
        return state.getValue(HORIZONTAL_AXIS);
    }

    public static boolean isLarge(BlockState state) {
        return isVault(state) && state.hasProperty(LARGE) && state.getValue(LARGE);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        if (context.getPlayer() == null || !context.getPlayer().isShiftKeyDown()) {
            BlockState placedOn = context.getLevel()
                    .getBlockState(context.getClickedPos().relative(context.getClickedFace().getOpposite()));
            Axis preferredAxis = getVaultAxis(placedOn);
            if (preferredAxis != null) {
                return defaultBlockState().setValue(HORIZONTAL_AXIS, preferredAxis);
            }
        }
        return defaultBlockState().setValue(HORIZONTAL_AXIS, context.getHorizontalDirection().getAxis());
    }

    @Override
    public BlockEntityType<? extends ItemVaultBlockEntity> getBlockEntityType() {
        return ModBlockEntities.LINKED_ITEM_VAULT.get();
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

        if (!(level.getBlockEntity(pos) instanceof LinkedItemVaultBlockEntity vault)) {
            return LinkedStorageInteractionHelper.finishSneakWrench(state, context, serverLevel, player);
        }

        LinkedItemVaultBlockEntity boundController = resolveBoundController(vault);
        if (boundController == null) {
            return LinkedStorageInteractionHelper.finishSneakWrench(state, context, serverLevel, player);
        }

        return LinkedStorageInteractionHelper.finishSneakWrench(state, context, serverLevel, player, () -> {
            LinkedStorageManualBindingHelper.markPendingTriggerRemoval(level, pos);
            LinkedStorageManualBindingHelper.collapseItemVaultFromPlayer(boundController, pos);
        });
    }

    @Override
    public boolean onDestroyedByPlayer(BlockState state, Level level, BlockPos pos, Player player, boolean willHarvest,
                                       FluidState fluid) {
        if (!level.isClientSide() && level.getBlockEntity(pos) instanceof LinkedItemVaultBlockEntity vault) {
            LinkedItemVaultBlockEntity boundController = resolveBoundController(vault);
            if (boundController != null) {
                LinkedStorageManualBindingHelper.markPendingTriggerRemoval(level, pos);
                LinkedStorageManualBindingHelper.collapseItemVaultFromPlayer(boundController, pos);
                level.destroyBlock(pos, !player.isCreative());
                return false;
            }
        }
        return super.onDestroyedByPlayer(state, level, pos, player, willHarvest, fluid);
    }

    @Override
    public void onRemove(BlockState state, Level world, BlockPos pos, BlockState newState, boolean pIsMoving) {
        if (state.getBlock() == newState.getBlock()) {
            super.onRemove(state, world, pos, newState, pIsMoving);
            return;
        }

        if (LinkedStorageManualBindingHelper.consumePendingTriggerRemoval(world, pos)) {
            super.onRemove(state, world, pos, newState, pIsMoving);
            return;
        }

        if (LinkedStorageManualBindingHelper.isStructureCollapsing(world, pos)) {
            super.onRemove(state, world, pos, newState, pIsMoving);
            return;
        }

        if (world.getBlockEntity(pos) instanceof LinkedItemVaultBlockEntity vault) {
            LinkedItemVaultBlockEntity boundController = resolveBoundController(vault);
            if (!world.isClientSide() && boundController != null) {
                LinkedStorageManualBindingHelper.unlinkItemVault(boundController, pos);
            } else {
                vault.clearSharedRegistration(pos);
            }
        }
        super.onRemove(state, world, pos, newState, pIsMoving);
    }

    @Override
    protected List<ItemStack> getDrops(BlockState state, LootParams.Builder params) {
        return List.of(new ItemStack(AllBlocks.ITEM_VAULT.get()));
    }

    @Override
    public ItemStack getCloneItemStack(BlockState state, HitResult target, LevelReader level, BlockPos pos, Player player) {
        return new ItemStack(AllBlocks.ITEM_VAULT.get());
    }

    private static LinkedItemVaultBlockEntity resolveBoundController(LinkedItemVaultBlockEntity vault) {
        if (vault.isController() && vault.hasBinding()) {
            return vault;
        }
        ItemVaultBlockEntity controllerBE = vault.getControllerBE();
        return controllerBE instanceof LinkedItemVaultBlockEntity linkedController && linkedController.hasBinding()
                ? linkedController
                : null;
    }
}