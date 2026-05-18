package com.modmake.createpocketfactory.block;

import java.util.List;

import javax.annotation.Nullable;

import com.modmake.createpocketfactory.block.entity.LinkedClutchBindingHelper;
import com.modmake.createpocketfactory.block.entity.LinkedClutchBlockEntity;
import com.modmake.createpocketfactory.block.entity.LinkedClutchEndpoint;
import com.modmake.createpocketfactory.block.entity.ModBlockEntities;
import com.modmake.createpocketfactory.world.PocketFactorySavedData;
import com.simibubi.create.AllBlocks;
import com.simibubi.create.content.kinetics.transmission.ClutchBlock;
import com.simibubi.create.content.kinetics.transmission.SplitShaftBlockEntity;

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

public class LinkedClutchBlock extends ClutchBlock {
    public LinkedClutchBlock(BlockBehaviour.Properties properties) {
        super(properties);
    }

    @SuppressWarnings("unchecked")
    @Override
    public Class<SplitShaftBlockEntity> getBlockEntityClass() {
        return (Class<SplitShaftBlockEntity>) (Class<?>) LinkedClutchBlockEntity.class;
    }

    @Override
    public BlockEntityType<? extends LinkedClutchBlockEntity> getBlockEntityType() {
        return ModBlockEntities.LINKED_CLUTCH.get();
    }

    @Override
    public void setPlacedBy(Level level, BlockPos pos, BlockState state, @Nullable LivingEntity placer, ItemStack stack) {
        super.setPlacedBy(level, pos, state, placer, stack);
        if (!level.isClientSide && !(level.getBlockEntity(pos) instanceof LinkedClutchEndpoint linkedClutch && linkedClutch.hasBinding())) {
            level.setBlock(pos, LinkedClutchBindingHelper.toVanillaState(state), Block.UPDATE_ALL_IMMEDIATE);
        }
    }

    @Override
    public void onRemove(BlockState state, Level world, BlockPos pos, BlockState newState, boolean isMoving) {
        if (state.getBlock() != newState.getBlock() && !world.isClientSide && world.getServer() != null
            && !LinkedClutchBindingHelper.isLinkedClutch(newState)
            && world.getBlockEntity(pos) instanceof LinkedClutchEndpoint linkedClutch
            && linkedClutch.hasBinding()) {
            PocketFactorySavedData savedData = PocketFactorySavedData.get(world.getServer());
            PocketFactorySavedData.BindingEndpoints endpoints = savedData.getBindingEndpoints(
                linkedClutch.getBindingId(),
                PocketFactorySavedData.BindingChannel.LINKED_CLUTCH);
            savedData.disposeBinding(linkedClutch.getBindingId(), PocketFactorySavedData.BindingChannel.LINKED_CLUTCH);
            LinkedClutchBindingHelper.restoreOppositeEndpoint(world, pos, endpoints);
        }
        super.onRemove(state, world, pos, newState, isMoving);
    }

    @Override
    protected List<ItemStack> getDrops(BlockState state, net.minecraft.world.level.storage.loot.LootParams.Builder params) {
        return List.of(AllBlocks.CLUTCH.asStack());
    }

    @Override
    public ItemStack getCloneItemStack(BlockState state, HitResult target, LevelReader level, BlockPos pos, Player player) {
        return com.modmake.createpocketfactory.item.ModEnchantments.applyLinkedEnchantment(
                new ItemStack(asItem()),
                player.level().registryAccess()
        );
    }
}