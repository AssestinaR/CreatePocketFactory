package com.assestinar.createpocketfactory.block.entity;

import javax.annotation.Nullable;

import com.assestinar.createpocketfactory.block.ModBlocks;
import com.assestinar.createpocketfactory.world.PocketFactorySavedData;
import com.simibubi.create.AllBlocks;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;

public final class LinkedClutchBindingHelper {
    private LinkedClutchBindingHelper() {
    }

    public static boolean isBindableClutch(BlockState state) {
        return AllBlocks.CLUTCH.has(state);
    }

    public static boolean isLinkedClutch(BlockState state) {
        return state.is(ModBlocks.LINKED_CLUTCH.get());
    }

    public static @Nullable LinkedClutchEndpoint getLinkedClutch(LevelAccessor level, BlockPos pos) {
        return level.getBlockEntity(pos) instanceof LinkedClutchEndpoint linkedClutch ? linkedClutch : null;
    }

    public static BlockState toLinkedState(BlockState vanillaState) {
        if (AllBlocks.CLUTCH.has(vanillaState)) {
            return ModBlocks.LINKED_CLUTCH.get().defaultBlockState()
                .setValue(BlockStateProperties.AXIS, vanillaState.getValue(BlockStateProperties.AXIS))
                .setValue(BlockStateProperties.POWERED, vanillaState.getValue(BlockStateProperties.POWERED));
        }

        if (isLinkedClutch(vanillaState)) {
            return vanillaState;
        }

        throw new IllegalArgumentException("Unsupported clutch state: " + vanillaState);
    }

    public static BlockState toVanillaState(BlockState linkedState) {
        if (linkedState.is(ModBlocks.LINKED_CLUTCH.get())) {
            return AllBlocks.CLUTCH.getDefaultState()
                .setValue(BlockStateProperties.AXIS, linkedState.getValue(BlockStateProperties.AXIS))
                .setValue(BlockStateProperties.POWERED, linkedState.getValue(BlockStateProperties.POWERED));
        }

        if (AllBlocks.CLUTCH.has(linkedState)) {
            return linkedState;
        }

        throw new IllegalArgumentException("Unsupported linked clutch state: " + linkedState);
    }

    public static void restoreOppositeEndpoint(Level level, BlockPos removedPos,
                                               @Nullable PocketFactorySavedData.BindingEndpoints endpoints) {
        if (endpoints == null || level.getServer() == null) {
            return;
        }

        String removedEndpointKey = BindingEndpointHelper.endpointKey(level, removedPos);
        String oppositeEndpointKey = BindingEndpointHelper.resolveOppositeEndpointKey(endpoints, removedEndpointKey);
        if (oppositeEndpointKey == null) {
            return;
        }

        BindingEndpointHelper.EndpointLocation endpointLocation = BindingEndpointHelper.parseEndpointKey(oppositeEndpointKey);
        if (endpointLocation == null) {
            return;
        }

        Level targetLevel = level.getServer().getLevel(endpointLocation.dimension());
        if (targetLevel == null) {
            return;
        }

        BlockState oppositeState = targetLevel.getBlockState(endpointLocation.pos());
        if (!isLinkedClutch(oppositeState)) {
            return;
        }

        targetLevel.setBlock(endpointLocation.pos(), toVanillaState(oppositeState), Block.UPDATE_ALL_IMMEDIATE);
    }
}