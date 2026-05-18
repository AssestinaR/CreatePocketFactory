package com.modmake.createpocketfactory.block.entity;

import javax.annotation.Nullable;

import com.modmake.createpocketfactory.block.ModBlocks;
import com.modmake.createpocketfactory.world.PocketFactorySavedData;
import com.simibubi.create.AllBlocks;
import com.simibubi.create.content.fluids.pump.PumpBlock;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;

public final class LinkedPumpBindingHelper {
    private LinkedPumpBindingHelper() {
    }

    public static boolean isBindablePump(BlockState state) {
        return AllBlocks.MECHANICAL_PUMP.has(state);
    }

    public static boolean isLinkedPump(BlockState state) {
        return state.is(ModBlocks.LINKED_MECHANICAL_PUMP.get());
    }

    public static @Nullable LinkedPumpEndpoint getLinkedPump(LevelAccessor level, BlockPos pos) {
        return level.getBlockEntity(pos) instanceof LinkedPumpEndpoint linkedPump ? linkedPump : null;
    }

    public static BlockState toLinkedState(BlockState vanillaState) {
        if (AllBlocks.MECHANICAL_PUMP.has(vanillaState)) {
            BlockState target = ModBlocks.LINKED_MECHANICAL_PUMP.get().defaultBlockState()
                    .setValue(PumpBlock.FACING, vanillaState.getValue(PumpBlock.FACING));
            if (vanillaState.hasProperty(BlockStateProperties.WATERLOGGED)) {
                target = target.setValue(BlockStateProperties.WATERLOGGED, vanillaState.getValue(BlockStateProperties.WATERLOGGED));
            }
            return target;
        }

        if (isLinkedPump(vanillaState)) {
            return vanillaState;
        }

        throw new IllegalArgumentException("Unsupported pump state: " + vanillaState);
    }

    public static BlockState toVanillaState(BlockState linkedState) {
        if (linkedState.is(ModBlocks.LINKED_MECHANICAL_PUMP.get())) {
            BlockState target = AllBlocks.MECHANICAL_PUMP.getDefaultState()
                    .setValue(PumpBlock.FACING, linkedState.getValue(PumpBlock.FACING));
            if (linkedState.hasProperty(BlockStateProperties.WATERLOGGED)) {
                target = target.setValue(BlockStateProperties.WATERLOGGED, linkedState.getValue(BlockStateProperties.WATERLOGGED));
            }
            return target;
        }

        if (AllBlocks.MECHANICAL_PUMP.has(linkedState)) {
            return linkedState;
        }

        throw new IllegalArgumentException("Unsupported linked pump state: " + linkedState);
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
        if (!isLinkedPump(oppositeState)) {
            return;
        }

        targetLevel.setBlock(endpointLocation.pos(), toVanillaState(oppositeState), Block.UPDATE_ALL_IMMEDIATE);
    }
}