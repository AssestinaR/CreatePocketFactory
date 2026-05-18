package com.modmake.createpocketfactory.block;

import java.util.List;

import javax.annotation.Nullable;

import com.modmake.createpocketfactory.block.entity.LinkedPumpBindingHelper;
import com.modmake.createpocketfactory.block.entity.LinkedPumpEndpoint;
import com.modmake.createpocketfactory.block.entity.LinkedPumpBlockEntity;
import com.modmake.createpocketfactory.block.entity.ModBlockEntities;
import com.modmake.createpocketfactory.world.PocketFactorySavedData;
import com.simibubi.create.AllBlocks;
import com.simibubi.create.content.fluids.pump.PumpBlock;
import com.simibubi.create.content.fluids.pump.PumpBlockEntity;

import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;

public class LinkedMechanicalPumpBlock extends PumpBlock {
    public LinkedMechanicalPumpBlock(BlockBehaviour.Properties properties) {
        super(properties);
    }

    @SuppressWarnings("unchecked")
    @Override
    public Class<PumpBlockEntity> getBlockEntityClass() {
        return (Class<PumpBlockEntity>) (Class<?>) LinkedPumpBlockEntity.class;
    }

    @Override
    public BlockEntityType<? extends LinkedPumpBlockEntity> getBlockEntityType() {
        return ModBlockEntities.LINKED_MECHANICAL_PUMP.get();
    }

    @Override
    public void setPlacedBy(Level level, BlockPos pos, BlockState state, @Nullable LivingEntity placer, ItemStack stack) {
        super.setPlacedBy(level, pos, state, placer, stack);
        if (!level.isClientSide && !(level.getBlockEntity(pos) instanceof LinkedPumpEndpoint linkedPump && linkedPump.hasBinding())) {
            level.setBlock(pos, LinkedPumpBindingHelper.toVanillaState(state), Block.UPDATE_ALL_IMMEDIATE);
        }
    }

    @Override
    public void onRemove(BlockState state, Level world, BlockPos pos, BlockState newState, boolean isMoving) {
        if (state.getBlock() != newState.getBlock() && !world.isClientSide && world.getServer() != null
                && !LinkedPumpBindingHelper.isLinkedPump(newState)
                && world.getBlockEntity(pos) instanceof LinkedPumpEndpoint linkedPump
                && linkedPump.hasBinding()) {
            PocketFactorySavedData savedData = PocketFactorySavedData.get(world.getServer());
            PocketFactorySavedData.BindingEndpoints endpoints = savedData.getBindingEndpoints(
                    linkedPump.getBindingId(),
                    PocketFactorySavedData.BindingChannel.LINKED_PUMP);
            savedData.disposeBinding(linkedPump.getBindingId(), PocketFactorySavedData.BindingChannel.LINKED_PUMP);
            LinkedPumpBindingHelper.restoreOppositeEndpoint(world, pos, endpoints);
        }
        super.onRemove(state, world, pos, newState, isMoving);
    }

    @Override
    protected List<ItemStack> getDrops(BlockState state, net.minecraft.world.level.storage.loot.LootParams.Builder params) {
        return List.of(AllBlocks.MECHANICAL_PUMP.asStack());
    }

    @Override
    public ItemStack getCloneItemStack(BlockState state, HitResult target, LevelReader level, BlockPos pos, Player player) {
        return com.modmake.createpocketfactory.item.ModEnchantments.applyLinkedEnchantment(
                new ItemStack(asItem()),
                player.level().registryAccess()
        );
    }
}